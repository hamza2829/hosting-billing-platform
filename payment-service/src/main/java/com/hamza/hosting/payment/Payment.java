package com.hamza.hosting.payment;
import com.hamza.hosting.common.BillingEvent;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="payments")
public class Payment {
    @Id public UUID invoiceId;
    public UUID customerId;
    public long amountMinor;
    public String currency;
    public String status;
    public String providerReference;
    public Payment() {}
    public Payment(BillingEvent e) {invoiceId=e.invoiceId();customerId=e.customerId();amountMinor=e.amountMinor();currency=e.currency();status="PROCESSING";}
    public void validate(BillingEvent e) {
        if(!customerId.equals(e.customerId()) || amountMinor!=e.amountMinor() || !currency.equals(e.currency()))throw new IllegalArgumentException("Invoice payment identity changed");
    }
}
