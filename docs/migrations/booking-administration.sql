-- Apply once after booking-recovery.sql, with all old booking-service instances stopped.
-- UUID matches the current project's ID representation; map it appropriately on another engine.
CREATE TABLE venue_booking_locks (
    venue_id BIGINT PRIMARY KEY,
    revision BIGINT DEFAULT 0 NOT NULL
);
INSERT INTO venue_booking_locks (venue_id)
    SELECT venue_id FROM bookings WHERE venue_id IS NOT NULL
    UNION SELECT venue_id FROM facility_closures WHERE venue_id IS NOT NULL;

ALTER TABLE bookings ADD COLUMN bulk_cancellation_job_id UUID;
ALTER TABLE facility_closures ADD COLUMN bulk_cancellation_job_id UUID;

CREATE TABLE booking_requests (
    id VARCHAR(64) PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    request_key UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    created_at BIGINT NOT NULL,
    CONSTRAINT booking_request_owner_key UNIQUE (user_email, request_key)
);

CREATE TABLE bulk_cancellation_jobs (
    id UUID PRIMARY KEY,
    version BIGINT DEFAULT 0 NOT NULL,
    owner_email VARCHAR(255) NOT NULL,
    request_key UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    venue_id BIGINT NOT NULL,
    court_id BIGINT,
    start_date DATE NOT NULL,
    end_date DATE,
    closure_id BIGINT NOT NULL REFERENCES facility_closures(id),
    state VARCHAR(24) NOT NULL CHECK (state IN ('ENQUEUING','REFUNDS_PENDING','COMPLETED')),
    queued_count BIGINT DEFAULT 0 NOT NULL,
    created_at BIGINT NOT NULL,
    CONSTRAINT bulk_owner_request_key UNIQUE (owner_email, request_key)
);
CREATE INDEX bulk_job_scan_idx ON bulk_cancellation_jobs(state, created_at);
CREATE INDEX booking_bulk_progress_idx ON bookings(bulk_cancellation_job_id, status);
CREATE INDEX booking_bulk_scope_idx ON bookings(venue_id, status, booking_date, court_id);
CREATE INDEX closure_venue_dates_idx ON facility_closures(venue_id, start_date, end_date);
CREATE INDEX closure_court_dates_idx ON facility_closures(court_id, start_date, end_date);
