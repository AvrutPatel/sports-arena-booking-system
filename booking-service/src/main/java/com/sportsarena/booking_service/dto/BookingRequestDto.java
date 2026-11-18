package com.sportsarena.booking_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;

@Data
public class BookingRequestDto {

    @NotNull(message = "Court ID is required")
    private Long courtId;

    @NotNull(message = "Booking date is required")
    private LocalDate bookingDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    @NotNull(message = "Venue ID is required")
    private Long venueId;

    // Retained for old clients; the server calculates and stores the actual price.
    private BigDecimal totalAmount;
}
