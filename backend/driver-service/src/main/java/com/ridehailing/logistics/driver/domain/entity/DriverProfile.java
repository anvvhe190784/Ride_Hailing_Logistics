package com.ridehailing.logistics.driver.domain.entity;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.DriverReviewStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "driver_profiles", schema = "driver")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DriverProfile {

  @Id
  @Column(name = "driver_id")
  private UUID driverId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "id_card_number", nullable = false, unique = true, length = 50)
  private String idCardNumber;

  @Column(name = "date_of_birth", nullable = false)
  private LocalDate dateOfBirth;

  @Column(name = "address", nullable = false)
  private String address;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false, length = 32)
  @Builder.Default
  private DriverReviewStatus reviewStatus = DriverReviewStatus.DRAFT;

  @Column(name = "status_reason")
  private String statusReason;

  @Column(name = "reviewer_id")
  private UUID reviewerId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "availability_status", nullable = false, length = 32)
  @Builder.Default
  private DriverAvailabilityStatus availabilityStatus = DriverAvailabilityStatus.OFFLINE;

  @Column(name = "rating", nullable = false, precision = 3, scale = 2)
  @Builder.Default
  private BigDecimal rating = new BigDecimal("5.00");

  @Column(name = "total_trips", nullable = false)
  @Builder.Default
  private Integer totalTrips = 0;

  @Version
  @Builder.Default
  private Long version = 0L;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DriverProfile other)) return false;
    return driverId != null && driverId.equals(other.getDriverId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
