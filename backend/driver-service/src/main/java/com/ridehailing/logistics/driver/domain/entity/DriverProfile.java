package com.ridehailing.logistics.driver.domain.entity;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.DriverReviewStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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

    @Column(name = "id_card_number", unique = true, length = 50)
    private String idCardNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 32)
    @Builder.Default
    private DriverReviewStatus reviewStatus = DriverReviewStatus.DRAFT;

    @Column(name = "status_reason", columnDefinition = "TEXT")
    private String statusReason;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 32)
    @Builder.Default
    private DriverAvailabilityStatus availabilityStatus = DriverAvailabilityStatus.OFFLINE;

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
