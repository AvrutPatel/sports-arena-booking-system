package com.sportsarena.venue_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CourtRequestDto {

    @NotNull(message = "Venue ID is required")
    private Long venueId;

    @NotBlank(message = "Court name is required")
    private String name;

    @NotBlank(message = "Sport type is required")
    private String sportType;

    @Min(value = 0, message = "Hourly rate cannot be negative")
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.DecimalMin("0.01")
    @jakarta.validation.constraints.Digits(integer = 17, fraction = 2)
    private java.math.BigDecimal hourlyRate;
}
