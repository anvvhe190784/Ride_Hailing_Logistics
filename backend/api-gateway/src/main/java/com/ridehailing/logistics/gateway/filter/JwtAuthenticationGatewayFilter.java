package com.ridehailing.logistics.gateway.filter;

import com.ridehailing.logistics.common.security.JwtTokenProvider;
import com.ridehailing.logistics.common.security.SecurityConstants;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

  private static final List<String> EXCLUDED_PATHS =
      List.of(
          "/api/v1/auth/login",
          "/api/v1/auth/register",
          "/api/v1/auth/refresh-token",
          "/actuator",
          "/eureka");
  private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider();

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getPath();

    // 1. Skip authentication for public endpoints
    boolean isPublic = EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    if (isPublic) {
      return chain.filter(exchange);
    }

    // 2. Validate Authorization header
    String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      log.warn("Missing or invalid Authorization header for path: {}", path);
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }

    String token = authHeader.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      log.warn("Expired or invalid JWT token for path: {}", path);
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }

    // 3. Extract claims and propagate to downstream microservices
    try {
      UUID userId = jwtTokenProvider.getUserIdFromToken(token);
      String role = jwtTokenProvider.getRoleFromToken(token);
      String phone = jwtTokenProvider.getPhoneFromToken(token);

      ServerHttpRequest mutatedRequest =
          request
              .mutate()
              .header(SecurityConstants.HEADER_USER_ID, userId.toString())
              .header(SecurityConstants.HEADER_USER_ROLE, role)
              .header(SecurityConstants.HEADER_USER_PHONE, phone != null ? phone : "")
              .header(SecurityConstants.HEADER_CORRELATION_ID, UUID.randomUUID().toString())
              .build();

      return chain.filter(exchange.mutate().request(mutatedRequest).build());
    } catch (Exception e) {
      log.error("Error processing JWT claims: {}", e.getMessage());
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }
  }

  @Override
  public int getOrder() {
    return -100; // High priority before standard routing
  }
}
