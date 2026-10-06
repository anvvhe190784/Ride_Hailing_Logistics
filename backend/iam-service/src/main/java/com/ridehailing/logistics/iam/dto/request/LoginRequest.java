package com.ridehailing.logistics.iam.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Phone number is required") String phone,
    @NotBlank(message = "Password is required") String password) {}
