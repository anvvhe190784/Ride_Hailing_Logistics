package com.ridehailing.logistics.pricing.dto;

import com.ridehailing.logistics.common.domain.enums.ServiceType;
import jakarta.validation.constraints.NotNull;

public record FareEstimateRequest(
        @NotNull(message = "Service type is required")
        ServiceType serviceType,

        String regionCode,

        @NotNull(message = "Pickup latitude is required")
        Double pickupLat,

        @NotNull(message = "Pickup longitude is required")
        Double pickupLng,

        String pickupAddress,

        @NotNull(message = "Dropoff latitude is required")
        Double dropoffLat,

        @NotNull(message = "Dropoff longitude is required")
        Double dropoffLng,

        String dropoffAddress
) {}
