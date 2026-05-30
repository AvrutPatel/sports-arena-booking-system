package com.sportsarena.booking_service.repository;

import com.sportsarena.booking_service.entity.FacilityClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FacilityClosureRepository extends JpaRepository<FacilityClosure, Long> {

    // This powerful query checks if the chosen date is blocked by a Court closure OR a Venue-wide closure.
    @Query("SELECT c FROM FacilityClosure c WHERE " +
            "(c.courtId = :courtId OR (c.venueId = :venueId AND c.courtId IS NULL)) " +
            "AND c.startDate <= :targetDate " +
            "AND (c.endDate IS NULL OR c.endDate >= :targetDate)")
    List<FacilityClosure> findActiveClosuresForDate(
            @Param("courtId") Long courtId,
            @Param("venueId") Long venueId,
            @Param("targetDate") LocalDate targetDate);
}