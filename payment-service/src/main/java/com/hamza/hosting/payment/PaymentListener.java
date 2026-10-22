package com.hamza.hosting.payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamza.hosting.common.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class PaymentListener {
    private final PaymentProcessor processor; private final ObjectMapper mapper;
    public PaymentListener(PaymentProcessor p,ObjectMapper m) {processor=p;mapper=m;}
    @KafkaListener(topics=Topics.EVENTS,groupId="payment-charger") public void receive(ConsumerRecord<String,String> record) throws Exception {
        String correlation=Trace.from(record);
        try(var ignored=Trace.scope(correlation)) {
            var event=mapper.readValue(record.value(),BillingEvent.class);
            if(event.type()==BillingEvent.Type.InvoiceCreated)processor.process(event,correlation);
        }
    }
}
