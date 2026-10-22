package com.hamza.hosting.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
public class Outbox {
  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper mapper;

  public Outbox(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.mapper = mapper;
  }

  public void add(BillingEvent event, String correlation) {
    try {
      raw(
          event.eventId(),
          Topics.EVENTS,
          event.invoiceId().toString(),
          mapper.writeValueAsString(event),
          correlation);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public void raw(UUID id, String topic, String key, String payload, String correlation) {
    jdbc.update(
        "INSERT INTO outbox(id,topic,event_key,payload,correlation_id) VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",
        id,
        topic,
        key,
        payload,
        correlation);
  }

  @Scheduled(fixedDelayString = "${outbox.delay-ms:300}")
  @Transactional
  public void publish() {
    var rows =
        jdbc.queryForList(
            "SELECT * FROM outbox WHERE published_at IS NULL ORDER BY created_at LIMIT 30 FOR UPDATE SKIP LOCKED");
    for (var row : rows) {
      String correlation = (String) row.get("correlation_id");
      try (var ignored = Trace.scope(correlation)) {
        var record =
            new ProducerRecord<String, String>(
                (String) row.get("topic"),
                (String) row.get("event_key"),
                (String) row.get("payload"));
        record.headers().add(Trace.HEADER, correlation.getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get(12, TimeUnit.SECONDS);
        jdbc.update("UPDATE outbox SET published_at=CURRENT_TIMESTAMP WHERE id=?", row.get("id"));
        LoggerFactory.getLogger(Outbox.class)
            .info("Published event id={} topic={}", row.get("id"), row.get("topic"));
      } catch (Exception e) {
        throw new IllegalStateException("Outbox delivery failed; transaction will retry", e);
      }
    }
  }
}
