package com.ridehailing.logistics.driver.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "driver_documents", schema = "driver")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DriverDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "driver_id", nullable = false)
  private UUID driverId;

  @Column(nullable = false, length = 32)
  private String type;

  @Column(name = "document_number", length = 100)
  private String documentNumber;

  @Column(name = "expiry_date")
  private LocalDate expiryDate;

  @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
  private String fileUrl;

  @Column(nullable = false, length = 32)
  @Builder.Default
  private String status = "PENDING";

  @Column(name = "rejection_reason", columnDefinition = "TEXT")
  private String rejectionReason;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DriverDocument other)) return false;
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
