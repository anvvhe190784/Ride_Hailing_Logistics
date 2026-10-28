package com.ridehailing.logistics.common.lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisDistributedLockService lockService;

  @BeforeEach
  void setUp() {
    lockService = new RedisDistributedLockService(redisTemplate);
  }

  @Test
  void shouldAcquireLockWhenKeyDoesNotExist() {
    String lockKey = "lock:trip:claim:123";
    String ownerToken = "driver-1:uuid";
    Duration ttl = Duration.ofSeconds(5);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(lockKey, ownerToken, ttl)).thenReturn(true);

    assertTrue(lockService.tryAcquire(lockKey, ownerToken, ttl));
    verify(valueOperations).setIfAbsent(lockKey, ownerToken, ttl);
  }

  @Test
  void shouldReturnFalseWhenLockAlreadyHeld() {
    String lockKey = "lock:trip:claim:123";
    String ownerToken = "driver-2:uuid";
    Duration ttl = Duration.ofSeconds(5);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(lockKey, ownerToken, ttl)).thenReturn(false);

    assertFalse(lockService.tryAcquire(lockKey, ownerToken, ttl));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReleaseLockViaAtomicLuaScriptWhenOwnerMatches() {
    String lockKey = "lock:trip:claim:123";
    String ownerToken = "driver-1:uuid";

    when(redisTemplate.execute(
            any(RedisScript.class), eq(Collections.singletonList(lockKey)), eq(ownerToken)))
        .thenReturn(1L);

    assertTrue(lockService.release(lockKey, ownerToken));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldNotReleaseLockWhenOwnerDoesNotMatch() {
    String lockKey = "lock:trip:claim:123";
    String ownerToken = "wrong-driver:uuid";

    when(redisTemplate.execute(
            any(RedisScript.class), eq(Collections.singletonList(lockKey)), eq(ownerToken)))
        .thenReturn(0L);

    assertFalse(lockService.release(lockKey, ownerToken));
  }
}
