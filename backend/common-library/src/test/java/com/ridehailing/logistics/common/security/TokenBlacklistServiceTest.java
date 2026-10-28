package com.ridehailing.logistics.common.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private TokenBlacklistService blacklistService;

  @BeforeEach
  void setUp() {
    blacklistService = new TokenBlacklistService(redisTemplate);
  }

  @Test
  void shouldBlacklistAndDetectTokenInRedis() {
    String token = "sample-test-token-123";
    String expectedKey = TokenBlacklistService.REDIS_BLACKLIST_PREFIX + token;
    Instant future = Instant.now().plusSeconds(300);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.hasKey(expectedKey)).thenReturn(false).thenReturn(true);

    assertFalse(blacklistService.isBlacklisted(token));
    blacklistService.blacklistToken(token, future);
    assertTrue(blacklistService.isBlacklisted(token));

    verify(valueOperations).set(eq(expectedKey), eq("revoked"), any(Duration.class));
  }

  @Test
  void shouldSkipAlreadyExpiredToken() {
    String token = "sample-expired-token";
    Instant past = Instant.now().minusSeconds(10);

    blacklistService.blacklistToken(token, past);

    verifyNoInteractions(redisTemplate);
  }

  @Test
  void shouldHandleNullOrBlankGracefully() {
    assertFalse(blacklistService.isBlacklisted(null));
    assertFalse(blacklistService.isBlacklisted("   "));

    blacklistService.blacklistToken(null, Instant.now().plusSeconds(60));
    blacklistService.blacklistToken("   ", Instant.now().plusSeconds(60));

    verifyNoInteractions(redisTemplate);
  }

  @Test
  void shouldReturnSizeAndClearKeysInRedis() {
    Set<String> keys = Set.of("blacklist:jwt:t1", "blacklist:jwt:t2");
    when(redisTemplate.keys("blacklist:jwt:*")).thenReturn(keys);

    assertEquals(2, blacklistService.size());

    blacklistService.clear();
    verify(redisTemplate).delete(keys);
  }
}
