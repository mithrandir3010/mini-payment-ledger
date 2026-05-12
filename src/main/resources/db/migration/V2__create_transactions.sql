CREATE TABLE transactions (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  VARCHAR(255)   NOT NULL UNIQUE,
    from_account_id  UUID           NOT NULL REFERENCES accounts(id),
    to_account_id    UUID           NOT NULL REFERENCES accounts(id),
    amount           NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)     NOT NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    description      TEXT,
    created_at       TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_idempotency_key ON transactions(idempotency_key);
