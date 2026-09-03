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

    // Find active bookings by court and date
    List<Booking> findByCourtIdAndBookingDateAndStatus(Long courtId, LocalDate bookingDate, String status);

    // Check overlapping time ranges
    @Query("SELECT COUNT(b) FROM Booking b " +
            "WHERE b.courtId = :courtId " +
            "AND b.bookingDate = :bookingDate " +
            "AND b.status IN ('CONFIRMED', 'PENDING') " +
            "AND (:startTime < b.endTime AND :endTime > b.startTime)")
    long countOverlappingBookings(
            @Param("courtId") Long courtId,
            @Param("bookingDate") LocalDate bookingDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    List<Booking> findByUserEmailOrderByBookingDateDesc(String userEmail);

    // Find bookings in date range
    List<Booking> findByCourtIdAndBookingDateBetweenAndStatus(Long courtId, LocalDate startDate, LocalDate endDate, String status);

    // Find future court bookings
    List<Booking> findByCourtIdAndBookingDateGreaterThanEqualAndStatus(Long courtId, LocalDate startDate, String status);

    // Find future venue bookings
    List<Booking> findByVenueIdAndBookingDateGreaterThanEqualAndStatus(Long venueId, LocalDate startDate, String status);

    // Find all venue bookings sorted
    List<com.sportsarena.booking_service.entity.Booking> findByVenueIdOrderByBookingDateDesc(Long venueId);

    // Find stale pending bookings
    List<Booking> findByStatusAndCreatedAtBefore(String status, java.time.LocalDateTime threshold);
}