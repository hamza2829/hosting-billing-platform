package com.hamza.hosting.billing;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="invoices")
public class Invoice {
    @Id public UUID id;
    public UUID subscriptionId;
    public UUID customerId;
    public String period;
    public long amountMinor;
    public String currency;
    public String status;
    public Instant createdAt;
    public Invoice() {}
    public Invoice(Subscription s,String period) {
        id=UUID.randomUUID();subscriptionId=s.id;customerId=s.customerId;this.period=period;
        amountMinor=s.amountMinor;currency=s.currency;status="OPEN";createdAt=Instant.now();
    }
}
