package com.sportsarena.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "booking_overlap_lookup_idx", columnList = "court_id,booking_date,status"),
        @Index(name = "booking_hold_expiry_idx", columnList = "status,hold_expires_at"),
        @Index(name = "booking_bulk_progress_idx", columnList = "bulk_cancellation_job_id,status"),
        @Index(name = "booking_bulk_scope_idx", columnList = "venue_id,status,booking_date,court_id")
})
@Data
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private long version;

    // Auth-service email
    private String userEmail;

    // Venue-service court ID
    private Long courtId;

    private LocalDate bookingDate;

    private LocalTime startTime;

    private LocalTime endTime;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    private boolean isReviewed = false;

    private Long venueId;

    private UUID bulkCancellationJobId;

    private String status = "CONFIRMED";

    // Epoch milliseconds; portable BIGINT storage, independent of database timezone types.
    private Long holdExpiresAt;

    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
}
