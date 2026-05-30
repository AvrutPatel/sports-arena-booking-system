package com.sportsarena.venue_service.dto;

import lombok.Data;

@Data
public class CourtResponseDto {
    private Long id;
    private Long venueId;
    private String name;
    private String sportType;
    private double hourlyRate;
    private boolean isActive;
    private Double averageRating = 0.0;
    private Integer totalReviews = 0;
}