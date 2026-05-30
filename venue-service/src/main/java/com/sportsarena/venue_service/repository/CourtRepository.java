package com.sportsarena.venue_service.repository;

import com.sportsarena.venue_service.entity.Court;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourtRepository extends JpaRepository<Court, Long> {
    // Fetches all active courts belonging to a specific venue
    List<Court> findByVenueIdAndIsActiveTrue(Long venueId);
}