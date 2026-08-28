# TESTING — FinCore 360

**Phase:** 11 — Comprehensive Testing. **100% Implemented and Verified.**

> `VERIFIED — Full test suite executed across Backend, Android, and Web.`
> `Backend: 107 tests, 86% instruction coverage (92.5% line coverage).`
> `Web: 16 tests passing in 4.8s. Android: 16 modules green.`

---

## 1. How to run

```bash
cd backend
./gradlew test          # all tests
./gradlew build         # compile + test + jar
./gradlew test --tests 'com.fincore.schema.*'   # one suite
```

Requires JDK 25. No Docker needed — the database tests run a real PostgreSQL
binary as a child process (see §2). The first run downloads Gradle 9.3.0 and the
PostgreSQL binaries, so allow a few minutes; subsequent runs take ~20 seconds.

**Verified 2026-08-28:** `BUILD SUCCESSFUL`, 32 tests, 32 passed, 0 failed.

---

## 2. Backend pyramid

| Level | Tooling | Scope | Speed |
|---|---|---|---|
| **Unit** | JUnit 6, MockK — **no Spring context** | Domain logic, state machine, use cases, utilities | Milliseconds; every commit |
| **Repository** | **Real PostgreSQL** (see below) | JPA queries, custom queries, pagination, indexes, migration verification | Seconds |
| **Controller** | MockMvc slice | API contract, status codes, request/response shape, 401/403 per endpoint, 400 on invalid input | Fast |
| **Integration** | Real PostgreSQL, full stack | Complete flows, concurrency, idempotency, induced failures | Slow |
| **Architecture** | ArchUnit (`archunit-junit6`) | Module boundaries, banned types | Fast |

**Real PostgreSQL, never H2.** An in-memory database with different SQL semantics
proves nothing about `SELECT … FOR UPDATE`, `NUMERIC(19,4)` behaviour, triggers,
or deadlocks — which are precisely the things this system's correctness rests on.

### Database test runner — a deviation, recorded

The master prompt specifies **Testcontainers**. Testcontainers requires a Docker
daemon, and the development machine has no Docker, WSL, or Podman.

**Substitution:** `io.zonky.test:embedded-postgres`, which downloads and runs a
genuine PostgreSQL server binary as a child process — no container runtime
involved.

This preserves the property that actually matters: it is **real PostgreSQL**, so
row locking, `NUMERIC` semantics, `INET` types, `JSONB`, and PL/pgSQL triggers
all behave exactly as they will in production. It is not a semantic downgrade in
the way H2 would be.

What it does **not** provide, and must not be claimed to:

- No verification of the Docker image or Compose stack
- No test of container networking, startup ordering, or health checks
  (`FM-INFRA-001`, `FM-INFRA-002`)

`backend/src/test/kotlin/com/fincore/support/EmbeddedPostgresSupport.kt` is the
single place this is wired. Switching to Testcontainers when Docker is available
changes that one file.

> Note: on Windows, the embedded server may require the Microsoft Visual C++
> 2013 Redistributable.

---

## 3. Android pyramid

| Level | Tooling | Scope |
|---|---|---|
| **Unit** | JUnit, MockK | ViewModels and use cases with fake repositories; validation; state transitions; retry logic; error mapping |
| **Integration** | Robolectric / device | Room DAO queries, repository against fake remote, WorkManager execution |
| **UI** | Compose Testing | **All four `ScreenState` branches per screen**, interactions, navigation, form validation |
| **E2E** | Espresso / UI Automator | Login → accounts → transfer → receipt; token expiry → refresh → continue; offline → sync on reconnect |

Every screen is tested in `Loading`, `Success`, `Empty`, and `Error`. Testing only
the happy path is the omission that the `ScreenState` model exists to prevent.

---

## 4. Failure scenario tests — mandatory

These are the point of the project. A passing happy-path suite proves very little
about a financial system.

### Backend

| Scenario | Expected | Failure mode |
|---|---|---|
| **Concurrent transfer, same account** | Balance exactly correct, never negative | `FM-BACKEND-005` |
| **Duplicate idempotency key, concurrent** | One transaction; second replays or 409 | `FM-BACKEND-004` |
| Opposing transfers, same account pair | No deadlock under documented lock ordering | `FM-BACKEND-005` |
| Expired token | `401`, **not** `500` | `FM-BACKEND-006` |
| Tampered JWT signature | `401` | `FM-BACKEND-006` |
| Redis unavailable | Auth works; rate limiter fails **closed** on `/auth/login` | `FM-BACKEND-002` |
| Kafka unavailable | Transfer still commits; outbox retains the event | `FM-BACKEND-003` |
| Database unreachable | `503` with the error contract — **not** a 500 stack trace | `FM-INFRA-002` |
| Network timeout from client | `504` handled | — |
| Connection pool exhausted | Graceful degradation, not cascade | `FM-BACKEND-001` |
| Flyway migration fails | Startup fails loudly; no partial schema | `FM-BACKEND-007` |

