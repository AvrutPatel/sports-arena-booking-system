package com.sportsarena.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "facility_closures")
@Data
@NoArgsConstructor
public class FacilityClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // If courtId is null, it means the ENTIRE venue is closed!
    private Long venueId;
    private Long courtId;

    private LocalDate startDate;

    // If endDate is null, it is closed permanently from the startDate.
    private LocalDate endDate;

    @Column(length = 500)
    private String reason;
}