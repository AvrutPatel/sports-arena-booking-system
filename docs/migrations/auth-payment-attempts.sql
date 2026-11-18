-- Apply once to the AUTH database before enabling booking recovery.
CREATE TABLE wallet_payment_attempts (
    operation_id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('APPLIED', 'DECLINED', 'VOIDED'))
);
