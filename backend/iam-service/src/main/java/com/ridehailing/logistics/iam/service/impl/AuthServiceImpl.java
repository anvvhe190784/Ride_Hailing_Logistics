package com.ridehailing.logistics.iam.service.impl;

import com.ridehailing.logistics.common.domain.enums.AccountStatus;
import com.ridehailing.logistics.common.domain.enums.UserRole;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.common.security.JwtTokenProvider;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.common.security.TokenBlacklistService;
import com.ridehailing.logistics.iam.domain.entity.AuditLog;
import com.ridehailing.logistics.iam.domain.entity.RefreshToken;
import com.ridehailing.logistics.iam.domain.entity.User;
import com.ridehailing.logistics.iam.dto.request.LoginRequest;
import com.ridehailing.logistics.iam.dto.request.LogoutRequest;
import com.ridehailing.logistics.iam.dto.request.RefreshTokenRequest;
import com.ridehailing.logistics.iam.dto.request.RegisterRequest;
import com.ridehailing.logistics.iam.dto.response.AuthResponse;
import com.ridehailing.logistics.iam.dto.response.UserResponse;
import com.ridehailing.logistics.iam.mapper.UserMapper;
import com.ridehailing.logistics.iam.repository.AuditLogRepository;
import com.ridehailing.logistics.iam.repository.RefreshTokenRepository;
import com.ridehailing.logistics.iam.repository.UserRepository;
import com.ridehailing.logistics.iam.service.AuthService;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

  private static final String ERR_INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final AuditLogRepository auditLogRepository;
  private final TokenBlacklistService tokenBlacklistService;

  public AuthServiceImpl(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      UserMapper userMapper,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this(
        userRepository,
        refreshTokenRepository,
        userMapper,
        passwordEncoder,
        jwtTokenProvider,
        null,
        new TokenBlacklistService());
  }

  @Autowired
  public AuthServiceImpl(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      UserMapper userMapper,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      @Autowired(required = false) AuditLogRepository auditLogRepository,
      @Autowired(required = false) TokenBlacklistService tokenBlacklistService) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.auditLogRepository = auditLogRepository;
    this.tokenBlacklistService =
        tokenBlacklistService != null ? tokenBlacklistService : new TokenBlacklistService();
  }

  @Override
  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (request.role() != UserRole.CUSTOMER && request.role() != UserRole.DRIVER) {
      throw BaseDomainException.badRequest(
          "INVALID_REGISTRATION_ROLE",
          "Self-registration is only allowed for CUSTOMER or DRIVER roles");
    }

    if (userRepository.existsByPhone(request.phone())) {
      throw BaseDomainException.conflict(
          "PHONE_ALREADY_REGISTERED", "Phone number is already registered");
    }
    if (request.email() != null && userRepository.existsByEmail(request.email())) {
      throw BaseDomainException.conflict("EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }

    User user =
        User.builder()
            .phone(request.phone())
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .fullName(request.fullName())
            .role(request.role())
            .build();

    user = userRepository.save(user);
    log.info("Registered new user ID: {} with role: {}", user.getId(), user.getRole());
    recordAuditLog(user.getId(), "REGISTER", "User registered with role: " + user.getRole());

    String accessToken =
        jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(), user.getRole());
    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
    persistRefreshToken(user.getId(), refreshToken);

    return AuthResponse.of(
        accessToken,
        refreshToken,
        SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS,
        userMapper.toResponse(user));
  }

  @Override
  public AuthResponse login(LoginRequest request) {
    // FR-IAM-006: Do not reveal whether account exists when authentication fails
    User user =
        userRepository
            .findByPhone(request.phone())
            .orElseThrow(
                () ->
                    BaseDomainException.badRequest(
                        "INVALID_CREDENTIALS", "Invalid phone number or password"));

    Instant now = Instant.now();
    if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
      long remainingMinutes = Duration.between(now, user.getLockedUntil()).toMinutes() + 1;
      throw BaseDomainException.forbidden(
          "ACCOUNT_LOCKED",
          "Account is temporarily locked due to multiple failed login attempts. Please try again in "
              + remainingMinutes
              + " minute(s)");
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      int attempts = user.getFailedLoginAttempts() + 1;
      user.setFailedLoginAttempts(attempts);
      if (attempts >= 5) {
        user.setLockedUntil(now.plus(15, ChronoUnit.MINUTES));
        log.warn(
            "Account ID {} locked for 15 minutes due to 5 consecutive failed login attempts",
            user.getId());
        recordAuditLog(
            user.getId(),
            "ACCOUNT_LOCKED",
            "Account locked for 15 minutes due to 5 consecutive failed login attempts");
      } else {
        recordAuditLog(user.getId(), "LOGIN_FAILED", "Failed login attempt #" + attempts);
      }
      userRepository.save(user);
      throw BaseDomainException.badRequest(
          "INVALID_CREDENTIALS", "Invalid phone number or password");
    }

    if (user.getStatus() != AccountStatus.ACTIVE) {
      throw BaseDomainException.forbidden(
          "ACCOUNT_NOT_ACTIVE", "Account is not active or has been suspended");
    }

    // Reset failed login attempts on successful authentication
    if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
      user.setFailedLoginAttempts(0);
      user.setLockedUntil(null);
      userRepository.save(user);
    }

    recordAuditLog(user.getId(), "LOGIN_SUCCESS", "User authenticated successfully");

    String accessToken =
        jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(), user.getRole());
    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
    persistRefreshToken(user.getId(), refreshToken);

    return AuthResponse.of(
        accessToken,
        refreshToken,
        SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS,
        userMapper.toResponse(user));
  }

  @Override
  @Transactional
  public AuthResponse refreshToken(RefreshTokenRequest request) {
    Claims claims;
    try {
      claims = jwtTokenProvider.parseClaims(request.refreshToken());
    } catch (Exception e) {
      throw BaseDomainException.badRequest(
          ERR_INVALID_REFRESH_TOKEN, "Expired or invalid refresh token");
    }

    if (!jwtTokenProvider.isRefreshToken(claims)) {
      throw BaseDomainException.badRequest(
          ERR_INVALID_REFRESH_TOKEN, "Provided token is not a refresh token");
    }

    String tokenHash = hashToken(request.refreshToken());
    RefreshToken storedToken =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    BaseDomainException.forbidden(
                        ERR_INVALID_REFRESH_TOKEN, "Refresh token not recognized or revoked"));
    if (Boolean.TRUE.equals(storedToken.getRevoked())) {
      throw BaseDomainException.forbidden("TOKEN_REVOKED", "Refresh token has been revoked");
    }

    UUID userId = UUID.fromString(claims.getSubject());
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> BaseDomainException.notFound("USER_NOT_FOUND", "User not found"));

    if (user.getStatus() != AccountStatus.ACTIVE) {
      throw BaseDomainException.forbidden(
          "ACCOUNT_NOT_ACTIVE", "Account is not active or has been suspended");
    }

    // Revoke old refresh token upon rotation
    storedToken.setRevoked(true);
    refreshTokenRepository.save(storedToken);

    String newAccessToken =
        jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(), user.getRole());
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
    persistRefreshToken(user.getId(), newRefreshToken);

    recordAuditLog(
        user.getId(), "TOKEN_REFRESH", "Rotated refresh token and issued new access token");

    return AuthResponse.of(
        newAccessToken,
        newRefreshToken,
        SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS,
        userMapper.toResponse(user));
  }

  @Override
  @Transactional
  public void logout(LogoutRequest request) {
    doLogout(request, null);
  }

  @Override
  @Transactional
  public void logout(LogoutRequest request, String authHeader) {
    doLogout(request, authHeader);
  }

  private void doLogout(LogoutRequest request, String authHeader) {
    if (request == null) {
      return;
    }

    String rawAccessToken = resolveAccessToken(request, authHeader);
    UUID userId = revokeAccessToken(rawAccessToken);
    userId = revokeRefreshToken(request.refreshToken(), userId);

    recordAuditLog(userId, "LOGOUT", "Tokens successfully revoked for user ID: " + userId);
  }

  private String resolveAccessToken(LogoutRequest request, String authHeader) {
    if (request.accessToken() != null && !request.accessToken().isBlank()) {
      return request.accessToken();
    }
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }
    return null;
  }

  private UUID revokeAccessToken(String rawAccessToken) {
    if (rawAccessToken == null || rawAccessToken.isBlank()) {
      return null;
    }
    try {
      Claims claims = jwtTokenProvider.parseClaims(rawAccessToken);
      UUID userId = UUID.fromString(claims.getSubject());
      Instant expiresAt = jwtTokenProvider.extractExpiration(claims);
      String jti = jwtTokenProvider.extractJti(claims);

      if (tokenBlacklistService != null) {
        tokenBlacklistService.blacklistToken(rawAccessToken, expiresAt);
        if (jti != null && !jti.isBlank()) {
          tokenBlacklistService.blacklistToken(jti, expiresAt);
        }
      }
      log.info("Blacklisted access token for user ID: {}, jti: {}", userId, jti);
      return userId;
    } catch (Exception e) {
      log.debug("Access token claims parsing during logout: {}", e.getMessage());
      return null;
    }
  }

  private UUID revokeRefreshToken(String refreshToken, UUID currentUserId) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return currentUserId;
    }
    UUID resolvedUserId = currentUserId;
    try {
      Claims refreshClaims = jwtTokenProvider.parseClaims(refreshToken);
      if (resolvedUserId == null) {
        resolvedUserId = UUID.fromString(refreshClaims.getSubject());
      }
    } catch (Exception e) {
      log.debug("Refresh token claims parsing during logout: {}", e.getMessage());
    }

    String tokenHash = hashToken(refreshToken);
    Optional<RefreshToken> storedOpt = refreshTokenRepository.findByTokenHash(tokenHash);
    if (storedOpt.isPresent()) {
      RefreshToken tokenEntity = storedOpt.get();
      tokenEntity.setRevoked(true);
      refreshTokenRepository.save(tokenEntity);
      if (resolvedUserId == null) {
        resolvedUserId = tokenEntity.getUserId();
      }
    } else if (resolvedUserId != null) {
      RefreshToken revokedToken =
          RefreshToken.builder()
              .userId(resolvedUserId)
              .tokenHash(tokenHash)
              .expiresAt(
                  Instant.now().plusSeconds(SecurityConstants.REFRESH_TOKEN_VALIDITY_SECONDS))
              .revoked(true)
              .build();
      refreshTokenRepository.save(revokedToken);
    }
    return resolvedUserId;
  }

  @Override
  @Transactional(readOnly = true)
  public UserResponse getUserProfile(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> BaseDomainException.notFound("USER_NOT_FOUND", "User profile not found"));
    return userMapper.toResponse(user);
  }

  private void recordAuditLog(UUID userId, String action, String details) {
    recordAuditLog(userId, action, details, null);
  }

  private void recordAuditLog(UUID userId, String action, String details, String ipAddress) {
    log.info(
        "AUDIT LOG: [action={}, userId={}, ipAddress={}] - {}", action, userId, ipAddress, details);
    if (auditLogRepository != null) {
      try {
        AuditLog auditLog =
            AuditLog.builder()
                .userId(userId)
                .action(action)
                .details(details)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(auditLog);
      } catch (Exception e) {
        log.warn("Failed to persist audit log: {}", e.getMessage());
      }
    }
  }

  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  private void persistRefreshToken(UUID userId, String rawToken) {
    String hash = hashToken(rawToken);
    Claims claims = jwtTokenProvider.parseClaims(rawToken);
    Instant expiresAt = claims.getExpiration().toInstant();
    RefreshToken entity =
        RefreshToken.builder()
            .userId(userId)
            .tokenHash(hash)
            .expiresAt(expiresAt)
            .revoked(false)
            .build();
    refreshTokenRepository.save(entity);
  }
}
