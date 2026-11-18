package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.repository.BookingRecoveryTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import java.time.Clock;
import java.util.UUID;

@Component
@Transactional(propagation = Propagation.NEVER)
public class BookingRecoveryWorker {
    private static final Logger LOG = LoggerFactory.getLogger(BookingRecoveryWorker.class);
    private final BookingRecoveryTaskRepository tasks;
    private final ReservationTransactionService transactions;
    private final WalletClient wallet;
    private final Clock clock;

    public BookingRecoveryWorker(BookingRecoveryTaskRepository tasks, ReservationTransactionService transactions,
                                 WalletClient wallet, Clock clock) {
        this.tasks = tasks; this.transactions = transactions; this.wallet = wallet; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.recovery.interval-ms:5000}")
    public void runOnce() {
        for (UUID id : tasks.findOverduePayments(clock.millis(), PageRequest.of(0, 100))) {
            try { transactions.releaseOverduePayment(id); }
            catch (RuntimeException ex) { LOG.warn("Inventory recovery will retry booking {}", id); }
        }
        for (UUID id : tasks.findDue(clock.millis(), PageRequest.of(0, 20))) processOne(id);
    }

    public void processOne(UUID id) {
        RecoveryWork work;
        try { work = transactions.claimRecovery(id); }
        catch (RuntimeException ex) {
            LOG.warn("Could not claim recovery for booking {}; a future scan will retry", id);
            return;
        }
        if (work == null) return;
        try {
            if ("PAYMENT".equals(work.kind())) {
                transactions.completeDebit(work, wallet.settleDebit(work));
            } else if ("REFUND".equals(work.kind())) {
                wallet.refund(work);
                transactions.completeRefund(work);
            } else {
                throw new IllegalStateException("Invalid recovery task kind.");
            }
        } catch (RuntimeException ex) {
            String code = ex instanceof HttpStatusCodeException http ? "HTTP_" + http.getStatusCode().value()
                    : ex.getClass().getSimpleName();
            try { transactions.retryRecovery(work, code); }
            catch (RuntimeException databaseFailure) {
                LOG.warn("Could not save recovery retry for booking {}; lease expiry will recover it", id);
            }
            LOG.warn("Booking {} {} recovery remains pending ({})", id, work.kind(), code);
        }
    }
}
