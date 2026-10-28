package com.ridehailing.logistics.gateway.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.UserRole;
import com.ridehailing.logistics.common.security.JwtTokenProvider;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.common.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthenticationGatewayFilterTest {

  private static final String TEST_SECRET =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private JwtAuthenticationGatewayFilter filter;
  private JwtTokenProvider jwtTokenProvider;
  private TokenBlacklistService tokenBlacklistService;

  @BeforeEach
  void setUp() {
    tokenBlacklistService = mock(TokenBlacklistService.class);
    filter = new JwtAuthenticationGatewayFilter(TEST_SECRET, tokenBlacklistService);
    jwtTokenProvider = new JwtTokenProvider(TEST_SECRET);
  }

  @Test
  void shouldStripSpoofedHeadersOnPublicEndpoints() {
    MockServerHttpRequest request =
        MockServerHttpRequest.post("/api/v1/auth/login")
            .header(SecurityConstants.HEADER_USER_ID, UUID.randomUUID().toString())
            .header(SecurityConstants.HEADER_USER_ROLE, "ADMIN")
            .header(SecurityConstants.HEADER_USER_PHONE, "+84999999999")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicReference<ServerWebExchange> filteredExchange = new AtomicReference<>();
    GatewayFilterChain chain =
        ex -> {
          filteredExchange.set(ex);
          return Mono.empty();
        };

    filter.filter(exchange, chain).block();

    assertNotNull(filteredExchange.get());
    HttpHeaders downstreamHeaders = filteredExchange.get().getRequest().getHeaders();
    assertFalse(downstreamHeaders.containsKey(SecurityConstants.HEADER_USER_ID));
    assertFalse(downstreamHeaders.containsKey(SecurityConstants.HEADER_USER_ROLE));
    assertFalse(downstreamHeaders.containsKey(SecurityConstants.HEADER_USER_PHONE));
  }

  @Test
  void shouldReturnUnauthorizedWhenAuthHeaderIsMissing() {
    MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/drivers/me").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = mock(GatewayFilterChain.class);

    filter.filter(exchange, chain).block();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    verify(chain, never()).filter(any());
  }

  @Test
  void shouldReturnUnauthorizedWhenTokenIsInvalid() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/v1/drivers/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = mock(GatewayFilterChain.class);

    filter.filter(exchange, chain).block();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    verify(chain, never()).filter(any());
  }

  @Test
  void shouldAuthenticateAndPropagateClaimsToDownstream() {
    UUID userId = UUID.randomUUID();
    String phone = "+84912345678";
    UserRole role = UserRole.CUSTOMER;
    String validToken = jwtTokenProvider.generateAccessToken(userId, phone, role);

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/v1/drivers/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
            .header(SecurityConstants.HEADER_USER_ROLE, "SPOOFED_ADMIN") // Spoofed header
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicReference<ServerWebExchange> filteredExchange = new AtomicReference<>();
    GatewayFilterChain chain =
        ex -> {
          filteredExchange.set(ex);
          return Mono.empty();
        };

    filter.filter(exchange, chain).block();

    assertNotNull(filteredExchange.get());
    HttpHeaders downstreamHeaders = filteredExchange.get().getRequest().getHeaders();
    assertEquals(userId.toString(), downstreamHeaders.getFirst(SecurityConstants.HEADER_USER_ID));
    assertEquals(role.name(), downstreamHeaders.getFirst(SecurityConstants.HEADER_USER_ROLE));
    assertEquals(phone, downstreamHeaders.getFirst(SecurityConstants.HEADER_USER_PHONE));
    assertNotNull(downstreamHeaders.getFirst(SecurityConstants.HEADER_CORRELATION_ID));
  }

  @Test
  void shouldReturnUnauthorizedWhenTokenIsBlacklisted() {
    UUID userId = UUID.randomUUID();
    String validToken = jwtTokenProvider.generateAccessToken(userId, "+84912345678", UserRole.CUSTOMER);
    when(tokenBlacklistService.isBlacklisted(validToken)).thenReturn(true);

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/v1/drivers/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = mock(GatewayFilterChain.class);

    filter.filter(exchange, chain).block();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    verify(chain, never()).filter(any());
  }

  @Test
  void shouldReturnUnauthorizedWhenJtiIsBlacklisted() {
    UUID userId = UUID.randomUUID();
    String validToken = jwtTokenProvider.generateAccessToken(userId, "+84912345678", UserRole.CUSTOMER);
    Claims claims = jwtTokenProvider.parseClaims(validToken);
    when(tokenBlacklistService.isBlacklisted(claims.getId())).thenReturn(true);

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/v1/drivers/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = mock(GatewayFilterChain.class);

    filter.filter(exchange, chain).block();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    verify(chain, never()).filter(any());
  }
}
