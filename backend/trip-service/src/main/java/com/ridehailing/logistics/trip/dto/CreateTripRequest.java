package com.ridehailing.logistics.trip.dto;

import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTripRequest(
    @NotNull(message = "Quote ID is required") UUID quoteId,
    @NotBlank(message = "Idempotency key is required") String idempotencyKey,
    @NotNull(message = "Payment method is required") PaymentMethod paymentMethod) {}
