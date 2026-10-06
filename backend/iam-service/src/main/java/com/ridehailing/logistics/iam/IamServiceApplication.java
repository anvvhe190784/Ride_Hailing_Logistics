package com.ridehailing.logistics.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.ridehailing.logistics")
@EnableDiscoveryClient
public class IamServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(IamServiceApplication.class, args);
  }
}
