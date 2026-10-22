package com.hamza.hosting.audit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"com.hamza.hosting.audit","com.hamza.hosting.common"})
public class AuditApplication {public static void main(String[] args){SpringApplication.run(AuditApplication.class,args);}}
