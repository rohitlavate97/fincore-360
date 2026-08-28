# INTERVIEW GUIDE — FinCore 360

Interview questions and answers grounded in the **actual** FinCore 360
implementation.

**Phase:** 0.

> `NOT VERIFIED — no code exists. The entries below are grounded in decisions
> that have been made and documented, not in implementations that have been
> built and run. Each is marked with what would need to be true before it can be
> claimed as implementation experience.`
>
> **The rule this document exists to enforce:** never claim in an interview
> something the repository cannot demonstrate. An answer that references a
> decision ("I chose X over Y because Z, and it costs W") is honest at Phase 0.
> An answer that references behaviour ("under load we saw…") is not, until it
> has been observed.

---

## Format

```
TOPIC:            subject
CONTEXT:          how it appears in FinCore 360
Q:                the interview question
A:                answer referencing the actual implementation
WHAT:             what it is
WHY:              why it was needed here
HOW:              how it was implemented
ALTERNATIVES:     what else was considered, and why rejected
TRADEOFFS:        what this approach costs
FAILURE MODES:    how it fails in production
INTERVIEW TRAP:   the common wrong answer
```

---

## Coverage tracker

Topics are added as their component is built. Nothing is marked complete on the
strength of a document.

| Area | Topics | State |
|---|---|---|
| **Architecture** | Modular monolith, Clean Architecture, MVVM, repository pattern, event-driven design, CQRS concepts, microservice tradeoffs, API design, error handling | Decision-level only |
| **Data** | ACID, isolation levels, optimistic vs pessimistic locking, N+1, indexing, migrations, monetary precision | Decision-level only |
| **Security** | JWT internals, refresh rotation, OWASP Top 10, RBAC vs ABAC, Android Keystore, XSS, CSRF, rate limiting | Decision-level only |
| **Backend** | Spring Boot, Spring Security, JWT, OAuth2 concepts, RBAC, idempotency, concurrency, PostgreSQL transactions, Redis, Kafka, Flyway, OpenAPI, Testcontainers | Not started |
| **Android** | Compose, ViewModel, StateFlow, Coroutines, Flow, Room, Hilt, Retrofit, WorkManager, Keystore, offline-first, modularization, state management, navigation | Not started |
| **Android legacy** | Views, XML layouts, RecyclerView, Fragment lifecycle | Not started |
| **Operations** | Structured logging, distributed tracing, metrics, health checks, Docker, Kubernetes, CI/CD, blue-green deployment | Not started |

---

# Phase 0 entries

Five topics where the decision itself — with its rejected alternatives and its
costs — is the substance of the answer. These are defensible **now**, because
what is being claimed is a decision, not an outcome.

---

## TOPIC: Monetary representation

**CONTEXT:** Every amount in FinCore 360.
**CLAIMABLE NOW:** the decision and its reasoning.
**REQUIRES BUILDING:** any claim about observed reconciliation or arithmetic
behaviour.

**Q: How do you represent money in a financial system?**

**A:** As a four-layer chain, and the layer people lose is transport.
`BigDecimal` in the JVM, `NUMERIC(19,4)` in PostgreSQL, and — critically — a
**JSON string** on the wire. `double` and `float` are banned at every layer.

**WHY:** `0.1 + 0.2 == 0.30000000000000004` in binary floating point. In a
ledger that error compounds across transactions until balances stop reconciling
against the transaction history, and no amount of rounding at the edges repairs
it, because the storage type introduced the error.

**HOW:** Scale 4 rather than 2, so intermediate results — interest, fee
apportionment, FX — have room below the minor unit before a final rounding step.
Amount and currency always travel together; a bare amount is meaningless.

**ALTERNATIVES:** Integer minor units (pence/cents) is a legitimate choice used
by real ledgers — exact and fast. Rejected because sub-minor-unit intermediates
get awkward and per-currency exponents differ (JPY has 0 decimals, KWD has 3), so
every boundary has to remember the scale. `NUMERIC`/`BigDecimal` keeps scale in
the type.

