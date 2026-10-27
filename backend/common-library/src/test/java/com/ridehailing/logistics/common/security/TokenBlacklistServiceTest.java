package com.ridehailing.logistics.common.security;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenBlacklistServiceTest {

  private TokenBlacklistService blacklistService;

  @BeforeEach
  void setUp() {
    blacklistService = new TokenBlacklistService();
  }

  @Test
  void shouldBlacklistAndDetectToken() {
    String token = "sample-test-token-123";
    Instant future = Instant.now().plusSeconds(300);

    assertFalse(blacklistService.isBlacklisted(token));
    blacklistService.blacklistToken(token, future);
    assertTrue(blacklistService.isBlacklisted(token));
  }

  @Test
  void shouldAutoExpireBlacklistedToken() {
    String token = "sample-expired-token";
    Instant past = Instant.now().minusSeconds(10);

    blacklistService.blacklistToken(token, past);
    // When queried, it detects expiration and removes entry
    assertFalse(blacklistService.isBlacklisted(token));
  }

  @Test
  void shouldHandleNullOrBlankGracefully() {
    assertFalse(blacklistService.isBlacklisted(null));
    assertFalse(blacklistService.isBlacklisted("   "));

    blacklistService.blacklistToken(null, Instant.now().plusSeconds(60));
    blacklistService.blacklistToken("   ", Instant.now().plusSeconds(60));
    assertEquals(0, blacklistService.size());
  }

  @Test
  void shouldCleanupExpiredEntries() {
    blacklistService.blacklistToken("token1", Instant.now().minusSeconds(60));
    blacklistService.blacklistToken("token2", Instant.now().plusSeconds(300));

    blacklistService.cleanupExpired();
    assertEquals(1, blacklistService.size());
    assertTrue(blacklistService.isBlacklisted("token2"));
    assertFalse(blacklistService.isBlacklisted("token1"));
  }
}
