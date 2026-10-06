package com.ridehailing.logistics.iam.service.impl;

import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.common.security.JwtTokenProvider;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.iam.domain.entity.User;
import com.ridehailing.logistics.iam.dto.request.LoginRequest;
import com.ridehailing.logistics.iam.dto.request.RefreshTokenRequest;
import com.ridehailing.logistics.iam.dto.request.RegisterRequest;
import com.ridehailing.logistics.iam.dto.response.AuthResponse;
import com.ridehailing.logistics.iam.dto.response.UserResponse;
import com.ridehailing.logistics.iam.mapper.UserMapper;
import com.ridehailing.logistics.iam.repository.UserRepository;
import com.ridehailing.logistics.iam.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider();

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByPhone(request.phone())) {
            throw BaseDomainException.conflict("PHONE_ALREADY_REGISTERED", "Phone number is already registered");
        }
        if (request.email() != null && userRepository.existsByEmail(request.email())) {
            throw BaseDomainException.conflict("EMAIL_ALREADY_REGISTERED", "Email is already registered");
        }

        User user = User.builder()
                .phone(request.phone())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(request.role())
                .build();

        user = userRepository.save(user);
        log.info("Registered new user ID: {} with role: {}", user.getId(), user.getRole());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.of(accessToken, refreshToken, SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS, userMapper.toResponse(user));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // FR-IAM-006: Do not reveal whether account exists when authentication fails
        User user = userRepository.findByPhone(request.phone())
                .orElseThrow(() -> BaseDomainException.badRequest("INVALID_CREDENTIALS", "Invalid phone number or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BaseDomainException.badRequest("INVALID_CREDENTIALS", "Invalid phone number or password");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.of(accessToken, refreshToken, SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS, userMapper.toResponse(user));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            throw BaseDomainException.badRequest("INVALID_REFRESH_TOKEN", "Expired or invalid refresh token");
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BaseDomainException.notFound("USER_NOT_FOUND", "User not found"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.of(newAccessToken, newRefreshToken, SecurityConstants.ACCESS_TOKEN_VALIDITY_SECONDS, userMapper.toResponse(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BaseDomainException.notFound("USER_NOT_FOUND", "User profile not found"));
        return userMapper.toResponse(user);
    }
}
