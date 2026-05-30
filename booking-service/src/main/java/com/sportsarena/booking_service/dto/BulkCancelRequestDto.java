package com.sportsarena.booking_service.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class BulkCancelRequestDto {
    private Long venueId;
    private Long courtId; // If null, the engine will assume you are deleting the whole venue
    private LocalDate startDate;
    private LocalDate endDate; // If null, it cancels everything from startDate forever
    private String message; // The optional owner apology
}