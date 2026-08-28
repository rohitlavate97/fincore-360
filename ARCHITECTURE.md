# ARCHITECTURE — FinCore 360

**Phase:** 0 — this document describes *intended* architecture.
**Status:** No component described here has been built. See
[PROJECT-STATUS.md](PROJECT-STATUS.md).

---

## 1. System context

```
   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
   │ ANDROID APP │   │ WEB PORTAL  │   │  ADMIN UI   │
   │ Kotlin      │   │ React + TS  │   │ React + TS  │
   │ Compose     │   │             │   │             │
   └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
          │                 │                 │
          └────────┬────────┴────────┬────────┘
                   │   HTTPS / REST  │
                   │  X-Correlation-ID
                   │  Idempotency-Key
                   ▼
        ┌──────────────────────────────┐
        │      API GATEWAY LAYER       │
        │  TLS · routing · rate limit  │
        └──────────────┬───────────────┘
                       ▼
   ┌────────────────────────────────────────────┐
   │       MODULAR BACKEND MONOLITH             │
   │            Kotlin · Spring Boot            │
   │                                            │
   │  identity   accounts   transactions        │
   │  payments   customer   notifications       │
   │  audit                                     │
   │                                            │
   │  shared: error · security · pagination     │
   │          · correlation                     │
   └───┬──────────────┬──────────────┬──────────┘
       │              │              │
       ▼              ▼              ▼
 ┌───────────┐  ┌──────────┐  ┌──────────┐
 │PostgreSQL │  │  Redis   │  │  Kafka   │
 │           │  │          │  │          │
 │ ledger    │  │ rate     │  │ domain   │
 │ audit     │  │ limits   │  │ events   │
 │ idempot.  │  │ cache    │  │ (Ph. 7)  │
 └─────┬─────┘  └──────────┘  └──────────┘
       │
       ├── Flyway migrations
       └── Append-only audit store
                       │
                       ▼
       ┌───────────────────────────────┐
       │  OpenTelemetry · Prometheus   │
       │  Grafana · structured logs    │
       └───────────────────────────────┘
```

---

## 2. Architectural style and why

| Decision | Choice | ADR |
|---|---|---|
| Backend decomposition | Modular monolith, **not** microservices | [ADR-006](docs/adr/ADR-006-Modular-Monolith.md) |
| Backend language | Kotlin | [ADR-015](docs/adr/ADR-015-Backend-Language-Kotlin.md) |
| Client architecture | Clean Architecture + MVVM | [ADR-002](docs/adr/ADR-002-Clean-Architecture.md) |
| Datastore | PostgreSQL; concurrency at DB level | [ADR-007](docs/adr/ADR-007-PostgreSQL-Primary-DB.md) |
| Events | Kafka, deferred to Phase 7 | [ADR-009](docs/adr/ADR-009-Kafka-Async-Events.md) |

The controlling principle from the master prompt: **the right architecture is the
simplest one that satisfies the actual requirements.** A service is extracted from
the monolith only when independent scaling, independent deployment, separate team
ownership, or a genuinely different data model is *demonstrated* — and the
extraction gets its own ADR first.

---

## 3. Domain modules

| Module | Owns |
|---|---|
| `identity` | Registration, login, sessions, MFA simulation, tokens, RBAC |
| `customer` | Profile, KYC simulation, beneficiaries, preferences |
| `accounts` | Account lifecycle, available vs ledger balance, statements |
| `transactions` | Transfer lifecycle, state machine, idempotency, concurrency |
| `payments` | Payment instruments and flows |
| `notifications` | Notification requests, delivery, FCM integration |
| `audit` | Append-only audit records, audit queries |
| `shared` | Error contract, security filters, pagination, correlation IDs |

Boundary rule: modules talk through application service interfaces. No JPA
repository crosses a module boundary. Enforced by ArchUnit (Phase 1).

---

## 4. Cross-cutting mechanisms

These four are the load-bearing parts of the design. Each is specified in its own
ADR and repeated here only in summary.

### 4.1 Monetary representation — [ADR-012](docs/adr/ADR-012-Monetary-Representation.md)

```
BigDecimal (JVM) ──► NUMERIC(19,4) (PostgreSQL) ──► "1234.5600" (JSON string)
```

`double`/`float` are banned for money at every layer. The JSON-string hop is the
one most systems miss — a JSON number is parsed into an IEEE-754 double by any
JavaScript client, destroying the precision preserved everywhere else.

### 4.2 Idempotency — [ADR-010](docs/adr/ADR-010-Idempotency-Strategy.md)

Every mutation carries a client-generated `Idempotency-Key`. The key record and
the business change commit in **one database transaction**; a unique constraint
serialises concurrent duplicates. Redis is a fast path only, never authoritative.

### 4.3 Concurrency — [ADR-007](docs/adr/ADR-007-PostgreSQL-Primary-DB.md)

Balance contention is resolved with `SELECT … FOR UPDATE`. JVM-level locks are
explicitly rejected: they do not span replicas. Multi-account operations lock
rows in deterministic order to avoid deadlock.

### 4.4 Correlation — [OBSERVABILITY.md](OBSERVABILITY.md)

One `X-Correlation-ID` per user action, generated on the client, propagated
through the HTTP request, into the log MDC, into the Kafka envelope, and returned
as `traceId` in every error response.

---

## 5. Transaction lifecycle

The state machine is enforced at the domain layer. Invalid transitions throw a
domain exception — never a generic error.

