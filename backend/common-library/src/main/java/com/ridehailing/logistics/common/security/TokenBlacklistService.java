package com.ridehailing.logistics.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Pure Redis-backed blacklist service for revoked JWT access tokens and JTIs. Stores token
 * identifiers exclusively in Redis with TTL matching their natural expiration (Zero RAM cache).
 */
@Slf4j
@Service
public class TokenBlacklistService {

  public static final String REDIS_BLACKLIST_PREFIX = "blacklist:jwt:";

  private final StringRedisTemplate redisTemplate;

  public TokenBlacklistService() {
    this(null);
  }

  @Autowired
  public TokenBlacklistService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Adds a token or JTI to the Redis revocation blacklist until its natural expiration.
   *
   * @param tokenOrJti raw JWT string or JTI claim identifier
   * @param expiresAt token expiration instant
   */
  public void blacklistToken(String tokenOrJti, Instant expiresAt) {
    if (tokenOrJti == null || tokenOrJti.isBlank() || redisTemplate == null) {
      return;
    }

    Instant expiry =
        expiresAt != null
            ? expiresAt
            : Instant.now().plusSeconds(SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS);

    long ttlSeconds = Duration.between(Instant.now(), expiry).toSeconds();
    if (ttlSeconds <= 0) {
      log.debug("Token/JTI already expired, skipping Redis blacklist: {}", tokenOrJti);
      return;
    }

    String redisKey = REDIS_BLACKLIST_PREFIX + tokenOrJti;
    redisTemplate.opsForValue().set(redisKey, "revoked", Duration.ofSeconds(ttlSeconds));
    log.info("Blacklisted token/jti in Redis: {}, ttl: {}s", redisKey, ttlSeconds);
  }

  /**
   * Checks if a token or JTI is currently blacklisted in Redis.
   *
   * @param tokenOrJti raw JWT string or JTI claim identifier
   * @return true if the token exists in Redis blacklist
   */
  public boolean isBlacklisted(String tokenOrJti) {
    if (tokenOrJti == null || tokenOrJti.isBlank() || redisTemplate == null) {
      return false;
    }
    return Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_BLACKLIST_PREFIX + tokenOrJti));
  }

  /** Returns the count of currently blacklisted tokens in Redis. */
  public int size() {
    if (redisTemplate == null) {
      return 0;
    }
    Set<String> keys = redisTemplate.keys(REDIS_BLACKLIST_PREFIX + "*");
    return keys != null ? keys.size() : 0;
  }

  /** Clears all blacklisted tokens in Redis. */
  public void clear() {
    if (redisTemplate == null) {
      return;
    }
    Set<String> keys = redisTemplate.keys(REDIS_BLACKLIST_PREFIX + "*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }
}
