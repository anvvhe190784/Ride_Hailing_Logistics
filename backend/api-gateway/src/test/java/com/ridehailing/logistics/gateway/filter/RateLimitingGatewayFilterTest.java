package com.ridehailing.logistics.gateway.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.security.SecurityConstants;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class RateLimitingGatewayFilterTest {

  private RateLimitingGatewayFilter rateLimitingFilter;
  private ObjectMapper objectMapper;
  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    objectMapper = new ObjectMapper();
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    when(valueOperations.increment(anyString()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              return counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
            });

    // Burst capacity 2
    rateLimitingFilter =
        new RateLimitingGatewayFilter(true, 2.0, objectMapper, redisTemplate);
  }

  @Test
  void shouldAllowRequestsWithinBurstCapacity() {
    MockServerHttpRequest request =
        MockServerHttpRequest.post("/api/v1/auth/login")
            .header(SecurityConstants.HEADER_USER_ID, "user-123")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainExecuted = new AtomicBoolean(false);
    GatewayFilterChain chain =
        ex -> {
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
    MockServerHttpRequest request =
        MockServerHttpRequest.post("/api/v1/trips/quote")
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
    GatewayFilterChain chain =
        ex -> {
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
        new RateLimitingGatewayFilter(false, 1.0, objectMapper, redisTemplate);

    MockServerHttpRequest request =
        MockServerHttpRequest.post("/api/v1/auth/login")
            .header(SecurityConstants.HEADER_USER_ID, "user-789")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainExecuted = new AtomicBoolean(false);
    GatewayFilterChain chain =
        ex -> {
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
