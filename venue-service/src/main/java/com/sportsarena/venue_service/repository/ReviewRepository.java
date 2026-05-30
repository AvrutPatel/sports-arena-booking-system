package com.sportsarena.venue_service.repository;

import com.sportsarena.venue_service.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByVenueIdOrderByCreatedAtDesc(Long venueId);
    List<Review> findByCourtIdOrderByCreatedAtDesc(Long courtId);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.venueId = :venueId")
    Double getAverageRatingForVenue(Long venueId);
    int countByVenueId(Long venueId);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.courtId = :courtId")
    Double getAverageRatingForCourt(Long courtId);
    int countByCourtId(Long courtId);
}