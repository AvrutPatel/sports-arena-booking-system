-- Apply to the BOOKING database with services stopped, after taking a backup.
BEGIN;
ALTER TABLE bookings
    ALTER COLUMN total_amount TYPE NUMERIC(19, 2) USING ROUND(total_amount::numeric, 2),
    ALTER COLUMN total_amount SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT booking_amount_valid
    CHECK (total_amount > 0 AND total_amount <> 'NaN'::numeric);
COMMIT;
