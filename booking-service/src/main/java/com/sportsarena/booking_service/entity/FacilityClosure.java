package com.sportsarena.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "facility_closures", indexes = {
        @Index(name = "closure_venue_dates_idx", columnList = "venue_id,start_date,end_date"),
        @Index(name = "closure_court_dates_idx", columnList = "court_id,start_date,end_date")
})
@Data
@NoArgsConstructor
public class FacilityClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // If courtId is null, it means the ENTIRE venue is closed!
    private Long venueId;
    private Long courtId;

    private java.util.UUID bulkCancellationJobId;

    private LocalDate startDate;

    // If endDate is null, it is closed permanently from the startDate.
    private LocalDate endDate;

    @Column(length = 500)
    private String reason;
}
