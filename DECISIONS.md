# DECISIONS — FinCore 360

Every major decision, one line each. Full reasoning, rejected alternatives, and
costs live in the linked ADR.

**Last updated:** 2026-08-28

---

## Index

| ADR | Decision | Status | Impl. phase |
|---|---|---|---|
| [001](docs/adr/ADR-001-Compose-over-Views.md) | Jetpack Compose + Material 3, not XML Views | Accepted | 2 |
| [002](docs/adr/ADR-002-Clean-Architecture.md) | Clean Architecture layers with MVVM presentation | Accepted | 2 |
| [003](docs/adr/ADR-003-Room-Local-DB.md) | Room as local DB **and single source of truth** for UI | Accepted | 2, 6 |
| [004](docs/adr/ADR-004-Retrofit-Networking.md) | Retrofit + OkHttp; cross-cutting concerns as interceptors | Accepted | 2, 3 |
| [005](docs/adr/ADR-005-Hilt-DI.md) | Hilt for DI — compile-time binding resolution | Accepted | 2 |
| [006](docs/adr/ADR-006-Modular-Monolith.md) | Modular monolith, **not** microservices | Accepted | 1 |
| [007](docs/adr/ADR-007-PostgreSQL-Primary-DB.md) | PostgreSQL; concurrency resolved **in the database** | Accepted | 1, 5 |
| [008](docs/adr/ADR-008-Redis-Cache-Sessions.md) | Redis for rate limits + cache — **not** sessions, not authoritative | Accepted | 3, 10 |
| [009](docs/adr/ADR-009-Kafka-Async-Events.md) | Kafka for domain events, **deferred to Phase 7**, outbox required | Accepted | 7 |
| [010](docs/adr/ADR-010-Idempotency-Strategy.md) | `Idempotency-Key` on every mutation; key + change in one transaction | Accepted | 5 |
| [011](docs/adr/ADR-011-Offline-First-Sync.md) | Offline-first **per operation class**; transfers are online-only | Accepted | 6 |
| [012](docs/adr/ADR-012-Monetary-Representation.md) | `BigDecimal` → `NUMERIC(19,4)` → JSON **string** | Accepted | 1, 5 |
| [013](docs/adr/ADR-013-JWT-Auth-Model.md) | RS256 JWT access (15 min) + rotating opaque refresh (7 d) | Accepted | 3 |
| [014](docs/adr/ADR-014-RBAC-Authorization.md) | RBAC at the **service** layer + ownership checks; append-only audit | Accepted | 3, 7 |
| [015](docs/adr/ADR-015-Backend-Language-Kotlin.md) | Kotlin backend, for compiler-enforced state machines | Accepted | 1 |
| [016](docs/adr/ADR-016-Serialization-Kotlinx.md) | kotlinx.serialization on Android, Jackson on backend | Accepted | 1, 2 |
| [017](docs/adr/ADR-017-Flyway-Migrations.md) | Flyway, plain SQL, `ddl-auto=validate` | Accepted | 1 |

---

## The six that carry the most weight

If only six decisions were kept, these are they — each prevents a specific,
concrete way the system silently produces wrong answers about money.

**1 · Money never touches floating point** — [ADR-012](docs/adr/ADR-012-Monetary-Representation.md)
`0.1 + 0.2 == 0.30000000000000004`. In a ledger that drift compounds and stops
reconciling. The chain must hold at all four layers, including the JSON-string
transport that most implementations lose.

**2 · Concurrency is the database's job** — [ADR-007](docs/adr/ADR-007-PostgreSQL-Primary-DB.md)
A JVM lock protects one process. Two replicas both hold their own lock and
corrupt the same row. `SELECT … FOR UPDATE` is correct regardless of instance
count.

**3 · Idempotency is atomicity, not a lookup** — [ADR-010](docs/adr/ADR-010-Idempotency-Strategy.md)
"Check whether the key exists, then process" is a check-then-act race that
executes both transfers. The key record and the balance change commit together,
serialised by a unique constraint.

**4 · Two token types, because they have different jobs** — [ADR-013](docs/adr/ADR-013-JWT-Auth-Model.md)
Stateless JWT for cheap validation with a 15-minute blast radius; opaque,
server-stored, rotating refresh token because that is the only kind that can
actually be revoked.

**5 · Authorization asks two questions** — [ADR-014](docs/adr/ADR-014-RBAC-Authorization.md)
Role permits the operation *and* actor owns the resource. Checking only the first
is textbook IDOR. Both live at the service layer, because controller annotations
are bypassed by jobs and event consumers.

**6 · Offline-first is a per-operation decision** — [ADR-011](docs/adr/ADR-011-Offline-First-Sync.md)
Queuing a transfer tells a customer their money moved when no server authorised
it. Reads cache; balance changes do not.

---

## Decisions deliberately deferred

Recording these prevents them being mistaken for oversights.

| Question | Deferred to | Why |
|---|---|---|
| All dependency versions | Phase 1 | Must be verified against official compatibility matrices at implementation time, never assumed |
| Kafka introduction | Phase 7 | No consumer benefits from decoupling before then |
| Certificate pinning (Android) | Phase 10 | Security hardening phase; deliberately not claimed earlier |
| Deterministic lock ordering for multi-account operations | Phase 5 | Must be specified in `DATABASE-DESIGN.md` before transfers ship |
| Audit table partitioning | When volume demands it | Premature otherwise |
| Migrations at startup vs deploy job | Phase 13 | The Kubernetes answer differs from the Compose one |
| `jti` denylist for urgent token revocation | If required | Reintroduces a per-request lookup; only worth it against a real requirement |
| Schema-per-module | If a split is ever justified | Single schema is correct while the domain model is still moving |

---

## Decisions explicitly rejected

| Rejected | Instead | Why |
|---|---|---|
| Microservice per domain | Modular monolith | A transfer would need a saga; no independent scaling need demonstrated |
| Redis as session store | Stateless JWT | Would make Redis a hard dependency of every authenticated request |
| Redis as authoritative idempotency store | PostgreSQL | Loses the atomic write with the business transaction |
| `synchronized` / `ReentrantLock` for balances | `SELECT … FOR UPDATE` | Does not span replicas |
| Hibernate `ddl-auto=update` | Flyway + `validate` | An ORM must never mutate a schema holding financial data |
| JWT refresh tokens | Opaque + DB row | A self-contained refresh token cannot be revoked |
| Access token in `localStorage` | In-memory + `HttpOnly` cookie | Readable by any XSS payload |
| Queuing transfers offline | Online-only | Reports success for money that has not moved |
| Client-side authorization as a control | Server-side at service layer | The client is attacker-controlled |
| JSON numbers for amounts | JSON strings | JavaScript parses them into doubles |
| Gson on Android | kotlinx.serialization | Ignores Kotlin nullability; puts `null` in non-null fields |

---

## Superseded

None yet.
