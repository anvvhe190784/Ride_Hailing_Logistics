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

  private final SecretKey secretKey;
  private final long accessTokenValiditySeconds;
  private final long refreshTokenValiditySeconds;

  public JwtTokenProvider() {
    this(
        SecurityConstants.DEFAULT_JWT_SECRET,
        SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS,
        SecurityConstants.REFRESH_TOKEN_VALIDITY_SECONDS);
  }

  public JwtTokenProvider(String secret, long accessValidity, long refreshValidity) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenValiditySeconds = accessValidity;
    this.refreshTokenValiditySeconds = refreshValidity;
  }

  public String generateAccessToken(UUID userId, String phone, UserRole role) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(accessTokenValiditySeconds);

    return Jwts.builder()
        .subject(userId.toString())
        .claim(SecurityConstants.CLAIM_PHONE, phone)
        .claim(SecurityConstants.CLAIM_ROLE, role.name())
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
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(secretKey)
        .compact();
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
}
