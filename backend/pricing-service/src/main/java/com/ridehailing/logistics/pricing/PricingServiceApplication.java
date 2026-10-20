package com.ridehailing.logistics.pricing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.ridehailing.logistics")
@EnableDiscoveryClient
@EnableScheduling
public class PricingServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(PricingServiceApplication.class, args);
  }
}
