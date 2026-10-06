package com.ridehailing.logistics.common.event;

import com.ridehailing.logistics.common.domain.enums.ServiceType;
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
@SuppressWarnings("unused")
public class TripRequestedEvent {
  private UUID tripId;
  private String tripCode;
  private ServiceType serviceType;
  private UUID customerId;
  private double pickupLatitude;
  private double pickupLongitude;
  private String pickupAddress;
  private double dropoffLatitude;
  private double dropoffLongitude;
  private String dropoffAddress;
  private BigDecimal estimatedFare;
  private Instant requestedAt;
}