**TRADEOFFS:** `BigDecimal` is slower and allocates. It is also easy to misuse in
ways that produce wrong answers rather than errors.

**FAILURE MODES:** Amount serialised as a JSON number → a JavaScript client
parses it into a double → precision destroyed in transit despite being exact at
rest.

**INTERVIEW TRAP:** "We use `BigDecimal`." Correct and incomplete — it addresses
one layer of four. Two follow-ups separate real experience from recall:
`new BigDecimal(0.1)` inherits the very binary error you were avoiding (use the
`String` constructor), and `BigDecimal.equals` compares **scale**, so
`1.0 != 1.00` — you compare with `compareTo`.

**Reference:** [ADR-012](docs/adr/ADR-012-Monetary-Representation.md)

---

## TOPIC: Concurrency control on balances

**CONTEXT:** Two transfers debiting one account simultaneously.
**CLAIMABLE NOW:** the decision and the failure analysis.
**REQUIRES BUILDING:** any claim to have run the concurrent test — Phase 5.

**Q: Two transfers withdraw from the same account at the same time. How do you
prevent the balance going negative?**

**A:** A row lock taken inside the transaction that performs the debit —
`SELECT … FOR UPDATE` on the account row. The database is the only thing every
application replica shares, so it is the only place the guarantee can live.

**WHY:** Without a lock this is a lost update. Both requests read balance 100,
both check `100 ≥ 80`, both write 20 — and one debit vanishes. Isolation level
does not save you: `READ COMMITTED`, PostgreSQL's default, permits exactly this.

**ALTERNATIVES:** `synchronized` or `ReentrantLock` protects one JVM; run two
replicas and both hold their own lock while corrupting the same row. Optimistic
locking with a `version` column is correct but retry-storms under high
contention, so it is used for ordinary updates and not for balances. A Redis
distributed lock adds a second system to the correctness path for a guarantee
Postgres already provides transactionally.

**TRADEOFFS:** Pessimistic locking serialises throughput on hot accounts, and it
introduces **deadlock**: two transfers in opposite directions between accounts A
and B will hang if each locks its own source first. The mitigation is locking in
deterministic order — ascending account ID — regardless of direction.

**FAILURE MODES:** `FM-BACKEND-005`.

**INTERVIEW TRAP:** "We used `synchronized` to prevent double spending." This is
the most common wrong answer in the space and it is wrong the moment there are
two replicas — which in production there always are.

**Reference:** [ADR-007](docs/adr/ADR-007-PostgreSQL-Primary-DB.md)

---

## TOPIC: Idempotency

**CONTEXT:** Every state-mutating endpoint.
**CLAIMABLE NOW:** the design.
**REQUIRES BUILDING:** the concurrent duplicate test — Phase 5.

**Q: A user taps Transfer twice on a slow network. How do you prevent two
transfers?**

**A:** The client generates a UUID per user *action* and sends it as
`Idempotency-Key`. The server stores the key record and the balance change in the
**same database transaction**, with a unique constraint on
`(key, user_id, endpoint)`.

**WHY:** The client cannot distinguish "not delivered" from "delivered, response
lost". Only the client knows whether this is a new intent, which is why
server-side request-hash deduplication fails — two deliberate identical £50
transfers are indistinguishable from a duplicate.

**HOW:** Unseen key → process and persist atomically. Seen and complete → replay
the stored response. Seen and in progress → 409 with retry guidance.

**TRADEOFFS:** An extra write and lookup on every mutation; a table that must
actually be purged or it grows unbounded on the hot path; and the in-flight 409,
which is genuinely awkward — the honest answer to "did my transfer happen?" is
temporarily "unknown", and the client must render that as **pending**, not
failed.

**FAILURE MODES:** `FM-BACKEND-004`.

**INTERVIEW TRAP:** "We check whether the key exists before processing." That is
check-then-act: two concurrent requests both find no key and both transfer. The
mechanism is the **atomicity and the unique constraint**, not the lookup. It is
also why the key cannot live in Redis — you lose the shared transaction.

