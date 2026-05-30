package com.sportsarena.venue_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CourtUpdateRequestDto {
    @NotNull(message = "Hourly rate is required")
    @Positive(message = "Rate must be positive")
    private Double hourlyRate;
}