package com.ridehailing.logistics.iam.repository;

import com.ridehailing.logistics.iam.domain.entity.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
  List<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