**Reference:** [ADR-010](docs/adr/ADR-010-Idempotency-Strategy.md)

---

## TOPIC: Token model and the refresh race

**CONTEXT:** Authentication across Android and web.
**CLAIMABLE NOW:** the design and the race analysis.
**REQUIRES BUILDING:** the concurrent-401 test — Phase 3.

**Q: Walk me through your authentication model.**

**A:** Two token types, because they have different jobs. A 15-minute RS256 JWT
access token — stateless validation, no database on the hot path. A 7-day
**opaque** refresh token stored server-side as a hash, rotated on every use.

**WHY OPAQUE:** A JWT refresh token would be self-contained and therefore
unrevocable, which defeats the purpose of having one. Opaque plus a database row
is what makes revocation possible at all.

**WHY RS256:** Verifiers hold only the public key, so a component that can
validate a token cannot mint one. With HS256 every verifier holds the signing
secret.

**TRADEOFFS — stated plainly:** access tokens genuinely **cannot** be revoked. A
locked user keeps working for up to 15 minutes. Closing that needs a `jti`
denylist checked per request, which reintroduces the lookup the stateless model
avoided. That is an accepted gap, not a solved problem.

**FAILURE MODES:** `FM-ANDROID-001` — and this is the part worth volunteering.
Rotation means that if five in-flight requests get 401 together and the client
fires five refreshes, four present a token the first already consumed. The server
correctly reads that as theft and revokes the device family, so your own security
control logs out a legitimate user. The client must therefore single-flight the
refresh: one runs, the rest queue on its result.

**INTERVIEW TRAP:** "JWTs are stateless so we don't need a database for auth."
Then: how do you log someone out? The honest answer is that you cannot revoke a
stateless access token — you bound its lifetime and keep revocation in the
refresh token.

**Reference:** [ADR-013](docs/adr/ADR-013-JWT-Auth-Model.md),
[ADR-004](docs/adr/ADR-004-Retrofit-Networking.md)

---

## TOPIC: Authorization and IDOR

**CONTEXT:** Five roles over shared endpoints.
**CLAIMABLE NOW:** the model.
**REQUIRES BUILDING:** per-endpoint security tests — Phase 3.

**Q: How do you handle authorization?**

**A:** Two questions on every request, and most implementations only ask one.
"Does this role permit this operation?" and "is this actor entitled to **this
specific resource**?" Both are enforced server-side at the **service** layer.

**WHY THE SERVICE LAYER:** A controller annotation is bypassed by any other
caller into that service — a scheduled job, a Kafka consumer, a second
controller. The service layer is the last common chokepoint.

**WHY OWNERSHIP MATTERS MOST:** Checking only the role is textbook IDOR. Any
customer reads any account by editing an ID in the URL. This is ranked the
**highest-likelihood real vulnerability** in the project's threat model, because
every new endpoint is a fresh chance to forget the check and nothing structural
prevents it.

**ALTERNATIVES:** ABAC with a policy engine is more expressive and correct if
rules were dynamic or tenant-specific. Rejected as over-engineering for five
static roles.

**TRADEOFFS:** Ownership checks are per-resource and easy to omit; per-endpoint
security tests are an ongoing tax rather than a solved problem. Five hard-coded
roles will not stretch — a sixth means a code change.

**ON AUDIT:** Append-only enforced by a **database trigger**, not by application
code. If an attacker who controls the application can also edit the audit log,
the log proves nothing about the breach. And `actor_role` is recorded as it was
*at the time of the action*, because roles change and a record you cannot
interpret later is not an audit trail.

**INTERVIEW TRAP:** "We use `@PreAuthorize` with roles." That is a role check,
not an authorization model, and the IDOR is wide open.

**Reference:** [ADR-014](docs/adr/ADR-014-RBAC-Authorization.md),
[THREAT-MODEL.md](THREAT-MODEL.md)

---

---

# Phase 1 entries

The first entries backed by **executed tests** rather than by decisions alone.

---

## TOPIC: Making an audit log actually tamper-evident

