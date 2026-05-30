package com.sportsarena.venue_service.dto;

import lombok.Data;
import java.util.List;

@Data
public class VenueUpdateRequestDto {
    private String description;
    private List<String> amenities;
}