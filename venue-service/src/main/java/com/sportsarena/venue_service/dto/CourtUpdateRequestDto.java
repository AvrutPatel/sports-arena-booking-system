package com.sportsarena.venue_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CourtUpdateRequestDto {
    @NotNull(message = "Hourly rate is required")
    @Positive(message = "Rate must be positive")
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.DecimalMin("0.01")
    @jakarta.validation.constraints.Digits(integer = 17, fraction = 2)
    private java.math.BigDecimal hourlyRate;
}
