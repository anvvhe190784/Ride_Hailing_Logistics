package com.ridehailing.logistics.driver.dto.request;

import com.ridehailing.logistics.common.domain.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleCreateRequest(
    @NotNull(message = "Vehicle type is required") VehicleType type,
    @NotBlank(message = "License plate is required") String licensePlate,
    String brand,
    String model,
    String color) {}
