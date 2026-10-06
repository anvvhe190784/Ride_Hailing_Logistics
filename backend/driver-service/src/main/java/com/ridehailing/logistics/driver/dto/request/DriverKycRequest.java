package com.ridehailing.logistics.driver.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record DriverKycRequest(
    @NotBlank(message = "ID card number is required") String idCardNumber,
    @NotNull(message = "Date of birth is required") LocalDate dateOfBirth,
    @NotBlank(message = "Address is required") String address) {}
