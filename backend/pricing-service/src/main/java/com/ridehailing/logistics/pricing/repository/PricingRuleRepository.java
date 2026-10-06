package com.ridehailing.logistics.pricing.repository;

import com.ridehailing.logistics.common.domain.enums.ServiceType;
import com.ridehailing.logistics.pricing.domain.entity.PricingRule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unused")
public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {
  Optional<PricingRule> findFirstByServiceTypeAndRegionCodeAndIsActiveTrueOrderByVersionDesc(
      ServiceType serviceType, String regionCode);
}
