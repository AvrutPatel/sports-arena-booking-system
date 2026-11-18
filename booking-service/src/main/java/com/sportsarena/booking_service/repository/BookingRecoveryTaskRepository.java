package com.sportsarena.booking_service.repository;

import com.sportsarena.booking_service.entity.BookingRecoveryTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface BookingRecoveryTaskRepository extends JpaRepository<BookingRecoveryTask, UUID> {
    @Query("select t.bookingId from BookingRecoveryTask t where "
            + "(t.state = 'PENDING' and t.nextAttemptAt <= :now) "
            + "or (t.state = 'RUNNING' and t.leaseUntil <= :now) order by t.nextAttemptAt, t.bookingId")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    List<UUID> findDue(@Param("now") long now, Pageable page);

    @Query("select t.bookingId from BookingRecoveryTask t, Booking b where t.bookingId = b.id "
            + "and t.kind = 'PAYMENT' and t.state <> 'COMPLETED' and t.paymentDeadline <= :now "
            + "and b.status in ('PAYMENT_PROCESSING', 'PAYMENT_UNKNOWN') order by t.paymentDeadline, t.bookingId")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    List<UUID> findOverduePayments(@Param("now") long now, Pageable page);
}
