package com.ridehailing.logistics.trip.config;

import com.ridehailing.logistics.common.filter.CorrelationIdFilter;
import com.ridehailing.logistics.common.security.SecurityConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class FeignCorrelationIdInterceptor implements RequestInterceptor {

  @Override
  public void apply(RequestTemplate template) {
    String correlationId =
        Optional.ofNullable(MDC.get(CorrelationIdFilter.MDC_KEY))
            .filter(id -> !id.isBlank())
            .orElseGet(() -> UUID.randomUUID().toString());
    template.header(SecurityConstants.HEADER_CORRELATION_ID, correlationId);
  }
}
