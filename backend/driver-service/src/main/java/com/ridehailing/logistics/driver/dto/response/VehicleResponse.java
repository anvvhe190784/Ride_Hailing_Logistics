package com.ridehailing.logistics.driver.dto.response;

import com.ridehailing.logistics.common.domain.enums.VehicleType;

import java.util.UUID;

public record VehicleResponse(
        UUID id,
        UUID driverId,
        VehicleType type,
        String licensePlate,
        String brand,
        String model,
        String color,
        String status
) {}
