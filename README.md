# Mini Payment Ledger

A production-grade, event-driven payment processing system built as a fintech portfolio project. Demonstrates the architecture patterns used at companies like Stripe, Wise, and Revolut.

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.3 |
| Messaging | Apache Kafka |
| Database | PostgreSQL 16 (Flyway migrations V1–V7) |
| Cache / Idempotency | Redis 7 |
| Resilience | Spring Retry, Dead Letter Queue |
| Observability | Spring Actuator, Micrometer, Prometheus, Grafana, OpenTelemetry |
| API Docs | springdoc-openapi (Swagger UI) |
| Infrastructure | Docker Compose, AWS ECS (prod) |

## Architecture

```
POST /payments
      │
      ▼
IdempotencyService (Redis — 24h TTL)
      │ MISS
      ▼
PaymentService ──► validate (account exists, currency match, not self-transfer)
      │
      ▼
TransactionRepository (PENDING) ──► Kafka: payment.events (3 partitions)
      │
      └──────────────────────────────────────────────────────┐
                                                             ▼
                                                  LedgerWriterConsumer
                                                  (group: ledger-writers)
                                                             │
                                          ┌──────────────────┴─────────────────┐
                                   PAYMENT type                         REVERSAL type
                                          │                                     │
                                  SELECT FOR UPDATE                    SELECT FOR UPDATE
                                  (sender account)                     (receiver account)
                                          │                                     │
                                   hybrid balance check              hybrid balance check
                                          │                                     │
                                   double-entry write                reverse-entry write
                                   SETTLED / FAILED                  SETTLED / FAILED
                                          │                                     │
                                          └────────────┬────────────────────────┘
                                                       ▼
                                            maybeTakeSnapshot()
                                            (both accounts, REQUIRES_NEW)
                                                       │
                                               3 retries on fail → payment.dlq
                                                       │
                                               DlqConsumer (ERROR log + metric)
```

### Double-Entry Ledger

Every payment produces exactly two `ledger_entries` rows. **Balance is never stored** — always derived via hybrid strategy:

```sql
-- Snapshot exists: O(delta) instead of O(n)
SELECT snapshot.balance
     + SUM(CASE WHEN entry_type='CREDIT' THEN amount ELSE -amount END)
FROM ledger_entries
WHERE account_id = ? AND created_at > snapshot.snapshotted_at

-- No snapshot: full scan fallback
SELECT SUM(CASE WHEN entry_type='CREDIT' THEN amount ELSE -amount END)
FROM ledger_entries WHERE account_id = ?
```

Balance source is transparent in the response: `"source": "CACHE"` or `"source": "DB"`.

### State Machine

```
PENDING → PROCESSING → SETTLED → REVERSED
                    └→ FAILED  → REVERSED
```

Invalid transitions throw `InvalidStateTransitionException` → HTTP 409. All transitions are written to `audit_log`.

### Race Condition Protection

| Risk | Guard |
|---|---|
| Double-spend on concurrent payments | `SELECT FOR UPDATE` on sender's account row — serializes all payments from same account |
| Snapshot gap during concurrent writes | `SERIALIZABLE` isolation + `REQUIRES_NEW` propagation on `takeSnapshot` |
| Duplicate API requests | Redis idempotency key (24h TTL) on all mutating endpoints |

### Snapshotting

| Trigger | Strategy |
|---|---|
| Daily at 02:00 | `SnapshotScheduler` — all ACTIVE accounts |
| Delta ≥ 1000 entries | `maybeTakeSnapshot` — called by `LedgerWriterConsumer` after each settlement |

## API Reference

| Method | Endpoint | Auth Header | Description |
|---|---|---|---|
| `POST` | `/api/v1/payments` | `Idempotency-Key` | Initiate payment — `202 Accepted` |
| `GET` | `/api/v1/payments/{id}` | — | Get payment status |
| `POST` | `/api/v1/payments/{id}/reverse` | `Idempotency-Key` | Reverse / refund — `202 Accepted` |
| `GET` | `/api/v1/accounts/{id}/balance` | — | Hybrid real-time balance |
| `GET` | `/api/v1/accounts/{id}/ledger` | — | Paginated ledger history |

### Error Responses (RFC 9457 Problem Detail)

