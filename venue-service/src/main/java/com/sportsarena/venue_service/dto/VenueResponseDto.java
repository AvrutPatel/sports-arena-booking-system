package com.sportsarena.venue_service.dto;

import lombok.Data;

import java.util.List;

@Data
public class VenueResponseDto {
    private Long id;
    private String name;
    private String address;
    private String city;
    private boolean isActive;
    private Double averageRating = 0.0;
    private Integer totalReviews = 0;
    private List<String> amenities;
    private String description;
}