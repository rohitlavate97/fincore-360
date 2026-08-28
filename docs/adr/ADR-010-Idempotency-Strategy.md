# ADR-010: Idempotency for every state-mutating operation

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 5

---

## Context

The concrete scenario this exists to prevent:

> A customer taps **Transfer**. The network is slow. Nothing appears to happen,
> so they tap again. Two HTTP requests reach the backend. Without idempotency,
> two transfers execute and £500 leaves the account twice.

The same shape arises from automatic retries, load balancer replays, and mobile
network handoffs. Any of them can deliver a request more than once, and the
client cannot distinguish "not delivered" from "delivered, response lost".

## Decision

Every state-mutating endpoint requires an **`Idempotency-Key`** header carrying a
client-generated UUID, scoped to one user action.

**Server algorithm:**

| Key state | Response |
|---|---|
| Not seen | Process, persist key + response atomically |
| Seen, complete | **Replay the stored response** — do not re-execute |
| Seen, in progress | `409 Conflict` with retry guidance |

**Storage:** PostgreSQL, not Redis. The key record and the transaction it
authorised are written in the **same database transaction** — that atomicity is
the entire mechanism, and it is unavailable across two systems. Redis may act as
a read-through fast path (ADR-008) but is never authoritative.

**Concurrency:** two requests with the same key arriving simultaneously are
serialised by a **unique constraint** on the key column plus row-level locking.
The loser of the insert race observes the winner's row and either replays it or
returns 409. Application-level synchronisation is explicitly not used — it does
not span replicas (ADR-007).

**Expiry:** keys expire after a configurable window, default **24 hours**.

**Scope:** a key is scoped to `(user, endpoint)`. The same key presented by a
different user is rejected — otherwise one customer could probe another's
responses.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Server-side deduplication on a request hash | A legitimate identical transfer (paying the same person £50 twice, deliberately) is indistinguishable from a duplicate. The client is the only party that knows whether this is a new *intent*. |
| Client retries with exponential backoff, no key | Backoff reduces duplicate probability; it does not eliminate it. Reducing the odds of losing money is not a design. |
| Redis-only key storage | Loses the atomic write with the business transaction. A Redis eviction or restart between the two operations permits a replayed transfer to execute twice. |
| Idempotency only on transfers | Every mutation is retryable by the same mechanisms. Partial coverage means the gap is discovered in production. |

## Consequences

### Positive

- A duplicate submission returns the *original* result, so the client shows a
  consistent outcome and the customer sees one transfer.
- The stored response makes replay indistinguishable from the first call.
- Combined with retry policy (ADR-004), retries become safe by construction.

### Negative — what this costs us

- **Every mutation costs an extra write and a lookup.** This is real latency on
  the hot path.
- An idempotency table that grows without cleanup becomes a large table on the
  critical path; expiry must actually be enforced by a scheduled purge, not just
  documented.
- Storing full responses means storing financial data in a second place, with the
  same protection obligations.
- The in-progress `409` case is genuinely awkward for clients: the answer to
  "did my transfer happen?" is temporarily "unknown, ask again". This must be
  surfaced to the user as a pending state, not as an error.
- Clients that reuse a key across *different* intents get a silently wrong
  replay. Key generation must be per-action, and that correctness burden sits on
  the client.

### Neutral / follow-on work

- Android generates the key when the user taps Transfer, and **persists it** —
  regenerating on retry after process death would defeat the mechanism entirely.

## Verification

- Test: identical request sent twice sequentially → one transaction created,
  identical response both times.
- Test: identical request sent twice **concurrently** → one transaction created,
  the second receives a replay or `409`, never a second debit.
- Test: key expiry window elapses → the key is purged and reuse is permitted.
- Test: key presented by a different user → rejected.

> `NOT VERIFIED — no idempotency implementation, table, or tests exist. The
> concurrent-duplicate case in particular has never been raced.`

## Interview notes

**The trap:** "We check whether the key already exists before processing." That
is a check-then-act race. Two concurrent requests both find no key, both proceed,
and both transfer.

**The senior answer:** The mechanism is not the lookup, it is the **atomicity**.
The idempotency record and the balance change commit in one database
transaction, and a unique constraint on the key is what serialises concurrent
duplicates — the second insert fails, and that failure is the signal to replay
rather than execute. This is also why the key cannot live in Redis: you would
lose the shared transaction, and a well-timed eviction lets the replay execute a
second transfer. The uncomfortable part worth volunteering is the in-flight case
— when a duplicate arrives while the first is still processing, the only honest
response is 409 with retry guidance, and the client has to render that as
*pending* rather than *failed*.
