package com.sportsarena.venue_service.controller;

import com.sportsarena.venue_service.dto.CourtRequestDto;
import com.sportsarena.venue_service.dto.CourtResponseDto;
import com.sportsarena.venue_service.service.CourtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courts")
public class CourtController {

    @Autowired
    private CourtService courtService;

    // Only Academy Owners can add courts
    @PostMapping("/courts")
    @PreAuthorize("hasRole('ACADEMY_OWNER')")
    public ResponseEntity<String> addCourt(@Valid @RequestBody CourtRequestDto courtDto) {
        String response = courtService.createCourt(courtDto);
        return ResponseEntity.ok(response);
    }

    // Publicly accessible to view courts inside an academy
    @GetMapping("/venue/{venueId}")
    public ResponseEntity<List<CourtResponseDto>> getCourtsByVenue(@PathVariable Long venueId) {
        return ResponseEntity.ok(courtService.getCourtsByVenue(venueId));
    }

    // ADD THIS INSIDE CourtController.java
    @PatchMapping("/{courtId}/rate")
    public ResponseEntity<com.sportsarena.venue_service.dto.CourtResponseDto> updateCourtRate(
            @PathVariable Long courtId,
            @RequestBody com.sportsarena.venue_service.dto.CourtUpdateRequestDto dto) {

        String ownerId = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(courtService.updateCourtRate(courtId, dto.getHourlyRate(), ownerId));
    }
}