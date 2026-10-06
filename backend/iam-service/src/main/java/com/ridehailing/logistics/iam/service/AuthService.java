package com.ridehailing.logistics.iam.service;

import com.ridehailing.logistics.iam.dto.request.LoginRequest;
import com.ridehailing.logistics.iam.dto.request.RefreshTokenRequest;
import com.ridehailing.logistics.iam.dto.request.RegisterRequest;
import com.ridehailing.logistics.iam.dto.response.AuthResponse;
import com.ridehailing.logistics.iam.dto.response.UserResponse;
import java.util.UUID;

public interface AuthService {
  AuthResponse register(RegisterRequest request);

  AuthResponse login(LoginRequest request);

  AuthResponse refreshToken(RefreshTokenRequest request);

  UserResponse getUserProfile(UUID userId);
}
