package com.hamza.hosting.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamza.hosting.common.*;
import java.util.*;
import org.junit.jupiter.api.*;

public class PaymentProcessorTest {
  PaymentRepository repo;
  PaymentClient client;
  Outbox outbox;
  PaymentProcessor processor;
  BillingEvent event;
  Payment payment;

  @BeforeEach
  void setup() {
    repo = mock(PaymentRepository.class);
    client = mock(PaymentClient.class);
    outbox = mock(Outbox.class);
    processor =
        new PaymentProcessor(repo, client, outbox, new ObjectMapper().findAndRegisterModules());
    event =
        BillingEvent.create(
            BillingEvent.Type.InvoiceCreated,
            UUID.randomUUID(),
            UUID.randomUUID(),
            1200,
            "EUR",
            "VPS");
    payment = new Payment(event);
    when(repo.locked(event.invoiceId())).thenReturn(Optional.of(payment));
  }

  @Test
  void successfulPaymentPublishesReceived() {
    when(client.charge(event)).thenReturn(new PaymentClient.Result("charge-1"));
    processor.process(event, "trace");
    assertEquals("SUCCEEDED", payment.status);
    verify(outbox).add(argThat(e -> e.type() == BillingEvent.Type.PaymentReceived), eq("trace"));
  }

  @Test
  void duplicateNeverCallsProvider() {
    payment.status = "SUCCEEDED";
    processor.process(event, "trace");
    verifyNoInteractions(client, outbox);
  }

  @Test
  void declinedPaymentPublishesFailed() {
    when(client.charge(event)).thenThrow(new PaymentClient.Declined());
    processor.process(event, "trace");
    assertEquals("FAILED", payment.status);
    verify(outbox).add(argThat(e -> e.type() == BillingEvent.Type.PaymentFailed), eq("trace"));
  }

  @Test
  void transientFailureIsRetriable() {
    when(client.charge(event)).thenThrow(new IllegalStateException("503"));
    assertThrows(IllegalStateException.class, () -> processor.process(event, "trace"));
    verifyNoInteractions(outbox);
  }

  @Test
  void duplicateChangedPayloadIsRejected() {
    payment.amountMinor = 999;
    assertThrows(IllegalArgumentException.class, () -> processor.process(event, "trace"));
    verifyNoInteractions(client, outbox);
  }
}
