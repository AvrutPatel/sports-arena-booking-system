package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.dto.BulkCancelRequestDto;
import com.sportsarena.booking_service.entity.*;
import com.sportsarena.booking_service.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
@Transactional(propagation = Propagation.NEVER)
public class BookingAdministrationService {
    public record JobView(UUID id, String state, Long venueId, Long courtId, Long closureId,
                          long queuedBookings, long pendingRefunds, String message) {}
    private final VenueBookingAccessClient access;
    private final ReservationService reservations;
    private final BookingAdministrationTransactions transactions;
    private final BulkCancellationJobRepository jobs;
    private final FacilityClosureRepository closures;
    public BookingAdministrationService(VenueBookingAccessClient access, ReservationService reservations,
            BookingAdministrationTransactions transactions, BulkCancellationJobRepository jobs, FacilityClosureRepository closures) {
        this.access = access; this.reservations = reservations; this.transactions = transactions; this.jobs = jobs; this.closures = closures;
    }
    public JobView submit(BulkCancelRequestDto request, UUID key) {
        VenueBookingAccessClient.Scope scope;
        if (request.getCourtId() != null) {
            scope = access.requireCourtOwner(request.getCourtId());
            if (request.getVenueId() != null && !request.getVenueId().equals(scope.venueId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Court does not belong to the requested venue.");
            }
        } else {
            if (request.getVenueId() == null) throw new IllegalArgumentException("A venue or court is required.");
            scope = access.requireVenueOwner(request.getVenueId());
        }
        reservations.prepareVenue(scope.venueId());
        String owner = SecurityContextHolder.getContext().getAuthentication().getName();
        BulkCancellationJob job;
        try { job = transactions.create(request, scope, owner, key); }
        catch (DataIntegrityViolationException race) {
            job = jobs.findByOwnerEmailAndRequestKey(owner, key).orElseThrow(() -> race);
            BookingAdministrationTransactions.checkFingerprint(job, BookingAdministrationTransactions.fingerprint(request, scope));
        }
        return view(job);
    }
    public JobView status(UUID id) {
        BulkCancellationJob job = jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk job not found."));
        access.requireVenueOwner(job.getVenueId());
        return view(job);
    }
    public List<JobView> list(Long venueId) {
        access.requireVenueOwner(venueId);
        List<BulkCancellationJob> page = jobs.findByVenueIdOrderByCreatedAtDesc(venueId, PageRequest.of(0, 100));
        Map<UUID, Long> counts = transactions.pendingRefundsForJobs(page.stream().map(BulkCancellationJob::getId).toList());
        return page.stream().map(job -> view(job, counts.getOrDefault(job.getId(), 0L))).toList();
    }
    public List<FacilityClosure> closures(Long venueId) {
        access.requireVenueOwner(venueId);
        return closures.findByVenueIdOrderByStartDateDesc(venueId);
    }
    public void removeClosure(Long id) {
        FacilityClosure closure = closures.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Closure not found."));
        var scope = closure.getCourtId() != null ? access.requireCourtOwner(closure.getCourtId()) : access.requireVenueOwner(closure.getVenueId());
        reservations.prepareVenue(scope.venueId());
        transactions.removeClosure(id, scope.venueId());
    }
    private JobView view(BulkCancellationJob job) {
        return view(job, transactions.pendingRefunds(job.getId()));
    }
    private JobView view(BulkCancellationJob job, long pending) {
        return new JobView(job.getId(), job.getState(), job.getVenueId(), job.getCourtId(), job.getClosureId(),
                job.getQueuedCount(), pending, "Cancellation accepted. Progress and refunds are processed automatically.");
    }
}
