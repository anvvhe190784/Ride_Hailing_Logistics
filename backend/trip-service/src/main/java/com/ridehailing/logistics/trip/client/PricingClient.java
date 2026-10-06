package com.ridehailing.logistics.trip.client;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

//noinspection InjectedReferences
@SuppressWarnings({"InjectedReferences", "unused"})
@FeignClient(
    name = "${feign.client.pricing-service.name:pricing-service}",
    contextId = "pricingClient",
    fallback = PricingClientFallback.class)
public interface PricingClient {

  @GetMapping("/api/v1/pricing/quote/{quoteId}")
  ApiResponse<FareQuoteResponse> getQuote(@PathVariable("quoteId") UUID quoteId);
}
