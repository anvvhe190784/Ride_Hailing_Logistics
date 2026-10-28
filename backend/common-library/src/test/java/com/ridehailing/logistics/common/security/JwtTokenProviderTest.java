package com.ridehailing.logistics.common.security;

import static org.junit.jupiter.api.Assertions.*;

import com.ridehailing.logistics.common.domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider();
  }

  @Test
  void shouldGenerateAndValidateAccessTokenSuccessfully() {
    UUID userId = UUID.randomUUID();
    String phone = "+84987654321";
    UserRole role = UserRole.DRIVER;

    String token = jwtTokenProvider.generateAccessToken(userId, phone, role);

    assertNotNull(token);
    assertTrue(jwtTokenProvider.validateToken(token));
    assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
    assertEquals(UserRole.DRIVER.name(), jwtTokenProvider.getRoleFromToken(token));
    assertEquals(phone, jwtTokenProvider.getPhoneFromToken(token));

    Claims claims = jwtTokenProvider.parseClaims(token);
    assertNotNull(claims.getId());
    assertEquals(claims.getId(), jwtTokenProvider.extractJti(claims));
    assertNotNull(jwtTokenProvider.extractExpiration(claims));
  }

  @Test
  void shouldGenerateAndValidateRefreshTokenSuccessfully() {
    UUID userId = UUID.randomUUID();

    String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

    assertNotNull(refreshToken);
    assertTrue(jwtTokenProvider.validateToken(refreshToken));
    assertEquals(userId, jwtTokenProvider.getUserIdFromToken(refreshToken));
  }

  @Test
  void shouldReturnFalseForInvalidToken() {
    assertFalse(jwtTokenProvider.validateToken("invalid.token.string"));
  }

  @Test
  void shouldWorkWithExplicitSecret() {
    String customSecret = "this-is-a-custom-32-byte-secret-for-testing!!";
    JwtTokenProvider customProvider = new JwtTokenProvider(customSecret);
    UUID userId = UUID.randomUUID();
    String token = customProvider.generateAccessToken(userId, "+84123456789", UserRole.CUSTOMER);

    assertNotNull(token);
    assertTrue(customProvider.validateToken(token));
    assertEquals(userId, customProvider.getUserIdFromToken(token));
  }

  @Test
  void shouldShareDeterministicDevSecretAcrossDefaultInstances() {
    JwtTokenProvider provider1 = new JwtTokenProvider();
    JwtTokenProvider provider2 = new JwtTokenProvider();
    UUID userId = UUID.randomUUID();

    String token = provider1.generateAccessToken(userId, "+84900000000", UserRole.CUSTOMER);
    assertTrue(provider2.validateToken(token));
    assertEquals(userId, provider2.getUserIdFromToken(token));
  }
}
