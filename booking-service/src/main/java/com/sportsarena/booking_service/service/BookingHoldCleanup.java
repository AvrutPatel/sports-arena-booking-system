package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class BookingHoldCleanup {
    private static final Logger LOG = LoggerFactory.getLogger(BookingHoldCleanup.class);
    private final BookingRepository bookings;
    private final ReservationTransactionService reservations;
    private final Clock clock;

    public BookingHoldCleanup(BookingRepository bookings, ReservationTransactionService reservations, Clock clock) {
        this.bookings = bookings;
        this.reservations = reservations;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.hold.cleanup-interval-ms:60000}")
    public void runOnce() {
        for (UUID id : bookings.findExpiredHoldIds(clock.millis(), PageRequest.of(0, 100))) {
            try {
                reservations.expire(id);
            } catch (RuntimeException ex) {
                LOG.warn("Could not process expired booking hold {}: {}", id, ex.getClass().getSimpleName());
            }
        }
    }
}
