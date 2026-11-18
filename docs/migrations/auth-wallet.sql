-- Apply to the AUTH database with services stopped, after taking a backup.
-- Existing balances are opening balances; this does not fabricate historical debits.
BEGIN;

ALTER TABLE user_profiles
    ALTER COLUMN wallet_balance TYPE NUMERIC(19, 2)
        USING ROUND(wallet_balance::numeric, 2),
    ALTER COLUMN wallet_balance SET DEFAULT 0.00,
    ALTER COLUMN wallet_balance SET NOT NULL;

ALTER TABLE user_profiles ADD CONSTRAINT wallet_balance_valid
    CHECK (wallet_balance >= 0 AND wallet_balance <> 'NaN'::numeric);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    operation_id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    debit_reference UUID,
    balance_after NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Add guards even if Hibernate has already created the new table in development.
ALTER TABLE wallet_transactions
    ADD CONSTRAINT wallet_tx_email_fk FOREIGN KEY (email) REFERENCES user_profiles(email),
    ADD CONSTRAINT wallet_tx_debit_fk FOREIGN KEY (debit_reference) REFERENCES wallet_transactions(operation_id),
    ADD CONSTRAINT wallet_tx_type_valid CHECK (type IN ('TOPUP', 'DEBIT', 'REFUND')),
    ADD CONSTRAINT wallet_tx_amount_valid CHECK (amount > 0 AND amount <> 'NaN'::numeric),
    ADD CONSTRAINT wallet_tx_balance_valid CHECK (balance_after >= 0 AND balance_after <> 'NaN'::numeric),
    ADD CONSTRAINT wallet_tx_refund_reference_valid CHECK (
        (type = 'REFUND' AND debit_reference IS NOT NULL)
        OR (type <> 'REFUND' AND debit_reference IS NULL));

CREATE INDEX IF NOT EXISTS wallet_debit_reference_idx ON wallet_transactions(debit_reference);
COMMIT;
