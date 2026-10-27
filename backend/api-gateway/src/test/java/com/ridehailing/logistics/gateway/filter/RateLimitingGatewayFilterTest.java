package com.ridehailing.logistics.gateway.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.security.SecurityConstants;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class RateLimitingGatewayFilterTest {

  private RateLimitingGatewayFilter rateLimitingFilter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    // Burst capacity 2, replenish rate 1 token/sec
    rateLimitingFilter = new RateLimitingGatewayFilter(true, 1.0, 2.0, objectMapper);
  }

  @Test
  void shouldAllowRequestsWithinBurstCapacity() {
    MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login")
        .header(SecurityConstants.HEADER_USER_ID, "user-123")
        .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainExecuted = new AtomicBoolean(false);
    GatewayFilterChain chain = ex -> {
      chainExecuted.set(true);
      return Mono.empty();
    };

    // Request 1: should pass
    rateLimitingFilter.filter(exchange, chain).block();
    assertTrue(chainExecuted.get());

    // Request 2: should pass
    chainExecuted.set(false);
    rateLimitingFilter.filter(exchange, chain).block();
    assertTrue(chainExecuted.get());
  }

  @Test
  void shouldReturn429WhenRateLimitExceeded() {
    MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/trips/quote")
        .header(SecurityConstants.HEADER_USER_ID, "user-456")
        .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayFilterChain chain = ex -> Mono.empty();

    // Consume all 2 tokens
    rateLimitingFilter.filter(exchange, chain).block();
    rateLimitingFilter.filter(exchange, chain).block();

    // Request 3: should be rate-limited
    MockServerWebExchange exchangeExceeded = MockServerWebExchange.from(request);
    rateLimitingFilter.filter(exchangeExceeded, chain).block();

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchangeExceeded.getResponse().getStatusCode());
  }

  @Test
  void shouldSkipRateLimitingOnNonConfiguredPaths() {
    MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainExecuted = new AtomicBoolean(false);
    GatewayFilterChain chain = ex -> {
      chainExecuted.set(true);
      return Mono.empty();
    };

    // Even if flooded, non-rate-limited paths always pass
    for (int i = 0; i < 5; i++) {
      chainExecuted.set(false);
      rateLimitingFilter.filter(exchange, chain).block();
      assertTrue(chainExecuted.get());
    }
  }

  @Test
  void shouldSkipWhenRateLimiterDisabled() {
    RateLimitingGatewayFilter disabledFilter =
        new RateLimitingGatewayFilter(false, 1.0, 1.0, objectMapper);

    MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login")
        .header(SecurityConstants.HEADER_USER_ID, "user-789")
        .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainExecuted = new AtomicBoolean(false);
    GatewayFilterChain chain = ex -> {
      chainExecuted.set(true);
      return Mono.empty();
    };

    for (int i = 0; i < 10; i++) {
      chainExecuted.set(false);
      disabledFilter.filter(exchange, chain).block();
      assertTrue(chainExecuted.get());
    }
  }
}
