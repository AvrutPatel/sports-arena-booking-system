package com.sportsarena.booking_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity @Table(name = "venue_booking_locks") @Getter @NoArgsConstructor
public class VenueBookingLock implements Persistable<Long> {
    @Id private Long venueId;
    private long revision;
    public VenueBookingLock(Long id) { this.venueId = id; }
    @Override @Transient public Long getId() { return venueId; }
    @Override @Transient public boolean isNew() { return true; }
}
