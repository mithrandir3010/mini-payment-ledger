CREATE TABLE api_keys (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    key_hash    VARCHAR(64)  NOT NULL UNIQUE,
    owner_name  VARCHAR(100) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_key_hash ON api_keys (key_hash);
