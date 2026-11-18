package com.sportsarena.booking_service.repository;

import com.sportsarena.booking_service.entity.VenueBookingLock;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VenueBookingLockRepository extends JpaRepository<VenueBookingLock, Long> {
    @Modifying
    @Query("update VenueBookingLock v set v.revision = v.revision + 1 where v.venueId = :id")
    int lockVenue(@Param("id") Long id);
}
