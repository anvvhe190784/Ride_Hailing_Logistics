package com.ridehailing.logistics.location;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.ridehailing.logistics")
@EnableDiscoveryClient
@EnableCaching
public class LocationServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(LocationServiceApplication.class, args);
  }
}
