package com.ridehailing.logistics.trip.domain.entity;

import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.common.domain.enums.ServiceType;
import com.ridehailing.logistics.common.domain.enums.TripStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trips", schema = "trip")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Trip {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trip_code", nullable = false, unique = true, length = 32)
  private String tripCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "service_type", nullable = false, length = 32)
  private ServiceType serviceType;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "driver_id")
  private UUID driverId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  @Builder.Default
  private TripStatus status = TripStatus.CREATED;

  @Column(name = "quote_id", nullable = false)
  private UUID quoteId;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
  private String idempotencyKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "quote_snapshot", nullable = false)
  private String quoteSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "delivery_package_snapshot")
  private String deliveryPackageSnapshot;

  @Column(name = "cancellation_reason", columnDefinition = "TEXT")
  private String cancellationReason;

  @Column(name = "cancelled_by")
  private UUID cancelledBy;

  @Column(name = "cancellation_fee", precision = 12, scale = 2)
  @Builder.Default
  private BigDecimal cancellationFee = BigDecimal.ZERO;

  @Column(name = "final_fare", precision = 12, scale = 2)
  private BigDecimal finalFare;

  @Column(nullable = false, length = 3)
  @Builder.Default
  private String currency = "VND";

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 32)
  private PaymentMethod paymentMethod;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Trip other)) return false;
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
