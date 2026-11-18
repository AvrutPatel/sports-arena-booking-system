package com.sportsarena.booking_service.service;

import com.sportsarena.booking_service.repository.BulkCancellationJobRepository;
import org.slf4j.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.NEVER)
public class BulkCancellationWorker {
    private static final Logger LOG = LoggerFactory.getLogger(BulkCancellationWorker.class);
    private final BulkCancellationJobRepository jobs;
    private final BookingAdministrationTransactions transactions;
    public BulkCancellationWorker(BulkCancellationJobRepository jobs, BookingAdministrationTransactions transactions) {
        this.jobs = jobs; this.transactions = transactions;
    }
    @Scheduled(fixedDelayString = "${booking.bulk.interval-ms:5000}")
    public void runOnce() {
        for (var job : jobs.findByStateOrderByCreatedAtAsc("ENQUEUING", PageRequest.of(0, 20))) {
            try { transactions.processBatch(job.getId()); }
            catch (RuntimeException ex) { LOG.warn("Bulk cancellation {} will resume on a later scan", job.getId()); }
        }
        for (var id : jobs.findFinishedRefundJobs(PageRequest.of(0, 20))) {
            try { transactions.finishIfRefundsComplete(id); }
            catch (RuntimeException ex) { LOG.warn("Bulk cancellation {} progress will be retried", id); }
        }
    }
}
