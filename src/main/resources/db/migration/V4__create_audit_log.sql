CREATE TABLE audit_log (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID        NOT NULL REFERENCES transactions(id),
    from_status    VARCHAR(20),
    to_status      VARCHAR(20) NOT NULL,
    reason         TEXT,
    created_at     TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_transaction_id ON audit_log(transaction_id);
