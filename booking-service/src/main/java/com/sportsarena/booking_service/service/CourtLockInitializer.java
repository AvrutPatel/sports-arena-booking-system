package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.entity.CourtBookingLock;
import com.sportsarena.booking_service.repository.CourtBookingLockRepository;
import com.sportsarena.booking_service.repository.VenueBookingLockRepository;
import com.sportsarena.booking_service.entity.VenueBookingLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourtLockInitializer {
    private final CourtBookingLockRepository locks;
    @Autowired private VenueBookingLockRepository venues;

    public CourtLockInitializer(CourtBookingLockRepository locks) {
        this.locks = locks;
    }

    // A concurrent first insert may lose the PK race. Roll back only this small transaction.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureCourt(Long courtId) {
        if (!locks.existsById(courtId)) {
            locks.saveAndFlush(new CourtBookingLock(courtId));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureVenue(Long venueId) {
        if (!venues.existsById(venueId)) venues.saveAndFlush(new VenueBookingLock(venueId));
    }
}
