package com.hamza.hosting.billing;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamza.hosting.common.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class PaymentEvents {
    private final BillingOperations operations; private final ObjectMapper mapper;
    public PaymentEvents(BillingOperations o,ObjectMapper m) {operations=o;mapper=m;}
    @KafkaListener(topics=Topics.EVENTS,groupId="billing-projection") public void onEvent(ConsumerRecord<String,String> record) throws Exception {
        try(var ignored=Trace.scope(Trace.from(record))) {operations.apply(mapper.readValue(record.value(),BillingEvent.class));}
    }
}
