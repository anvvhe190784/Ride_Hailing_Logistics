package com.ridehailing.logistics.iam.service.impl;

import com.ridehailing.logistics.common.domain.enums.AccountStatus;
import com.ridehailing.logistics.common.domain.enums.UserRole;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.common.security.JwtTokenProvider;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.iam.domain.entity.RefreshToken;
import com.ridehailing.logistics.iam.domain.entity.User;
import com.ridehailing.logistics.iam.dto.request.LoginRequest;
import com.ridehailing.logistics.iam.dto.request.LogoutRequest;
import com.ridehailing.logistics.iam.dto.request.RefreshTokenRequest;
import com.ridehailing.logistics.iam.dto.request.RegisterRequest;
import com.ridehailing.logistics.iam.dto.response.AuthResponse;
import com.ridehailing.logistics.iam.dto.response.UserResponse;
import com.ridehailing.logistics.iam.mapper.UserMapper;
import com.ridehailing.logistics.iam.repository.RefreshTokenRepository;
import com.ridehailing.logistics.iam.repository.UserRepository;
import com.ridehailing.logistics.iam.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

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
  public AuthResponse login(LoginRequest request) {
    // FR-IAM-006: Do not reveal whether account exists when authentication fails
    User user =
        userRepository
            .findByPhone(request.phone())
            .orElseThrow(
                () ->
                    BaseDomainException.badRequest(
                        "INVALID_CREDENTIALS", "Invalid phone number or password"));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw BaseDomainException.badRequest(
          "INVALID_CREDENTIALS", "Invalid phone number or password");
    }

    if (user.getStatus() != AccountStatus.ACTIVE) {
      throw BaseDomainException.forbidden(
          "ACCOUNT_NOT_ACTIVE", "Account is not active or has been suspended");
    }

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
    io.jsonwebtoken.Claims claims;
    try {
      claims = jwtTokenProvider.parseClaims(request.refreshToken());
    } catch (Exception e) {
      throw BaseDomainException.badRequest(
          "INVALID_REFRESH_TOKEN", "Expired or invalid refresh token");
    }

    if (!jwtTokenProvider.isRefreshToken(claims)) {
      throw BaseDomainException.badRequest(
          "INVALID_REFRESH_TOKEN", "Provided token is not a refresh token");
    }

    String tokenHash = hashToken(request.refreshToken());
    RefreshToken storedToken =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    BaseDomainException.forbidden(
                        "INVALID_REFRESH_TOKEN", "Refresh token not recognized or revoked"));
    if (Boolean.TRUE.equals(storedToken.getRevoked())) {
      throw BaseDomainException.forbidden(
          "TOKEN_REVOKED", "Refresh token has been revoked");
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

    return AuthResponse.of(
        newAccessToken,
        newRefreshToken,
        SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS,
        userMapper.toResponse(user));
  }

  @Override
  @Transactional
  public void logout(LogoutRequest request) {
    if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
      return;
    }
    String token = request.refreshToken();
    String tokenHash = hashToken(token);

    UUID userId = null;
    try {
      var claims = jwtTokenProvider.parseClaims(token);
      userId = UUID.fromString(claims.getSubject());
    } catch (Exception e) {
      log.debug("Token claims parsing during logout: {}", e.getMessage());
    }

    Optional<RefreshToken> storedOpt = refreshTokenRepository.findByTokenHash(tokenHash);
    if (storedOpt.isPresent()) {
      RefreshToken tokenEntity = storedOpt.get();
      tokenEntity.setRevoked(true);
      refreshTokenRepository.save(tokenEntity);
      if (userId == null) {
        userId = tokenEntity.getUserId();
      }
    } else if (userId != null) {
      RefreshToken revokedToken =
          RefreshToken.builder()
              .userId(userId)
              .tokenHash(tokenHash)
              .expiresAt(Instant.now().plusSeconds(SecurityConstants.REFRESH_TOKEN_VALIDITY_SECONDS))
              .revoked(true)
              .build();
      refreshTokenRepository.save(revokedToken);
    }

    log.info("AUDIT LOG: [action=LOGOUT, userId={}, tokenHash={}] - Refresh token successfully revoked", userId, tokenHash);
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
    try {
      String hash = hashToken(rawToken);
      var claims = jwtTokenProvider.parseClaims(rawToken);
      Instant expiresAt = claims.getExpiration().toInstant();
      RefreshToken entity =
          RefreshToken.builder()
              .userId(userId)
              .tokenHash(hash)
              .expiresAt(expiresAt)
              .revoked(false)
              .build();
      refreshTokenRepository.save(entity);
    } catch (Exception e) {
      log.warn("Failed to persist refresh token: {}", e.getMessage());
    }
  }
}
