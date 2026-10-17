package com.ridehailing.logistics.iam.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.AccountStatus;
import com.ridehailing.logistics.common.domain.enums.UserRole;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.common.security.JwtTokenProvider;
import com.ridehailing.logistics.iam.domain.entity.User;
import com.ridehailing.logistics.iam.dto.request.LoginRequest;
import com.ridehailing.logistics.iam.dto.request.RefreshTokenRequest;
import com.ridehailing.logistics.iam.dto.request.RegisterRequest;
import com.ridehailing.logistics.iam.dto.response.AuthResponse;
import com.ridehailing.logistics.iam.dto.response.UserResponse;
import com.ridehailing.logistics.iam.mapper.UserMapper;
import com.ridehailing.logistics.iam.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

  @Mock private UserRepository userRepository;
  @Mock private UserMapper userMapper;
  @Mock private PasswordEncoder passwordEncoder;

  private JwtTokenProvider jwtTokenProvider;
  private AuthServiceImpl authService;

  @BeforeEach
  void setUp() {
    jwtTokenProvider =
        new JwtTokenProvider("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    authService =
        new AuthServiceImpl(userRepository, userMapper, passwordEncoder, jwtTokenProvider);
  }

  @Test
  void shouldAllowRegistrationForCustomerAndDriver() {
    RegisterRequest request =
        new RegisterRequest("+84987654321", "cust@example.com", "password123", "Customer Name", UserRole.CUSTOMER);
    when(userRepository.existsByPhone(request.phone())).thenReturn(false);
    when(userRepository.existsByEmail(request.email())).thenReturn(false);
    when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

    UUID userId = UUID.randomUUID();
    User savedUser =
        User.builder()
            .id(userId)
            .phone(request.phone())
            .email(request.email())
            .passwordHash("hashedPassword")
            .fullName(request.fullName())
            .role(request.role())
            .status(AccountStatus.ACTIVE)
            .build();
    when(userRepository.save(any(User.class))).thenReturn(savedUser);
    when(userMapper.toResponse(any())).thenReturn(
        new UserResponse(userId, request.phone(), request.email(), request.fullName(), null, UserRole.CUSTOMER, AccountStatus.ACTIVE, Instant.now()));

    AuthResponse response = authService.register(request);

    assertNotNull(response);
    assertNotNull(response.accessToken());
    verify(userRepository).save(any(User.class));
  }

  @Test
  void shouldRejectRegistrationForPrivilegedRoles() {
    RegisterRequest adminRequest =
        new RegisterRequest("+84987654321", "admin@example.com", "password123", "Admin User", UserRole.ADMIN);

    BaseDomainException ex =
        assertThrows(BaseDomainException.class, () -> authService.register(adminRequest));
    assertEquals(400, ex.getHttpStatus());
    assertEquals("INVALID_REGISTRATION_ROLE", ex.getErrorCode());
  }

  @Test
  void shouldRejectLoginWhenAccountIsSuspended() {
    LoginRequest loginRequest = new LoginRequest("+84987654321", "password123");
    User suspendedUser =
        User.builder()
            .id(UUID.randomUUID())
            .phone(loginRequest.phone())
            .passwordHash("hashed")
            .role(UserRole.CUSTOMER)
            .status(AccountStatus.SUSPENDED)
            .build();

    when(userRepository.findByPhone(loginRequest.phone())).thenReturn(Optional.of(suspendedUser));
    when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

    BaseDomainException ex =
        assertThrows(BaseDomainException.class, () -> authService.login(loginRequest));
    assertEquals(403, ex.getHttpStatus());
    assertEquals("ACCOUNT_NOT_ACTIVE", ex.getErrorCode());
  }

  @Test
  void shouldRejectRefreshTokenWhenAccountIsNotActive() {
    UUID userId = UUID.randomUUID();
    String refreshToken = jwtTokenProvider.generateRefreshToken(userId);
    RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);

    User suspendedUser =
        User.builder()
            .id(userId)
            .phone("+84987654321")
            .role(UserRole.CUSTOMER)
            .status(AccountStatus.SUSPENDED)
            .build();
    when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

    BaseDomainException ex =
        assertThrows(BaseDomainException.class, () -> authService.refreshToken(refreshRequest));
    assertEquals(403, ex.getHttpStatus());
    assertEquals("ACCOUNT_NOT_ACTIVE", ex.getErrorCode());
  }
}