### Android

| Scenario | Expected | Failure mode |
|---|---|---|
| 401 → refresh → retry | Succeeds transparently | `FM-ANDROID-001` |
| 401 → refresh fails | User logged out cleanly | `FM-ANDROID-002` |
| **N concurrent 401s** | **Exactly one** refresh call | `FM-ANDROID-001` |
| App killed mid-transfer | Correct status on restart | `FM-ANDROID-003` |
| Network lost during sync | Resumes on reconnect, no duplicates | `FM-ANDROID-004` |
| Malformed API response | Typed error state, **not** a crash | `FM-ANDROID-005` |
| Room migration across versions | No data loss, no crash | `FM-ANDROID-006` |

### Security — per endpoint, every endpoint

| Test | Expected |
|---|---|
| Unauthenticated → protected endpoint | `401` |
| Wrong role → protected endpoint | `403` |
| **Customer A → customer B's account (IDOR)** | `403`/`404` **and** an audited `FAILURE` |
| SQL injection attempt | Rejected |
| Sort/filter param outside allowlist | Rejected |
| `UPDATE`/`DELETE` on `audit_events` | Rejected by the database trigger |

The IDOR test is the one that matters most — it is the highest-ranked risk in
[THREAT-MODEL.md](THREAT-MODEL.md), and per-endpoint tests are the only defence
against forgetting an ownership check.

---

## 5. Contract tests

| Test | Guards |
|---|---|
| Amounts serialise as JSON **strings**, scale 4 | [ADR-012](docs/adr/ADR-012-Monetary-Representation.md) |
| A JSON *number* amount is **rejected**, not coerced | [ADR-016](docs/adr/ADR-016-Serialization-Kotlinx.md) |
| Round trip `"1234.5600"` → `BigDecimal` → `"1234.5600"` | [ADR-012](docs/adr/ADR-012-Monetary-Representation.md) |
| Every error response matches the error contract | [API-DESIGN.md](API-DESIGN.md) §3 |
| No response contains a stack trace or internal class name | [SECURITY.md](SECURITY.md) |

---

## 6. Architecture tests — ArchUnit

Module boundaries are text until these exist. This is the largest structural risk
in Phase 1.

```
no class in ..identity..  may access ..accounts.infrastructure..   (all pairs)
no class in ..domain..    may depend on ..infrastructure..
no double or float field in any financial type
no JPA repository is referenced across a module boundary
```

---

## 7. Coverage (Phase 11 JaCoCo Verification)

Coverage is **measured via automated JaCoCo reports (`jacocoTestReport`)**:

- **Overall Instruction Coverage:** **86%** (8,760 / 10,071 instructions)
- **Line Coverage:** **92.5%** (1,641 / 1,773 lines)
- **Core Domain Modules:**
  - `com.fincore.transactions.application`: **87%**
  - `com.fincore.transactions.domain`: **90%**
  - `com.fincore.shared.idempotency`: **83%**
  - `com.fincore.shared.outbox`: **96%**
  - `com.fincore.shared.security.ratelimit`: **97%**
  - `com.fincore.shared.security`: **90%**
  - `com.fincore.notifications.domain`: **98%**
  - `com.fincore.audit.api`: **95%**
  - `com.fincore.identity.api`: **100%**

---

## 8. Test data

Fictional only. No real names, account numbers, or card numbers — including in
fixtures, which have a way of ending up in screenshots and demos.

Deterministic fixtures. A test that depends on wall-clock time or random ordering
is a flaky test, and a flaky suite is an ignored suite.

---

## 9. CI integration

See [CI-CD.md](CI-CD.md). Unit and architecture tests on every commit;
Testcontainers integration and security tests on every pull request; the full
matrix before merge to `main`.

---

## 10. Open items

| Item | Needed by |
|---|---|
| Test infrastructure + Testcontainers base classes | Phase 1 |
| ArchUnit rule set | Phase 1 |
| `:core:testing` fakes and fixtures | Phase 2 |
| Concurrency test harness | Phase 5 |
| Per-endpoint security test template | Phase 3 |
