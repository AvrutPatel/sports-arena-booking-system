-- Apply to the VENUE database with services stopped, after taking a backup.
BEGIN;
ALTER TABLE courts
    ALTER COLUMN hourly_rate TYPE NUMERIC(19, 2) USING ROUND(hourly_rate::numeric, 2),
    ALTER COLUMN hourly_rate SET NOT NULL;
ALTER TABLE courts ADD CONSTRAINT court_rate_valid
    CHECK (hourly_rate > 0 AND hourly_rate <> 'NaN'::numeric);
COMMIT;
