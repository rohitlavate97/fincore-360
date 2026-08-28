# ADR-009: Kafka for asynchronous domain events — deferred to Phase 7

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 7

---

## Context

Several actions must happen after a transfer completes — write an audit record,
send a notification, update read models — and none of them should sit inside the
transfer's database transaction or extend its latency. That is the honest case
for asynchronous events.

The dishonest case is "banking systems use Kafka, so we use Kafka." The master
prompt's own rule applies: use Kafka only where the use case genuinely benefits
from asynchronous processing.

## Decision

Adopt **Kafka** for domain events, **deferred until Phase 7**. Phases 1–6 use
direct synchronous calls and in-process Spring events. Kafka is introduced only
once there is a real consumer that benefits from decoupling.

**Event envelope** (all topics):

```
eventId        UUID
eventType      string (enum value)
aggregateId    resource ID (account ID, transaction ID)
aggregateType  resource type
actorId        who caused it
correlationId  request correlation ID
timestamp      UTC ISO 8601
version        schema version
payload        event-specific data
```

**Topics:** `fincore.identity.*`, `fincore.accounts.*`, `fincore.transactions.*`,
`fincore.notifications.*`, `fincore.audit.*` — full list in `ARCHITECTURE.md`.

**Consumer rules:**

- Delivery is **at-least-once**, so every consumer must be idempotent. This is
  not optional; duplicates *will* arrive.
- Partition by `accountId` — ordering is guaranteed within a partition only, and
  events for one account must be ordered.
- Failed messages route to a dead-letter topic after N attempts.
- The dead-letter topic is monitored and alerted on. An unmonitored DLQ is a
  silent data-loss channel.
- One consumer group per logical consumer.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Spring in-process `ApplicationEventPublisher` only | Correct for Phases 1–6 and used there. Does not survive process restart and offers no replay, which the audit pipeline eventually needs. |
| RabbitMQ | Good at routing and per-message acknowledgement. Weaker at retained, replayable logs — and replay is the property the audit consumer wants. |
| Database outbox polled by a scheduler | Genuinely viable and simpler. Rejected as the *end state* only because event streaming is an explicit learning goal — **but the outbox pattern is still required alongside Kafka**, see below. |
| Kafka from Phase 1 | Adds a broker, consumer lag, and DLQ operations before any consumer needs them. Complexity with no payoff yet. |

## Consequences

### Positive

- Audit and notification work leaves the transfer's critical path.
- Retained topics allow a consumer to be rebuilt by replaying history.
- Correlation ID travels in the envelope, so a transfer is traceable from HTTP
  request through to the notification.

### Negative — what this costs us

- **The dual-write problem is now ours.** Committing a database transaction and
  publishing to Kafka are two operations with no shared atomicity. If the commit
  succeeds and the publish fails, the transfer happened and no audit event
  exists. This requires a **transactional outbox** — write the event to an outbox
  table inside the same transaction, relay it to Kafka separately. Skipping this
  is the most common way event-driven banking systems lose audit records.
- At-least-once delivery means duplicate handling is mandatory in every consumer.
- Consumer lag becomes an operational metric someone has to watch.
- Schema evolution across producers and consumers needs a versioning policy — the
  `version` field exists for this, but the policy still has to be written.
- A broker in local development is more to run and more to break.

### Neutral / follow-on work

- The outbox table and relay must be specified in `DATABASE-DESIGN.md` before
  Phase 7 begins.
- `FM-BACKEND-003` covers Kafka unavailability behaviour.

## Verification

- Integration test: consumer receives the same event twice; the resulting state
  is identical (idempotency proven, not assumed).
- Failure test: Kafka stopped mid-transfer — the transfer still commits and the
  outbox retains the event for later relay.
- Ordering test: multiple events for one account arrive in order.

> `NOT VERIFIED — Kafka is not integrated, no topics or consumers exist, and the
> outbox relay is unimplemented. The dual-write hazard above is identified, not
> mitigated.`

## Interview notes

**The trap:** "We publish an event after the transfer completes." Asked what
happens if the broker is down at that moment, the answer is usually silence —
because the transfer committed and the event is simply gone.

**The senior answer:** Lead with the dual-write problem. A database commit and a
Kafka publish cannot be made atomic, so publishing after commit loses events on
broker failure, and publishing before commit emits events for transactions that
roll back. The fix is a transactional outbox: the event is inserted into an
outbox table in the *same* transaction as the balance change, and a separate
relay moves it to Kafka. That converts an atomicity problem into a
delivery-retry problem, which is solvable. Then acknowledge the consequence —
at-least-once delivery means every consumer must be idempotent, and partitioning
by account ID is what preserves per-account ordering.
