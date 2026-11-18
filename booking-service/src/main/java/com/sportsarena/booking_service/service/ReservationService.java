package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.dto.BookingRequestDto;
import com.sportsarena.booking_service.entity.Booking;
import com.sportsarena.booking_service.repository.CourtBookingLockRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import com.sportsarena.booking_service.repository.VenueBookingLockRepository;
import com.sportsarena.booking_service.repository.BookingRequestRecordRepository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Prepares a stable court key before the reservation transaction takes any locks. */
@Service
@Transactional(propagation = Propagation.NEVER)
public class ReservationService {
    private final CourtLockInitializer initializer;
    private final CourtBookingLockRepository locks;
    private final ReservationTransactionService transactions;
    @Autowired private VenueBookingLockRepository venues;
    @Autowired private BookingRequestRecordRepository requests;

    public ReservationService(CourtLockInitializer initializer, CourtBookingLockRepository locks,
                              ReservationTransactionService transactions) {
        this.initializer = initializer;
        this.locks = locks;
        this.transactions = transactions;
    }

    public Booking reserve(BookingRequestDto request, String email, BigDecimal price) {
        return reserve(request, email, price, UUID.randomUUID());
    }

    public Booking lookup(BookingRequestDto request, String email, UUID requestKey) {
        if (requestKey == null) throw new IllegalArgumentException("An Idempotency-Key is required.");
        var existing = requests.findByUserEmailAndRequestKey(email, requestKey).orElse(null);
        if (existing == null) return null;
        if (!existing.getFingerprint().equals(RequestFingerprints.booking(request))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used with different booking details.");
        }
        return transactions.snapshot(existing.getBookingId());
    }

    public Booking reserve(BookingRequestDto request, String email, BigDecimal price, UUID requestKey) {
        Booking replay = lookup(request, email, requestKey);
        if (replay != null) return replay;
        if (request == null || request.getCourtId() == null || request.getCourtId() <= 0) {
            throw new IllegalArgumentException("A valid court ID is required.");
        }
        prepareVenue(request.getVenueId());
        try {
            initializer.ensureCourt(request.getCourtId());
        } catch (DataIntegrityViolationException race) {
            // A competing initializer may have committed the same PK; its rollback is already complete.
            if (!locks.existsById(request.getCourtId())) throw race;
        }
        try { return transactions.reserve(request, email, price, requestKey); }
        catch (DataIntegrityViolationException race) {
            Booking winner = lookup(request, email, requestKey);
            if (winner == null) throw race;
            return winner;
        }
    }

    public void prepareVenue(Long venueId) {
        if (venueId == null || venueId <= 0) throw new IllegalArgumentException("A valid venue ID is required.");
        try { initializer.ensureVenue(venueId); }
        catch (DataIntegrityViolationException race) { if (!venues.existsById(venueId)) throw race; }
    }

    public void expireCourtHolds(Long courtId) {
        // Availability reads need not create a row for courts with no reservations.
        if (locks.existsById(courtId)) transactions.expireCourtHolds(courtId);
    }
}
