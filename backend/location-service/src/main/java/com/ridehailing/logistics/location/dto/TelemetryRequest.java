package com.ridehailing.logistics.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record TelemetryRequest(
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
        @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
        BigDecimal latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
        @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
        BigDecimal longitude,

        BigDecimal accuracyMeters,
        BigDecimal headingDegrees,
        BigDecimal speedMps,

        @NotNull(message = "Sequence number is required")
        Long sequenceNum,

        @NotNull(message = "Device timestamp is required")
        Instant deviceTimestamp
) {}
