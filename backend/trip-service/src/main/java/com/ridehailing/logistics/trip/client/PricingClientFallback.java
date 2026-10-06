package com.ridehailing.logistics.trip.client;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class PricingClientFallback implements PricingClient {

    @Override
    public ApiResponse<FareQuoteResponse> getQuote(UUID quoteId) {
        log.warn("Fallback triggered for PricingClient.getQuote with quoteId: {}", quoteId);
        return ApiResponse.error("PRICING_SERVICE_UNAVAILABLE", "Pricing service is currently degraded", null);
    }
}
