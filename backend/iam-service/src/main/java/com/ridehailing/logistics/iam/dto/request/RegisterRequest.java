package com.ridehailing.logistics.iam.dto.request;

import com.ridehailing.logistics.common.domain.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Phone number is required")
        @Pattern(
            regexp = "^(0|\\+84)\\d{9,10}$",
            message = "Invalid Vietnamese phone number format")
        String phone,
    String email,
    @NotBlank(message = "Password is required")
        @Size(min = 6, max = 50, message = "Password must be between 6 and 50 characters")
        String password,
    @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,
    @NotNull(message = "Role is required") UserRole role) {}
