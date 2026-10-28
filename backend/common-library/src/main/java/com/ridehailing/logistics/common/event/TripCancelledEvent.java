package com.ridehailing.logistics.common.event;

import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCancelledEvent {
  private UUID tripId;
  private String tripCode;
  private UUID customerId;
  private UUID driverId;
  private BigDecimal cancellationFee;
  private String currency;
  private PaymentMethod paymentMethod;
  private UUID cancelledBy;
  private String cancellationReason;
  private Instant cancelledAt;
}
