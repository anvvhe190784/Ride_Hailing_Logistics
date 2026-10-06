package com.ridehailing.logistics.common.event;

import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.common.domain.enums.ServiceType;
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
public class TripCompletedEvent {
    private UUID tripId;
    private String tripCode;
    private UUID customerId;
    private UUID driverId;
    private ServiceType serviceType;
    private BigDecimal finalFare;
    private String currency;
    private PaymentMethod paymentMethod;
    private Instant completedAt;
}
