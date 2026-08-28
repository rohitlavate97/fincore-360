# ADR-007: PostgreSQL as the primary datastore, and database-level concurrency control

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 1 (schema), 5 (concurrency)

---

## Context

Two requirements dominate the datastore choice:

1. **Exact decimal arithmetic.** Money cannot be stored in binary floating point
   (ADR-012). The database needs a true fixed-precision decimal type.
2. **Correct behaviour under concurrent balance mutation.** Two transfers
   debiting the same account simultaneously must not drive the available balance
   negative — and the fix must survive multiple application replicas.

Point 2 rules out the tempting shortcut. A `synchronized` block or a
`ReentrantLock` in the JVM protects one process. Run two replicas behind a load
balancer and both hold their own lock happily while corrupting the same row.

## Decision

Use **PostgreSQL** as the primary datastore. Resolve all balance contention in
the **database**, never in application code.

| Situation | Mechanism |
|---|---|
| Balance debit/credit — high contention, integrity critical | **Pessimistic:** `SELECT … FOR UPDATE` on the account row |
| Ordinary entity updates — low contention | **Optimistic:** JPA `@Version` column |
| Duplicate idempotency key arriving concurrently | Unique constraint + row lock (ADR-010) |

Supporting conventions (full detail in `DATABASE-DESIGN.md`):

- UUID primary keys — not sequential integers, which leak volume and permit
  enumeration
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL`
- Monetary columns `NUMERIC(19,4)`; currency `CHAR(3)` (ISO 4217)
- Audit table append-only — no `UPDATE`, no `DELETE`, enforced by trigger

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| MySQL / MariaDB | Workable, and `DECIMAL` is exact. Postgres wins on `NUMERIC` semantics, richer constraint support, partial and expression indexes, and native partitioning for the audit table. |
| MongoDB | No multi-document ACID guarantees in the shape needed here, and `Decimal128` is a weaker fit than `NUMERIC`. Balance integrity is precisely what a document store makes hard. |
| Application-level locking over any database | Does not survive horizontal scaling. This is the mistake the project exists to demonstrate *not* making. |
| Redis-based distributed lock (Redlock) | Adds a second system to the correctness path for a guarantee Postgres already provides transactionally. Lock expiry during a long transaction is a real failure mode. |

## Consequences

### Positive

- `NUMERIC(19,4)` is exact decimal arithmetic in the database, matching
  `BigDecimal` in the application without a lossy conversion at the boundary.
- `SELECT … FOR UPDATE` serialises debits on a row correctly regardless of how
  many application instances are running.
- Constraints, triggers, and partial indexes let invariants be enforced where
  they cannot be bypassed by a code path that forgot.

### Negative — what this costs us

- **Pessimistic locks serialise throughput on hot rows and can deadlock.** Two
  transfers in opposite directions between the same pair of accounts will
  deadlock unless rows are locked in a consistent order (e.g. by account ID).
  This is a real bug we must design against, not a theoretical one.
- A lock held across a long transaction blocks other writers; transaction scope
  must stay tight, and slow queries inside a locked transaction become
  `FM-BACKEND-008`.
- The database becomes the scaling bottleneck and a single point of failure until
  replication is introduced.
- `NUMERIC` is slower than integer or float arithmetic. Correct, and slower.

### Neutral / follow-on work

- Deterministic lock ordering for multi-account operations must be specified in
  `DATABASE-DESIGN.md` before Phase 5.
- Connection pool sizing interacts directly with lock hold time
  (`FM-BACKEND-001`).

## Verification

- Concurrency test (JUnit + Testcontainers): two simultaneous transfers from one
  account; final balance is exactly correct and never negative.
- Deadlock test: opposing transfers between the same two accounts complete
  without deadlock under the documented lock ordering.
- Schema assertion: every monetary column is `NUMERIC(19,4)`.

> `NOT VERIFIED — no schema, entities, or concurrency tests exist. No race has
> been run. The deadlock hazard above is identified but unmitigated in code.`

## Interview notes

**The trap:** "We used `synchronized` / a `ReentrantLock` to prevent double
spending." This is the single most common wrong answer in this space, and it is
wrong the moment there are two replicas.

**The senior answer:** Concurrency control belongs where the data is. `SELECT …
FOR UPDATE` takes a row lock inside the transaction, so a second transfer blocks
until the first commits, and that holds no matter how many application instances
exist. Then state the cost without being asked: pessimistic locking serialises
throughput on hot accounts, and it introduces deadlock risk — two transfers in
opposite directions between the same pair of accounts will deadlock unless you
lock rows in a deterministic order. Naming the deadlock before the interviewer
does is what separates a memorised answer from an operated one.
