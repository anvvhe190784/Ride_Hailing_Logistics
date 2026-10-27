package com.ridehailing.logistics.common.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Thread-safe in-memory blacklist service for revoked JWT access tokens and JTIs. Stores token
 * identifiers mapped to their expiration Instant with automatic lazy cleanup.
 */
@Slf4j
@Component
public class TokenBlacklistService {

  private final Map<String, Instant> blacklist = new ConcurrentHashMap<>();

  /**
   * Adds a token or JTI to the revocation blacklist until its natural expiration.
   *
   * @param tokenOrJti raw JWT string or JTI claim identifier
   * @param expiresAt token expiration instant
   */
  public void blacklistToken(String tokenOrJti, Instant expiresAt) {
    if (tokenOrJti == null || tokenOrJti.isBlank()) {
      return;
    }
    // Lazy cleanup of expired tokens
    cleanupExpired();

    Instant expiry =
        expiresAt != null
            ? expiresAt
            : Instant.now().plusSeconds(SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS);
    blacklist.put(tokenOrJti, expiry);
    log.debug("Blacklisted token/jti identifier: {}, expiresAt: {}", tokenOrJti, expiry);
  }

  /**
   * Checks if a token or JTI is currently blacklisted.
   *
   * @param tokenOrJti raw JWT string or JTI claim identifier
   * @return true if the token is blacklisted and not yet expired
   */
  public boolean isBlacklisted(String tokenOrJti) {
    if (tokenOrJti == null || tokenOrJti.isBlank()) {
      return false;
    }
    Instant expiresAt = blacklist.get(tokenOrJti);
    if (expiresAt == null) {
      return false;
    }
    if (Instant.now().isAfter(expiresAt)) {
      blacklist.remove(tokenOrJti);
      return false;
    }
    return true;
  }

  /** Cleans up expired entries from the blacklist map. */
  public void cleanupExpired() {
    Instant now = Instant.now();
    blacklist.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
  }

  /** Returns the count of currently blacklisted tokens. */
  public int size() {
    return blacklist.size();
  }

  /** Clears all blacklisted tokens. */
  public void clear() {
    blacklist.clear();
  }
}
