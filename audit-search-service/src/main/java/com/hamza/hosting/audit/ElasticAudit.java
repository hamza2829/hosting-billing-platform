package com.hamza.hosting.audit;

import com.fasterxml.jackson.databind.*;
import com.hamza.hosting.common.*;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class ElasticAudit implements HealthIndicator {
  private final RestClient client;
  private final ObjectMapper mapper;

  public ElasticAudit(@Value("${elasticsearch.url}") String url, ObjectMapper mapper) {
    client = RestClient.builder().baseUrl(url).build();
    this.mapper = mapper;
  }

  @PostConstruct
  void initialize() {
    Map<String, Object> properties = new LinkedHashMap<>();
    for (String name :
        List.of(
            "eventId", "invoiceId", "customerId", "type", "status", "correlationId", "currency"))
      properties.put(name, Map.of("type", "keyword"));
    properties.put("timestamp", Map.of("type", "date"));
    properties.put("detail", Map.of("type", "text"));
    properties.put("amountMinor", Map.of("type", "long"));
    try {
      client
          .put()
          .uri("/billing-events")
          .body(
              Map.of(
                  "settings",
                  Map.of("number_of_shards", 1, "number_of_replicas", 0),
                  "mappings",
                  Map.of("properties", properties)))
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException e) {
      if (!e.getResponseBodyAsString().contains("resource_already_exists_exception")) throw e;
    }
  }

  @KafkaListener(
      topics = {Topics.EVENTS, Topics.DLT},
      groupId = "audit-indexer")
  public void consume(ConsumerRecord<String, String> record) throws Exception {
    String correlation = Trace.from(record);
    try (var ignored = Trace.scope(correlation)) {
      Map<String, Object> doc = new LinkedHashMap<>();
      if (record.topic().equals(Topics.DLT)) {
        JsonNode envelope = mapper.readTree(record.value());
        doc.put("eventId", envelope.path("eventId").asText());
        doc.put("type", "DeadLetter");
        doc.put("status", "DEAD_LETTER");
        doc.put("invoiceId", envelope.path("invoiceId").asText());
        doc.put("customerId", envelope.path("customerId").asText());
        doc.put("detail", envelope.path("reason").asText() + ": " + envelope.path("raw").asText());
        doc.put("timestamp", envelope.path("occurredAt").asText());
      } else {
        try {
          var event = mapper.readValue(record.value(), BillingEvent.class);
          doc.put("eventId", event.eventId().toString());
          doc.put("type", event.type().name());
          doc.put("invoiceId", event.invoiceId().toString());
          doc.put("customerId", event.customerId().toString());
          doc.put("status", event.status());
          doc.put("amountMinor", event.amountMinor());
          doc.put("currency", event.currency());
          doc.put("timestamp", event.occurredAt().toString());
          doc.put("detail", event.detail());
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
          doc.put(
              "eventId",
              UUID.nameUUIDFromBytes(record.value().getBytes(StandardCharsets.UTF_8)).toString());
          doc.put("type", "InvalidEvent");
          doc.put("status", "INVALID");
          doc.put("timestamp", Instant.now().toString());
          doc.put("detail", "Invalid event contract");
        }
      }
      doc.put("correlationId", correlation);
      client
          .put()
          .uri("/billing-events/_doc/" + doc.get("eventId"))
          .body(doc)
          .retrieve()
          .toBodilessEntity();
      if (Set.of("FAILED", "DEAD_LETTER", "INVALID").contains(doc.get("status")))
        LoggerFactory.getLogger(getClass())
            .error(
                "Alert raised eventId={} invoiceId={} status={}",
                doc.get("eventId"),
                doc.get("invoiceId"),
                doc.get("status"));
      else
        LoggerFactory.getLogger(getClass())
            .info(
                "Audit indexed eventId={} invoiceId={}", doc.get("eventId"), doc.get("invoiceId"));
    }
  }

  public JsonNode search(Map<String, Object> query) {
    return client.post().uri("/billing-events/_search").body(query).retrieve().body(JsonNode.class);
  }

  @Override
  public Health health() {
    try {
      client.get().uri("/_cluster/health").retrieve().toBodilessEntity();
      return Health.up().build();
    } catch (Exception e) {
      return Health.down().build();
    }
  }
}
