package com.ridehailing.logistics.trip.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "trip_stops", schema = "trip")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuppressWarnings("JpaDataSourceORMInspection")
public class TripStop {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trip_id", nullable = false)
  private UUID tripId;

  @Column(name = "stop_sequence", nullable = false)
  private Integer stopSequence;

  @Column(nullable = false)
  private String address;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal longitude;

  @Column(name = "contact_name", length = 100)
  private String contactName;

  @Column(name = "contact_phone", length = 20)
  private String contactPhone;

  @Column(nullable = false, length = 20)
  @Builder.Default
  private String status = "PENDING";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TripStop other)) return false;
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
