package com.ridehailing.logistics.pricing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.domain.enums.ServiceType;
import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.pricing.domain.entity.FareQuote;
import com.ridehailing.logistics.pricing.domain.entity.PricingRule;
import com.ridehailing.logistics.pricing.dto.FareEstimateRequest;
import com.ridehailing.logistics.pricing.repository.FareQuoteRepository;
import com.ridehailing.logistics.pricing.repository.PricingRuleRepository;
import com.ridehailing.logistics.pricing.service.impl.PricingServiceImpl;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

  @Mock private PricingRuleRepository pricingRuleRepository;
  @Mock private FareQuoteRepository fareQuoteRepository;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private PricingServiceImpl pricingService;

  private PricingRule mockRule;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    pricingService =
        new PricingServiceImpl(
            pricingRuleRepository, fareQuoteRepository, redisTemplate, objectMapper);

    mockRule =
        PricingRule.builder()
            .id(UUID.randomUUID())
            .serviceType(ServiceType.RIDE)
            .regionCode("HN")
            .baseFare(new BigDecimal("15000.00"))
            .perKmRate(new BigDecimal("10000.00"))
            .perMinuteRate(new BigDecimal("1000.00"))
            .minimumFare(new BigDecimal("20000.00"))
            .version(1)
            .effectiveFrom(Instant.now())
            .isActive(true)
            .build();
  }

  @Test
  void shouldCreateFareQuoteSuccessfully() {
    UUID customerId = UUID.randomUUID();
    FareEstimateRequest request =
        new FareEstimateRequest(
            ServiceType.RIDE,
            "HN",
            21.028511,
            105.804817,
            "Hanoi Opera House",
            21.036237,
            105.834641,
            "West Lake Hanoi");

    when(pricingRuleRepository.findFirstByServiceTypeAndRegionCodeAndIsActiveTrueOrderByVersionDesc(
            ServiceType.RIDE, "HN"))
        .thenReturn(Optional.of(mockRule));

    when(fareQuoteRepository.save(any(FareQuote.class)))
        .thenAnswer(
            invocation -> {
              FareQuote saved = invocation.getArgument(0);
              return FareQuote.builder()
                  .id(UUID.randomUUID())
                  .customerId(saved.getCustomerId())
                  .serviceType(saved.getServiceType())
                  .pickupPoint(saved.getPickupPoint())
                  .pickupAddress(saved.getPickupAddress())
                  .dropoffPoint(saved.getDropoffPoint())
                  .dropoffAddress(saved.getDropoffAddress())
                  .estimatedDistanceMeters(saved.getEstimatedDistanceMeters())
                  .estimatedDurationSeconds(saved.getEstimatedDurationSeconds())
                  .basePrice(saved.getBasePrice())
                  .distancePrice(saved.getDistancePrice())
                  .durationPrice(saved.getDurationPrice())
                  .surgeMultiplier(saved.getSurgeMultiplier())
                  .totalFare(saved.getTotalFare())
                  .currency(saved.getCurrency())
                  .ruleVersion(saved.getRuleVersion())
                  .expiresAt(saved.getExpiresAt())
                  .build();
            });

    FareQuoteResponse response = pricingService.createQuote(customerId, request);

    assertNotNull(response);
    assertEquals(customerId, response.customerId());
    assertEquals(ServiceType.RIDE, response.serviceType());
    assertTrue(response.totalFare().compareTo(mockRule.getMinimumFare()) >= 0);
    assertTrue(response.estimatedDistanceMeters() > 0);
    assertNotNull(response.expiresAt());

    verify(pricingRuleRepository, times(1))
        .findFirstByServiceTypeAndRegionCodeAndIsActiveTrueOrderByVersionDesc(
            ServiceType.RIDE, "HN");
    verify(fareQuoteRepository, times(1)).save(any(FareQuote.class));
    verify(valueOperations, times(1))
        .set(
            eq(PricingServiceImpl.REDIS_RULE_CACHE_PREFIX + "RIDE:HN"),
            anyString(),
            eq(Duration.ofMinutes(5)));
  }

  @Test
  @DisplayName("should use cached pricing rule from Redis without hitting repository")
  void shouldReturnCachedPricingRuleFromRedis() throws Exception {
    UUID customerId = UUID.randomUUID();
    FareEstimateRequest request =
        new FareEstimateRequest(
            ServiceType.RIDE,
            "HN",
            21.028511,
            105.804817,
            "Hanoi Opera House",
            21.036237,
            105.834641,
            "West Lake Hanoi");

    String redisRuleKey = PricingServiceImpl.REDIS_RULE_CACHE_PREFIX + "RIDE:HN";
    String ruleJson = objectMapper.writeValueAsString(mockRule);
    when(valueOperations.get(redisRuleKey)).thenReturn(ruleJson);

    when(fareQuoteRepository.save(any(FareQuote.class)))
        .thenAnswer(
            invocation -> {
              FareQuote saved = invocation.getArgument(0);
              return FareQuote.builder()
                  .id(UUID.randomUUID())
                  .customerId(saved.getCustomerId())
                  .serviceType(saved.getServiceType())
                  .pickupPoint(saved.getPickupPoint())
                  .pickupAddress(saved.getPickupAddress())
                  .dropoffPoint(saved.getDropoffPoint())
                  .dropoffAddress(saved.getDropoffAddress())
                  .estimatedDistanceMeters(saved.getEstimatedDistanceMeters())
                  .estimatedDurationSeconds(saved.getEstimatedDurationSeconds())
                  .basePrice(saved.getBasePrice())
                  .distancePrice(saved.getDistancePrice())
                  .durationPrice(saved.getDurationPrice())
                  .surgeMultiplier(saved.getSurgeMultiplier())
                  .totalFare(saved.getTotalFare())
                  .currency(saved.getCurrency())
                  .ruleVersion(saved.getRuleVersion())
                  .expiresAt(saved.getExpiresAt())
                  .build();
            });

    FareQuoteResponse response = pricingService.createQuote(customerId, request);

    assertNotNull(response);
    verifyNoInteractions(pricingRuleRepository);
    verify(fareQuoteRepository, times(1)).save(any(FareQuote.class));
  }

  @Test
  @DisplayName("should consume quote successfully with atomic CAS")
  void shouldConsumeQuoteSuccessfully() {
    UUID quoteId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();

    FareQuote quote =
        FareQuote.builder()
            .id(quoteId)
            .customerId(customerId)
            .serviceType(ServiceType.RIDE)
            .estimatedDistanceMeters(5000)
            .estimatedDurationSeconds(600)
            .basePrice(new BigDecimal("15000.00"))
            .distancePrice(new BigDecimal("50000.00"))
            .durationPrice(new BigDecimal("10000.00"))
            .surgeMultiplier(BigDecimal.ONE)
            .totalFare(new BigDecimal("75000.00"))
            .currency("VND")
            .ruleVersion(1)
            .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
            .used(true)
            .build();

    when(fareQuoteRepository.consumeQuoteAtomically(eq(quoteId), eq(customerId), any(Instant.class)))
        .thenReturn(1);
    when(fareQuoteRepository.findById(quoteId)).thenReturn(Optional.of(quote));

    FareQuoteResponse response = pricingService.consumeQuote(quoteId, customerId);

    assertNotNull(response);
    assertEquals(quoteId, response.quoteId());
    assertEquals(customerId, response.customerId());
    verify(fareQuoteRepository, times(1))
        .consumeQuoteAtomically(eq(quoteId), eq(customerId), any(Instant.class));
  }

  @Test
  @DisplayName("should throw QUOTE_ALREADY_USED when quote already used")
  void shouldThrowWhenQuoteAlreadyUsed() {
    UUID quoteId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();

    FareQuote quote =
        FareQuote.builder()
            .id(quoteId)
            .customerId(customerId)
            .serviceType(ServiceType.RIDE)
            .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
            .used(true)
            .build();

    when(fareQuoteRepository.consumeQuoteAtomically(eq(quoteId), eq(customerId), any(Instant.class)))
        .thenReturn(0);
    when(fareQuoteRepository.findById(quoteId)).thenReturn(Optional.of(quote));

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> pricingService.consumeQuote(quoteId, customerId));

    assertEquals("QUOTE_ALREADY_USED", ex.getErrorCode());
  }

  @Test
  @DisplayName("should throw QUOTE_EXPIRED when quote has expired")
  void shouldThrowWhenQuoteExpired() {
    UUID quoteId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();

    FareQuote quote =
        FareQuote.builder()
            .id(quoteId)
            .customerId(customerId)
            .serviceType(ServiceType.RIDE)
            .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
            .used(false)
            .build();

    when(fareQuoteRepository.consumeQuoteAtomically(eq(quoteId), eq(customerId), any(Instant.class)))
        .thenReturn(0);
    when(fareQuoteRepository.findById(quoteId)).thenReturn(Optional.of(quote));

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> pricingService.consumeQuote(quoteId, customerId));

    assertEquals("QUOTE_EXPIRED", ex.getErrorCode());
  }

  @Test
  @DisplayName("should throw QUOTE_NOT_FOUND when quote does not exist")
  void shouldThrowWhenQuoteNotFound() {
    UUID quoteId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();

    when(fareQuoteRepository.consumeQuoteAtomically(eq(quoteId), eq(customerId), any(Instant.class)))
        .thenReturn(0);
    when(fareQuoteRepository.findById(quoteId)).thenReturn(Optional.empty());

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> pricingService.consumeQuote(quoteId, customerId));

    assertEquals("QUOTE_NOT_FOUND", ex.getErrorCode());
  }

  @Test
  @DisplayName("should throw QUOTE_ACCESS_DENIED when customer does not match")
  void shouldThrowWhenCustomerDoesNotMatch() {
    UUID quoteId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID otherCustomer = UUID.randomUUID();

    FareQuote quote =
        FareQuote.builder()
            .id(quoteId)
            .customerId(otherCustomer)
            .serviceType(ServiceType.RIDE)
            .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
            .used(false)
            .build();

    when(fareQuoteRepository.consumeQuoteAtomically(eq(quoteId), eq(customerId), any(Instant.class)))
        .thenReturn(0);
    when(fareQuoteRepository.findById(quoteId)).thenReturn(Optional.of(quote));

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> pricingService.consumeQuote(quoteId, customerId));

    assertEquals("QUOTE_ACCESS_DENIED", ex.getErrorCode());
  }

  @Test
  @DisplayName("should throw FORBIDDEN_ACCESS when non-admin accesses another user's quote")
  void shouldThrowForbiddenWhenGettingQuoteAsDifferentNonAdminCustomer() {
    UUID quoteId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();

    FareQuote quote =
        FareQuote.builder()
            .id(quoteId)
            .customerId(ownerId)
            .serviceType(ServiceType.RIDE)
            .estimatedDistanceMeters(2000)
            .estimatedDurationSeconds(300)
            .basePrice(new BigDecimal("15000.00"))
            .distancePrice(new BigDecimal("20000.00"))
            .durationPrice(new BigDecimal("5000.00"))
            .surgeMultiplier(BigDecimal.ONE)
            .totalFare(new BigDecimal("40000.00"))
            .currency("VND")
            .ruleVersion(1)
            .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
            .build();

    when(fareQuoteRepository.findById(quoteId)).thenReturn(Optional.of(quote));

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> pricingService.getQuote(quoteId, callerId, "CUSTOMER"));

    assertEquals("FORBIDDEN_ACCESS", ex.getErrorCode());
  }

  @Test
  @DisplayName("should allow owner or ADMIN to get quote")
  void shouldAllowOwnerOrAdminToGetQuote() {
    UUID quoteId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    FareQuote quote =
        FareQuote.builder()
            .id(quoteId)
            .customerId(ownerId)
            .serviceType(ServiceType.RIDE)
            .estimatedDistanceMeters(2000)
            .estimatedDurationSeconds(300)
            .basePrice(new BigDecimal("15000.00"))
            .distancePrice(new BigDecimal("20000.00"))
            .durationPrice(new BigDecimal("5000.00"))
            .surgeMultiplier(BigDecimal.ONE)
            .totalFare(new BigDecimal("40000.00"))
            .currency("VND")
            .ruleVersion(1)
            .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
            .build();

    when(fareQuoteRepository.findById(quoteId)).thenReturn(Optional.of(quote));

    // As owner
    FareQuoteResponse responseOwner = pricingService.getQuote(quoteId, ownerId, "CUSTOMER");
    assertNotNull(responseOwner);
    assertEquals(quoteId, responseOwner.quoteId());

    // As admin
    FareQuoteResponse responseAdmin = pricingService.getQuote(quoteId, adminId, "ADMIN");
    assertNotNull(responseAdmin);
    assertEquals(quoteId, responseAdmin.quoteId());
  }

  @Test
  @DisplayName("should deduplicate quote creation when using same idempotency key via Redis")
  void shouldDeduplicateCreateQuoteWithSameIdempotencyKey() {
    UUID customerId = UUID.randomUUID();
    FareEstimateRequest request =
        new FareEstimateRequest(
            ServiceType.RIDE,
            "HN",
            21.028511,
            105.804817,
            "Hanoi Opera House",
            21.036237,
            105.834641,
            "West Lake Hanoi");

    when(pricingRuleRepository.findFirstByServiceTypeAndRegionCodeAndIsActiveTrueOrderByVersionDesc(
            ServiceType.RIDE, "HN"))
        .thenReturn(Optional.of(mockRule));

    when(fareQuoteRepository.save(any(FareQuote.class)))
        .thenAnswer(
            invocation -> {
              FareQuote saved = invocation.getArgument(0);
              return FareQuote.builder()
                  .id(UUID.randomUUID())
                  .customerId(saved.getCustomerId())
                  .serviceType(saved.getServiceType())
                  .pickupPoint(saved.getPickupPoint())
                  .pickupAddress(saved.getPickupAddress())
                  .dropoffPoint(saved.getDropoffPoint())
                  .dropoffAddress(saved.getDropoffAddress())
                  .estimatedDistanceMeters(saved.getEstimatedDistanceMeters())
                  .estimatedDurationSeconds(saved.getEstimatedDurationSeconds())
                  .basePrice(saved.getBasePrice())
                  .distancePrice(saved.getDistancePrice())
                  .durationPrice(saved.getDurationPrice())
                  .surgeMultiplier(saved.getSurgeMultiplier())
                  .totalFare(saved.getTotalFare())
                  .currency(saved.getCurrency())
                  .ruleVersion(saved.getRuleVersion())
                  .expiresAt(saved.getExpiresAt())
                  .build();
            });

    String idempotencyKey = "TEST-IDEMP-" + UUID.randomUUID();
    String redisQuoteKey = PricingServiceImpl.REDIS_QUOTE_IDEMPOTENCY_PREFIX + idempotencyKey;

    AtomicReference<String> redisStore = new AtomicReference<>();
    when(valueOperations.get(redisQuoteKey)).thenAnswer(inv -> redisStore.get());
    doAnswer(
            inv -> {
              redisStore.set(inv.getArgument(1));
              return null;
            })
        .when(valueOperations)
        .set(eq(redisQuoteKey), anyString(), eq(Duration.ofMinutes(10)));

    FareQuoteResponse first = pricingService.createQuote(customerId, request, idempotencyKey);
    FareQuoteResponse second = pricingService.createQuote(customerId, request, idempotencyKey);

    assertNotNull(first);
    assertNotNull(second);
    assertEquals(first.quoteId(), second.quoteId());
    assertEquals(first.totalFare(), second.totalFare());
    verify(fareQuoteRepository, times(1)).save(any(FareQuote.class));
    verify(valueOperations, times(1))
        .set(eq(redisQuoteKey), anyString(), eq(Duration.ofMinutes(10)));
  }

  @Test
  @DisplayName("should return cached quote directly when idempotency key already exists in Redis")
  void shouldReturnCachedQuoteWhenIdempotencyKeyHitInRedis() throws Exception {
    UUID customerId = UUID.randomUUID();
    UUID quoteId = UUID.randomUUID();
    FareEstimateRequest request =
        new FareEstimateRequest(
            ServiceType.RIDE,
            "HN",
            21.028511,
            105.804817,
            "Hanoi Opera House",
            21.036237,
            105.834641,
            "West Lake Hanoi");

    FareQuoteResponse cachedResponse =
        new FareQuoteResponse(
            quoteId,
            customerId,
            ServiceType.RIDE,
            "Hanoi Opera House",
            "West Lake Hanoi",
            5000,
            600,
            new BigDecimal("15000.00"),
            new BigDecimal("50000.00"),
            new BigDecimal("10000.00"),
            BigDecimal.ONE,
            new BigDecimal("75000.00"),
            "VND",
            1,
            Instant.now().plus(10, ChronoUnit.MINUTES),
            21.028511,
            105.804817,
            21.036237,
            105.834641);

    String idempotencyKey = "EXISTING-KEY";
    String redisQuoteKey = PricingServiceImpl.REDIS_QUOTE_IDEMPOTENCY_PREFIX + idempotencyKey;
    when(valueOperations.get(redisQuoteKey)).thenReturn(objectMapper.writeValueAsString(cachedResponse));

    FareQuoteResponse response = pricingService.createQuote(customerId, request, idempotencyKey);

    assertNotNull(response);
    assertEquals(quoteId, response.quoteId());
    assertEquals(new BigDecimal("75000.00"), response.totalFare());
    verifyNoInteractions(pricingRuleRepository);
    verifyNoInteractions(fareQuoteRepository);
  }
}
