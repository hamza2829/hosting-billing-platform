package com.hamza.hosting.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.hamza.hosting.payment", "com.hamza.hosting.common"})
@EnableScheduling
public class PaymentApplication {
  public static void main(String[] args) {
    SpringApplication.run(PaymentApplication.class, args);
  }
}
