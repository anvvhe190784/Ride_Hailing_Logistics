package com.ridehailing.logistics.common.security;

public final class SecurityConstants {
  public static final String HEADER_USER_ID = "X-User-Id";
  public static final String HEADER_USER_ROLE = "X-User-Role";
  public static final String HEADER_USER_PHONE = "X-User-Phone";
  public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
  public static final String CLAIM_ROLE = "role";
  public static final String CLAIM_PHONE = "phone";
  // 256-bit secret key for HMAC-SHA256
  public static final String DEFAULT_JWT_SECRET =
      "4c568912e9b940989012a4b8902cd56ef1234567890abcdef1234567890abcde";
  public static final long ACCESS_TOKEN_VALIDITY_SECONDS = 15L * 60; // 15 mins
  public static final long REFRESH_TOKEN_VALIDITY_SECONDS = 7L * 24 * 60 * 60; // 7 days

  private SecurityConstants() {}
}
