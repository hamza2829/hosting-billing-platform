package com.hamza.hosting.billing;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="subscriptions")
public class Subscription {
    @Id public UUID id;
    public UUID customerId;
    public String plan;
    public long amountMinor;
    public String currency;
    public LocalDate startsOn;
    public boolean active;
    public Subscription() {}
    public Subscription(UUID customer,String plan,long amount,String currency,LocalDate starts) {
        id=UUID.randomUUID();customerId=customer;this.plan=plan;amountMinor=amount;this.currency=currency;startsOn=starts;active=true;
    }
}
