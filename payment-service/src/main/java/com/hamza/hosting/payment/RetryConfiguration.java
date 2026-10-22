package com.hamza.hosting.payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
@Configuration
public class RetryConfiguration {
    @Bean DefaultErrorHandler kafkaErrorHandler(PaymentProcessor processor,@Value("${payment.retry-delay-ms}") long delay) {
        var backoff=new ExponentialBackOffWithMaxRetries(2);backoff.setInitialInterval(delay);backoff.setMultiplier(2);backoff.setMaxInterval(delay*4);
        var handler=new DefaultErrorHandler(processor::exhausted,backoff);
        handler.setRetryListeners((record,error,attempt)->{
            try(var ignored=com.hamza.hosting.common.Trace.scope(com.hamza.hosting.common.Trace.from(record))) {
                org.slf4j.LoggerFactory.getLogger(RetryConfiguration.class).warn("Payment delivery failed attempt={} partition={} offset={}",attempt,record.partition(),record.offset());
            }
        });
        return handler;
    }
}
