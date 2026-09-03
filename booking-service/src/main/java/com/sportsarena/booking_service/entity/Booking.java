package com.sportsarena.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Auth-service email
    private String userEmail;

    // Venue-service court ID
    private Long courtId;

    private LocalDate bookingDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private double totalAmount;

    private boolean isReviewed = false;

    private Long venueId;

    private String status = "CONFIRMED"; // Status values: CONFIRMED, CANCELLED, PENDING, FAILED

    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
}