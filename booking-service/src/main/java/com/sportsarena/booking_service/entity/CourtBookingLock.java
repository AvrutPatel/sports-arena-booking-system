package com.sportsarena.booking_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/** Stable lock target even when a court has no bookings. Never delete these rows during normal operation. */
@Entity
@Table(name = "court_booking_locks")
@Getter
@NoArgsConstructor
public class CourtBookingLock implements Persistable<Long> {
    @Id
    private Long courtId;

    // An actual update obtains the write lock even on engines with weaker SELECT FOR UPDATE behavior.
    private long revision;

    public CourtBookingLock(Long courtId) {
        this.courtId = courtId;
    }

    @Override
    @Transient
    public Long getId() { return courtId; }

    @Override
    @Transient
    public boolean isNew() { return true; }
}
