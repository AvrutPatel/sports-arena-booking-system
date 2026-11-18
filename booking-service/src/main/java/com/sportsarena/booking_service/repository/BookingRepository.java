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
import java.util.Optional;
import org.springframework.data.domain.Pageable;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    // Find active bookings by court and date
    List<Booking> findByCourtIdAndBookingDateAndStatus(Long courtId, LocalDate bookingDate, String status);

    List<Booking> findByCourtIdAndBookingDateAndStatusIn(Long courtId, LocalDate bookingDate, List<String> statuses);

    // Check overlapping time ranges
    @Query("SELECT COUNT(b) FROM Booking b " +
            "WHERE b.courtId = :courtId " +
            "AND b.bookingDate = :bookingDate " +
            "AND b.status IN ('CONFIRMED', 'PENDING', 'PAYMENT_PROCESSING', 'PAYMENT_UNKNOWN') " +
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

    @Query("select b.courtId from Booking b where b.id = :id")
    Optional<Long> findCourtIdForBooking(@Param("id") UUID id);

    @Query("select b.venueId from Booking b where b.id = :id")
    Optional<Long> findVenueIdForBooking(@Param("id") UUID id);

    @Query("select b.id from Booking b where b.status in ('PENDING', 'PAYMENT_PROCESSING', 'PAYMENT_UNKNOWN') "
            + "and b.holdExpiresAt <= :now order by b.holdExpiresAt, b.id")
    List<UUID> findExpiredHoldIds(@Param("now") long now, Pageable page);

    @Query("select b.id from Booking b where b.courtId = :courtId "
            + "and b.status in ('PENDING', 'PAYMENT_PROCESSING', 'PAYMENT_UNKNOWN') and b.holdExpiresAt <= :now")
    List<UUID> findExpiredHoldIdsForCourt(@Param("courtId") Long courtId, @Param("now") long now);

    @Query("select b from Booking b where b.venueId = :venueId and (:courtId is null or b.courtId = :courtId) "
            + "and b.bookingDate >= :startDate and (:endDate is null or b.bookingDate <= :endDate) "
            + "and b.status in ('CONFIRMED','PENDING','PAYMENT_PROCESSING','PAYMENT_UNKNOWN') order by b.courtId,b.id")
    List<Booking> findBulkCandidates(@Param("venueId") Long venueId, @Param("courtId") Long courtId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable page);

    long countByBulkCancellationJobIdAndStatusIn(UUID jobId, List<String> statuses);
    long countByBulkCancellationJobId(UUID jobId);

    @Query("select b.bulkCancellationJobId,count(b) from Booking b where b.bulkCancellationJobId in :ids "
            + "and b.status in ('REFUND_PENDING','PAYMENT_RELEASED','PAYMENT_PROCESSING','PAYMENT_UNKNOWN') group by b.bulkCancellationJobId")
    List<Object[]> countPendingBulkRefunds(@Param("ids") List<UUID> ids);
}
