package com.ridehailing.logistics.pricing.repository;

import com.ridehailing.logistics.common.domain.enums.ServiceType;
import com.ridehailing.logistics.pricing.domain.entity.PricingRule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unused")
public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {
  Optional<PricingRule> findFirstByServiceTypeAndRegionCodeAndIsActiveTrueOrderByVersionDesc(
      ServiceType serviceType, String regionCode);

  @Query("""
      SELECT r FROM PricingRule r
      WHERE r.serviceType = :serviceType
        AND r.regionCode = :regionCode
        AND r.isActive = true
        AND r.effectiveFrom <= :now
        AND (r.effectiveTo IS NULL OR r.effectiveTo >= :now)
      ORDER BY r.version DESC
  """)
  List<PricingRule> findActiveRules(
      @Param("serviceType") ServiceType serviceType,
      @Param("regionCode") String regionCode,
      @Param("now") Instant now);

  default Optional<PricingRule> findActiveRule(
      ServiceType serviceType, String regionCode, Instant now) {
    return findActiveRules(serviceType, regionCode, now).stream().findFirst();
  }
}
