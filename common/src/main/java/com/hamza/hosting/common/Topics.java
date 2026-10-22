package com.hamza.hosting.common;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class Topics {
    public static final String EVENTS = "billing.events";
    public static final String DLT = "billing.events.DLT";
    @Bean NewTopic billingEvents() { return TopicBuilder.name(EVENTS).partitions(3).replicas(1).build(); }
    @Bean NewTopic deadLetters() { return TopicBuilder.name(DLT).partitions(3).replicas(1).build(); }
}
