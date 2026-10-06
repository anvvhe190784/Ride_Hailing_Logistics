package com.ridehailing.logistics.pricing.service;

import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import com.ridehailing.logistics.pricing.dto.FareEstimateRequest;
import java.util.UUID;

public interface PricingService {
  FareQuoteResponse createQuote(UUID customerId, FareEstimateRequest request);

  FareQuoteResponse getQuote(UUID quoteId);
}
