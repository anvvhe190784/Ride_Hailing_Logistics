package com.ridehailing.logistics.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.security.SecurityConstants;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class RateLimitingGatewayFilter implements GlobalFilter, Ordered {

  public static final String REDIS_RATE_LIMIT_PREFIX = "ratelimit:gateway:";

  private static final List<String> RATE_LIMITED_PREFIXES =
      List.of("/api/v1/auth", "/api/v1/trips", "/api/v1/pricing");

  private final boolean enabled;
  private final double burstCapacity;
  private final ObjectMapper objectMapper;
  private final StringRedisTemplate redisTemplate;

  public RateLimitingGatewayFilter() {
    this(true, 100.0, new ObjectMapper(), null);
  }

  public RateLimitingGatewayFilter(
      @Value("${app.gateway.rate-limit.enabled:true}") boolean enabled,
      @Value("${app.gateway.rate-limit.burst-capacity:100}") double burstCapacity) {
    this(enabled, burstCapacity, new ObjectMapper(), null);
  }

  public RateLimitingGatewayFilter(
      boolean enabled,
      double burstCapacity,
      ObjectMapper objectMapper) {
    this(enabled, burstCapacity, objectMapper, null);
  }

  @Autowired
  public RateLimitingGatewayFilter(
      @Value("${app.gateway.rate-limit.enabled:true}") boolean enabled,
      @Value("${app.gateway.rate-limit.burst-capacity:100}") double burstCapacity,
      @Autowired(required = false) ObjectMapper objectMapper,
      @Autowired(required = false) StringRedisTemplate redisTemplate) {
    this.enabled = enabled;
    this.burstCapacity = burstCapacity;
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!enabled) {
      return chain.filter(exchange);
    }

    String path = exchange.getRequest().getURI().getPath();
    if (!isRateLimitedPath(path)) {
      return chain.filter(exchange);
    }

    String clientKey = resolveClientKey(exchange.getRequest());
    if (tryConsume(clientKey)) {
      return chain.filter(exchange);
    }

    log.warn("Rate limit exceeded for client key: {} on path: {}", clientKey, path);
    return handleRateLimitExceeded(exchange);
  }

  public boolean tryConsume(String clientIp) {
    if (redisTemplate == null) {
      return true;
    }
    String key = REDIS_RATE_LIMIT_PREFIX + clientIp;
    Long currentCount = redisTemplate.opsForValue().increment(key);
    if (currentCount != null && currentCount == 1L) {
      redisTemplate.expire(key, Duration.ofSeconds(60));
    }
    return currentCount == null || currentCount <= (long) burstCapacity;
  }

  private boolean isRateLimitedPath(String path) {
    if (path == null) {
      return false;
    }
    for (String prefix : RATE_LIMITED_PREFIXES) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private String resolveClientKey(ServerHttpRequest request) {
    String userId = request.getHeaders().getFirst(SecurityConstants.HEADER_USER_ID);
    if (userId != null && !userId.isBlank()) {
      return "user:" + userId;
    }

    String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return "ip:" + xForwardedFor.split(",")[0].trim();
    }

    InetSocketAddress remoteAddress = request.getRemoteAddress();
    if (remoteAddress != null && remoteAddress.getAddress() != null) {
      return "ip:" + remoteAddress.getAddress().getHostAddress();
    }

    return "ip:anonymous";
  }

  private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    String correlationId =
        exchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_CORRELATION_ID);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }

    ApiResponse<Void> errorResponse =
        ApiResponse.error(
            "TOO_MANY_REQUESTS", "Rate limit exceeded. Please try again later.", correlationId);

    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(errorResponse);
    } catch (JsonProcessingException e) {
      bytes =
          String.format(
                  "{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Please try again later.\",\"correlationId\":\"%s\"}",
                  correlationId)
              .getBytes(StandardCharsets.UTF_8);
    }

    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }

  @Override
  public int getOrder() {
    return -2; // Runs after JWT Authentication filter (-100) and before route forwarding
  }
}
