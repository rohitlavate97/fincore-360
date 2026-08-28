# PRODUCTION FAILURE MODES — FinCore 360

A living catalogue of proactive failure analysis. Every component gets its
failure modes documented **before** it is declared complete.

**Phase:** 0 — 21 mandated failure modes registered.

> `NOT VERIFIED — no component exists, and no failure has been induced or
> observed. Three entries below carry full analysis derived from the design and
> from generally-known behaviour of these technologies; their Detection and
> Investigation sections reference instrumentation that is planned, not built.
> The remaining eighteen are registered stubs. None has been reproduced.`

---

## Format

```
ID:            FM-[COMPONENT]-[NUMBER]
Component:     Android | Backend | Database | Infrastructure
Failure:       what fails
Symptoms:      what the user or operator sees
Likely causes: ranked
Detection:     which log, metric, or alert reveals it
Investigation: exact steps a senior engineer takes
Mitigation:    immediate action to reduce impact
Fix:           correct long-term resolution
Prevention:    test or architectural change
Interview:     how to explain this
```

**Rule:** `Detection`, `Investigation`, and `Fix` cannot be written honestly
before the component exists and has been made to fail deliberately. A stub is
better than a guess — a guessed investigation step sends someone down a wrong
path during a real incident.

---

## Status register

| ID | Failure | Owning phase | State |
|---|---|---|---|
| `FM-ANDROID-001` | Token expired mid-session | 3 | **Analysed** |
| `FM-ANDROID-002` | Refresh token expired — full re-login required | 3 | Stub |
| `FM-ANDROID-003` | Network lost during transfer | 5 | Stub |
| `FM-ANDROID-004` | App killed during background sync | 6 | Stub |
| `FM-ANDROID-005` | Malformed API response causes crash | 2 | Stub |
| `FM-ANDROID-006` | Room migration failure on upgrade | 6 | Stub |
| `FM-ANDROID-007` | Biometric hardware unavailable | 3 | Stub |
| `FM-ANDROID-008` | FCM token invalidated, push not delivered | 8 | Stub |
| `FM-BACKEND-001` | Database connection pool exhausted | 1 | Stub |
| `FM-BACKEND-002` | Redis unavailable — cache miss behaviour | 3 | Stub |
| `FM-BACKEND-003` | Kafka unavailable — event not published | 7 | Stub |
| `FM-BACKEND-004` | Duplicate transaction request | 5 | **Analysed** |
| `FM-BACKEND-005` | Concurrent balance deduction — race condition | 5 | **Analysed** |
| `FM-BACKEND-006` | JWT with tampered claims accepted | 3 | Stub |
| `FM-BACKEND-007` | Flyway migration fails on startup | 1 | Stub |
| `FM-BACKEND-008` | Slow query causes transaction timeout | 4 | Stub |
| `FM-INFRA-001` | Container starts but application not ready | 13 | Stub |
| `FM-INFRA-002` | Database not ready when application starts | 1 | Stub |
| `FM-INFRA-003` | Secrets not injected — wrong config used | 13 | Stub |
| `FM-INFRA-004` | Health check passes but API returns errors | 13 | Stub |
| `FM-INFRA-005` | New deployment creates request failures during rollout | 13 | Stub |

---

## FM-BACKEND-005 — Concurrent balance deduction

**Component:** Backend / Database
**Failure:** Two transfers debit the same account simultaneously; the available
balance goes negative, or one debit is silently lost.

**Symptoms**

- Account balance is negative when the domain forbids it
- Balance does not equal the sum of the transaction ledger
- Two transfers each report success, but only one debit is reflected
- Intermittent, load-dependent, and typically not reproducible on a developer
  machine — which is why it reaches production

**Likely causes (ranked)**

1. Read-then-write without a lock: both requests read balance 100, both check
   `100 ≥ 80`, both write 20. One debit vanishes (lost update).
2. Application-level locking (`synchronized`, `ReentrantLock`) with more than one
   replica — each JVM holds its own lock and neither excludes the other.
3. Optimistic locking used where contention is high — retries storm and some
   fail.
4. Balance validated outside the transaction that performs the debit.
5. Transaction isolation assumed to prevent this. `READ COMMITTED` — the
   PostgreSQL default — does **not** prevent lost updates.

**Detection**

- Reconciliation job: `SUM(ledger) ≠ balance` for an account
- Constraint violation on a non-negative balance check
- `deadlock_count` or `lock_wait_time` metric elevated
  ([OBSERVABILITY.md](OBSERVABILITY.md) §3)

*Planned instrumentation — not built.*

**Investigation**

1. Pull both transactions by `correlation_id` and compare timestamps — do the
   transaction windows overlap?
2. Read the actual SQL: is the balance read inside a `SELECT … FOR UPDATE`?
3. Confirm the read and the write are in the **same** transaction.
4. Check replica count. If more than one, any application-level lock is
   irrelevant by construction.
5. Check isolation level. Do not assume `READ COMMITTED` prevents lost updates —
   it does not.
6. Reproduce with a concurrent Testcontainers test before changing anything.

**Mitigation (immediate)**