```
        ┌─────────┐
        │ PENDING │
        └────┬────┘
             │
    ┌────────┼────────┐
    │        │        │
    ▼        ▼        ▼
CANCELLED  PROCESSING │
           │          │
      ┌────┴────┐     │
      ▼         ▼     │
  COMPLETED  FAILED   │
      │               │
      ▼               │
  REVERSED ◄──────────┘
  (within reversal window)
```

| From | Permitted to |
|---|---|
| `PENDING` | `PROCESSING`, `CANCELLED` |
| `PROCESSING` | `COMPLETED`, `FAILED` |
| `COMPLETED` | `REVERSED` (within window) |
| `CANCELLED`, `FAILED`, `REVERSED` | — terminal |

Kotlin sealed types plus exhaustive `when` make an unhandled transition a compile
error rather than a runtime one — the deciding argument in ADR-015.

---

## 6. Request lifecycle — a transfer

```
1  Android: user taps Transfer
2  Android: generate + PERSIST Idempotency-Key, generate X-Correlation-ID
3  POST /api/v1/transfers  ──►  gateway  ──►  backend
4  Correlation filter      : put correlationId in MDC
5  JWT filter              : validate signature, extract sub + roles
6  Controller @PreAuthorize: role check
7  Service layer           : resource ownership check  ◄── IDOR defence
8  Idempotency check       : new key? replay? in-progress → 409
9  BEGIN TRANSACTION
     SELECT … FOR UPDATE on source account   ◄── deterministic lock order
     validate available balance
     debit source, credit destination
     insert transaction row (state machine validated)
     insert idempotency record
     insert audit record
     insert outbox event                     ◄── Phase 7
   COMMIT
10 Outbox relay → Kafka → audit / notification consumers  (Phase 7)
11 Response: 201 with amounts as strings, traceId = correlationId
```

Steps 7, 8, and 9 are where the system's correctness actually lives.

---

## 7. Domain events — Phase 7

Envelope: `eventId`, `eventType`, `aggregateId`, `aggregateType`, `actorId`,
`correlationId`, `timestamp`, `version`, `payload`.

```
fincore.identity.user-registered          fincore.transactions.transfer-initiated
fincore.identity.login-succeeded          fincore.transactions.transfer-completed
fincore.identity.login-failed             fincore.transactions.transfer-failed
fincore.accounts.account-created          fincore.transactions.transfer-reversed
fincore.accounts.account-frozen           fincore.notifications.notification-requested
                                          fincore.audit.audit-event-created
```

Partitioned by `accountId` — ordering is guaranteed within a partition only.
At-least-once delivery, so every consumer must be idempotent. Failures route to a
monitored dead-letter topic.

`PLANNED — not implemented.`

---

## 8. Technology stack

Versions are **not** selected. They must be verified against official
compatibility documentation during Phase 1, not assumed.

| Layer | Technology | ADR |
|---|---|---|
| Android UI | Kotlin, Compose, Material 3 | [001](docs/adr/ADR-001-Compose-over-Views.md) |
| Android DI / data / net | Hilt, Room, Retrofit + OkHttp, DataStore, WorkManager | [005](docs/adr/ADR-005-Hilt-DI.md) [003](docs/adr/ADR-003-Room-Local-DB.md) [004](docs/adr/ADR-004-Retrofit-Networking.md) |
| Android serialisation | kotlinx.serialization | [016](docs/adr/ADR-016-Serialization-Kotlinx.md) |
| Backend | Kotlin, Spring Boot, Spring Security, Spring Data JPA | [015](docs/adr/ADR-015-Backend-Language-Kotlin.md) |
| Backend serialisation | Jackson | [016](docs/adr/ADR-016-Serialization-Kotlinx.md) |
| Data | PostgreSQL, Redis, Kafka, Flyway | [007](docs/adr/ADR-007-PostgreSQL-Primary-DB.md) [008](docs/adr/ADR-008-Redis-Cache-Sessions.md) [009](docs/adr/ADR-009-Kafka-Async-Events.md) [017](docs/adr/ADR-017-Flyway-Migrations.md) |
| Web | React, TypeScript, Vitest, Playwright | — |
| Infra | Docker, Kubernetes, Terraform, GitHub Actions | — |
| Observability | OpenTelemetry, Prometheus, Grafana | — |

---

## 9. Known architectural risks

Recorded now so they are not discovered later.

| Risk | Consequence | Mitigation | State |
|---|---|---|---|
| Module boundaries enforced by convention only | Monolith decays into a layered ball of mud | ArchUnit rules in Phase 1 | Unmitigated |
| Dual write: DB commit + Kafka publish | Audit events lost on broker failure | Transactional outbox | Designed, unimplemented |
| Pessimistic lock deadlock on opposing transfers | Transfers fail under contention | Deterministic lock ordering by account ID | Identified, unspecified |
| Access tokens cannot be revoked before expiry | Locked user retains access ≤15 min | Short lifetime; `jti` denylist only if required | Accepted gap |
| IDOR on any new endpoint | Cross-customer data exposure | Per-endpoint security tests | Ongoing tax |
| Append-only audit vs data erasure requests | Regulatory conflict beyond simulation | Not addressed | Open |

---

## 10. Diagrams

ASCII diagrams above are the current form. Rendered diagrams land in
`docs/diagrams/` in Phase 12.

`PLANNED — not implemented.`
