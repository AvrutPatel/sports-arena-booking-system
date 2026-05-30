package com.sportsarena.venue_service.service;
import java.util.List;
import java.util.stream.Collectors;
import com.sportsarena.venue_service.dto.VenueRequestDto;
import com.sportsarena.venue_service.dto.VenueResponseDto;
import com.sportsarena.venue_service.dto.VenueUpdateRequestDto;
import com.sportsarena.venue_service.entity.Venue;
import com.sportsarena.venue_service.repository.ReviewRepository;
import com.sportsarena.venue_service.repository.VenueRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class VenueService {

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ModelMapper modelMapper;

    public VenueResponseDto createVenue(VenueRequestDto dto, String ownerEmail) {
        Venue venue = modelMapper.map(dto, Venue.class);

        venue.setOwnerId(ownerEmail);

        venue = venueRepository.save(venue);
        return modelMapper.map(venue, VenueResponseDto.class);
    }

    // Fetch a single venue profile
    public VenueResponseDto getVenueById(Long id) {
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Venue not found with ID: " + id));

        VenueResponseDto dto = modelMapper.map(venue, VenueResponseDto.class);

        // Dynamically fetch and attach review statistics
        dto.setAverageRating(reviewRepository.getAverageRatingForVenue(id));
        dto.setTotalReviews(reviewRepository.countByVenueId(id));

        return dto;
    }

    // Search and filter venues dynamically
    public List<VenueResponseDto> searchAllVenues(String city, String sportType) {
        List<Venue> venues = venueRepository.searchVenues(city, sportType);

        return venues.stream()
                .map(venue -> {
                    VenueResponseDto dto = modelMapper.map(venue, VenueResponseDto.class);
                    dto.setAverageRating(reviewRepository.getAverageRatingForVenue(venue.getId()));
                    dto.setTotalReviews(reviewRepository.countByVenueId(venue.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // 2. Add this new method to fetch only the logged-in owner's venues
    public List<VenueResponseDto> getMyVenues(String ownerEmail) {
        return venueRepository.findByOwnerId(ownerEmail).stream()
                .map(venue -> {
                    VenueResponseDto dto = modelMapper.map(venue, VenueResponseDto.class);
                    dto.setAverageRating(reviewRepository.getAverageRatingForVenue(venue.getId()));
                    dto.setTotalReviews(reviewRepository.countByVenueId(venue.getId()));
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    //method to update description and amenities
    public VenueResponseDto updateVenueDetails(Long venueId, VenueUpdateRequestDto dto, String ownerId) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new RuntimeException("Venue not found"));

        // Security check: Only the actual owner can modify it
        if (!venue.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("Unauthorized: You do not own this academy.");
        }

        if (dto.getDescription() != null) {
            venue.setDescription(dto.getDescription());
        }
        if (dto.getAmenities() != null) {
            venue.setAmenities(dto.getAmenities());
        }

        venue = venueRepository.save(venue);
        return modelMapper.map(venue, VenueResponseDto.class);
    }
}