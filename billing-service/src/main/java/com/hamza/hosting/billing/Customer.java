package com.hamza.hosting.billing;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="customers")
public class Customer {
    @Id public UUID id;
    public String name;
    public String email;
    public Customer() {}
    public Customer(String name,String email) { this.id=UUID.randomUUID();this.name=name;this.email=email; }
}
