package com.ridehailing.logistics.pricing.dto;

import com.ridehailing.logistics.common.domain.enums.ServiceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record FareEstimateRequest(
    @NotNull(message = "Service type is required") ServiceType serviceType,
    String regionCode,
    @NotNull(message = "Pickup latitude is required")
        @DecimalMin(value = "-90.0", message = "Pickup latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Pickup latitude must be between -90 and 90")
        Double pickupLat,
    @NotNull(message = "Pickup longitude is required")
        @DecimalMin(value = "-180.0", message = "Pickup longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Pickup longitude must be between -180 and 180")
        Double pickupLng,
    String pickupAddress,
    @NotNull(message = "Dropoff latitude is required")
        @DecimalMin(value = "-90.0", message = "Dropoff latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Dropoff latitude must be between -90 and 90")
        Double dropoffLat,
    @NotNull(message = "Dropoff longitude is required")
        @DecimalMin(value = "-180.0", message = "Dropoff longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Dropoff longitude must be between -180 and 180")
        Double dropoffLng,
    String dropoffAddress) {}
