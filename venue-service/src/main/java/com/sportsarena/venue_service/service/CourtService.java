package com.sportsarena.venue_service.service;

import com.sportsarena.venue_service.dto.CourtRequestDto;
import com.sportsarena.venue_service.dto.CourtResponseDto;
import com.sportsarena.venue_service.entity.Court;
import com.sportsarena.venue_service.entity.Venue;
import com.sportsarena.venue_service.repository.CourtRepository;
import com.sportsarena.venue_service.repository.ReviewRepository;
import com.sportsarena.venue_service.repository.VenueRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourtService {

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ModelMapper modelMapper;

    public String createCourt(CourtRequestDto dto) {
        // 1. Verify the venue exists
        Venue venue = venueRepository.findById(dto.getVenueId())
                .orElseThrow(() -> new RuntimeException("Venue not found with ID: " + dto.getVenueId()));

        // 2. Security Check: Verify the logged-in owner actually owns this venue
        String currentOwnerEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!venue.getOwnerId().equalsIgnoreCase(currentOwnerEmail)) {
            throw new RuntimeException("Access Denied: You do not have permission to modify this venue.");
        }

        // 3. Map DTO to Entity
        Court court = modelMapper.map(dto, Court.class);
        // FIX: Force the Court ID back to null so Hibernate knows it is a brand-new INSERT,
        // preventing it from trying to run an UPDATE on a row that doesn't exist yet.
        court.setId(null);

        // 4. Link the managed venue entity
        court.setVenue(venue);

        // 5. Save to DB
        courtRepository.save(court);

        return "Court '" + court.getName() + "' successfully added to venue: " + venue.getName();
    }

    public List<CourtResponseDto> getCourtsByVenue(Long venueId) {
        return courtRepository.findByVenueIdAndIsActiveTrue(venueId)
                .stream()
                .map(court -> {
                    CourtResponseDto responseDto = modelMapper.map(court, CourtResponseDto.class);
                    responseDto.setVenueId(court.getVenue().getId());

                    responseDto.setAverageRating(reviewRepository.getAverageRatingForCourt(court.getId()));
                    responseDto.setTotalReviews(reviewRepository.countByCourtId(court.getId()));

                    return responseDto;
                })
                .collect(Collectors.toList());
    }

    // to update court pricing
    public CourtResponseDto updateCourtRate(Long courtId, Double newRate, String ownerId) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new RuntimeException("Court not found"));

        // Security check: Check the venue owner through the court relationship
        if (!court.getVenue().getOwnerId().equals(ownerId)) {
            throw new RuntimeException("Unauthorized: You do not own this court.");
        }

        court.setHourlyRate(newRate);
        court = courtRepository.save(court);
        return modelMapper.map(court, CourtResponseDto.class);
    }
}