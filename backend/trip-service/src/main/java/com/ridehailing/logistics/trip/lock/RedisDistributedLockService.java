package com.ridehailing.logistics.trip.lock;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class RedisDistributedLockService {

  private final StringRedisTemplate redisTemplate;

  public boolean acquireLock(String lockKey, Duration ttl) {
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", ttl);
    boolean success = Boolean.TRUE.equals(acquired);
    if (success) {
      log.debug("Acquired distributed lock: {}", lockKey);
    } else {
      log.warn("Failed to acquire distributed lock: {}", lockKey);
    }
    return success;
  }

  public void releaseLock(String lockKey) {
    redisTemplate.delete(lockKey);
    log.debug("Released distributed lock: {}", lockKey);
  }
}
