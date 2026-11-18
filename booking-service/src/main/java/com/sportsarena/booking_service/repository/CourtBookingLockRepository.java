package com.sportsarena.booking_service.repository;

import com.sportsarena.booking_service.entity.CourtBookingLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourtBookingLockRepository extends JpaRepository<CourtBookingLock, Long> {
    @Modifying
    @Query("update CourtBookingLock c set c.revision = c.revision + 1 where c.courtId = :courtId")
    int lockCourt(@Param("courtId") Long courtId);
}
