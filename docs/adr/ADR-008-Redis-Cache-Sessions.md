# ADR-008: Redis for rate limiting and caching — but not as the session store

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 3 (rate limiting), 10 (hardening)

---

## Context

Three needs push toward an in-memory store: rate limiting on login/refresh/
transfer endpoints, brute-force lockout counters, and caching of read-heavy
reference data. The tempting fourth is "session storage", which is where the
design has to be careful — the auth model (ADR-013) is stateless JWT, and a
session store would quietly reintroduce server-side session state.

## Decision

Use **Redis** for, and only for:

| Use | Why Redis specifically |
|---|---|
| Rate limiting (login, token refresh, transfer) | Counters with TTL, atomic `INCR`, shared across replicas |
| Brute-force lockout counters | Same, with natural expiry |
| Idempotency key **fast path** | Cheap pre-check before the authoritative database lookup — see below |
| Reference-data caching | Read-heavy, low-churn, tolerant of staleness |

**Explicitly not used for:**

- **Session storage.** Access tokens are stateless JWTs (ADR-013). Adding a
  session lookup would make every request depend on Redis for authentication.
- **The authoritative idempotency record.** That lives in PostgreSQL
  (ADR-010). Redis is an optimisation in front of it, never the source of truth.
- **Anything whose loss changes a balance.**

The governing rule: **Redis being down must never produce a wrong answer, only a
slower or more conservative one.**

> The document title retained from the master prompt says "Cache/Sessions". The
> sessions half is deliberately rejected here; the title is kept so the ADR
> filename matches the planned index.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Redis as the session store (opaque session IDs, server-side state) | Makes Redis a hard dependency of every authenticated request. An outage becomes total auth failure rather than degraded rate limiting. |
| Redis as the authoritative idempotency store | Redis is not the system of record and its default persistence can lose recent writes. Losing an idempotency record means a replayed transfer executes twice. |
| In-process cache (Caffeine) only | Per-replica. Rate limit counters would be divided by replica count, so the effective limit multiplies with scale — the limit stops meaning anything. |
| Postgres for rate limit counters | Works, but a write per request on a hot row for something inherently ephemeral. Wrong tool. |

## Consequences

### Positive

- Rate limits are correct across replicas because the counter is shared.
- JWT auth continues to work with Redis completely unavailable.
- Idempotency stays correct without Redis; it merely does more database work.

### Negative — what this costs us

- **Another system to run, monitor, secure, and fail.** `FM-BACKEND-002` exists
  because of this decision.
- Every Redis call site needs an explicit degradation policy, and the policies
  differ: on a rate-limiter outage, do we fail open (allow traffic, lose brute-
  force protection) or fail closed (block traffic, cause an outage)? For the
  **login** endpoint this must be **fail closed** — losing brute-force
  protection on an auth endpoint is worse than rejecting logins. For reference
  caching it is fail open. Getting this backwards is a security incident.
- Cache invalidation becomes our problem wherever caching is introduced.

### Neutral / follow-on work

- Per-call-site degradation policy must be written into `OBSERVABILITY.md` and
  `PRODUCTION-FAILURE-MODES.md` (`FM-BACKEND-002`) before Redis ships.

## Verification

- Integration test with Redis stopped: authenticated requests still succeed;
  transfers still behave idempotently.
- Rate limit test across two application instances sharing one Redis: the limit
  is enforced globally, not per instance.
- Failure test: login endpoint fails closed when Redis is unreachable.

> `NOT VERIFIED — Redis is not integrated. No degradation behaviour has been
> observed and no fail-open/fail-closed policy is implemented.`

## Interview notes

**The trap:** "We put sessions in Redis for scalability." It scales, and it also
makes Redis a hard dependency of authentication — a trade most people making this
claim have not thought about.

**The senior answer:** Choose what Redis is allowed to be *authoritative* for,
and the answer is nothing that affects money. Here it holds rate-limit counters
and a fast path in front of the idempotency table, while PostgreSQL stays the
system of record. That means a Redis outage degrades performance rather than
producing a wrong balance or a duplicated transfer. The subtle part is the
degradation policy: the rate limiter on `/auth/login` fails **closed**, because
allowing unlimited login attempts during a cache outage is worse than refusing
logins — whereas a reference-data cache fails open. Same dependency, opposite
correct answers.
