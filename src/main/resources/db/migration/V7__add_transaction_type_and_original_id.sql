ALTER TABLE transactions
    ADD COLUMN transaction_type     VARCHAR(10) NOT NULL DEFAULT 'PAYMENT',
    ADD COLUMN original_transaction_id UUID REFERENCES transactions(id);

CREATE INDEX idx_transactions_original_id ON transactions(original_transaction_id)
    WHERE original_transaction_id IS NOT NULL;
