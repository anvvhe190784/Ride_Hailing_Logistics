package com.ridehailing.logistics.iam.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "refresh_tokens", schema = "iam")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 255)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Builder.Default
  @Column(name = "revoked", nullable = false)
  private Boolean revoked = false;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
