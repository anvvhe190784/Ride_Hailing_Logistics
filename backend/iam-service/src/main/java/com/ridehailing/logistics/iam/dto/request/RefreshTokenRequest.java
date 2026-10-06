package com.ridehailing.logistics.iam.dto.request;

import jakarta.validation.constraints.NotBlank;

@SuppressWarnings("unused")
public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required") String refreshToken) {}
