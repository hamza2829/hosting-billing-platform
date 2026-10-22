package com.hamza.hosting.common;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.nio.charset.StandardCharsets;
import org.springframework.mock.web.*;
public class ContractTest {
    @Test void eventRoundTrip() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var event=BillingEvent.create(BillingEvent.Type.InvoiceCreated,UUID.randomUUID(),UUID.randomUUID(),1200,"EUR","Hosting");
        assertEquals(event,mapper.readValue(mapper.writeValueAsString(event),BillingEvent.class));
    }
    @Test void rejectsUnknownVersion() {assertThrows(IllegalArgumentException.class,()->new BillingEvent(2,UUID.randomUUID(),BillingEvent.Type.InvoiceCreated,UUID.randomUUID(),UUID.randomUUID(),1,"EUR",Instant.now(),"test"));}
    @Test void rejectsNonPositiveAmount() {assertThrows(IllegalArgumentException.class,()->BillingEvent.create(BillingEvent.Type.InvoiceCreated,UUID.randomUUID(),UUID.randomUUID(),0,"EUR","test"));}
    @Test void readsKafkaCorrelationHeader() {
        var record=new ConsumerRecord<String,String>(Topics.EVENTS,0,1,"key","value");record.headers().add(Trace.HEADER,"trace-test".getBytes(StandardCharsets.UTF_8));assertEquals("trace-test",Trace.from(record));
    }
    @Test void sanitizesUnsafeCorrelation() {assertNotEquals("unsafe\nvalue",Trace.valid("unsafe\nvalue"));}
    @Test void clearsMdcAfterScope() {try(var scope=Trace.scope("scope-test")){assertEquals("scope-test",Trace.current());}assertNull(org.slf4j.MDC.get("correlationId"));}
    @Test void rejectsMissingApiKey() throws Exception {
        var request=new MockHttpServletRequest("GET","/customers");var response=new MockHttpServletResponse();new ApiFilter("test-secret").doFilter(request,response,new MockFilterChain());assertEquals(401,response.getStatus());assertNotNull(response.getHeader(Trace.HEADER));
    }
    @Test void allowsValidApiKey() throws Exception {
        var request=new MockHttpServletRequest("GET","/customers");request.addHeader("X-API-Key","test-secret");request.addHeader(Trace.HEADER,"request-test");var response=new MockHttpServletResponse();var chain=new MockFilterChain();new ApiFilter("test-secret").doFilter(request,response,chain);assertNotNull(chain.getRequest());assertEquals("request-test",response.getHeader(Trace.HEADER));
    }
}
