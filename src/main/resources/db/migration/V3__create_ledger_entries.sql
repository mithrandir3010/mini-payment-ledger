CREATE TABLE ledger_entries (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID           NOT NULL REFERENCES transactions(id),
    account_id     UUID           NOT NULL REFERENCES accounts(id),
    entry_type     VARCHAR(10)    NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount         NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)     NOT NULL,
    created_at     TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_entries_account_id     ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries(transaction_id);
