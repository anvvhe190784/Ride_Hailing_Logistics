package com.ridehailing.logistics.trip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.ridehailing.logistics")
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class TripServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(TripServiceApplication.class, args);
  }
}
