package com.sportsarena.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

/** Durable outbox/work item, created in the same transaction as its booking state change. */
@Entity
@Table(name = "booking_recovery_tasks", indexes = {
        @Index(name = "recovery_due_idx", columnList = "state,next_attempt_at,lease_until"),
        @Index(name = "recovery_deadline_idx", columnList = "kind,payment_deadline")
})
@Data
@NoArgsConstructor
public class BookingRecoveryTask {
    @Id private UUID bookingId;
    @Version private long version;
    @Column(nullable = false, length = 12) private String kind;
    @Column(nullable = false, length = 12) private String state;
    @Column(nullable = false) private UUID operationId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private long paymentDeadline;
    @Column(nullable = false) private long nextAttemptAt;
    private UUID leaseToken;
    private Long leaseUntil;
    @Column(nullable = false) private int attempts;
    @Column(length = 100) private String lastError;

    public BookingRecoveryTask(UUID bookingId, String kind, UUID operationId, BigDecimal amount, long deadline, long now) {
        this.bookingId = bookingId; this.kind = kind; this.operationId = operationId; this.amount = amount;
        this.paymentDeadline = deadline; this.nextAttemptAt = now; this.state = "PENDING";
    }
}