**CONTEXT:** `audit_events` in the FinCore schema.
**CLAIMABLE NOW:** the implementation *and* its verification — the trigger exists
and two tests prove it rejects `UPDATE` and `DELETE` against a real PostgreSQL.

**Q: You say your audit log is append-only. How is that enforced?**

**A:** A PL/pgSQL `BEFORE UPDATE` and `BEFORE DELETE` trigger on `audit_events`
that raises an exception unconditionally. Not an application rule — a database
one.

**WHY THE DATABASE:** The threat model for an audit log includes an attacker who
has already compromised the application. If immutability is enforced in
application code, the same access that let them act also lets them erase the
record of it. Enforcement has to sit somewhere the application cannot reach past.

**HOW IT WAS VERIFIED:** Two tests insert a row, then attempt `UPDATE` and
`DELETE` and assert both fail with the trigger's message. They run against a real
PostgreSQL server — not H2, which would not execute PL/pgSQL at all and would
have silently "passed" by doing nothing.

**TRADEOFFS:** Append-only conflicts directly with data-erasure requirements. In
a regulated jurisdiction you would need pseudonymisation at write time or
crypto-shredding, and I have not solved that — it is recorded as an open risk.
The table also only grows, so partitioning becomes necessary at volume. And a
DBA-level actor can still drop the trigger; this raises the bar, it does not make
tampering impossible.

**FAILURE MODES:** Losing events entirely rather than altering them — the dual
write between the database commit and Kafka (`FM-BACKEND-003`). A perfectly
immutable log of events that never arrived is still a useless log.

**INTERVIEW TRAP:** "We never call update or delete on it." That is a convention,
not a control, and it says nothing about an attacker who is not using your code
paths.

**Reference:** [ADR-014](docs/adr/ADR-014-RBAC-Authorization.md),
`V1__baseline_schema.sql`, `SchemaMigrationTest`

---

## TOPIC: Correlation IDs and the MDC leak

**CONTEXT:** `CorrelationIdFilter`, verified by four passing tests.

**Q: How do you trace one user action across your system?**

**A:** A `X-Correlation-ID` UUID generated per user action, carried on the
request, put into the SLF4J MDC so every log line emitted during that request
includes it automatically, echoed on the response, and returned as `traceId` in
every error body. A customer quoting an error code gives support a direct log
lookup.

**HOW:** A servlet filter at `HIGHEST_PRECEDENCE`, so the ID exists before
anything else can log. If the header is absent the server generates one — a
request must never be untraceable.

**THE DETAIL THAT MATTERS:** The MDC is cleared in a `finally` block. Servlet
threads are pooled and reused, so failing to clear it leaks one request's
correlation ID onto an unrelated later request — which is worse than having no
ID at all, because it produces confidently wrong traces that send an
investigation after the wrong user.

**TRADEOFFS:** MDC is thread-local, so it does not automatically propagate across
thread boundaries — an `@Async` method or a reactive chain loses it and needs
explicit propagation. That is a real limitation of this approach, not something
the filter solves.

**INTERVIEW TRAP:** "We log a request ID." The follow-up is whether it crosses
service boundaries and whether the *client* generates it. If the server mints it,
you cannot correlate the client-side view of a failure with the server-side one —
and for a mobile app, the client's story is half the incident.

**Reference:** [OBSERVABILITY.md](OBSERVABILITY.md) §1, `ApplicationStartupTest`

---

## The meta-answer

The most valuable thing this project produces for an interview is not a list of
technologies. It is the ability to answer **"what does that cost you?"** for
every choice — and to name the failure mode before the interviewer does.

Every ADR in this repository has a mandatory "Negative — what this costs us"
section for that reason. An engineer who can only list benefits has read about a
technology. An engineer who leads with the deadlock risk, the unrevocable token
window, or the dual-write problem has operated one.

**And the discipline underneath it:** say `NOT VERIFIED` when something is
unverified. In an interview that sounds like "I designed this but have not yet
load-tested it" — which is a far stronger answer than a confident claim that
collapses under one follow-up question.
