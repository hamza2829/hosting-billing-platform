package com.hamza.hosting.common;

import java.time.Instant;
import java.util.UUID;

public record BillingEvent(
    int schemaVersion,
    UUID eventId,
    Type type,
    UUID invoiceId,
    UUID customerId,
    long amountMinor,
    String currency,
    Instant occurredAt,
    String detail) {
  public enum Type {
    InvoiceCreated,
    PaymentReceived,
    PaymentFailed
  }

  public BillingEvent {
    if (schemaVersion != 1
        || eventId == null
        || type == null
        || invoiceId == null
        || customerId == null
        || amountMinor <= 0
        || !java.util.Set.of("EUR", "USD").contains(currency)
        || occurredAt == null
        || detail == null
        || detail.length() > 1000) {
      throw new IllegalArgumentException("Invalid billing event contract");
    }
  }

  public static BillingEvent create(
      Type type, UUID invoice, UUID customer, long amount, String currency, String detail) {
    return new BillingEvent(
        1, UUID.randomUUID(), type, invoice, customer, amount, currency, Instant.now(), detail);
  }

  public String status() {
    return switch (type) {
      case InvoiceCreated -> "OPEN";
      case PaymentReceived -> "PAID";
      case PaymentFailed -> "FAILED";
    };
  }
}
