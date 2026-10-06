package com.ridehailing.logistics.common.dto;

import com.ridehailing.logistics.common.domain.enums.ServiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FareQuoteResponse(
    UUID quoteId,
    UUID customerId,
    ServiceType serviceType,
    String pickupAddress,
    String dropoffAddress,
    Integer estimatedDistanceMeters,
    Integer estimatedDurationSeconds,
    BigDecimal basePrice,
    BigDecimal distancePrice,
    BigDecimal durationPrice,
    BigDecimal surgeMultiplier,
    BigDecimal totalFare,
    String currency,
    Integer ruleVersion,
    Instant expiresAt) {}
