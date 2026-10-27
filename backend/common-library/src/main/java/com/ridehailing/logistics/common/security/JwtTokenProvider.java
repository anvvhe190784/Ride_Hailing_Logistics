package com.ridehailing.logistics.common.security;

import com.ridehailing.logistics.common.domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JwtTokenProvider {

  public static final String DEFAULT_DEV_SECRET =
      "ride-hailing-logistics-deterministic-dev-jwt-secret-key-2026-hmac-sha256";

  private final SecretKey secretKey;
  private final long accessTokenValiditySeconds;
  private final long refreshTokenValiditySeconds;

  public JwtTokenProvider() {
    this(
        null,
        SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS,
        SecurityConstants.REFRESH_TOKEN_VALIDITY_SECONDS);
  }

  public JwtTokenProvider(String secret) {
    this(
        secret,
        SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS,
        SecurityConstants.REFRESH_TOKEN_VALIDITY_SECONDS);
  }

  public JwtTokenProvider(String secret, long accessValidity, long refreshValidity) {
    String resolvedSecret = resolveSecret(secret);
    this.secretKey = Keys.hmacShaKeyFor(resolvedSecret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenValiditySeconds = accessValidity;
    this.refreshTokenValiditySeconds = refreshValidity;
  }

  private static String resolveSecret(String explicitSecret) {
    if (explicitSecret != null && !explicitSecret.isBlank()) {
      return explicitSecret;
    }
    String envSecret = System.getenv(SecurityConstants.JWT_SECRET_ENV);
    if (envSecret != null && !envSecret.isBlank()) {
      return envSecret;
    }
    String propSecret = System.getProperty(SecurityConstants.JWT_SECRET_PROPERTY);
    if (propSecret != null && !propSecret.isBlank()) {
      return propSecret;
    }
    log.warn(
        "No JWT secret configured via environment variable '{}' or system property '{}'. "
            + "Using deterministic fallback secret for development/testing profiles. "
            + "DO NOT USE THIS FALLBACK IN PRODUCTION!",
        SecurityConstants.JWT_SECRET_ENV,
        SecurityConstants.JWT_SECRET_PROPERTY);
    return DEFAULT_DEV_SECRET;
  }

  public String generateAccessToken(UUID userId, String phone, UserRole role) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(accessTokenValiditySeconds);

    return Jwts.builder()
        .id(UUID.randomUUID().toString())
        .subject(userId.toString())
        .claim(SecurityConstants.CLAIM_PHONE, phone)
        .claim(SecurityConstants.CLAIM_ROLE, role.name())
        .claim(SecurityConstants.CLAIM_TYPE, SecurityConstants.TYPE_ACCESS)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(secretKey)
        .compact();
  }

  public String generateRefreshToken(UUID userId) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(refreshTokenValiditySeconds);

    return Jwts.builder()
        .subject(userId.toString())
        .claim(SecurityConstants.CLAIM_TYPE, SecurityConstants.TYPE_REFRESH)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(secretKey)
        .compact();
  }

  public boolean isAccessToken(Claims claims) {
    return SecurityConstants.TYPE_ACCESS.equals(claims.get(SecurityConstants.CLAIM_TYPE, String.class));
  }

  public boolean isRefreshToken(Claims claims) {
    return SecurityConstants.TYPE_REFRESH.equals(claims.get(SecurityConstants.CLAIM_TYPE, String.class));
  }

  public Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("Invalid JWT token: {}", e.getMessage());
      return false;
    }
  }

  public UUID getUserIdFromToken(String token) {
    return UUID.fromString(parseClaims(token).getSubject());
  }

  public String getRoleFromToken(String token) {
    return parseClaims(token).get(SecurityConstants.CLAIM_ROLE, String.class);
  }

  public String getPhoneFromToken(String token) {
    return parseClaims(token).get(SecurityConstants.CLAIM_PHONE, String.class);
  }

  public String extractJti(Claims claims) {
    return claims != null ? claims.getId() : null;
  }

  public Instant extractExpiration(Claims claims) {
    if (claims == null || claims.getExpiration() == null) {
      return null;
    }
    return claims.getExpiration().toInstant();
  }
}
