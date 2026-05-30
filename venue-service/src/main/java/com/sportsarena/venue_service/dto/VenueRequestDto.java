package com.sportsarena.venue_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class VenueRequestDto {

    @NotBlank(message = "Venue name is required")
    private String name;

    @NotBlank(message = "Address cannot be blank")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    private List<String> amenities;
}