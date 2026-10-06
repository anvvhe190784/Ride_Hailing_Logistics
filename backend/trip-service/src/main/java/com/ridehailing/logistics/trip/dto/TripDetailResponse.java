package com.ridehailing.logistics.trip.dto;

import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.common.domain.enums.ServiceType;
import com.ridehailing.logistics.common.domain.enums.TripStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TripDetailResponse(
    UUID tripId,
    String tripCode,
    ServiceType serviceType,
    UUID customerId,
    UUID driverId,
    TripStatus status,
    UUID quoteId,
    BigDecimal finalFare,
    String currency,
    PaymentMethod paymentMethod,
    Instant createdAt,
    Instant completedAt) {}
