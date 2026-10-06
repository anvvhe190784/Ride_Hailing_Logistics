package com.ridehailing.logistics.iam.repository;

import com.ridehailing.logistics.iam.domain.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unused")
public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByPhone(String phone);

  Optional<User> findByEmail(String email);

  boolean existsByPhone(String phone);

  boolean existsByEmail(String email);
}
