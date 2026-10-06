package com.ridehailing.logistics.trip.client;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "pricing-service", fallback = PricingClientFallback.class)
public interface PricingClient {

  @GetMapping("/api/v1/pricing/quote/{quoteId}")
  ApiResponse<FareQuoteResponse> getQuote(@PathVariable("quoteId") UUID quoteId);
}