Freeze the affected account. Reconcile the balance from the ledger — this is why
the ledger is authoritative and the balance column is derived
([DATABASE-DESIGN.md](DATABASE-DESIGN.md) §2).

**Fix**

`SELECT … FOR UPDATE` on the account row, inside the transaction that performs
the debit. For multi-account operations, lock in **ascending account ID order**
to avoid the deadlock this introduces
([ADR-007](docs/adr/ADR-007-PostgreSQL-Primary-DB.md)).

**Prevention**

- Concurrent JUnit + Testcontainers test — N simultaneous transfers, assert exact
  final balance ([TESTING.md](TESTING.md) §4)
- Database check constraint on non-negative available balance
- Code review rule: no balance mutation outside a locked transaction
- Periodic reconciliation job

**Interview**

> **Trap:** "We used `synchronized` to prevent double spending." Wrong the moment
> there are two replicas — and there always are.
>
> **Senior answer:** This is a lost update, and isolation level alone will not
> save you: `READ COMMITTED` permits it. The fix is a row lock taken inside the
> transaction — `SELECT … FOR UPDATE` — because the database is the only thing
> both replicas share. Then volunteer the cost: pessimistic locking serialises
> throughput on hot accounts and introduces deadlock, so two transfers in
> opposite directions between the same pair will hang unless you lock rows in a
> deterministic order. Naming the deadlock before you are asked is the tell that
> you have actually run this.

---

## FM-BACKEND-004 — Duplicate transaction request

**Component:** Backend
**Failure:** The same user action produces two transactions. Money leaves the
account twice.

**Symptoms**

- Two identical transactions, seconds apart, same amount and destination
- Customer reports being charged twice
- Correlates with poor network conditions or a client retry

**Likely causes (ranked)**

1. No `Idempotency-Key` on the endpoint at all.
2. Key present but checked **before** the transaction rather than inside it —
   a check-then-act race: both requests find no key, both proceed.
3. Key stored in Redis rather than PostgreSQL, so the key write and the balance
   change are not atomic; an eviction between them permits a second execution.
4. Client regenerates the key on retry — including after process death — so the
   two requests carry different keys and are legitimately distinct.
5. Retry policy applied to a non-idempotent request
   ([ADR-004](docs/adr/ADR-004-Retrofit-Networking.md)).

**Detection**

- `duplicate_detected` metric, and `DUPLICATE_DETECTED` audit events
- Two transactions sharing a `correlation_id` but not an idempotency key
- Idempotency replay rate spike ([OBSERVABILITY.md](OBSERVABILITY.md) §3)

*Planned instrumentation — not built.*

**Investigation**

1. Fetch both transactions; compare `idempotency_key` and `correlation_id`.
2. Different keys → the **client** is the problem (regenerating on retry).
   Same key → the **server** is the problem (the check raced).
3. If server-side: confirm the key insert and the balance change are in one
   transaction, and that a unique constraint exists on the key.
4. Reproduce with a concurrent duplicate-key test.

**Mitigation (immediate)**

Reverse the duplicate through the `REVERSED` transition — never by editing a
balance directly. Balance edits outside the ledger are how the two permanently
diverge.

**Fix**

Enforce `Idempotency-Key` on every mutation; persist the key record and the
business change in **one** database transaction, with a unique constraint on
`(key, user_id, endpoint)` serialising concurrent duplicates
([ADR-010](docs/adr/ADR-010-Idempotency-Strategy.md)).

**Prevention**

- Sequential duplicate test and **concurrent** duplicate test
- Client persists the key so it survives process death
- Contract test asserting the header is required

**Interview**

> **Trap:** "We check whether the key already exists before processing." That is
> check-then-act. Two concurrent requests both see no key and both transfer.
>
> **Senior answer:** The mechanism is atomicity, not the lookup. The idempotency
> record and the balance change commit in the same transaction, and a unique
> constraint on the key is what serialises concurrent duplicates — the losing
> insert fails, and that failure is the signal to replay the stored response
> rather than execute. It is also why the key cannot live in Redis: you would
> lose the shared transaction. The uncomfortable case worth raising unprompted is
> the in-flight duplicate — the only honest answer is 409 with retry guidance,
> and the client must render that as *pending*, not *failed*.

---

## FM-ANDROID-001 — Token expired mid-session

**Component:** Android
**Failure:** The 15-minute access token expires while requests are in flight.
Handled badly, this logs the user out mid-session — or worse, logs out *every*
user whose client has several concurrent requests.

**Symptoms**

- User is returned to login while actively using the app
- A burst of 401s followed by a logout
- Correlates with roughly 15-minute session intervals
- Spike in the server-side token refresh rate metric

**Likely causes (ranked)**

1. **The refresh race.** Five in-flight requests get 401 simultaneously, five
   refresh calls fire. The first rotates the refresh token; the other four
   present a token that is now consumed. The server's reuse detection correctly
   interprets this as theft and revokes the device family — logging out a
   legitimate user ([ADR-013](docs/adr/ADR-013-JWT-Auth-Model.md)).
