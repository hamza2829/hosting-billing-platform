package com.hamza.hosting.common;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;

public final class Trace {
    public static final String HEADER = "X-Correlation-ID";
    private Trace() {}
    public static String valid(String value) {
        return value != null && value.matches("[a-zA-Z0-9_-]{1,64}") ? value : UUID.randomUUID().toString();
    }
    public static String current() { return valid(MDC.get("correlationId")); }
    public static String from(ConsumerRecord<?, ?> record) {
        var header = record.headers().lastHeader(HEADER);
        return valid(header == null ? null : new String(header.value(), StandardCharsets.UTF_8));
    }
    public static MDC.MDCCloseable scope(String id) { return MDC.putCloseable("correlationId", valid(id)); }
}
