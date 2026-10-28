package com.ridehailing.logistics.common.config;

import java.security.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Enforces dynamic DNS cache TTL for JVM containers in Kubernetes. By default, JVM caches DNS
 * lookups forever (TTL=-1), causing stale IP routing when downstream pods restart or scale.
 */
@Configuration(proxyBeanMethods = false)
public class DnsCacheConfig {
  private static final Logger log = LoggerFactory.getLogger(DnsCacheConfig.class);

  private DnsCacheConfig() {}

  static {
    // 30 seconds cache TTL for successful lookups
    Security.setProperty("networkaddress.cache.ttl", "30");
    // 10 seconds cache TTL for negative/failed lookups
    Security.setProperty("networkaddress.cache.negative.ttl", "10");
    log.info("JVM DNS Cache TTL configured: networkaddress.cache.ttl=30s, negative.ttl=10s");
  }
}
