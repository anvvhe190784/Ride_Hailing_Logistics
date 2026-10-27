package com.ridehailing.logistics.location.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

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

  @Column(name = "accuracy_meters")
  private Double accuracyMeters;

  @Column(name = "heading_degrees")
  private Double headingDegrees;

  @Column(name = "speed_mps")
  private Double speedMps;

  @Column(name = "is_suspicious_gps", nullable = false)
  @Builder.Default
  private boolean isSuspiciousGps = false;

  @Column(name = "is_available", nullable = false)
  @Builder.Default
  private boolean isAvailable = true;

  @Column(name = "sequence_num", nullable = false)
  private Long sequenceNum;

  @Column(name = "device_timestamp", nullable = false)
  private Instant deviceTimestamp;

  @Column(name = "server_timestamp", nullable = false)
  @Builder.Default
  private Instant serverTimestamp = Instant.now();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Instant getServerTimestamp() {
    return serverTimestamp != null ? serverTimestamp : updatedAt;
  }

  public void setServerTimestamp(Instant serverTimestamp) {
    this.serverTimestamp = serverTimestamp;
    this.updatedAt = serverTimestamp;
  }

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
