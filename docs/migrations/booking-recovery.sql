-- Apply once to the BOOKING database after booking-holds.sql, with every booking instance stopped.
-- UUID is the project's current database ID type. Adapt to the physical booking ID type on another engine.
CREATE TABLE booking_recovery_tasks (
    booking_id UUID PRIMARY KEY REFERENCES bookings(id),
    version BIGINT DEFAULT 0 NOT NULL,
    kind VARCHAR(12) NOT NULL CHECK (kind IN ('PAYMENT', 'REFUND')),
    state VARCHAR(12) NOT NULL CHECK (state IN ('PENDING', 'RUNNING', 'COMPLETED')),
    operation_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    payment_deadline BIGINT NOT NULL,
    next_attempt_at BIGINT NOT NULL,
    lease_token UUID,
    lease_until BIGINT,
    attempts INTEGER DEFAULT 0 NOT NULL,
    last_error VARCHAR(100)
);
CREATE INDEX recovery_due_idx ON booking_recovery_tasks(state, next_attempt_at, lease_until);
CREATE INDEX recovery_deadline_idx ON booking_recovery_tasks(kind, payment_deadline);

-- Resume pre-existing uncertain payments. A missing deadline means close/reconcile, not start a new debit.
INSERT INTO booking_recovery_tasks
    (booking_id, version, kind, state, operation_id, amount, payment_deadline, next_attempt_at, attempts)
    SELECT id, 0, 'PAYMENT', 'PENDING', id, total_amount, COALESCE(hold_expires_at, 0), 0, 0
    FROM bookings WHERE status IN ('PAYMENT_PROCESSING', 'PAYMENT_UNKNOWN');
