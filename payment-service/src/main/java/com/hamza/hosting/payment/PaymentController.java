package com.hamza.hosting.payment;
import com.hamza.hosting.common.Errors;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class PaymentController {
    private final PaymentRepository payments;
    public PaymentController(PaymentRepository p){payments=p;}
    @GetMapping("/payments") public List<Payment> all(){return payments.findAll();}
    @GetMapping("/payments/{id}") public Payment get(@PathVariable UUID id){return payments.findById(id).orElseThrow(()->new Errors.Missing("Payment not found"));}
}
