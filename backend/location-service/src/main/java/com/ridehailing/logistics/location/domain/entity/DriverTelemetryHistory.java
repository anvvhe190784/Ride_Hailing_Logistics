package com.ridehailing.logistics.location.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "driver_telemetry_history", schema = "location")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DriverTelemetryHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "driver_id", nullable = false)
  private UUID driverId;

  @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
  private Point location;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  @Column(name = "speed_mps")
  private Double speed;

  @Column(name = "heading_degrees")
  private Double bearing;

  @Column(name = "accuracy_meters")
  private Double accuracy;

  @Column(name = "recorded_at", nullable = false)
  @Builder.Default
  private Instant recordedAt = Instant.now();

  public Double getSpeedMps() {
    return speed;
  }

  public void setSpeedMps(Double speedMps) {
    this.speed = speedMps;
  }

  public Double getHeadingDegrees() {
    return bearing;
  }

  public void setHeadingDegrees(Double headingDegrees) {
    this.bearing = headingDegrees;
  }

  public Double getAccuracyMeters() {
    return accuracy;
  }

  public void setAccuracyMeters(Double accuracyMeters) {
    this.accuracy = accuracyMeters;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DriverTelemetryHistory other)) return false;
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