| HTTP | Trigger |
|---|---|
| `400` | Missing required header |
| `404` | Account or payment not found |
| `409` | Invalid state transition |
| `422` | Validation failure (amount, currency, self-transfer) |

## Observability

### Endpoints

| Tool | URL | Credentials |
|---|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` | — |
| Prometheus scrape | `http://localhost:8080/actuator/prometheus` | — |
| Grafana | `http://localhost:3000` | admin / admin |
| Jaeger (traces) | `http://localhost:16686` | — |

### Custom Metrics

| Metric | Tags | Description |
|---|---|---|
| `payments.initiated` | `idempotency=hit\|miss` | Counts every POST /payments call |
| `payments.processed` | `status=settled\|failed` | Counts terminal state transitions in LedgerWriter |

Prometheus datasource is auto-provisioned in Grafana — no manual setup needed. Every HTTP request is traced end-to-end via OpenTelemetry and exported to Jaeger over OTLP (port 4318).

## Running Locally

**Prerequisite:** Maven requires JDK 21. Add to `~/.zshrc`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

```bash
# Start infrastructure (PostgreSQL, Redis, Kafka, Prometheus, Grafana, Jaeger)
docker-compose up -d

# Create Kafka topics
bash scripts/create-topics.sh

# Seed test data (Ali & Ayse accounts + initial balance)
bash scripts/seed-data.sh

# Start application
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Example Requests

```bash
# Initiate a payment
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: pay-001" \
  -d '{
    "fromAccountId": "a0000000-0000-0000-0000-000000000001",
    "toAccountId":   "a0000000-0000-0000-0000-000000000002",
    "amount": 250.00,
    "currency": "TRY"
  }'

# Check balance (note the source field: CACHE vs DB)
curl http://localhost:8080/api/v1/accounts/<uuid>/balance

# Reverse a payment
curl -X POST http://localhost:8080/api/v1/payments/<id>/reverse \
  -H "Idempotency-Key: rev-001"

# Monitor DLQ
docker exec ledger-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 --topic payment.dlq --from-beginning

# Actuator endpoints
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics/payments.initiated
curl http://localhost:8080/actuator/scheduledtasks
curl http://localhost:8080/actuator/prometheus
```

## Key Design Decisions

| Decision | Reason |
|---|---|
| `202 Accepted` on POST | Async — Kafka hasn't settled the transaction yet |
| `202 Accepted` on reverse | Reversal also goes through Kafka; same async contract |
| Hybrid balance (snapshot + delta) | O(delta) instead of O(n) on 100M+ row tables |
| Redis idempotency | TTL-native, low latency, no extra DB load |
| `SELECT FOR UPDATE` on sender | Prevents double-spend under concurrent requests without full table locks |
| `SERIALIZABLE` + `REQUIRES_NEW` on snapshot | Guarantees no entry is missed during balance capture |
| DLQ after 3 retries | Events are never lost; `payment.dlq.received` metric alerts on failures |
| `auto.create.topics.enable: false` | Partition count and retention controlled explicitly |
| Flyway migrations | Reproducible schema across all environments |
| `source` + `calculatedAt` in BalanceResponse | Eventual consistency is visible, not hidden |

## Phase Progress

- [x] **Phase 1** — Docker + Spring Boot + Flyway + entities + POST /payments
- [x] **Phase 2** — Kafka producer/consumer + LedgerWriter + StateMachine + audit log
- [x] **Phase 3** — Idempotency (Redis) + Spring Retry + DLQ + GET balance
- [x] **Phase 4** — Reverse/refund + paginated ledger + Actuator + README
- [x] **Phase 5** — Balance Projector + Redis cache-aside + eventual consistency
- [x] **Phase 6** — Snapshotting + hybrid balance + race condition guard + scheduler
- [x] **Phase 7** — Hardening: double-spend lock, exception handling, reversal via Kafka, validation, DLQ metrics
- [x] **Phase 8** — Observability: Prometheus + Grafana + Jaeger (OTel tracing) + OpenAPI/Swagger UI
- [x] **Phase 9** — Security: API Key auth (X-API-Key, SHA-256 + Redis cache), rate limiting (Bucket4j), account creation, multi-currency FX
