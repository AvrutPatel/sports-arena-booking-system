package com.sportsarena.venue_service.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CourtResponseDto {
    private Long id;
    private Long venueId;
    private String name;
    private String sportType;
    private BigDecimal hourlyRate;
    private boolean isActive;
    private Double averageRating = 0.0;
    private Integer totalReviews = 0;
}
