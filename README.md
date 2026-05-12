# Mini Payment Ledger

A production-grade, event-driven payment processing system built as a fintech portfolio project. Demonstrates the architecture patterns used at companies like Stripe, Wise, and Revolut.

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.3 |
| Messaging | Apache Kafka |
| Database | PostgreSQL 16 (Flyway migrations) |
| Cache / Idempotency | Redis 7 |
| Resilience | Spring Retry, Dead Letter Queue |
| Observability | Spring Actuator |
| Infrastructure | Docker Compose, AWS ECS (prod) |

## Architecture

```
POST /payments
      │
      ▼
IdempotencyFilter (Redis — 24h TTL)
      │
      ▼
PaymentService ──► TransactionRepository (PENDING)
      │
      ▼
PaymentEventProducer ──► Kafka: payment.events (3 partitions)
      │
      └──────────────────────────────────────┐
                                             ▼
                                  LedgerWriterConsumer
                                  (group: ledger-writers)
                                             │
                                 ┌───────────┴───────────┐
                            balance OK?              balance fail?
                                 │                        │
                                 ▼                        ▼
                         double-entry write           FAILED status
                         SETTLED status               ─────────────
                                                  3 retries → payment.dlq
```

### Double-Entry Ledger

Every payment produces exactly two `ledger_entries` rows. **Balance is never stored** — it is always derived:

```sql
SELECT SUM(CASE WHEN entry_type='CREDIT' THEN amount ELSE -amount END)
FROM ledger_entries WHERE account_id = ?
```

### State Machine

```
PENDING → PROCESSING → SETTLED → REVERSED
                    └→ FAILED  → REVERSED
```

All other transitions throw `IllegalStateException` and are recorded in `audit_log`.

## API Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/payments` | Initiate payment — returns `202 Accepted` |
| `GET` | `/api/v1/payments/{id}` | Get payment status |
| `POST` | `/api/v1/payments/{id}/reverse` | Reverse / refund |
| `GET` | `/api/v1/accounts/{id}/balance` | Derived real-time balance |
| `GET` | `/api/v1/accounts/{id}/ledger` | Paginated ledger history |

All mutating endpoints require `Idempotency-Key` header.

## Running Locally

```bash
# Start infrastructure
docker-compose up -d

# Create Kafka topics
bash scripts/create-topics.sh

# Seed test data (Ali & Ayse accounts + initial balance)
bash scripts/seed-data.sh

# Start application
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Example Request

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-001" \
  -d '{
    "fromAccountId": "a0000000-0000-0000-0000-000000000001",
    "toAccountId":   "a0000000-0000-0000-0000-000000000002",
    "amount": 250.00,
    "currency": "TRY"
  }'
```

## Key Design Decisions

| Decision | Reason |
|---|---|
| `202 Accepted` on POST | Async — Kafka hasn't settled the transaction yet |
| Derived balance | Prevents drift, enables point-in-time queries |
| Redis idempotency | TTL-native, low latency, no extra DB load |
| DLQ after 3 retries | Events are never lost, manual inspection possible |
| Flyway migrations | Reproducible schema across all environments |
| `auto.create.topics.enable: false` | Partition count and retention controlled explicitly |
