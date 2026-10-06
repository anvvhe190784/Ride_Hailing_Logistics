package com.ridehailing.logistics.common.exception;

import lombok.Getter;

@Getter
public class BaseDomainException extends RuntimeException {
    private final String errorCode;
    private final int httpStatus;

    public BaseDomainException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public static BaseDomainException badRequest(String errorCode, String message) {
        return new BaseDomainException(errorCode, message, 400);
    }

    public static BaseDomainException notFound(String errorCode, String message) {
        return new BaseDomainException(errorCode, message, 404);
    }

    public static BaseDomainException conflict(String errorCode, String message) {
        return new BaseDomainException(errorCode, message, 409);
    }

    public static BaseDomainException businessRuleViolation(String message) {
        return new BaseDomainException("BUSINESS_RULE_VIOLATION", message, 422);
    }
}
