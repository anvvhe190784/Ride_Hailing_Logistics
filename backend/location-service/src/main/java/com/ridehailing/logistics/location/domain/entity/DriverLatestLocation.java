package com.ridehailing.logistics.location.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_latest_locations", schema = "location")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DriverLatestLocation {

    @Id
    @Column(name = "driver_id")
    private UUID driverId;

    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point location;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "accuracy_meters", precision = 6, scale = 2)
    private BigDecimal accuracyMeters;

    @Column(name = "heading_degrees", precision = 5, scale = 2)
    private BigDecimal headingDegrees;

    @Column(name = "speed_mps", precision = 6, scale = 2)
    private BigDecimal speedMps;

    @Column(name = "sequence_num", nullable = false)
    private Long sequenceNum;

    @Column(name = "device_timestamp", nullable = false)
    private Instant deviceTimestamp;

    @UpdateTimestamp
    @Column(name = "server_timestamp", nullable = false)
    private Instant serverTimestamp;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DriverLatestLocation other)) return false;
        return driverId != null && driverId.equals(other.getDriverId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
