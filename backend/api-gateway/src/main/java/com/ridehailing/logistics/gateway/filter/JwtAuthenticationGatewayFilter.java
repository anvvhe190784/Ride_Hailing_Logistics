package com.ridehailing.logistics.gateway.filter;

import com.ridehailing.logistics.common.security.JwtTokenProvider;
import com.ridehailing.logistics.common.security.SecurityConstants;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
  private final JwtTokenProvider jwtTokenProvider;

  public JwtAuthenticationGatewayFilter(
      @Value("${app.security.jwt.secret:}") String jwtSecret) {
    this.jwtTokenProvider = new JwtTokenProvider(jwtSecret);
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // 1. Sanitize incoming request by stripping potential header spoofing attempts
    ServerHttpRequest sanitizedRequest =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  headers.remove(SecurityConstants.HEADER_USER_ID);
                  headers.remove(SecurityConstants.HEADER_USER_ROLE);
                  headers.remove(SecurityConstants.HEADER_USER_PHONE);
                })
            .build();
    ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

    String path = sanitizedRequest.getURI().getPath();

    // 2. Skip authentication for public endpoints
    boolean isPublic = EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    if (isPublic) {
      return chain.filter(sanitizedExchange);
    }

    // 3. Validate Authorization header
    String authHeader = sanitizedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      log.warn("Missing or invalid Authorization header for path: {}", path);
      sanitizedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return sanitizedExchange.getResponse().setComplete();
    }

    String token = authHeader.substring(7);
    Claims claims;
    try {
      claims = jwtTokenProvider.parseClaims(token);
    } catch (Exception e) {
      log.warn("Expired or invalid JWT token for path: {}", path);
      sanitizedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return sanitizedExchange.getResponse().setComplete();
    }

    if (!jwtTokenProvider.isAccessToken(claims)) {
      log.warn("Non-access token presented for path: {}", path);
      sanitizedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return sanitizedExchange.getResponse().setComplete();
    }

    // 4. Extract claims and propagate trusted headers to downstream microservices
    try {
      UUID userId = UUID.fromString(claims.getSubject());
      String role = claims.get(SecurityConstants.CLAIM_ROLE, String.class);
      String phone = claims.get(SecurityConstants.CLAIM_PHONE, String.class);

      ServerHttpRequest authenticatedRequest =
          sanitizedRequest
              .mutate()
              .header(SecurityConstants.HEADER_USER_ID, userId.toString())
              .header(SecurityConstants.HEADER_USER_ROLE, role)
              .header(SecurityConstants.HEADER_USER_PHONE, phone != null ? phone : "")
              .header(SecurityConstants.HEADER_CORRELATION_ID, UUID.randomUUID().toString())
              .build();

      return chain.filter(sanitizedExchange.mutate().request(authenticatedRequest).build());
    } catch (Exception e) {
      log.error("Error processing JWT claims: {}", e.getMessage());
      sanitizedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return sanitizedExchange.getResponse().setComplete();
    }
  }

  @Override
  public int getOrder() {
    return -100; // High priority before standard routing
  }
}
