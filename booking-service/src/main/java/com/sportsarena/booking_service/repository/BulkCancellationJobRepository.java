package com.sportsarena.booking_service.repository;

import com.sportsarena.booking_service.entity.BulkCancellationJob;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface BulkCancellationJobRepository extends JpaRepository<BulkCancellationJob, UUID> {
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    Optional<BulkCancellationJob> findByOwnerEmailAndRequestKey(String email, UUID key);
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    List<BulkCancellationJob> findByStateOrderByCreatedAtAsc(String state, Pageable page);
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    List<BulkCancellationJob> findByVenueIdOrderByCreatedAtDesc(Long venueId, Pageable page);
    boolean existsByClosureIdAndState(Long closureId, String state);

    @Query("select j.id from BulkCancellationJob j where j.state = 'REFUNDS_PENDING' and not exists "
            + "(select b.id from Booking b where b.bulkCancellationJobId = j.id "
            + "and b.status in ('REFUND_PENDING','PAYMENT_RELEASED','PAYMENT_PROCESSING','PAYMENT_UNKNOWN')) order by j.createdAt")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    List<UUID> findFinishedRefundJobs(Pageable page);
}
