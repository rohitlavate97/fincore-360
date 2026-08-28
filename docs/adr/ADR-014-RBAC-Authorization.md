# ADR-014: RBAC enforced server-side at the service layer, with append-only audit

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 3 (RBAC), 7 (audit)

---

## Context

Five roles need different access to the same data:

| Role | Intent |
|---|---|
| `CUSTOMER` | Self-service on **their own** accounts only |
| `SUPPORT_AGENT` | Read customer data to assist with issues |
| `OPERATIONS` | Transaction monitoring and operational tasks |
| `AUDITOR` | Read-only access to audit logs |
| `ADMIN` | User management and system configuration |

Role alone is insufficient. A `CUSTOMER` is authorised to view *an* account —
the question is whether it is *theirs*. Checking only the role produces an IDOR:
change the account ID in the request and read someone else's balance.

## Decision

**Authorization is enforced server-side, at the service layer, on every
request.** Two checks, both required:

1. **Role check** — does this role permit this operation? (`@PreAuthorize` at the
   controller, plus method security at the service.)
2. **Resource ownership check** — does this actor have a right to *this specific
   resource*? Performed in the service layer before data is returned.

**Controller annotations are not sufficient on their own.** A service method
reachable from a second controller, a scheduled job, or an event consumer would
bypass a controller-only check. The service layer is the last common chokepoint,
so the authoritative check lives there.

**Client-side guards are navigation only.** Android route guards and React
route protection improve UX and are **not** a security boundary. The backend
denies unauthorised requests regardless of what any client did.

**Admin operations require role *and* explicit permission** — role alone is too
coarse for destructive actions.

### Audit — append-only

Every security-sensitive and financial action produces an immutable audit record.

```
event_id       UUID PK          resource_type  what was affected
event_type     enum             resource_id    which resource
actor_id       who acted        outcome        SUCCESS | FAILURE
actor_role     role at the time reason         failure reason if any
ip_address     originating IP   correlation_id links to the request
user_agent     client           timestamp      UTC, immutable
```

**No `UPDATE`. No `DELETE`.** Enforced by a database trigger *and* application
rules — not by convention. An audit log that a compromised application can edit
is not evidence of anything.

`actor_role` records the role **at the time of the action**, because roles change
and the record must remain interpretable years later.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Controller-level `@PreAuthorize` only | Bypassed by any second entry point into the same service — another controller, a job, a Kafka consumer. |
| Role checks without ownership checks | Textbook IDOR. Any customer reads any account by changing an ID. |
| ABAC / policy engine (OPA, Cedar) | More expressive and a better fit if rules were data-driven or tenant-specific. Rejected as over-engineering for five static roles — the master prompt's own "simplest architecture that satisfies the requirement" rule applies. Revisit if rules become dynamic. |
| Client-side authorization | Not a security control. The client is attacker-controlled. |
| Mutable audit table with a "deleted" flag | Soft delete is still a write path that can hide records. Append-only means append-only. |

## Consequences

### Positive

- Ownership is checked at the one layer every caller passes through.
- Recording `actor_role` at action time keeps historical records meaningful after
  role changes.
- Database-enforced immutability means an application-level compromise cannot
  rewrite history.

### Negative — what this costs us

- **Ownership checks are per-resource and easy to forget.** Every new endpoint is
  a fresh opportunity for an IDOR, and nothing structural prevents it. Security
  tests per endpoint are the only real defence, which makes this an ongoing tax
  rather than a solved problem.
- Append-only conflicts with data deletion requests. A jurisdiction requiring
  erasure would force a redesign — pseudonymisation at write time, or
  crypto-shredding. Not addressed here, and a genuine open risk for anything
  beyond simulation.
- The audit table only grows. Monthly partitioning is planned but adds
  operational work.
- Writing an audit record inside the business transaction adds latency; writing
  it outside risks losing it (see ADR-009's outbox discussion).
- Five hard-coded roles will not stretch. Adding a sixth touches code, not
  configuration.

### Neutral / follow-on work

- The role-to-permission matrix belongs in `SECURITY.md`, maintained as the
  single reference.
- Audit partitioning strategy goes in `DATABASE-DESIGN.md`.

## Verification

- Per-endpoint security tests: unauthenticated → `401`; wrong role → `403`.
- IDOR test: customer A requests customer B's account → `403`, and the attempt is
  audited as `FAILURE`.
- Trigger test: `UPDATE` and `DELETE` against the audit table are rejected by the
  database.
- Audit completeness test: a transfer produces the full trail from initiation to
  completion, linked by `correlation_id`.

> `NOT VERIFIED — no authorization code, audit schema, triggers, or security
> tests exist. The IDOR defence is currently a documented intention.`

## Interview notes

**The trap:** "We use `@PreAuthorize` with roles." That is a role check, not an
authorization model, and it leaves the IDOR wide open.

**The senior answer:** Authorization is two questions, and most implementations
only ask one. "May this role perform this operation?" is the easy one. "Is this
actor entitled to *this specific resource*?" is where the breaches are — check
only the role and any customer reads any account by editing an ID in the URL. The
ownership check goes at the service layer rather than the controller, because a
controller annotation is bypassed by any other caller into that service: a
scheduled job, an event consumer, a second controller. On audit, the property
that matters is append-only enforced by a database trigger, not by application
code — if an attacker who owns the application can also edit the audit log, the
log proves nothing about the breach. And record the actor's role *at the time*,
because roles change and a record you cannot interpret later is not an audit
trail.
