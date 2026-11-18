package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.dto.BulkCancelRequestDto;
import com.sportsarena.booking_service.entity.*;
import com.sportsarena.booking_service.repository.*;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.*;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
public class BookingAdministrationTransactions {
    private final BulkCancellationJobRepository jobs;
    private final FacilityClosureRepository closures;
    private final VenueBookingLockRepository venues;
    private final BookingRepository bookings;
    private final ReservationTransactionService reservations;
    private final Clock clock;
    @PersistenceContext private EntityManager em;
    @Value("${booking.bulk.batch-size:50}") private int batchSize = 50;
    private static final List<String> FINANCIAL_PENDING = List.of("REFUND_PENDING", "PAYMENT_RELEASED", "PAYMENT_PROCESSING", "PAYMENT_UNKNOWN");

    public BookingAdministrationTransactions(BulkCancellationJobRepository jobs, FacilityClosureRepository closures,
            VenueBookingLockRepository venues, BookingRepository bookings, ReservationTransactionService reservations, Clock clock) {
        this.jobs = jobs; this.closures = closures; this.venues = venues;
        this.bookings = bookings; this.reservations = reservations; this.clock = clock;
    }

    public BulkCancellationJob create(BulkCancelRequestDto request, VenueBookingAccessClient.Scope scope,
                                      String owner, UUID key) {
        if (key == null || request.getStartDate() == null || (request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate()))) throw new IllegalArgumentException("Invalid cancellation range or key.");
        lockVenue(scope.venueId());
        String fingerprint = fingerprint(request, scope);
        BulkCancellationJob existing = jobs.findByOwnerEmailAndRequestKey(owner, key).orElse(null);
        if (existing != null) { checkFingerprint(existing, fingerprint); return existing; }
        BulkCancellationJob job = new BulkCancellationJob();
        job.setId(UUID.randomUUID()); job.setOwnerEmail(owner); job.setRequestKey(key); job.setFingerprint(fingerprint);
        job.setVenueId(scope.venueId()); job.setCourtId(scope.courtId());
        job.setStartDate(request.getStartDate()); job.setEndDate(request.getEndDate());
        job.setState("ENQUEUING"); job.setCreatedAt(clock.millis());
        FacilityClosure closure = new FacilityClosure();
        closure.setVenueId(scope.venueId()); closure.setCourtId(scope.courtId());
        closure.setStartDate(request.getStartDate()); closure.setEndDate(request.getEndDate());
        closure.setReason(reason(request)); closure.setBulkCancellationJobId(job.getId());
        closures.saveAndFlush(closure);
        job.setClosureId(closure.getId());
        return jobs.saveAndFlush(job);
    }

    public void processBatch(UUID jobId) {
        BulkCancellationJob scope = jobs.findById(jobId).orElseThrow();
        lockVenue(scope.getVenueId());
        em.refresh(scope, LockModeType.PESSIMISTIC_WRITE);
        if (!"ENQUEUING".equals(scope.getState())) return;
        List<Booking> batch = bookings.findBulkCandidates(scope.getVenueId(), scope.getCourtId(), scope.getStartDate(),
                scope.getEndDate(), PageRequest.of(0, Math.max(1, Math.min(200, batchSize))));
        for (Booking booking : batch) {
            // MANDATORY keeps every cancellation/task and this batch's progress in one transaction.
            reservations.cancelForBulk(booking.getId(), scope.getId());
            scope.setQueuedCount(scope.getQueuedCount() + 1);
        }
        if (bookings.findBulkCandidates(scope.getVenueId(), scope.getCourtId(), scope.getStartDate(),
                scope.getEndDate(), PageRequest.of(0, 1)).isEmpty()) {
            scope.setState(pendingRefunds(scope.getId()) == 0 ? "COMPLETED" : "REFUNDS_PENDING");
        }
    }

    public void finishIfRefundsComplete(UUID jobId) {
        BulkCancellationJob job = jobs.findById(jobId).orElseThrow();
        lockVenue(job.getVenueId()); em.refresh(job, LockModeType.PESSIMISTIC_WRITE);
        if ("REFUNDS_PENDING".equals(job.getState()) && pendingRefunds(jobId) == 0) job.setState("COMPLETED");
    }

    public void removeClosure(Long id, Long authorizedVenueId) {
        lockVenue(authorizedVenueId);
        FacilityClosure closure = closures.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Closure not found."));
        if (closure.getVenueId() != null && !authorizedVenueId.equals(closure.getVenueId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Closure belongs to another venue.");
        }
        if (jobs.existsByClosureIdAndState(id, "ENQUEUING")) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cancellation is still being queued. Reopen after every affected booking has a saved cancellation intent.");
        closures.delete(closure);
    }

    public long pendingRefunds(UUID id) { return bookings.countByBulkCancellationJobIdAndStatusIn(id, FINANCIAL_PENDING); }

    public Map<UUID, Long> pendingRefundsForJobs(List<UUID> ids) {
        Map<UUID, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) for (Object[] row : bookings.countPendingBulkRefunds(ids)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    public static String fingerprint(BulkCancelRequestDto request, VenueBookingAccessClient.Scope scope) {
        return RequestFingerprints.hash(scope.venueId() + "|" + scope.courtId() + "|" + request.getStartDate()
                + "|" + request.getEndDate() + "|" + reason(request));
    }
    public static void checkFingerprint(BulkCancellationJob job, String fingerprint) {
        if (!job.getFingerprint().equals(fingerprint)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Idempotency key was already used for another bulk cancellation request.");
    }
    private static String reason(BulkCancelRequestDto request) {
        return request.getMessage() == null || request.getMessage().isBlank()
                ? "Closed by academy administration." : request.getMessage().trim();
    }
    private void lockVenue(Long id) {
        if (venues.lockVenue(id) != 1) throw new IllegalStateException("Venue coordination row is missing.");
    }
}
