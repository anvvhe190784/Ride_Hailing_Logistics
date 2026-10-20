package com.ridehailing.logistics.iam.repository;

import com.ridehailing.logistics.iam.domain.entity.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  void deleteByTokenHash(String tokenHash);

  void deleteByUserId(UUID userId);
}
