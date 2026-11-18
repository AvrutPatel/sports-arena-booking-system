package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.dto.BookingRequestDto;
import com.sportsarena.booking_service.entity.Booking;
import com.sportsarena.booking_service.repository.BookingRepository;
import com.sportsarena.booking_service.repository.CourtBookingLockRepository;
import com.sportsarena.booking_service.repository.BookingRecoveryTaskRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.ResourceAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "debug=false", "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper=OFF",
        "spring.datasource.hikari.maximum-pool-size=6", "spring.datasource.hikari.connection-timeout=10000"
        , "booking.bulk.batch-size=2"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReservationService.class, ReservationTransactionService.class, CourtLockInitializer.class,
        BookingHoldCleanup.class, BookingRecoveryWorker.class, BookingService.class, BookingAdministrationTransactions.class,
        BulkCancellationWorker.class, AbstractReservationConcurrencyTests.TestClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class AbstractReservationConcurrencyTests {
    @Autowired ReservationService reservations;
    @MockitoSpyBean ReservationTransactionService transactions;
    @Autowired BookingRepository bookings;
    @Autowired CourtBookingLockRepository locks;
    @Autowired BookingHoldCleanup cleanup;
    @Autowired MutableClock clock;
    @Autowired BookingService bookingFlow;
    @Autowired BookingRecoveryWorker worker;
    @Autowired BookingRecoveryTaskRepository tasks;
    @Autowired com.sportsarena.booking_service.repository.BookingRequestRecordRepository requests;
    @Autowired com.sportsarena.booking_service.repository.VenueBookingLockRepository venueLocks;
    @Autowired com.sportsarena.booking_service.repository.FacilityClosureRepository closures;
    @Autowired com.sportsarena.booking_service.repository.BulkCancellationJobRepository bulkJobs;
    @Autowired BookingAdministrationTransactions administration;
    @Autowired BulkCancellationWorker bulkWorker;
    @MockitoBean VenueBookingAccessClient access;
    @MockitoBean WalletClient wallet;
    @MockitoBean BookingPriceService prices;

    @BeforeEach
    void reset() {
        bulkJobs.deleteAllInBatch();
        closures.deleteAllInBatch();
        requests.deleteAllInBatch();
        tasks.deleteAllInBatch();
        bookings.deleteAllInBatch();
        locks.deleteAllInBatch();
        venueLocks.deleteAllInBatch();
        clock.now.set(Instant.parse("2030-01-01T00:00:00Z"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void priceAndWalletCallsRunOutsideTransactionsAndObserveCommittedPaymentClaim() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("player@example.com", null));
        BookingRequestDto request = new BookingRequestDto();
        request.setCourtId(42L);
        request.setVenueId(1L);
        request.setBookingDate(LocalDate.now(clock).plusDays(1));
        request.setStartTime(LocalTime.of(10, 0));
        request.setEndTime(LocalTime.of(11, 0));
        when(prices.calculate(request)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new BigDecimal("100.00");
        });
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            RecoveryWork work = invocation.getArgument(0);
            assertEquals("PAYMENT_PROCESSING", bookings.findById(work.bookingId()).orElseThrow().getStatus());
            return "APPLIED";
        }).when(wallet).settleDebit(any());
        bookingFlow.checkAndCreateBooking(request);
        assertEquals("CONFIRMED", bookings.findAll().getFirst().getStatus());
    }

    @Test
    void simultaneousFirstBookingsOnEmptyCourtProduceOneWinner() throws Exception {
        List<Boolean> results = race(12, i -> attempt(42L, "10:00", "11:00"));
        assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, bookings.count());
        assertEquals(1, locks.count());
    }

    @Test
    void partiallyOverlappingIntervalsCannotBothCommit() throws Exception {
        List<Boolean> results = race(2, i -> i == 0
                ? attempt(42L, "10:00", "11:00") : attempt(42L, "10:30", "11:30"));
        assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, bookings.count());
    }

    @Test
    void adjacentIntervalsOnSameCourtCanBothCommit() throws Exception {
        assertTrue(race(2, i -> i == 0
                ? attempt(42L, "10:00", "11:00") : attempt(42L, "11:00", "12:00"))
                .stream().allMatch(Boolean::booleanValue));
        assertEquals(2, bookings.count());
    }

    @Test
    void identicalTimesOnDifferentCourtsCanBothCommit() throws Exception {
        assertTrue(race(2, i -> attempt(42L + i, "10:00", "11:00"))
                .stream().allMatch(Boolean::booleanValue));
        assertEquals(2, bookings.count());
    }

    @Test
    void expiredUnclaimedHoldIsReleasedBeforeNewReservation() {
        Booking old = reserve(42L, "10:00", "11:00");
        clock.advanceSeconds(301);
        Booking replacement = reserve(42L, "10:00", "11:00");
        assertEquals("EXPIRED", status(old));
        assertEquals("PENDING", status(replacement));
        assertThrows(ResponseStatusException.class, () -> transactions.confirm(old.getId()));
    }

    @Test
    void expiredPaymentClaimCommitsExpirationAndDoesNotStartPayment() {
        Booking booking = reserve(42L, "10:00", "11:00");
        clock.advanceSeconds(301);
        assertFalse(transactions.beginPayment(booking.getId()));
        assertEquals("EXPIRED", status(booking));
    }

    @Test
    void schedulerExpiresUnclaimedHoldsWithoutAvailabilityTraffic() {
        Booking booking = reserve(42L, "10:00", "11:00");
        clock.advanceSeconds(301);
        cleanup.runOnce();
        assertEquals("EXPIRED", status(booking));
    }

    @Test
    void staleProcessingPaymentReleasesInventoryButRetainsDurableRecovery() {
        Booking booking = reserve(42L, "10:00", "11:00");
        assertTrue(transactions.beginPayment(booking.getId()));
        clock.advanceSeconds(301);
        cleanup.runOnce();
        assertEquals("PAYMENT_RELEASED", status(booking));
        assertTrue(attempt(42L, "10:00", "11:00"));
        assertTrue(tasks.existsById(booking.getId()));
        assertThrows(ResponseStatusException.class, () -> transactions.confirm(booking.getId()));
    }

    @Test
    void cleanupRacingWithLateSuccessQueuesRefundWithoutRevivingInventory() throws Exception {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        RecoveryWork work = transactions.claimRecovery(booking.getId());
        clock.advanceSeconds(301);
        race(2, i -> {
            if (i == 0) transactions.expire(booking.getId());
            else transactions.completeDebit(work, "APPLIED");
            return true;
        });
        assertEquals("REFUND_PENDING", status(booking));
        assertTrue(attempt(42L, "10:00", "11:00"));
    }

    @Test
    void paymentTimeoutIsRetriedAutomaticallyWithSameOperationId() {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        when(wallet.settleDebit(any())).thenThrow(new ResourceAccessException("lost response")).thenReturn("APPLIED");
        worker.processOne(booking.getId());
        assertEquals("PAYMENT_UNKNOWN", status(booking));
        clock.advanceSeconds(3);
        worker.runOnce();
        assertEquals("CONFIRMED", status(booking));
        assertEquals("COMPLETED", tasks.findById(booking.getId()).orElseThrow().getState());
        verify(wallet, times(2)).settleDebit(argThat(w -> w.operationId().equals(booking.getId())));
    }

    @Test
    void crashedWorkerLeaseIsReclaimedWithoutCustomerRequest() {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        transactions.claimRecovery(booking.getId()); // Simulate process death before recording any result.
        when(wallet.settleDebit(any())).thenReturn("APPLIED");
        clock.advanceSeconds(61);
        worker.runOnce();
        assertEquals("CONFIRMED", status(booking));
        assertEquals(2, tasks.findById(booking.getId()).orElseThrow().getAttempts());
    }

    @Test
    void expiredLeaseCannotOverwriteNewWorkerResult() {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        RecoveryWork old = transactions.claimRecovery(booking.getId());
        clock.advanceSeconds(61);
        RecoveryWork replacement = transactions.claimRecovery(booking.getId());
        transactions.completeDebit(old, "APPLIED");
        assertEquals("PAYMENT_PROCESSING", status(booking));
        transactions.completeDebit(replacement, "DECLINED");
        assertEquals("FAILED", status(booking));
    }

    @Test
    void dayLongOutageDoesNotBlockInventoryAndLaterVoidsUnchargedPayment() {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        when(wallet.settleDebit(any())).thenThrow(new ResourceAccessException("service unavailable"));
        worker.processOne(booking.getId());
        clock.advanceSeconds(301);
        cleanup.runOnce();
        assertEquals("PAYMENT_RELEASED", status(booking));
        assertTrue(attempt(42L, "10:00", "11:00"));
        clock.advanceSeconds(86400);
        doReturn("VOIDED").when(wallet).settleDebit(any());
        worker.runOnce();
        assertEquals("FAILED", status(booking));
        verify(wallet).settleDebit(argThat(w -> !w.allowDebit()));
        verify(wallet, never()).refund(any());
    }

    @Test
    void lateChargeIsFullyRefundedWithoutReclaimingReleasedSlot() {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        RecoveryWork inFlight = transactions.claimRecovery(booking.getId());
        clock.advanceSeconds(301);
        cleanup.runOnce();
        assertTrue(attempt(42L, "10:00", "11:00"));
        transactions.completeDebit(inFlight, "APPLIED");
        assertEquals("REFUND_PENDING", status(booking));
        worker.runOnce();
        assertEquals("CANCELLED", status(booking));
        verify(wallet).refund(argThat(w -> w.amount().equals(new BigDecimal("100.00"))));
        verify(wallet, never()).settleDebit(any());
    }

    @Test
    void refundResponseLostIsRetriedWithFrozenAmountAndStableId() {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        transactions.confirm(booking.getId());
        transactions.requestCancellation(booking.getId(), "player@example.com", false);
        assertTrue(attempt(42L, "10:00", "11:00")); // Inventory release is independent of refund availability.
        java.util.Set<UUID> credited = new java.util.HashSet<>();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            RecoveryWork work = invocation.getArgument(0);
            assertEquals(new BigDecimal("50.00"), work.amount());
            credited.add(work.operationId());
            if (calls.getAndIncrement() == 0) throw new ResourceAccessException("refund applied, response lost");
            return null;
        }).when(wallet).refund(any());
        worker.processOne(booking.getId());
        assertEquals("REFUND_PENDING", status(booking));
        clock.advanceSeconds(172800); // Retry after the booking date: refund amount must not be recalculated.
        worker.runOnce();
        assertEquals("CANCELLED", status(booking));
        assertEquals(1, credited.size());
        assertEquals(2, calls.get());
    }

    @Test
    void refundSuccessFollowedByDatabaseFailureRecoversAutomatically() {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        transactions.confirm(booking.getId());
        transactions.requestCancellation(booking.getId(), "player@example.com", false);
        doThrow(new org.springframework.dao.CannotAcquireLockException("database unavailable"))
                .doCallRealMethod().when(transactions).completeRefund(any());
        worker.processOne(booking.getId());
        assertEquals("REFUND_PENDING", status(booking));
        clock.advanceSeconds(3);
        worker.runOnce();
        assertEquals("CANCELLED", status(booking));
        ArgumentCaptor<RecoveryWork> calls = ArgumentCaptor.forClass(RecoveryWork.class);
        verify(wallet, times(2)).refund(calls.capture());
        assertEquals(calls.getAllValues().get(0).operationId(), calls.getAllValues().get(1).operationId());
    }

    @Test
    void successfulDebitFollowedByConfirmationFailureIsReconciledAutomatically() {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        when(wallet.settleDebit(any())).thenReturn("APPLIED");
        doThrow(new org.springframework.dao.CannotAcquireLockException("confirmation failed"))
                .doCallRealMethod().when(transactions).completeDebit(any(), eq("APPLIED"));
        worker.processOne(booking.getId());
        assertEquals("PAYMENT_UNKNOWN", status(booking));
        clock.advanceSeconds(3);
        worker.runOnce();
        assertEquals("CONFIRMED", status(booking));
        verify(wallet, times(2)).settleDebit(argThat(w -> w.operationId().equals(booking.getId())));
    }

    @Test
    void concurrentRequestRetriesCreateOneBookingAndOnePaymentTask() throws Exception {
        UUID key = UUID.randomUUID();
        BookingRequestDto dto = request(42L, "10:00", "11:00");
        List<UUID> ids = race(10, i -> reservations.reserve(dto, "player@example.com", new BigDecimal("100.00"), key).getId());
        assertEquals(1, new java.util.HashSet<>(ids).size());
        assertEquals(1, bookings.count()); assertEquals(1, requests.count());
        race(6, i -> { transactions.startPaymentOnce(ids.getFirst()); return true; });
        assertEquals(1, tasks.count());
    }

    @Test
    void idempotencySurvivesCancellationAndIgnoresChangedServerPrice() {
        UUID key = UUID.randomUUID();
        BookingRequestDto dto = request(42L, "10:00", "11:00");
        Booking original = reservations.reserve(dto, "player@example.com", new BigDecimal("100.00"), key);
        transactions.beginPayment(original.getId()); transactions.confirm(original.getId());
        transactions.requestCancellation(original.getId(), "player@example.com", false);
        worker.processOne(original.getId());
        Booking replay = reservations.reserve(dto, "player@example.com", new BigDecimal("200.00"), key);
        assertEquals(original.getId(), replay.getId()); assertEquals("CANCELLED", replay.getStatus());
        assertEquals(new BigDecimal("100.00"), replay.getTotalAmount()); assertEquals(1, bookings.count());
        assertThrows(ResponseStatusException.class, () -> reservations.reserve(request(43L,"10:00","11:00"),
                "player@example.com", new BigDecimal("100.00"), key));
        assertNotEquals(original.getId(), reservations.reserve(request(43L,"10:00","11:00"),
                "other@example.com", new BigDecimal("100.00"), key).getId());
    }

    @Test
    void closureRaceCannotLeaveAnUncancelledReservationOrAllowNewCourts() throws Exception {
        reservations.prepareVenue(1L);
        race(2, i -> {
            if (i == 0) createJob(null, UUID.randomUUID());
            else attempt(42L, "10:00", "11:00");
            return true;
        });
        assertFalse(attempt(43L, "10:00", "11:00")); // Court did not exist in the lock table before closure.
        bulkWorker.runOnce();
        assertTrue(bookings.findAll().stream().noneMatch(b -> ReservationTransactionService.ACTIVE_STATUSES.contains(b.getStatus())));
    }

    @Test
    void courtClosureDoesNotCloseAnotherCourtAndChecksEndDate() {
        reservations.prepareVenue(1L);
        createJob(42L, UUID.randomUUID());
        assertFalse(attempt(42L,"10:00","11:00"));
        assertTrue(attempt(43L,"10:00","11:00"));
        BookingRequestDto later = request(42L,"10:00","11:00");
        later.setBookingDate(LocalDate.now(clock).plusDays(2));
        assertNotNull(reservations.reserve(later,"player@example.com",new BigDecimal("100.00")));
    }

    @Test
    void paymentSuccessAfterClosureTriggersFullRefundInsteadOfConfirmation() {
        Booking booking = reserve(42L,"10:00","11:00");
        transactions.beginPayment(booking.getId());
        RecoveryWork inFlight = transactions.claimRecovery(booking.getId());
        var job = createJob(null, UUID.randomUUID());
        transactions.completeDebit(inFlight, "APPLIED");
        assertEquals("REFUND_PENDING", status(booking));
        assertEquals(new BigDecimal("100.00"), tasks.findById(booking.getId()).orElseThrow().getAmount());
        bulkWorker.runOnce(); worker.runOnce(); bulkWorker.runOnce();
        assertEquals("CANCELLED", status(booking));
        assertEquals("COMPLETED", bulkJobs.findById(job.getId()).orElseThrow().getState());
        assertFalse(attempt(42L,"10:00","11:00")); // Closure still intentionally active.
    }

    @Test
    void bulkJobBatchesResumeAndClosureCannotBeRemovedBeforeEnqueueCompletes() {
        for (int hour = 10; hour < 13; hour++) {
            Booking booking = reserve(42L, hour + ":00", (hour + 1) + ":00");
            transactions.beginPayment(booking.getId()); transactions.confirm(booking.getId());
        }
        var job = createJob(null, UUID.randomUUID());
        administration.processBatch(job.getId());
        assertEquals(2, bulkJobs.findById(job.getId()).orElseThrow().getQueuedCount());
        assertEquals("ENQUEUING", bulkJobs.findById(job.getId()).orElseThrow().getState());
        assertThrows(ResponseStatusException.class, () -> administration.removeClosure(job.getClosureId(),1L));
        bulkWorker.runOnce();
        assertEquals("REFUNDS_PENDING", bulkJobs.findById(job.getId()).orElseThrow().getState());
        worker.runOnce(); bulkWorker.runOnce();
        assertEquals("COMPLETED", bulkJobs.findById(job.getId()).orElseThrow().getState(),
                () -> bookings.findAll().stream().map(b -> b.getStatus()+":"+b.getBulkCancellationJobId()).toList().toString());
        assertEquals(3, bulkJobs.findById(job.getId()).orElseThrow().getQueuedCount());
        administration.removeClosure(job.getClosureId(),1L);
        assertTrue(attempt(42L,"10:00","11:00"));
    }

    @Test
    void failedBulkBatchRollsBackTasksAndProgressAndAutomaticallyRetries() {
        for (int hour = 10; hour < 12; hour++) {
            Booking booking = reserve(42L, hour + ":00", (hour + 1) + ":00");
            transactions.beginPayment(booking.getId()); transactions.confirm(booking.getId());
        }
        var job = createJob(null, UUID.randomUUID());
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("process died mid-batch");
            return invocation.callRealMethod();
        }).when(org.springframework.test.util.AopTestUtils.<ReservationTransactionService>getUltimateTargetObject(transactions))
                .cancelForBulk(any(),eq(job.getId()));
        bulkWorker.runOnce();
        assertEquals(0, bulkJobs.findById(job.getId()).orElseThrow().getQueuedCount());
        assertTrue(bookings.findAll().stream().allMatch(b -> "CONFIRMED".equals(b.getStatus())));
        assertTrue(tasks.findAll().stream().allMatch(t -> "PAYMENT".equals(t.getKind())));
        bulkWorker.runOnce();
        assertEquals(2, bulkJobs.findById(job.getId()).orElseThrow().getQueuedCount());
        assertTrue(bookings.findAll().stream().allMatch(b -> "REFUND_PENDING".equals(b.getStatus())));
    }

    @Test
    void bulkRequestIdempotencyDoesNotCreateDuplicateClosures() {
        reservations.prepareVenue(1L);
        UUID key = UUID.randomUUID();
        var first = createJob(null,key);
        assertEquals(first.getId(), createJob(null,key).getId());
        assertEquals(1, closures.count()); assertEquals(1, bulkJobs.count());
        assertThrows(ResponseStatusException.class, () -> createJob(42L,key));
    }

    private com.sportsarena.booking_service.entity.BulkCancellationJob createJob(Long courtId, UUID key) {
        reservations.prepareVenue(1L);
        com.sportsarena.booking_service.dto.BulkCancelRequestDto dto = new com.sportsarena.booking_service.dto.BulkCancelRequestDto();
        dto.setVenueId(1L); dto.setCourtId(courtId); dto.setStartDate(LocalDate.now(clock).plusDays(1)); dto.setEndDate(dto.getStartDate());
        return administration.create(dto,new VenueBookingAccessClient.Scope(1L,courtId),"owner@example.com",key);
    }

    private BookingRequestDto request(Long courtId,String start,String end) {
        BookingRequestDto dto = new BookingRequestDto(); dto.setVenueId(1L); dto.setCourtId(courtId);
        dto.setBookingDate(LocalDate.now(clock).plusDays(1)); dto.setStartTime(LocalTime.parse(start)); dto.setEndTime(LocalTime.parse(end));
        return dto;
    }

    @Test
    void expirationRacingWithPaymentStartAndReplacementCannotReviveReleasedHold() throws Exception {
        Booking old = reserve(42L, "10:00", "11:00");
        clock.advanceSeconds(301);
        List<Boolean> results = race(2, i -> {
            if (i == 1) return attempt(42L, "10:00", "11:00");
            try {
                return transactions.beginPayment(old.getId());
            } catch (ResponseStatusException ex) {
                assertEquals(409, ex.getStatusCode().value());
                return false;
            }
        });
        assertFalse(results.get(0));
        assertTrue(results.get(1));
        assertEquals("EXPIRED", status(old));
        assertThrows(ResponseStatusException.class, () -> transactions.confirm(old.getId()));
    }

    @Test
    void definitiveFailureReleasesSlotAndCannotLaterConfirm() {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        transactions.paymentFailed(booking.getId());
        assertTrue(attempt(42L, "10:00", "11:00"));
        assertThrows(ResponseStatusException.class, () -> transactions.confirm(booking.getId()));
    }

    @Test
    void confirmedBookingCannotBeDemotedByRepeatedCleanupOrFailure() {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        transactions.confirm(booking.getId());
        transactions.confirm(booking.getId());
        clock.advanceSeconds(301);
        transactions.expire(booking.getId());
        transactions.paymentUnknown(booking.getId());
        assertThrows(ResponseStatusException.class, () -> transactions.paymentFailed(booking.getId()));
        assertEquals("CONFIRMED", status(booking));
    }

    @Test
    void cancellationIsIdempotentAndReleasedBookingCannotConfirmAgain() {
        Booking booking = reserve(42L, "10:00", "11:00");
        transactions.beginPayment(booking.getId());
        transactions.confirm(booking.getId());
        assertTrue(transactions.cancelConfirmed(booking.getId()));
        assertFalse(transactions.cancelConfirmed(booking.getId()));
        assertThrows(ResponseStatusException.class, () -> transactions.confirm(booking.getId()));
        assertTrue(attempt(42L, "10:00", "11:00"));
    }

    private String status(Booking booking) {
        return bookings.findById(booking.getId()).orElseThrow().getStatus();
    }

    private Booking reserve(Long courtId, String start, String end) {
        BookingRequestDto request = new BookingRequestDto();
        request.setCourtId(courtId);
        request.setVenueId(1L);
        request.setBookingDate(LocalDate.now(clock).plusDays(1));
        request.setStartTime(LocalTime.parse(start));
        request.setEndTime(LocalTime.parse(end));
        return reservations.reserve(request, "player@example.com", new BigDecimal("100.00"));
    }

    private boolean attempt(Long courtId, String start, String end) {
        try {
            reserve(courtId, start, end);
            return true;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 409) throw ex;
            return false;
        }
    }

    private <T> List<T> race(int count, IntFunction<T> operation) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(count)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return operation.apply(index);
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        }
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean MutableClock testClock() { return new MutableClock(); }
    }

    static final class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2030-01-01T00:00:00Z"));
        void advanceSeconds(long seconds) { now.updateAndGet(value -> value.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }
}
