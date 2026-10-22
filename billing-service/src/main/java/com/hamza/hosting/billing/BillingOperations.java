package com.hamza.hosting.billing;

import com.hamza.hosting.common.*;
import java.time.YearMonth;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingOperations {
  private final SubscriptionRepository subscriptions;
  private final InvoiceRepository invoices;
  private final Outbox outbox;
  private final JdbcTemplate jdbc;

  public BillingOperations(
      SubscriptionRepository subscriptions,
      InvoiceRepository invoices,
      Outbox outbox,
      JdbcTemplate jdbc) {
    this.subscriptions = subscriptions;
    this.invoices = invoices;
    this.outbox = outbox;
    this.jdbc = jdbc;
  }

  @Transactional
  public Invoice generate(UUID subscriptionId, YearMonth period) {
    var s =
        subscriptions
            .locked(subscriptionId)
            .orElseThrow(() -> new Errors.Missing("Subscription not found"));
    if (!s.active || s.startsOn.isAfter(period.atEndOfMonth()))
      throw new Errors.Conflict("Subscription not billable for period");
    var existing = invoices.findBySubscriptionIdAndPeriod(s.id, period.toString());
    if (existing.isPresent()) return existing.get();
    var invoice = invoices.save(new Invoice(s, period.toString()));
    outbox.add(
        BillingEvent.create(
            BillingEvent.Type.InvoiceCreated,
            invoice.id,
            s.customerId,
            s.amountMinor,
            s.currency,
            "Monthly hosting invoice for " + s.plan),
        Trace.current());
    LoggerFactory.getLogger(getClass())
        .info("Invoice created invoiceId={} period={}", invoice.id, period);
    return invoice;
  }

  @Transactional
  public Invoice paymentNotification(UUID id, boolean success, String reference) {
    var invoice = invoices.locked(id).orElseThrow(() -> new Errors.Missing("Invoice not found"));
    if ("PAID".equals(invoice.status)) return invoice;
    invoice.status = success ? "PAID" : "FAILED";
    outbox.add(
        BillingEvent.create(
            success ? BillingEvent.Type.PaymentReceived : BillingEvent.Type.PaymentFailed,
            id,
            invoice.customerId,
            invoice.amountMinor,
            invoice.currency,
            "Reconciliation: " + reference),
        Trace.current());
    return invoice;
  }

  @Transactional
  public void apply(BillingEvent event) {
    if (event.type() == BillingEvent.Type.InvoiceCreated) return;
    var invoice =
        invoices
            .locked(event.invoiceId())
            .orElseThrow(() -> new Errors.Missing("Event invoice not found"));
    if (!invoice.customerId.equals(event.customerId())
        || invoice.amountMinor != event.amountMinor()
        || !invoice.currency.equals(event.currency()))
      throw new IllegalArgumentException("Payment event does not match invoice");
    if (jdbc.update(
            "INSERT INTO processed_events(id) VALUES(?) ON CONFLICT DO NOTHING", event.eventId())
        == 0) return;
    if (!"PAID".equals(invoice.status)) invoice.status = event.status();
    LoggerFactory.getLogger(getClass())
        .info("Payment applied invoiceId={} status={}", invoice.id, invoice.status);
  }
}
