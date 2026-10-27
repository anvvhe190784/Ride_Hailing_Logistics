package com.ridehailing.logistics.gateway.filter;

import com.ridehailing.logistics.common.security.JwtTokenProvider;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.common.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
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
          "/api/v1/auth/logout",
          "/actuator",
          "/eureka");
  private final JwtTokenProvider jwtTokenProvider;
  private final TokenBlacklistService tokenBlacklistService;

  public JwtAuthenticationGatewayFilter(@Value("${app.security.jwt.secret:}") String jwtSecret) {
    this(jwtSecret, null, null);
  }

  public JwtAuthenticationGatewayFilter(
      String jwtSecret, TokenBlacklistService tokenBlacklistService) {
    this(jwtSecret, tokenBlacklistService, null);
  }

  @Autowired
  public JwtAuthenticationGatewayFilter(
      @Value("${app.security.jwt.secret:}") String jwtSecret,
      @Autowired(required = false) TokenBlacklistService tokenBlacklistService,
      @Autowired(required = false) StringRedisTemplate redisTemplate) {
    this.jwtTokenProvider = new JwtTokenProvider(jwtSecret);
    if (tokenBlacklistService != null) {
      this.tokenBlacklistService = tokenBlacklistService;
    } else if (redisTemplate != null) {
      this.tokenBlacklistService = new TokenBlacklistService(redisTemplate);
    } else {
      this.tokenBlacklistService = new TokenBlacklistService();
    }
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
      return unauthorized(sanitizedExchange, "Missing or invalid Authorization header");
    }

    String token = authHeader.substring(7);
    Claims claims;
    try {
      claims = jwtTokenProvider.parseClaims(token);
    } catch (Exception e) {
      log.warn("Expired or invalid JWT token for path: {}", path);
      return unauthorized(sanitizedExchange, "Expired or invalid JWT token");
    }

    if (!jwtTokenProvider.isAccessToken(claims)) {
      log.warn("Non-access token presented for path: {}", path);
      return unauthorized(sanitizedExchange, "Provided token is not an access token");
    }

    if (tokenBlacklistService != null
        && (tokenBlacklistService.isBlacklisted(token)
            || (claims.getId() != null && tokenBlacklistService.isBlacklisted(claims.getId())))) {
      log.warn("Revoked or blacklisted token presented for path: {}", path);
      return unauthorized(sanitizedExchange, "Token has been revoked");
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
      return unauthorized(sanitizedExchange, "Error processing JWT claims");
    }
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
    log.warn("Unauthorized request: {}", message);
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();
  }

  @Override
  public int getOrder() {
    return -100; // High priority before standard routing
  }
}
