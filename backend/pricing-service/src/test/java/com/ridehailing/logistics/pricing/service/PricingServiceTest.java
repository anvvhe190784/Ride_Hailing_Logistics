package com.ridehailing.logistics.pricing.service;

import com.ridehailing.logistics.common.domain.enums.ServiceType;
import com.ridehailing.logistics.pricing.domain.entity.FareQuote;
import com.ridehailing.logistics.pricing.domain.entity.PricingRule;
import com.ridehailing.logistics.pricing.dto.FareEstimateRequest;
import com.ridehailing.logistics.pricing.dto.FareQuoteResponse;
import com.ridehailing.logistics.pricing.repository.FareQuoteRepository;
import com.ridehailing.logistics.pricing.repository.PricingRuleRepository;
import com.ridehailing.logistics.pricing.service.impl.PricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private FareQuoteRepository fareQuoteRepository;

    @InjectMocks
    private PricingServiceImpl pricingService;

    private PricingRule mockRule;

    @BeforeEach
    void setUp() {
        mockRule = PricingRule.builder()
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
        FareEstimateRequest request = new FareEstimateRequest(
                ServiceType.RIDE,
                "HN",
                21.028511, 105.804817, "Hanoi Opera House",
                21.036237, 105.834641, "West Lake Hanoi"
        );

        when(pricingRuleRepository.findFirstByServiceTypeAndRegionCodeAndIsActiveTrueOrderByVersionDesc(
                ServiceType.RIDE, "HN"
        )).thenReturn(Optional.of(mockRule));

        when(fareQuoteRepository.save(any(FareQuote.class))).thenAnswer(invocation -> {
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
                .findFirstByServiceTypeAndRegionCodeAndIsActiveTrueOrderByVersionDesc(ServiceType.RIDE, "HN");
        verify(fareQuoteRepository, times(1)).save(any(FareQuote.class));
    }
}
