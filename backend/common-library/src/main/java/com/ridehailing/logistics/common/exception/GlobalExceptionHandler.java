package com.ridehailing.logistics.common.exception;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.security.SecurityConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BaseDomainException.class)
  public ResponseEntity<ApiResponse<Void>> handleBaseDomainException(BaseDomainException ex) {
    log.warn("Domain exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
    HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
    if (status == null) {
      status = HttpStatus.BAD_REQUEST;
    }
    return ResponseEntity.status(status)
        .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), resolveCorrelationId()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException ex) {
    String errorMessage =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
    log.warn("Validation error: {}", errorMessage);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("VALIDATION_ERROR", errorMessage, resolveCorrelationId()));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
      ConstraintViolationException ex) {
    String errorMessage =
        ex.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining("; "));
    log.warn("Constraint violation: {}", errorMessage);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("VALIDATION_ERROR", errorMessage, resolveCorrelationId()));
  }

  @ExceptionHandler({
      MissingRequestHeaderException.class,
      HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleBadRequestException(Exception ex) {
    log.warn("Bad request parameter or header: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("BAD_REQUEST", ex.getMessage(), resolveCorrelationId()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
    log.error("Unhandled exception: ", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("INTERNAL_SERVER_ERROR", "An unexpected error occurred", resolveCorrelationId()));
  }

  private String resolveCorrelationId() {
    String correlationId = MDC.get("correlationId");
    if (correlationId != null && !correlationId.isBlank()) {
      return correlationId;
    }
    return MDC.get(SecurityConstants.HEADER_CORRELATION_ID);
  }
}
