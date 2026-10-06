package com.ridehailing.logistics.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSucceededEvent {
    private UUID paymentId;
    private UUID tripId;
    private UUID customerId;
    private UUID driverId;
    private BigDecimal amount;
    private String currency;
    private Instant paidAt;
}
