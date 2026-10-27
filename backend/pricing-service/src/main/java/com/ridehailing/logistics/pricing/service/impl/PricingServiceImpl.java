package com.ridehailing.logistics.pricing.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.domain.enums.ServiceType;
import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.pricing.domain.entity.FareQuote;
import com.ridehailing.logistics.pricing.domain.entity.PricingRule;
import com.ridehailing.logistics.pricing.dto.FareEstimateRequest;
import com.ridehailing.logistics.pricing.repository.FareQuoteRepository;
import com.ridehailing.logistics.pricing.repository.PricingRuleRepository;
import com.ridehailing.logistics.pricing.service.PricingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PricingServiceImpl implements PricingService {

  public static final String REDIS_RULE_CACHE_PREFIX = "cache:pricing:rule:";
  public static final String REDIS_QUOTE_IDEMPOTENCY_PREFIX = "idempotency:pricing:quote:";

  private final PricingRuleRepository pricingRuleRepository;
  private final FareQuoteRepository fareQuoteRepository;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

  public PricingServiceImpl(
      PricingRuleRepository pricingRuleRepository,
      FareQuoteRepository fareQuoteRepository) {
    this(
        pricingRuleRepository,
        fareQuoteRepository,
        null,
        new ObjectMapper().findAndRegisterModules());
  }

  @Autowired
  public PricingServiceImpl(
      PricingRuleRepository pricingRuleRepository,
      FareQuoteRepository fareQuoteRepository,
      @Autowired(required = false) StringRedisTemplate redisTemplate,
      @Autowired(required = false) ObjectMapper objectMapper) {
    this.pricingRuleRepository = pricingRuleRepository;
    this.fareQuoteRepository = fareQuoteRepository;
    this.redisTemplate = redisTemplate;
    this.objectMapper =
        objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
  }

  @Override
  @Transactional
  public FareQuoteResponse createQuote(UUID customerId, FareEstimateRequest request) {
    return createQuote(customerId, request, null);
  }

  @Override
  @Transactional
  public FareQuoteResponse createQuote(
      UUID customerId, FareEstimateRequest request, String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank() && redisTemplate != null) {
      try {
        String cachedQuoteJson =
            redisTemplate.opsForValue().get(REDIS_QUOTE_IDEMPOTENCY_PREFIX + idempotencyKey);
        if (cachedQuoteJson != null && !cachedQuoteJson.isBlank()) {
          log.info("Returning cached fare quote for idempotency key: {}", idempotencyKey);
          return objectMapper.readValue(cachedQuoteJson, FareQuoteResponse.class);
        }
      } catch (Exception e) {
        log.warn(
            "Failed to read or deserialize cached quote for idempotency key {}: {}",
            idempotencyKey,
            e.getMessage());
      }
    }

    String region = request.regionCode() != null ? request.regionCode() : "HN";

    PricingRule rule = getCachedOrFetchRule(request.serviceType(), region);

    // Distance estimation (A-04 fallback: Haversine distance * 1.3 routing factor)
    double distanceMetersRaw =
        calculateHaversine(
                request.pickupLat(), request.pickupLng(),
                request.dropoffLat(), request.dropoffLng())
            * 1.3;
    int distanceMeters = Math.max(500, (int) Math.round(distanceMetersRaw));
    int durationSeconds =
        Math.max(180, (int) Math.round((distanceMeters / 1000.0) * 150)); // ~25 km/h

    BigDecimal km =
        BigDecimal.valueOf(distanceMeters)
            .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
    BigDecimal minutes =
        BigDecimal.valueOf(durationSeconds).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

    BigDecimal distancePrice = km.multiply(rule.getPerKmRate()).setScale(2, RoundingMode.HALF_UP);
    BigDecimal durationPrice =
        minutes.multiply(rule.getPerMinuteRate()).setScale(2, RoundingMode.HALF_UP);
    BigDecimal subTotal = rule.getBaseFare().add(distancePrice).add(durationPrice);

    BigDecimal surge = BigDecimal.valueOf(1.00); // Base surge
    BigDecimal totalFare = subTotal.multiply(surge).setScale(2, RoundingMode.HALF_UP);
    if (totalFare.compareTo(rule.getMinimumFare()) < 0) {
      totalFare = rule.getMinimumFare();
    }

    Point pickupPoint =
        geometryFactory.createPoint(new Coordinate(request.pickupLng(), request.pickupLat()));
    Point dropoffPoint =
        geometryFactory.createPoint(new Coordinate(request.dropoffLng(), request.dropoffLat()));

    FareQuote quote =
        FareQuote.builder()
            .customerId(customerId)
            .serviceType(request.serviceType())
            .pickupPoint(pickupPoint)
            .pickupAddress(
                request.pickupAddress() != null ? request.pickupAddress() : "Pickup Location")
            .dropoffPoint(dropoffPoint)
            .dropoffAddress(
                request.dropoffAddress() != null ? request.dropoffAddress() : "Dropoff Location")
            .estimatedDistanceMeters(distanceMeters)
            .estimatedDurationSeconds(durationSeconds)
            .basePrice(rule.getBaseFare())
            .distancePrice(distancePrice)
            .durationPrice(durationPrice)
            .surgeMultiplier(surge)
            .totalFare(totalFare)
            .currency("VND")
            .ruleVersion(rule.getVersion())
            .expiresAt(
                Instant.now()
                    .plus(
                        600 + ThreadLocalRandom.current().nextLong(-60, 61),
                        ChronoUnit.SECONDS)) // 10 mins validity with ±60s jitter against expiry cliff
            .build();

    quote = fareQuoteRepository.save(quote);
    log.info(
        "Generated FareQuote ID {} for Customer ID {}, total: {} VND",
        quote.getId(),
        customerId,
        totalFare);

    FareQuoteResponse response = toResponse(quote);
    if (idempotencyKey != null && !idempotencyKey.isBlank() && redisTemplate != null) {
      try {
        String json = objectMapper.writeValueAsString(response);
        redisTemplate
            .opsForValue()
            .set(REDIS_QUOTE_IDEMPOTENCY_PREFIX + idempotencyKey, json, Duration.ofMinutes(10));
      } catch (Exception e) {
        log.warn(
            "Failed to cache quote idempotency for key {}: {}", idempotencyKey, e.getMessage());
      }
    }

    return response;
  }

  private PricingRule getCachedOrFetchRule(ServiceType serviceType, String region) {
    String redisKey = REDIS_RULE_CACHE_PREFIX + serviceType.name() + ":" + region;
    if (redisTemplate != null) {
      try {
        String cachedRuleJson = redisTemplate.opsForValue().get(redisKey);
        if (cachedRuleJson != null && !cachedRuleJson.isBlank()) {
          return objectMapper.readValue(cachedRuleJson, PricingRule.class);
        }
      } catch (Exception e) {
        log.warn(
            "Failed to read or deserialize cached pricing rule for key {}: {}",
            redisKey,
            e.getMessage());
      }
    }

    Instant now = Instant.now();
    PricingRule rule =
        pricingRuleRepository
            .findActiveRule(serviceType, region, now)
            .or(
                () ->
                    pricingRuleRepository
                        .findFirstByServiceTypeAndRegionCodeAndIsActiveTrueOrderByVersionDesc(
                            serviceType, region))
            .orElseGet(
                () ->
                    PricingRule.builder()
                        .serviceType(serviceType)
                        .regionCode(region)
                        .baseFare(new BigDecimal("15000.00"))
                        .perKmRate(new BigDecimal("10000.00"))
                        .perMinuteRate(new BigDecimal("1000.00"))
                        .minimumFare(new BigDecimal("20000.00"))
                        .version(1)
                        .effectiveFrom(now.minus(1, ChronoUnit.DAYS))
                        .isActive(true)
                        .build());

    if (redisTemplate != null) {
      try {
        String ruleJson = objectMapper.writeValueAsString(rule);
        redisTemplate.opsForValue().set(redisKey, ruleJson, Duration.ofMinutes(5));
      } catch (Exception e) {
        log.warn("Failed to cache pricing rule for key {}: {}", redisKey, e.getMessage());
      }
    }

    return rule;
  }

  @Override
  @Transactional(readOnly = true)
  public FareQuoteResponse getQuote(UUID quoteId) {
    return getQuote(quoteId, null, null);
  }

  @Override
  @Transactional(readOnly = true)
  public FareQuoteResponse getQuote(UUID quoteId, UUID requesterId, String requesterRole) {
    FareQuote quote =
        fareQuoteRepository
            .findById(quoteId)
            .orElseThrow(
                () -> BaseDomainException.notFound("QUOTE_NOT_FOUND", "Fare quote not found"));

    if (requesterId != null
        && !"ADMIN".equalsIgnoreCase(requesterRole)
        && !quote.getCustomerId().equals(requesterId)) {
      throw BaseDomainException.forbidden(
          "FORBIDDEN_ACCESS", "You do not have permission to view this fare quote");
    }

    return toResponse(quote);
  }

  @Override
  @Transactional
  public FareQuoteResponse consumeQuote(UUID quoteId, UUID customerId) {
    Instant now = Instant.now();
    int updatedRows = fareQuoteRepository.consumeQuoteAtomically(quoteId, customerId, now);

    if (updatedRows == 0) {
      FareQuote existing =
          fareQuoteRepository
              .findById(quoteId)
              .orElseThrow(
                  () -> BaseDomainException.notFound("QUOTE_NOT_FOUND", "Fare quote not found"));

      if (!existing.getCustomerId().equals(customerId)) {
        throw BaseDomainException.forbidden(
            "QUOTE_ACCESS_DENIED", "Quote does not belong to customer " + customerId);
      }

      if (existing.isUsed()) {
        throw BaseDomainException.conflict(
            "QUOTE_ALREADY_USED", "Fare quote has already been consumed");
      }

      if (existing.getExpiresAt().isBefore(now)) {
        throw BaseDomainException.badRequest("QUOTE_EXPIRED", "Fare quote has expired");
      }

      throw BaseDomainException.conflict(
          "QUOTE_ALREADY_USED", "Fare quote could not be consumed");
    }

    FareQuote quote =
        fareQuoteRepository
            .findById(quoteId)
            .orElseThrow(
                () -> BaseDomainException.notFound("QUOTE_NOT_FOUND", "Fare quote not found"));

    log.info("Fare quote {} successfully consumed for customer {}", quoteId, customerId);

    return toResponse(quote);
  }

  private FareQuoteResponse toResponse(FareQuote q) {
    return new FareQuoteResponse(
        q.getId(),
        q.getCustomerId(),
        q.getServiceType(),
        q.getPickupAddress(),
        q.getDropoffAddress(),
        q.getEstimatedDistanceMeters(),
        q.getEstimatedDurationSeconds(),
        q.getBasePrice(),
        q.getDistancePrice(),
        q.getDurationPrice(),
        q.getSurgeMultiplier(),
        q.getTotalFare(),
        q.getCurrency(),
        q.getRuleVersion(),
        q.getExpiresAt(),
        q.getPickupPoint() != null ? q.getPickupPoint().getY() : null,
        q.getPickupPoint() != null ? q.getPickupPoint().getX() : null,
        q.getDropoffPoint() != null ? q.getDropoffPoint().getY() : null,
        q.getDropoffPoint() != null ? q.getDropoffPoint().getX() : null);
  }

  private double calculateHaversine(double lat1, double lon1, double lat2, double lon2) {
    final int R = 6371000; // Radius of the earth in meters
    double latDistance = Math.toRadians(lat2 - lat1);
    double lonDistance = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2)
                * Math.sin(lonDistance / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }
}
