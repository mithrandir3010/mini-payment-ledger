# Mini Payment Ledger API — CLAUDE.md

## Project Identity
- **Stack:** Java 21 · Spring Boot 3.x · Kafka · PostgreSQL 16 · Redis 7 · Docker · AWS ECS
- **Pattern:** Event-driven, async payment processing + double-entry ledger
- **Goal:** Portfolio project — fintech interview prep (Stripe/Wise/Revolut level)

---

## Core Rules (never break these)

1. **Balance is NEVER stored.** No `balance` column anywhere. Always derived:
   ```sql
   SELECT SUM(CASE WHEN entry_type='CREDIT' THEN amount ELSE -amount END)
   FROM ledger_entries WHERE account_id = ?
   ```
2. **Every payment produces exactly 2 ledger entries** — one DEBIT, one CREDIT.
3. **Every endpoint that mutates state requires `Idempotency-Key` header.**
4. **State transitions are strict** — illegal transitions throw `IllegalStateException`.
5. **POST /payments returns 202 Accepted**, not 200 — async, not synchronous.

---

## State Machine

```
PENDING → PROCESSING → SETTLED
                    └→ FAILED → REVERSED
SETTLED → REVERSED  (refund)
```
Illegal: any reverse direction (SETTLED→PENDING, REVERSED→PROCESSING, etc.)

---

## Architecture

```
REST API → Redis (idempotency check) → Kafka (payment.events)
                                              ├── LedgerWriterConsumer    → PostgreSQL
                                              ├── BalanceProjectorConsumer (cache only)
                                              └── NotificationConsumer    → webhook/log

Failed after 3 retries → payment.dlq
```

---

## Kafka Topics

| Topic | Partitions | Retention |
|---|---|---|
| `payment.events` | 3 | 7d |
| `payment.dlq` | 1 | 30d |
| `payment.notifications` | 2 | 3d |

Consumer groups: `ledger-writers`, `balance-projectors`, `notifiers` (independent offsets)

---

## Database Tables

```
accounts       → id, owner_id, currency, status, created_at
transactions   → id, idempotency_key(UNIQUE), from_account_id, to_account_id,
                 amount, currency, status, description, created_at, updated_at
ledger_entries → id, transaction_id, account_id, entry_type(DEBIT|CREDIT),
                 amount, currency, created_at
audit_log      → id, transaction_id, from_status, to_status, reason, created_at
```

Key indexes: `ledger_entries(account_id)`, `ledger_entries(transaction_id)`,
`transactions(idempotency_key)`, `audit_log(transaction_id)`

Migrations: Flyway, `db/migration/V1__V4__*.sql`

---

## API Endpoints

```
POST   /api/v1/payments                → initiate payment (202)
GET    /api/v1/payments/{id}           → status check
POST   /api/v1/payments/{id}/reverse   → refund / reverse
GET    /api/v1/accounts/{id}/balance   → derived balance
GET    /api/v1/accounts/{id}/ledger    → paginated ledger history
GET    /api/v1/transactions/{id}/audit → audit trail
```

---

## Package Structure

```
com.mehmetali.ledger
├── api/              → controllers + DTOs
├── domain/
│   ├── model/        → Transaction, LedgerEntry, Account, TransactionStatus (enum)
│   ├── service/      → PaymentService, LedgerService, BalanceService
│   └── statemachine/ → TransactionStateMachine
├── messaging/        → PaymentEventProducer, *Consumer classes, PaymentEvent
├── idempotency/      → IdempotencyService, IdempotencyFilter
└── infrastructure/   → KafkaConfig, RedisConfig, RetryConfig
```

---

## Idempotency Flow

```
Request arrives → check Redis for idempotency_key
  HIT  → return cached response (no reprocessing)
  MISS → process → write to Redis (TTL 24h) → publish to Kafka → 202
```

---

## Local Dev

```bash
docker-compose up -d   # starts Kafka, Zookeeper, PostgreSQL, Redis
```

Ports: PostgreSQL=5432, Redis=6379, Kafka=9092

---

## Phase Progress

- [ ] **Phase 1** — Docker + Spring Boot + Flyway + entities + POST /payments (DB only, no Kafka)
- [ ] **Phase 2** — Kafka producer/consumer + LedgerWriter + StateMachine + audit log
- [ ] **Phase 3** — Idempotency (Redis) + Spring Retry + DLQ + GET balance
- [ ] **Phase 4** — Reverse/refund + NotificationConsumer + paginated ledger + Actuator + README

---

## Key Decisions Log

| Decision | Reason |
|---|---|
| 202 not 200 on POST /payments | Async — Kafka hasn't processed yet |
| Derived balance, no stored column | Prevents drift, enables point-in-time queries |
| Redis for idempotency, not DB | TTL-native, low latency, no extra DB load |
| DLQ after 3 retries | Events never lost, manual inspection possible |
| Flyway for migrations | Reproducible schema across environments |
