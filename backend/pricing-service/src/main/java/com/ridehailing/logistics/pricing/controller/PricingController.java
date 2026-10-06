package com.ridehailing.logistics.pricing.controller;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.pricing.dto.FareEstimateRequest;
import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import com.ridehailing.logistics.pricing.service.PricingService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

  private final PricingService pricingService;

  @PostMapping("/quote")
  public ResponseEntity<ApiResponse<FareQuoteResponse>> estimateFare(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID customerId,
      @Valid @RequestBody FareEstimateRequest request,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    FareQuoteResponse response = pricingService.createQuote(customerId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, correlationId));
  }

  @GetMapping("/quote/{quoteId}")
  public ResponseEntity<ApiResponse<FareQuoteResponse>> getQuote(
      @PathVariable UUID quoteId,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    FareQuoteResponse response = pricingService.getQuote(quoteId);
    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
  }
}
