package com.hamza.hosting.billing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hamza.hosting.common.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

public class BillingOperationsTest {
  SubscriptionRepository subscriptions;
  InvoiceRepository invoices;
  Outbox outbox;
  JdbcTemplate jdbc;
  BillingOperations operations;
  Subscription s;

  @BeforeEach
  void setup() {
    subscriptions = mock(SubscriptionRepository.class);
    invoices = mock(InvoiceRepository.class);
    outbox = mock(Outbox.class);
    jdbc = mock(JdbcTemplate.class);
    operations = new BillingOperations(subscriptions, invoices, outbox, jdbc);
    s = new Subscription(UUID.randomUUID(), "Synthetic VPS", 1200, "EUR", LocalDate.of(2026, 1, 1));
    when(subscriptions.locked(s.id)).thenReturn(Optional.of(s));
    when(invoices.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void createsInvoiceAndEventTogether() {
    var invoice = operations.generate(s.id, YearMonth.of(2026, 2));
    assertEquals(1200, invoice.amountMinor);
    verify(outbox)
        .add(
            argThat(
                e ->
                    e.type() == BillingEvent.Type.InvoiceCreated
                        && e.invoiceId().equals(invoice.id)),
            anyString());
  }

  @Test
  void duplicatePeriodReturnsExisting() {
    var invoice = new Invoice(s, "2026-02");
    when(invoices.findBySubscriptionIdAndPeriod(s.id, "2026-02")).thenReturn(Optional.of(invoice));
    assertEquals(invoice, operations.generate(s.id, YearMonth.of(2026, 2)));
    verifyNoInteractions(outbox);
  }

  @Test
  void rejectsInactiveSubscription() {
    s.active = false;
    assertThrows(Errors.Conflict.class, () -> operations.generate(s.id, YearMonth.of(2026, 2)));
    verifyNoInteractions(outbox);
  }

  @Test
  void rejectsPeriodBeforeStart() {
    assertThrows(Errors.Conflict.class, () -> operations.generate(s.id, YearMonth.of(2025, 12)));
  }

  @Test
  void rejectsUnknownSubscription() {
    assertThrows(
        Errors.Missing.class, () -> operations.generate(UUID.randomUUID(), YearMonth.of(2026, 2)));
  }

  @Test
  void paidInvoiceIsTerminal() {
    var invoice = new Invoice(s, "2026-02");
    invoice.status = "PAID";
    when(invoices.locked(invoice.id)).thenReturn(Optional.of(invoice));
    assertEquals("PAID", operations.paymentNotification(invoice.id, false, "late decline").status);
    verifyNoInteractions(outbox);
  }

  @Test
  void rejectsPaymentAmountMismatch() {
    var invoice = new Invoice(s, "2026-02");
    when(invoices.locked(invoice.id)).thenReturn(Optional.of(invoice));
    var event =
        BillingEvent.create(
            BillingEvent.Type.PaymentReceived, invoice.id, s.customerId, 999, "EUR", "mismatch");
    assertThrows(IllegalArgumentException.class, () -> operations.apply(event));
    verifyNoInteractions(jdbc);
  }
}
