package com.ridehailing.logistics.iam.dto.response;

import com.ridehailing.logistics.common.domain.enums.AccountStatus;
import com.ridehailing.logistics.common.domain.enums.UserRole;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String phone,
    String email,
    String fullName,
    String avatarUrl,
    UserRole role,
    AccountStatus status,
    Instant createdAt) {}
