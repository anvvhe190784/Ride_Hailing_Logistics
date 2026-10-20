package com.ridehailing.logistics.trip.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "trip_status_history", schema = "trip")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TripStatusHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "trip_id", nullable = false)
  private UUID tripId;

  @Column(name = "from_status", length = 32)
  private String fromStatus;

  @Column(name = "to_status", nullable = false, length = 32)
  private String toStatus;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "actor_role", length = 32)
  private String actorRole;

  @Column(columnDefinition = "TEXT")
  private String reason;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
