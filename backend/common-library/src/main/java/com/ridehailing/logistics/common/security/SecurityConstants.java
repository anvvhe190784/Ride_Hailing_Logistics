package com.ridehailing.logistics.common.security;

public final class SecurityConstants {
  public static final String HEADER_USER_ID = "X-User-Id";
  public static final String HEADER_USER_ROLE = "X-User-Role";
  public static final String HEADER_USER_PHONE = "X-User-Phone";
  public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
  public static final String CLAIM_ROLE = "role";
  public static final String CLAIM_PHONE = "phone";
  public static final String CLAIM_TYPE = "type";
  public static final String TYPE_ACCESS = "ACCESS";
  public static final String TYPE_REFRESH = "REFRESH";
  public static final String JWT_SECRET_ENV = "JWT_SECRET";
  public static final String JWT_SECRET_PROPERTY = "app.security.jwt.secret";
  public static final long ACCESS_TOKEN_VALIDITY_SECONDS = 15L * 60; // 15 mins
  public static final long REFRESH_TOKEN_VALIDITY_SECONDS = 7L * 24 * 60 * 60; // 7 days

  private SecurityConstants() {}
}
