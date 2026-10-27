package com.ridehailing.logistics.trip.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CancellationFeeResponse(
    UUID tripId,
    BigDecimal cancellationFee,
    String currency,
    boolean gracePeriodActive,
    String policyDescription) {}
