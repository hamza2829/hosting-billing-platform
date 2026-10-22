package com.hamza.hosting.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.hamza.hosting.common.*;
import java.util.UUID;
import org.junit.jupiter.api.*;

public class PaymentClientTest {
  WireMockServer wire;
  PaymentClient client;
  BillingEvent event;

  @BeforeEach
  void start() {
    wire = new WireMockServer(0);
    wire.start();
    client = new PaymentClient(wire.baseUrl());
    event =
        BillingEvent.create(
            BillingEvent.Type.InvoiceCreated,
            UUID.randomUUID(),
            UUID.randomUUID(),
            1200,
            "EUR",
            "Synthetic VPS");
  }

  @AfterEach
  void stop() {
    wire.stop();
  }

  @Test
  void sendsStableKeyAndCorrelation() {
    wire.stubFor(post("/charges").willReturn(okJson("{\"id\":\"charge-1\"}")));
    try (var scope = Trace.scope("wiremock-trace")) {
      assertEquals("charge-1", client.charge(event).id());
    }
    wire.verify(
        postRequestedFor(urlEqualTo("/charges"))
            .withHeader("Idempotency-Key", equalTo(PaymentClient.key(event.invoiceId())))
            .withHeader(Trace.HEADER, equalTo("wiremock-trace")));
  }

  @Test
  void declineIsBusinessFailure() {
    wire.stubFor(post("/charges").willReturn(aResponse().withStatus(402)));
    assertThrows(PaymentClient.Declined.class, () -> client.charge(event));
  }

  @Test
  void transientFailureCanRetryWithSameKey() {
    wire.stubFor(
        post("/charges")
            .inScenario("retry")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("ready"));
    wire.stubFor(
        post("/charges")
            .inScenario("retry")
            .whenScenarioStateIs("ready")
            .willReturn(okJson("{\"id\":\"charge-retry\"}")));
    assertThrows(
        org.springframework.web.client.HttpServerErrorException.class, () -> client.charge(event));
    assertEquals("charge-retry", client.charge(event).id());
    wire.verify(
        2,
        postRequestedFor(urlEqualTo("/charges"))
            .withHeader("Idempotency-Key", equalTo(PaymentClient.key(event.invoiceId()))));
  }

  @Test
  void rejectsInvalidProviderResponse() {
    wire.stubFor(post("/charges").willReturn(okJson("{}")));
    assertThrows(IllegalStateException.class, () -> client.charge(event));
  }
}
