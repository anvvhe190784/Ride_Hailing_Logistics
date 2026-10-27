package com.ridehailing.logistics.pricing.service;

import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import com.ridehailing.logistics.pricing.dto.FareEstimateRequest;
import java.util.UUID;

public interface PricingService {
  FareQuoteResponse createQuote(
      UUID customerId, FareEstimateRequest request, String idempotencyKey);

  default FareQuoteResponse createQuote(UUID customerId, FareEstimateRequest request) {
    return createQuote(customerId, request, null);
  }

  FareQuoteResponse getQuote(UUID quoteId, UUID requesterId, String requesterRole);

  default FareQuoteResponse getQuote(UUID quoteId) {
    return getQuote(quoteId, null, null);
  }

  FareQuoteResponse consumeQuote(UUID quoteId, UUID customerId);
}
