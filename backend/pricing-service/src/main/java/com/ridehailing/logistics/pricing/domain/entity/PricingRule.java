package com.ridehailing.logistics.pricing.domain.entity;

import com.ridehailing.logistics.common.domain.enums.ServiceType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "pricing_rules", schema = "pricing")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PricingRule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "service_type", nullable = false, length = 32)
  private ServiceType serviceType;

  @Column(name = "region_code", nullable = false, length = 32)
  private String regionCode;

  @Column(name = "base_fare", nullable = false, precision = 12, scale = 2)
  private BigDecimal baseFare;

  @Column(name = "per_km_rate", nullable = false, precision = 12, scale = 2)
  private BigDecimal perKmRate;

  @Column(name = "per_minute_rate", nullable = false, precision = 12, scale = 2)
  private BigDecimal perMinuteRate;

  @Column(name = "minimum_fare", nullable = false, precision = 12, scale = 2)
  private BigDecimal minimumFare;

  @Column(name = "version", nullable = false)
  @Builder.Default
  private Integer version = 1;

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  @Column(name = "effective_to")
  private Instant effectiveTo;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private Boolean isActive = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PricingRule other)) return false;
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
