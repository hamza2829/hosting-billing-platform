package com.hamza.hosting.billing;

import com.hamza.hosting.common.Errors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class BillingController {
  public record NewCustomer(
      @NotBlank @Size(max = 100) String name, @NotBlank @Email @Size(max = 200) String email) {}

  public record NewSubscription(
      @NotNull UUID customerId,
      @NotBlank @Size(max = 100) String plan,
      @Positive @Max(100000000) long amountMinor,
      @Pattern(regexp = "EUR|USD") @NotNull String currency,
      @NotNull LocalDate startsOn) {}

  public record NewInvoice(
      @NotNull UUID subscriptionId,
      @NotNull @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String period) {}

  public record PaymentNotification(
      @NotNull Boolean received, @NotBlank @Size(max = 200) String reference) {}

  private final CustomerRepository customers;
  private final SubscriptionRepository subscriptions;
  private final InvoiceRepository invoices;
  private final BillingOperations operations;

  public BillingController(
      CustomerRepository c, SubscriptionRepository s, InvoiceRepository i, BillingOperations o) {
    customers = c;
    subscriptions = s;
    invoices = i;
    operations = o;
  }

  @PostMapping("/customers")
  @ResponseStatus(HttpStatus.CREATED)
  public Customer customer(@RequestBody @Valid NewCustomer body) {
    if (customers.existsByEmail(body.email())) throw new Errors.Conflict("Email already exists");
    return customers.save(new Customer(body.name(), body.email()));
  }

  @GetMapping("/customers")
  public List<Customer> customers() {
    return customers.findAll();
  }

  @GetMapping("/customers/{id}")
  public Customer customer(@PathVariable UUID id) {
    return customers.findById(id).orElseThrow(() -> new Errors.Missing("Customer not found"));
  }

  @PostMapping("/subscriptions")
  @ResponseStatus(HttpStatus.CREATED)
  public Subscription subscription(@RequestBody @Valid NewSubscription body) {
    if (!customers.existsById(body.customerId())) throw new Errors.Missing("Customer not found");
    return subscriptions.save(
        new Subscription(
            body.customerId(), body.plan(), body.amountMinor(), body.currency(), body.startsOn()));
  }

  @GetMapping("/subscriptions")
  public List<Subscription> subscriptions() {
    return subscriptions.findAll();
  }

  @PostMapping("/invoices")
  public Invoice invoice(@RequestBody @Valid NewInvoice body) {
    return operations.generate(body.subscriptionId(), YearMonth.parse(body.period()));
  }

  @GetMapping("/invoices")
  public List<Invoice> invoices() {
    return invoices.findAll();
  }

  @GetMapping("/invoices/{id}")
  public Invoice invoice(@PathVariable UUID id) {
    return invoices.findById(id).orElseThrow(() -> new Errors.Missing("Invoice not found"));
  }

  @PostMapping("/invoices/{id}/payment-notifications")
  public Invoice payment(@PathVariable UUID id, @RequestBody @Valid PaymentNotification body) {
    return operations.paymentNotification(id, body.received(), body.reference());
  }
}
