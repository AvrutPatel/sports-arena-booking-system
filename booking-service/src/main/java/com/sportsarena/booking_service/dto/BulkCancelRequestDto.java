package com.sportsarena.booking_service.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class BulkCancelRequestDto {
    private Long venueId;
    private Long courtId; // If null, the engine will assume you are deleting the whole venue
    @jakarta.validation.constraints.NotNull
    private LocalDate startDate;
    private LocalDate endDate; // If null, it cancels everything from startDate forever
    @jakarta.validation.constraints.Size(max = 500)
    private String message; // The optional owner apology
}
