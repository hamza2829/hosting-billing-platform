package com.hamza.hosting.payment;

import com.hamza.hosting.common.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class PaymentClient {
  public record Result(String id) {}

  public static class Declined extends RuntimeException {}

  private final RestClient client;

  public PaymentClient(@Value("${payment.provider-url}") String base) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(3));
    client = RestClient.builder().baseUrl(base).requestFactory(factory).build();
  }

  public static String key(UUID invoiceId) {
    return "invoice-" + invoiceId;
  }

  public Result charge(BillingEvent e) {
    try {
      var result =
          client
              .post()
              .uri("/charges")
              .header("Idempotency-Key", key(e.invoiceId()))
              .header(Trace.HEADER, Trace.current())
              .body(
                  Map.of(
                      "invoiceId",
                      e.invoiceId(),
                      "amountMinor",
                      e.amountMinor(),
                      "currency",
                      e.currency()))
              .retrieve()
              .body(Result.class);
      if (result == null
          || result.id() == null
          || result.id().isBlank()
          || result.id().length() > 200)
        throw new IllegalStateException("Invalid provider response");
      return result;
    } catch (HttpClientErrorException e2) {
      if (e2.getStatusCode().value() == 402) throw new Declined();
      throw e2;
    }
  }
}
