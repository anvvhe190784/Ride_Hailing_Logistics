package com.ridehailing.logistics.trip.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_offers", schema = "trip",
       uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "driver_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DriverOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING"; // PENDING, ACCEPTED, REJECTED, EXPIRED

    @Column(name = "estimated_pickup_distance_meters")
    private Integer estimatedPickupDistanceMeters;

    @CreationTimestamp
    @Column(name = "offered_at", nullable = false, updatable = false)
    private Instant offeredAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DriverOffer other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
