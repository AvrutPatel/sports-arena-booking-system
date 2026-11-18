-- Apply once to the BOOKING database with all booking-service instances stopped.
-- Audit and resolve pre-existing overlapping active bookings before reopening traffic.
-- These statements use ordinary relational tables, keys, columns, and indexes.
-- Adapt DDL syntax to your database's migration tool where necessary.

CREATE TABLE court_booking_locks (
    court_id BIGINT NOT NULL PRIMARY KEY,
    revision BIGINT DEFAULT 0 NOT NULL
);

ALTER TABLE bookings ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE bookings ADD COLUMN hold_expires_at BIGINT;

INSERT INTO court_booking_locks (court_id)
    SELECT DISTINCT court_id FROM bookings WHERE court_id IS NOT NULL;

-- Old PENDING records do not tell us whether a wallet debit was already sent.
-- Retain inventory until reconciled instead of assuming they can be released.
UPDATE bookings SET status = 'PAYMENT_UNKNOWN' WHERE status = 'PENDING';

CREATE INDEX booking_overlap_lookup_idx ON bookings (court_id, booking_date, status);
CREATE INDEX booking_hold_expiry_idx ON bookings (status, hold_expires_at);
