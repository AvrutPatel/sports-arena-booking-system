package com.sportsarena.booking_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SlotResponseDto {
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean isAvailable;
}