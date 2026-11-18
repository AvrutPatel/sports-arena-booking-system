package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.dto.BookingRequestDto;
import com.sportsarena.booking_service.entity.Booking;
import com.sportsarena.booking_service.repository.BookingRepository;
import com.sportsarena.booking_service.repository.CourtBookingLockRepository;
import com.sportsarena.booking_service.entity.BookingRecoveryTask;
import com.sportsarena.booking_service.entity.Notification;
import com.sportsarena.booking_service.repository.BookingRecoveryTaskRepository;
import com.sportsarena.booking_service.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.charset.StandardCharsets;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import com.sportsarena.booking_service.entity.BookingRequestRecord;
import com.sportsarena.booking_service.entity.FacilityClosure;
import com.sportsarena.booking_service.repository.BookingRequestRecordRepository;
import com.sportsarena.booking_service.repository.VenueBookingLockRepository;
import com.sportsarena.booking_service.repository.FacilityClosureRepository;

/** Every operation here is a short database transaction; no remote service calls. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
public class ReservationTransactionService {
    public static final List<String> ACTIVE_STATUSES =
            List.of("CONFIRMED", "PENDING", "PAYMENT_PROCESSING", "PAYMENT_UNKNOWN");

    private final BookingRepository bookings;
    private final CourtBookingLockRepository locks;
    private final Clock clock;
    private final long holdMillis;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired private BookingRecoveryTaskRepository tasks;
    @Autowired private NotificationRepository notifications;
    @Autowired private BookingRequestRecordRepository requests;
    @Autowired private VenueBookingLockRepository venues;
    @Autowired private FacilityClosureRepository closures;
    @Value("${booking.recovery.lease-seconds:60}") private long leaseSeconds = 60;

    public ReservationTransactionService(BookingRepository bookings, CourtBookingLockRepository locks,
            Clock clock, @Value("${booking.hold.seconds:300}") long holdSeconds) {
        if (holdSeconds < 1 || holdSeconds > 86400) {
            throw new IllegalArgumentException("booking.hold.seconds must be between 1 and 86400.");
        }
        this.bookings = bookings;
        this.locks = locks;
        this.clock = clock;
        this.holdMillis = holdSeconds * 1000;
    }

    public Booking reserve(BookingRequestDto request, String email, BigDecimal serverPrice) {
        return reserve(request, email, serverPrice, UUID.randomUUID());
    }

    public Booking reserve(BookingRequestDto request, String email, BigDecimal serverPrice, UUID requestKey) {
        lockVenue(request.getVenueId());
        var replay = requests.findByUserEmailAndRequestKey(email, requestKey).orElse(null);
        if (replay != null) {
            if (!replay.getFingerprint().equals(RequestFingerprints.booking(request))) throw conflict("Idempotency key has different booking details.");
            return bookings.findById(replay.getBookingId()).orElseThrow();
        }
        validate(request, email, serverPrice);
        lockCourt(request.getCourtId());
        if (!closures.findActiveClosuresForDate(request.getCourtId(), request.getVenueId(), request.getBookingDate()).isEmpty()) {
            throw conflict("This court or venue is closed on the selected date.");
        }
        expireCourtHoldsLocked(request.getCourtId());
        if (bookings.countOverlappingBookings(request.getCourtId(), request.getBookingDate(),
                request.getStartTime(), request.getEndTime()) > 0) {
            throw conflict("Selected slot has already been reserved.");
        }
        Booking booking = new Booking();
        booking.setCourtId(request.getCourtId());
        booking.setVenueId(request.getVenueId());
        booking.setBookingDate(request.getBookingDate());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setUserEmail(email);
        booking.setTotalAmount(serverPrice);
        booking.setStatus("PENDING");
        booking.setHoldExpiresAt(clock.millis() + holdMillis);
        bookings.saveAndFlush(booking);
        requests.saveAndFlush(new BookingRequestRecord(RequestFingerprints.hash(email + "|" + requestKey), email,
                requestKey, RequestFingerprints.booking(request), booking.getId(), clock.millis()));
        return booking;
    }

    public boolean beginPayment(UUID id) {
        Booking booking = lockBooking(id);
        if (!"PENDING".equals(booking.getStatus())) {
            throw conflict("Reservation is no longer awaiting payment.");
        }
        if (tagClosure(booking)) {
            booking.setStatus("EXPIRED");
            return false;
        }
        if (booking.getHoldExpiresAt() == null) {
            booking.setStatus("PAYMENT_UNKNOWN");
            return false; // Legacy hold: do not assume that no debit happened.
        }
        if (booking.getHoldExpiresAt() <= clock.millis()) {
            booking.setStatus("EXPIRED");
            return false; // Return so expiration commits before the caller reports the conflict.
        }
        booking.setStatus("PAYMENT_PROCESSING");
        booking.setHoldExpiresAt(clock.millis() + holdMillis);
        tasks.saveAndFlush(new BookingRecoveryTask(id, "PAYMENT", id, booking.getTotalAmount(),
                booking.getHoldExpiresAt(), clock.millis()));
        return true;
    }

    public void startPaymentOnce(UUID id) {
        Booking booking = lockBooking(id);
        if ("PENDING".equals(booking.getStatus())) beginPayment(id);
    }

    public boolean isClosed(Long venueId, Long courtId, LocalDate date) {
        lockVenue(venueId);
        return !closures.findActiveClosuresForDate(courtId, venueId, date).isEmpty();
    }

    public void confirm(UUID id) {
        Booking booking = lockBooking(id);
        if ("CONFIRMED".equals(booking.getStatus())) return;
        requirePaymentInFlight(booking);
        if (tagClosure(booking)) throw conflict("Facility is closed; payment recovery will refund any charge.");
        if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt() <= clock.millis()) {
            throw conflict("Payment window has closed; automatic reconciliation will refund any successful charge.");
        }
        // Processing/unknown payments have never released their inventory, even past the deadline.
        booking.setStatus("CONFIRMED");
        tasks.findById(id).filter(t -> "PAYMENT".equals(t.getKind())).ifPresent(this::completeTask);
    }

    public void paymentFailed(UUID id) {
        Booking booking = lockBooking(id);
        if ("FAILED".equals(booking.getStatus())) return;
        requirePaymentInFlight(booking);
        booking.setStatus("FAILED");
    }

    public void paymentUnknown(UUID id) {
        Booking booking = lockBooking(id);
        if ("CONFIRMED".equals(booking.getStatus()) || "PAYMENT_UNKNOWN".equals(booking.getStatus())) return;
        if (!"PAYMENT_PROCESSING".equals(booking.getStatus())) {
            throw conflict("Payment state cannot be changed for this reservation.");
        }
        booking.setStatus("PAYMENT_UNKNOWN");
    }

    public void expire(UUID id) {
        expireIfDue(lockBooking(id));
    }

    public void expireCourtHolds(Long courtId) {
        lockCourt(courtId);
        expireCourtHoldsLocked(courtId);
    }

    public boolean cancelConfirmed(UUID id) {
        Booking booking = lockBooking(id);
        if ("CANCELLED".equals(booking.getStatus())) return false;
        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw conflict("Only confirmed reservations can be cancelled.");
        }
        booking.setStatus("CANCELLED");
        return true;
    }

    public void markReviewed(UUID id, boolean reviewed) {
        lockBooking(id).setReviewed(reviewed);
    }

    private void expireCourtHoldsLocked(Long courtId) {
        for (UUID id : bookings.findExpiredHoldIdsForCourt(courtId, clock.millis())) {
            expireIfDue(refreshBooking(id));
        }
    }

    private void expireIfDue(Booking booking) {
        if (booking.getHoldExpiresAt() == null || booking.getHoldExpiresAt() > clock.millis()) return;
        if ("PENDING".equals(booking.getStatus())) booking.setStatus("EXPIRED");
        else if (List.of("PAYMENT_PROCESSING", "PAYMENT_UNKNOWN").contains(booking.getStatus())) {
            booking.setStatus(tasks.existsById(booking.getId()) ? "PAYMENT_RELEASED" : "PAYMENT_UNKNOWN");
        }
    }

    public Booking snapshot(UUID id) {
        Booking booking = bookings.findById(id).orElseThrow();
        entityManager.refresh(booking);
        return booking;
    }

    public Booking requestCancellation(UUID id, String customerEmail, boolean academyCancellation) {
        Booking booking = lockBooking(id);
        if (!academyCancellation && !booking.getUserEmail().equalsIgnoreCase(customerEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot cancel another customer's booking.");
        }
        if (List.of("REFUND_PENDING", "CANCELLED").contains(booking.getStatus())) return booking;
        if (!"CONFIRMED".equals(booking.getStatus())) throw conflict("Only confirmed reservations can be cancelled.");
        if (!academyCancellation && (booking.getBookingDate().isBefore(LocalDate.now(clock))
                || (booking.getBookingDate().equals(LocalDate.now(clock))
                && !booking.getStartTime().isAfter(LocalTime.now(clock))))) {
            throw conflict("A reservation cannot be cancelled after its start time.");
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), booking.getBookingDate());
        boolean closed = tagClosure(booking);
        BigDecimal percentage = academyCancellation || closed || days > 7 ? BigDecimal.ONE
                : days > 1 ? new BigDecimal("0.75") : new BigDecimal("0.50");
        BigDecimal amount = booking.getTotalAmount().multiply(percentage).setScale(2, RoundingMode.HALF_UP);
        // Freeze the agreed amount and operation reference before any refund HTTP call.
        BookingRecoveryTask task = tasks.findById(id).orElse(null);
        if (task == null) {
            task = new BookingRecoveryTask(id, "REFUND", refundId(id), amount, 0, clock.millis());
        } else {
            if (!"COMPLETED".equals(task.getState())) throw conflict("Payment reconciliation is still in progress.");
            prepareRefund(task, amount);
        }
        booking.setStatus("REFUND_PENDING");
        tasks.saveAndFlush(task);
        return booking;
    }

    public void releaseOverduePayment(UUID id) {
        Booking booking = lockBooking(id);
        BookingRecoveryTask task = lockedTask(id);
        if (task != null && "PAYMENT".equals(task.getKind()) && !"COMPLETED".equals(task.getState())
                && task.getPaymentDeadline() <= clock.millis()
                && List.of("PAYMENT_PROCESSING", "PAYMENT_UNKNOWN").contains(booking.getStatus())) {
            booking.setStatus("PAYMENT_RELEASED");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelForBulk(UUID id, UUID jobId) {
        Booking booking = lockBooking(id);
        if (!ACTIVE_STATUSES.contains(booking.getStatus())) return;
        if (booking.getBulkCancellationJobId() == null) booking.setBulkCancellationJobId(jobId);
        if ("CONFIRMED".equals(booking.getStatus())) {
            requestCancellation(id, null, true).setBulkCancellationJobId(jobId);
        } else if ("PENDING".equals(booking.getStatus())) {
            booking.setStatus("EXPIRED");
        } else {
            booking.setStatus("PAYMENT_RELEASED");
            BookingRecoveryTask task = lockedTask(id);
            if (task == null) {
                task = new BookingRecoveryTask(id, "PAYMENT", id, booking.getTotalAmount(), clock.millis(), clock.millis());
                tasks.save(task);
            } else if ("PAYMENT".equals(task.getKind())) {
                task.setPaymentDeadline(clock.millis());
                if ("PENDING".equals(task.getState())) task.setNextAttemptAt(clock.millis());
            }
        }
    }

    public RecoveryWork claimRecovery(UUID id) {
        Booking booking = lockBooking(id);
        BookingRecoveryTask task = lockedTask(id);
        long now = clock.millis();
        if (task == null || "COMPLETED".equals(task.getState())) return null;
        if ("RUNNING".equals(task.getState()) && task.getLeaseUntil() != null && task.getLeaseUntil() > now) return null;
        if ("PENDING".equals(task.getState()) && task.getNextAttemptAt() > now) return null;
        if ("PAYMENT".equals(task.getKind()) && "CONFIRMED".equals(booking.getStatus())) {
            completeTask(task);
            return null;
        }
        boolean allowDebit = "PAYMENT".equals(task.getKind()) && now < task.getPaymentDeadline()
                && List.of("PAYMENT_PROCESSING", "PAYMENT_UNKNOWN").contains(booking.getStatus());
        if ("PAYMENT".equals(task.getKind()) && !allowDebit
                && List.of("PAYMENT_PROCESSING", "PAYMENT_UNKNOWN").contains(booking.getStatus())) {
            booking.setStatus("PAYMENT_RELEASED");
        }
        task.setState("RUNNING");
        task.setLeaseToken(UUID.randomUUID());
        task.setLeaseUntil(now + Math.max(30, leaseSeconds) * 1000);
        task.setAttempts(task.getAttempts() == Integer.MAX_VALUE ? Integer.MAX_VALUE : task.getAttempts() + 1);
        return new RecoveryWork(id, task.getLeaseToken(), task.getKind(), task.getOperationId(),
                booking.getUserEmail(), task.getAmount(), allowDebit);
    }

    public void completeDebit(RecoveryWork work, String decision) {
        Booking booking = lockBooking(work.bookingId());
        BookingRecoveryTask task = lockedTask(work.bookingId());
        if (!ownsLease(task, work)) return;
        if (!"PAYMENT".equals(task.getKind())) throw conflict("Unexpected recovery task kind.");
        if ("APPLIED".equals(decision)) {
            boolean closed = tagClosure(booking);
            if (clock.millis() < task.getPaymentDeadline()
                    && !closed
                    && List.of("PAYMENT_PROCESSING", "PAYMENT_UNKNOWN").contains(booking.getStatus())) {
                booking.setStatus("CONFIRMED");
                completeTask(task);
                notifications.save(new Notification(booking.getUserEmail(), "Booking " + booking.getId() + " is confirmed."));
            } else {
                booking.setStatus("REFUND_PENDING");
                prepareRefund(task, booking.getTotalAmount());
            }
        } else if (List.of("DECLINED", "VOIDED").contains(decision)) {
            booking.setStatus("FAILED");
            completeTask(task);
            notifications.save(new Notification(booking.getUserEmail(), "Booking " + booking.getId()
                    + " was not completed. No wallet debit was applied."));
        } else {
            throw new IllegalArgumentException("Unrecognized payment decision.");
        }
    }

    public void completeRefund(RecoveryWork work) {
        Booking booking = lockBooking(work.bookingId());
        BookingRecoveryTask task = lockedTask(work.bookingId());
        if (!ownsLease(task, work)) return;
        if (!"REFUND".equals(task.getKind()) || !"REFUND_PENDING".equals(booking.getStatus())) {
            throw conflict("Unexpected refund state.");
        }
        booking.setStatus("CANCELLED");
        completeTask(task);
        notifications.save(new Notification(booking.getUserEmail(), "Refund of ₹" + task.getAmount()
                + " for booking " + booking.getId() + " has been credited to your wallet."));
    }

    public void retryRecovery(RecoveryWork work, String errorCode) {
        Booking booking = lockBooking(work.bookingId());
        BookingRecoveryTask task = lockedTask(work.bookingId());
        if (!ownsLease(task, work)) return;
        long now = clock.millis();
        if ("PAYMENT".equals(task.getKind())
                && List.of("PAYMENT_PROCESSING", "PAYMENT_UNKNOWN").contains(booking.getStatus())) {
            booking.setStatus(now >= task.getPaymentDeadline() ? "PAYMENT_RELEASED" : "PAYMENT_UNKNOWN");
        }
        long delay = Math.min(300000L, 1000L << Math.min(task.getAttempts(), 8));
        long next = now + delay;
        if ("PAYMENT".equals(task.getKind()) && task.getPaymentDeadline() > now) next = Math.min(next, task.getPaymentDeadline());
        task.setState("PENDING");
        task.setNextAttemptAt(next);
        task.setLastError(errorCode);
        task.setLeaseToken(null);
        task.setLeaseUntil(null);
    }

    private BookingRecoveryTask lockedTask(UUID id) {
        BookingRecoveryTask task = tasks.findById(id).orElse(null);
        if (task != null) entityManager.refresh(task, LockModeType.PESSIMISTIC_WRITE);
        return task;
    }

    private boolean ownsLease(BookingRecoveryTask task, RecoveryWork work) {
        return task != null && "RUNNING".equals(task.getState()) && work.leaseToken().equals(task.getLeaseToken());
    }

    private void prepareRefund(BookingRecoveryTask task, BigDecimal amount) {
        task.setKind("REFUND"); task.setOperationId(refundId(task.getBookingId())); task.setAmount(amount);
        task.setState("PENDING"); task.setNextAttemptAt(clock.millis()); task.setAttempts(0);
        task.setLeaseToken(null); task.setLeaseUntil(null); task.setLastError(null);
    }

    private UUID refundId(UUID id) {
        return UUID.nameUUIDFromBytes(("cancellation-refund:" + id).getBytes(StandardCharsets.UTF_8));
    }

    private void completeTask(BookingRecoveryTask task) {
        task.setState("COMPLETED"); task.setLeaseToken(null); task.setLeaseUntil(null); task.setLastError(null);
    }

    private Booking lockBooking(UUID id) {
        Long venueId = bookings.findVenueIdForBooking(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found."));
        lockVenue(venueId);
        Long courtId = bookings.findCourtIdForBooking(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found."));
        lockCourt(courtId);
        return refreshBooking(id);
    }

    private void lockVenue(Long id) {
        if (venues.lockVenue(id) != 1) throw new IllegalStateException("Venue coordination row is missing; apply the coordination migration.");
    }

    private boolean tagClosure(Booking booking) {
        List<FacilityClosure> active = closures.findActiveClosuresForDate(booking.getCourtId(), booking.getVenueId(), booking.getBookingDate());
        if (active.isEmpty()) return false;
        if (booking.getBulkCancellationJobId() == null) booking.setBulkCancellationJobId(active.getFirst().getBulkCancellationJobId());
        return true;
    }

    private Booking refreshBooking(UUID id) {
        Booking booking = bookings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found."));
        // Refresh also protects against stale entities held by OpenEntityManagerInView.
        entityManager.refresh(booking, LockModeType.PESSIMISTIC_WRITE);
        return booking;
    }

    private void lockCourt(Long courtId) {
        if (locks.lockCourt(courtId) != 1) {
            throw new IllegalStateException(
                    "Court lock is missing. Initialize existing court lock rows before serving bookings.");
        }
    }

    private void requirePaymentInFlight(Booking booking) {
        if (!List.of("PAYMENT_PROCESSING", "PAYMENT_UNKNOWN").contains(booking.getStatus())) {
            throw conflict("Payment cannot change a released or unclaimed reservation.");
        }
    }

    private void validate(BookingRequestDto request, String email, BigDecimal price) {
        if (request == null || request.getCourtId() == null || request.getCourtId() <= 0
                || request.getVenueId() == null || request.getVenueId() <= 0 || email == null || email.isBlank()
                || request.getBookingDate() == null || request.getStartTime() == null || request.getEndTime() == null
                || !request.getStartTime().isBefore(request.getEndTime())
                || request.getStartTime().isBefore(LocalTime.of(6, 0))
                || request.getEndTime().isAfter(LocalTime.of(22, 0))
                || request.getStartTime().getSecond() != 0 || request.getEndTime().getSecond() != 0
                || request.getStartTime().getNano() != 0 || request.getEndTime().getNano() != 0
                || price == null || price.signum() <= 0 || price.scale() > 2 || price.precision() > 19) {
            throw new IllegalArgumentException("Invalid reservation details, opening hours, or price.");
        }
        LocalDate today = LocalDate.now(clock);
        if (request.getBookingDate().isBefore(today) || request.getBookingDate().isAfter(today.plusDays(30))
                || (request.getBookingDate().equals(today) && request.getStartTime().isBefore(LocalTime.now(clock)))) {
            throw new IllegalArgumentException("Reservation must be in the future and within 30 days.");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
