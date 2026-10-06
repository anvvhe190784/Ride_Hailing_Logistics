package com.ridehailing.logistics.pricing.service.impl;

import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.pricing.domain.entity.FareQuote;
import com.ridehailing.logistics.pricing.domain.entity.PricingRule;
import com.ridehailing.logistics.pricing.dto.FareEstimateRequest;
import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import com.ridehailing.logistics.pricing.repository.FareQuoteRepository;
import com.ridehailing.logistics.pricing.repository.PricingRuleRepository;
import com.ridehailing.logistics.pricing.service.PricingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

  private final PricingRuleRepository pricingRuleRepository;
  private final FareQuoteRepository fareQuoteRepository;
  private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

  @Override
  @Transactional
  public FareQuoteResponse createQuote(UUID customerId, FareEstimateRequest request) {
    String region = request.regionCode() != null ? request.regionCode() : "HN";

    PricingRule rule =
        pricingRuleRepository
            .findFirstByServiceTypeAndRegionCodeAndIsActiveTrueOrderByVersionDesc(
                request.serviceType(), region)
            .orElseGet(
                () ->
                    PricingRule.builder()
                        .serviceType(request.serviceType())
                        .regionCode(region)
                        .baseFare(new BigDecimal("15000.00"))
                        .perKmRate(new BigDecimal("10000.00"))
                        .perMinuteRate(new BigDecimal("1000.00"))
                        .minimumFare(new BigDecimal("20000.00"))
                        .version(1)
                        .effectiveFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                        .isActive(true)
                        .build());

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
                Instant.now().plus(10, ChronoUnit.MINUTES)) // 10 mins validity per FR-PRI-013
            .build();

    quote = fareQuoteRepository.save(quote);
    log.info(
        "Generated FareQuote ID {} for Customer ID {}, total: {} VND",
        quote.getId(),
        customerId,
        totalFare);

    return toResponse(quote);
  }

  @Override
  @Transactional(readOnly = true)
  public FareQuoteResponse getQuote(UUID quoteId) {
    FareQuote quote =
        fareQuoteRepository
            .findById(quoteId)
            .orElseThrow(
                () -> BaseDomainException.notFound("QUOTE_NOT_FOUND", "Fare quote not found"));
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
        q.getExpiresAt());
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
