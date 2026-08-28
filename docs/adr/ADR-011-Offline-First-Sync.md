# ADR-011: Offline-first with per-operation classification

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 6

---

## Context

A banking client on a mobile network loses connectivity routinely. Showing a
blank screen or a spinner whenever the network drops is poor, and the cached data
is already on the device (ADR-003).

But "offline-first" applied uniformly to a *financial* app is dangerous. Queuing
a transfer for later replay means the customer believes money moved when it has
not, and the balance they are shown is a local fiction. The correct design is not
"everything works offline" — it is a deliberate classification of what may.

## Decision

Classify **every** operation into exactly one of three categories:

| Class | Operations | Behaviour offline |
|---|---|---|
| `ONLINE_ONLY` | Transfer, payment, anything changing a balance | Blocked. Explicit "connection required" state. Never queued. |
| `OFFLINE_READ` | Account list, balances, transaction history, beneficiaries, cards | Served from Room, labelled with last-synced time |
| `OFFLINE_QUEUEABLE` | Non-financial mutations — profile edits, notification preferences | Queued via WorkManager. Must already be idempotent before queuing. |

**Sync triggers:** app foreground (throttled — not every resume), connectivity
restored, WorkManager periodic sync, user pull-to-refresh.

**Sync process:** fetch changes since last sync timestamp → apply to Room →
detect conflicts → resolve → emit status via `StateFlow`.

**Conflict resolution: server wins for all financial data**, unconditionally. The
device cannot hold an authoritative balance.

**Sync must survive:** process death mid-sync, network interruption, server
error, database write failure.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Full offline-first — queue transfers too | The customer is told a transfer succeeded when it has not been authorised. If it later fails on insufficient funds, they learn hours after acting on false information. Unacceptable for money. |
| Online-only, no caching | Every network blip empties the screen, and cold start always waits on the network. The data is already local; refusing to use it helps nobody. |
| Last-write-wins including financial data | The device would overwrite server balances. The server is the ledger; the device is a view of it. |
| Client-side conflict prompts for financial records | Asks the customer to arbitrate their own balance. There is nothing to arbitrate — the server is right. |

## Consequences

### Positive

- Cold start renders instantly from Room, then refreshes.
- Read screens work with no connectivity, which is most of the app's usage.
- Because reads always come from Room, there is no "is this cached or live?"
  branch in the UI.

### Negative — what this costs us

- **Stale data is shown as though current unless labelled.** A balance from
  yesterday displayed without a timestamp is actively misleading in a banking
  app. Every cached financial figure must carry its last-synced time — this is a
  correctness requirement, not a UI nicety.
- Two code paths per feature (cached read, sync reconciliation) and the bugs
  that live between them.
- Sync must be resumable, which means sync state itself has to be persisted and
  a partially-applied sync must be safe to re-run.
- Queued non-financial mutations can still fail after the user has left the
  screen; the resulting error has to surface somewhere the user will see it.
- Financial data at rest on the device is an exposure surface (see ADR-003).

### Neutral / follow-on work

- Last-sync timestamps per entity type must be part of the Room schema from the
  start — retrofitting them is a migration.
- `FM-ANDROID-004` covers process death during sync.

## Verification

- Airplane mode: account list and history render from cache with a visible
  last-synced label.
- Airplane mode: attempting a transfer produces the connection-required state and
  **no** queued work item.
- Process killed mid-sync → on relaunch, sync resumes and final state matches the
  server.
- Server-wins test: local and remote both changed → remote value persists.

> `NOT VERIFIED — no sync implementation, WorkManager jobs, or offline tests
> exist. No offline behaviour has been observed.`

## Interview notes

**The trap:** "The app is fully offline-first — actions queue and sync later."
In a banking context this is a *bug description*, not a feature.

**The senior answer:** Offline capability is a per-operation decision, not an
app-wide one. Reads are served from the local database because staleness is
tolerable when it is labelled. Balance-changing operations are online-only,
because queuing one means telling a customer their money moved when no server
has authorised it — and if it later fails, they have acted on a false balance
for hours. Non-financial mutations queue, but only after they are idempotent,
otherwise a retry duplicates them. The detail people miss is the timestamp:
cached balances must be displayed with their last-synced time, because an
unlabelled stale balance is worse than no balance.
