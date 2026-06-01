#!/bin/bash

DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="ledger_db"
DB_USER="ledger_user"
DB_PASS="ledger_pass"

ALI_ID="a0000000-0000-0000-0000-000000000001"
AYSE_ID="a0000000-0000-0000-0000-000000000002"
ALICE_USD_ID="a0000000-0000-0000-0000-000000000003"
SEED_TX_ID="b0000000-0000-0000-0000-000000000001"

# Test API key: lk_live_test00000000000000000000000000000001
# SHA-256 hash (precomputed):
API_KEY_HASH=$(echo -n "lk_live_test00000000000000000000000000000001" | openssl dgst -sha256 | awk '{print $2}')

export PGPASSWORD=$DB_PASS

echo "Seeding accounts..."

psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME <<SQL
INSERT INTO accounts (id, owner_id, currency, status)
VALUES
  ('$ALI_ID',      gen_random_uuid(), 'TRY', 'ACTIVE'),
  ('$AYSE_ID',     gen_random_uuid(), 'TRY', 'ACTIVE'),
  ('$ALICE_USD_ID',gen_random_uuid(), 'USD', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO transactions (id, idempotency_key, from_account_id, to_account_id, amount, currency, status, description)
VALUES (
  '$SEED_TX_ID',
  'seed-initial-balance-ali',
  '$ALI_ID',
  '$ALI_ID',
  1000.00,
  'TRY',
  'SETTLED',
  'Initial balance'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO ledger_entries (account_id, transaction_id, entry_type, amount, currency)
VALUES ('$ALI_ID', '$SEED_TX_ID', 'CREDIT', 1000.00, 'TRY')
ON CONFLICT DO NOTHING;

INSERT INTO api_keys (key_hash, owner_name)
VALUES ('$API_KEY_HASH', 'seed-test-key')
ON CONFLICT (key_hash) DO NOTHING;
SQL

echo "Done."
echo "  Ali (TRY):        $ALI_ID"
echo "  Ayse (TRY):       $AYSE_ID"
echo "  Alice (USD):      $ALICE_USD_ID"
echo "  Test API Key:     lk_live_test00000000000000000000000000000001"
