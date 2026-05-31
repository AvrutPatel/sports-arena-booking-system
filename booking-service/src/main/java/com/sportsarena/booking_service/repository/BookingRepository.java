package com.sportsarena.booking_service.repository;

import com.sportsarena.booking_service.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    // Fetch existing active reservations for a given court and day
    List<Booking> findByCourtIdAndBookingDateAndStatus(Long courtId, LocalDate bookingDate, String status);

    // Advanced collision boundary query to identify overlapping time ranges
    @Query("SELECT COUNT(b) FROM Booking b " +
            "WHERE b.courtId = :courtId " +
            "AND b.bookingDate = :bookingDate " +
            "AND b.status = 'CONFIRMED' " +
            "AND (:startTime < b.endTime AND :endTime > b.startTime)")
    long countOverlappingBookings(
            @Param("courtId") Long courtId,
            @Param("bookingDate") LocalDate bookingDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    List<Booking> findByUserEmailOrderByBookingDateDesc(String userEmail);

    // 1. Find future active bookings for a specific court within a date range
    List<Booking> findByCourtIdAndBookingDateBetweenAndStatus(Long courtId, LocalDate startDate, LocalDate endDate, String status);

    // 2. Find future active bookings for a specific court forever onwards
    List<Booking> findByCourtIdAndBookingDateGreaterThanEqualAndStatus(Long courtId, LocalDate startDate, String status);

    // 3. Find future active bookings for an ENTIRE venue forever onwards
    List<Booking> findByVenueIdAndBookingDateGreaterThanEqualAndStatus(Long venueId, LocalDate startDate, String status);

    // Fetch all bookings for a venue, sorted by date (newest first)
    List<com.sportsarena.booking_service.entity.Booking> findByVenueIdOrderByBookingDateDesc(Long venueId);
}