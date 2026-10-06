package com.ridehailing.logistics.trip.client;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.dto.DriverProfileResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class DriverClientFallback implements DriverClient {

    @Override
    public ApiResponse<DriverProfileResponse> getDriverProfile(UUID driverId) {
        log.warn("Fallback triggered for DriverClient.getDriverProfile: {}", driverId);
        return ApiResponse.error("DRIVER_SERVICE_UNAVAILABLE", "Driver service is currently unavailable", null);
    }

    @Override
    public ApiResponse<DriverProfileResponse> updateDriverAvailability(DriverAvailabilityStatus status) {
        log.warn("Fallback triggered for DriverClient.updateDriverAvailability: {}", status);
        return ApiResponse.error("DRIVER_SERVICE_UNAVAILABLE", "Driver service is currently unavailable", null);
    }
}