2. Refresh not attempted at all — 401 mapped straight to logout.
3. Retry-after-refresh not implemented, so the original request fails even though
   refresh succeeded.
4. Clock skew between device and server causing premature `exp` evaluation.

**Detection**

- Server: token refresh rate spike, and refresh-reuse revocation events
- Client: crash-free-session-adjacent metric — unexpected logout rate
- Audit: `LOGOUT` events with no user-initiated trigger

*Planned instrumentation — not built.*

**Investigation**

1. Correlate the 401 burst by `correlationId` — how many distinct requests, how
   many refresh calls followed?
2. More than one refresh per expiry → the single-flight guarantee is broken.
3. Confirm refresh is handled by OkHttp's `Authenticator` (serialised) rather
   than per-call-site code.
4. Check server logs for refresh-token-reuse revocations at the same instant.
5. Reproduce with a concurrent-401 test.

**Mitigation (immediate)**

Increase the access token lifetime as a stopgap only — it widens the
irrevocability window, so it is a trade, not a fix.

**Fix**

Single-flight refresh: one refresh in progress at a time, all other 401s queue on
its result. Success → all retry with the new token. Failure → all fail and log
out once ([ADR-004](docs/adr/ADR-004-Retrofit-Networking.md)).

**Prevention**

- Test: N concurrent 401s produce **exactly one** refresh call
- Test: 401 → refresh → retry → success, transparent to the UI
- Test: 401 → refresh fails → single clean logout

**Interview**

> **Trap:** "On 401 we call refresh and retry." Correct for one request, and it
> is the multi-request case that breaks.
>
> **Senior answer:** Rotation on the server and concurrency on the client are one
> design, not two. If refresh tokens rotate — and they must, because that is what
> gives you reuse detection — then a client that fires N refreshes for N
> simultaneous 401s presents a consumed token N−1 times. The server does exactly
> the right thing and revokes the device as suspected theft, and your legitimate
> user is logged out by your own security control. So the client must
> single-flight the refresh. This is the example I would give of a security
> mechanism and a client concurrency bug producing an outage together.

---

## Registered stubs

The following are registered with component and failure statement. `Symptoms`,
`Detection`, `Investigation`, `Mitigation`, `Fix`, `Prevention`, and `Interview`
are filled in as each owning component is built **and deliberately made to
fail** — not before.

### Android

| ID | Failure | Phase |
|---|---|---|
| `FM-ANDROID-002` | Refresh token expired; full re-login required. Must be a clean, single, explained logout — not a crash or a silent failure loop. | 3 |
| `FM-ANDROID-003` | Network lost during transfer. The outcome is genuinely unknown to the client; it must show pending and reconcile, never assume failure. | 5 |
| `FM-ANDROID-004` | App killed during background sync. Must resume without duplicating or losing changes. | 6 |
| `FM-ANDROID-005` | Malformed API response causes a crash instead of a typed error state. | 2 |
| `FM-ANDROID-006` | Room migration failure on upgrade — a crash loop the user cannot escape without reinstalling. | 6 |
| `FM-ANDROID-007` | Biometric hardware unavailable, disabled, or with no enrolled credential. | 3 |
| `FM-ANDROID-008` | FCM token invalidated; push notifications silently stop being delivered. | 8 |

### Backend

| ID | Failure | Phase |
|---|---|---|
| `FM-BACKEND-001` | Connection pool exhausted. Usually a symptom — long transactions or slow queries holding connections. | 1 |
| `FM-BACKEND-002` | Redis unavailable. Correct behaviour differs per call site: rate limiter on `/auth/login` fails **closed**; reference cache fails open ([ADR-008](docs/adr/ADR-008-Redis-Cache-Sessions.md)). | 3 |
| `FM-BACKEND-003` | Kafka unavailable; event not published. The transfer must still commit, with the outbox retaining the event. | 7 |
| `FM-BACKEND-006` | JWT with tampered claims accepted — a total authorization bypass. Most likely cause is an algorithm-confusion or `alg: none` misconfiguration. | 3 |
| `FM-BACKEND-007` | Flyway migration fails on startup. Must fail loudly with no partial schema, and must not leave replicas in mixed states. | 1 |
| `FM-BACKEND-008` | Slow query causes transaction timeout — compounded when it occurs inside a locked transaction. | 4 |

### Infrastructure

| ID | Failure | Phase |
|---|---|---|
| `FM-INFRA-001` | Container starts but the application is not ready. Caused by conflating liveness and readiness probes ([DEPLOYMENT.md](DEPLOYMENT.md) §4). | 13 |
| `FM-INFRA-002` | Database not ready when the application starts. Requires retry-with-backoff, not a crash loop. | 1 |
| `FM-INFRA-003` | Secrets not injected; the application silently uses a default and runs against the wrong configuration. Must fail fast instead. | 13 |
| `FM-INFRA-004` | Health check passes while the API returns errors — a shallow health check that does not reflect real dependency state. | 13 |
| `FM-INFRA-005` | New deployment causes request failures during rollout, typically from a non-backward-compatible migration ([ADR-017](docs/adr/ADR-017-Flyway-Migrations.md)). | 13 |
