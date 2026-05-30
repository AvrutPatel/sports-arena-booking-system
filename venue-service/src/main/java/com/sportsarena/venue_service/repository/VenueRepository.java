package com.sportsarena.venue_service.repository;

import com.sportsarena.venue_service.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VenueRepository extends JpaRepository<Venue, Long> {
    List<Venue> findByCityIgnoreCase(String city);

    @Query("SELECT DISTINCT v FROM Venue v " +
            "LEFT JOIN Court c ON c.venue.id = v.id " +
            "WHERE (CAST(:city AS string) IS NULL OR LOWER(v.city) = LOWER(CAST(:city AS string))) " +
            "AND (CAST(:sportType AS string) IS NULL OR LOWER(c.sportType) = LOWER(CAST(:sportType AS string))) " +
            "AND v.isActive = true")
    List<Venue> searchVenues(@Param("city") String city, @Param("sportType") String sportType);

    List<Venue> findByOwnerId(String ownerId);
}