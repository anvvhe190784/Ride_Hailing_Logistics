package com.ridehailing.logistics.pricing.domain.entity;

import com.ridehailing.logistics.common.domain.enums.ServiceType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "fare_quotes", schema = "pricing")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FareQuote {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "service_type", nullable = false, length = 32)
  private ServiceType serviceType;

  @Column(name = "pickup_point", columnDefinition = "geometry(Point,4326)", nullable = false)
  private Point pickupPoint;

  @Column(name = "pickup_address", nullable = false, columnDefinition = "TEXT")
  private String pickupAddress;

  @Column(name = "dropoff_point", columnDefinition = "geometry(Point,4326)", nullable = false)
  private Point dropoffPoint;

  @Column(name = "dropoff_address", nullable = false, columnDefinition = "TEXT")
  private String dropoffAddress;

  @Column(name = "estimated_distance_meters", nullable = false)
  private Integer estimatedDistanceMeters;

  @Column(name = "estimated_duration_seconds", nullable = false)
  private Integer estimatedDurationSeconds;

  @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal basePrice;

  @Column(name = "distance_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal distancePrice;

  @Column(name = "duration_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal durationPrice;

  @Column(name = "surge_multiplier", nullable = false, precision = 4, scale = 2)
  @Builder.Default
  private BigDecimal surgeMultiplier = BigDecimal.valueOf(1.00);

  @Column(name = "total_fare", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalFare;

  @Column(nullable = false, length = 3)
  @Builder.Default
  private String currency = "VND";

  @Column(name = "rule_version", nullable = false)
  private Integer ruleVersion;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FareQuote other)) return false;
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
