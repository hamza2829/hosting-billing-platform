package com.hamza.hosting.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.hamza.hosting.billing", "com.hamza.hosting.common"})
@EnableScheduling
public class BillingApplication {
  public static void main(String[] args) {
    SpringApplication.run(BillingApplication.class, args);
  }
}
