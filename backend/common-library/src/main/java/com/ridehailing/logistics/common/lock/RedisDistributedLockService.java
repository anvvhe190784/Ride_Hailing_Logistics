package com.ridehailing.logistics.common.lock;

import java.time.Duration;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Distributed lock service backed by Redis (SET NX EX + Atomic Lua Release). Complies with
 * AGENTS.md Rule 2.4 (explicit TTL and atomic Lua owner token verification).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDistributedLockService {

  private static final String RELEASE_LOCK_LUA_SCRIPT =
      "if redis.call('get', KEYS[1]) == ARGV[1] then "
          + "return redis.call('del', KEYS[1]) "
          + "else "
          + "return 0 "
          + "end";

  private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
      new DefaultRedisScript<>(RELEASE_LOCK_LUA_SCRIPT, Long.class);

  private final StringRedisTemplate redisTemplate;

  /**
   * Attempts to acquire a distributed lock in Redis using SET NX EX.
   *
   * @param lockKey Redis key for the lock (e.g., "lock:trip:claim:<tripId>")
   * @param ownerToken Unique owner identifier (e.g., UUID or driverId:UUID)
   * @param ttl Explicit time-to-live to prevent deadlocks if node crashes
   * @return true if lock was acquired, false if already held by another caller
   */
  public boolean tryAcquire(String lockKey, String ownerToken, Duration ttl) {
    if (lockKey == null || lockKey.isBlank() || ownerToken == null || ownerToken.isBlank()) {
      return false;
    }
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, ownerToken, ttl);
    boolean success = Boolean.TRUE.equals(acquired);
    if (success) {
      log.debug("Acquired Redis lock: {} for owner: {} (TTL: {}s)", lockKey, ownerToken, ttl.toSeconds());
    } else {
      log.debug("Failed to acquire Redis lock (already locked): {}", lockKey);
    }
    return success;
  }

  /**
   * Releases a distributed lock atomically via Lua script ONLY if the current value matches
   * ownerToken.
   *
   * @param lockKey Redis key for the lock
   * @param ownerToken Unique owner identifier that acquired the lock
   * @return true if the lock was owned and deleted, false otherwise
   */
  public boolean release(String lockKey, String ownerToken) {
    if (lockKey == null || lockKey.isBlank() || ownerToken == null || ownerToken.isBlank()) {
      return false;
    }
    Long result =
        redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(lockKey), ownerToken);
    boolean released = result != null && result > 0;
    if (released) {
      log.debug("Released Redis lock: {} for owner: {}", lockKey, ownerToken);
    } else {
      log.debug("Redis lock not released (expired or owned by another): {}", lockKey);
    }
    return released;
  }
}
