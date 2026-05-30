package com.sportsarena.venue_service.controller;

import java.util.List;

import com.sportsarena.venue_service.dto.*;
import com.sportsarena.venue_service.service.CourtService;
import com.sportsarena.venue_service.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/venues")
public class VenueController {

    @Autowired
    private VenueService venueRepositoryService;

    @Autowired
    private CourtService courtService;

    @PostMapping
    @PreAuthorize("hasRole('ACADEMY_OWNER')")
    public ResponseEntity<VenueResponseDto> createVenue(@RequestBody VenueRequestDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(venueRepositoryService.createVenue(dto, email));
    }

    @GetMapping("/my-venues")
    public ResponseEntity<List<VenueResponseDto>> getMyVenues() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(venueRepositoryService.getMyVenues(email));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<VenueResponseDto> getVenueById(@PathVariable Long id) {
        return ResponseEntity.ok(venueRepositoryService.getVenueById(id));
    }

    @GetMapping
    public ResponseEntity<List<VenueResponseDto>> searchVenues(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String sportType) {

        List<VenueResponseDto> venues = venueRepositoryService.searchAllVenues(city, sportType);
        return ResponseEntity.ok(venues);
    }

    @PatchMapping("/{venueId}/details")
    public ResponseEntity<VenueResponseDto> updateVenueDetails(
            @PathVariable Long venueId,
            @RequestBody VenueUpdateRequestDto dto) {

        String ownerId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(venueRepositoryService.updateVenueDetails(venueId, dto, ownerId));
    }

    //Endpoint to update a Court's Hourly Rate
    @PatchMapping("/courts/{courtId}/rate")
    public ResponseEntity<CourtResponseDto> updateCourtRate(
            @PathVariable Long courtId,
            @RequestBody CourtUpdateRequestDto dto) {

        String ownerId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(courtService.updateCourtRate(courtId, dto.getHourlyRate(), ownerId));
    }

    @GetMapping("/{venueId}/courts")
    public ResponseEntity<List<com.sportsarena.venue_service.dto.CourtResponseDto>> getCourtsForVenue(@PathVariable Long venueId) {
        return ResponseEntity.ok(courtService.getCourtsByVenue(venueId));
    }

}