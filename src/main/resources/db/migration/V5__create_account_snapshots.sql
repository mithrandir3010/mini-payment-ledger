CREATE TABLE account_snapshots (
    id              BIGSERIAL      PRIMARY KEY,
    account_id      UUID           NOT NULL REFERENCES accounts(id),
    balance         NUMERIC(19, 4) NOT NULL,
    snapshotted_at  TIMESTAMP      NOT NULL,
    entry_count     BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT now()
);

-- Bir hesabın en son snapshot'ını hızlı bulmak için
CREATE INDEX idx_snapshots_account_latest
    ON account_snapshots(account_id, snapshotted_at DESC);
