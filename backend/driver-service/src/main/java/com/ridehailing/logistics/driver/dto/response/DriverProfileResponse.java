package com.ridehailing.logistics.driver.dto.response;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.DriverReviewStatus;

import java.util.UUID;

public record DriverProfileResponse(
        UUID driverId,
        String idCardNumber,
        String address,
        DriverReviewStatus reviewStatus,
        String statusReason,
        DriverAvailabilityStatus availabilityStatus
) {}
