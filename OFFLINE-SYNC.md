# OFFLINE AND SYNC — FinCore 360

**Phase:** Complete & Audited — Implemented via Room, WorkManager SyncWorker, and ConnectivityManagerNetworkMonitor in Phase 6.
See [PROJECT-STATUS.md](PROJECT-STATUS.md) and [AUDIT.md](AUDIT.md).

Governed by [ADR-011](docs/adr/ADR-011-Offline-First-Sync.md) and
[ADR-003](docs/adr/ADR-003-Room-Local-DB.md).

---

## 1. The principle

"Offline-first" applied uniformly to a banking app is a defect, not a feature.
Queuing a transfer tells a customer their money moved when no server has
authorised it — and if it later fails on insufficient funds, they have been
acting on a false balance for hours.

So offline capability is decided **per operation**, never app-wide.

---

## 2. Operation classification

Every operation is in exactly one class. Adding an operation without classifying
it is an incomplete implementation.

| Class | Operations | Offline behaviour |
|---|---|---|
| `ONLINE_ONLY` | Transfer, payment, reversal — anything that changes a balance | Blocked with an explicit "connection required" state. **Never queued.** |
| `OFFLINE_READ` | Account list, balances, transaction history, beneficiaries, cards | Served from Room, **labelled with last-synced time** |
| `OFFLINE_QUEUEABLE` | Profile edits, notification preferences — non-financial mutations | Queued via WorkManager. Must be idempotent **before** queuing. |

**The label is not decoration.** A balance from yesterday displayed as though
current is actively misleading in a banking app. Every cached financial figure
carries its last-synced timestamp — a correctness requirement, not UI polish.

---

## 3. Data flow

```
Remote ──► Repository ──► Room ──► UI
```

One direction, no shortcuts. The UI observes Room and **only** Room. A network
response updates the screen by being written to Room first, never by being
rendered directly.

Consequence: there is no "am I online?" branch in any Composable, and exactly one
place — the repository — where cache-versus-network conflicts are resolved.

Cost: a write-then-observe hop adds latency versus rendering the response
directly. Accepted.

---

## 4. Sync triggers

| Trigger | Notes |
|---|---|
| App foreground | **Throttled** — not on every resume, or a user switching apps hammers the API |
| Connectivity restored | Via connectivity callback |
| WorkManager periodic | Configurable interval |
| Pull to refresh | User-initiated, bypasses throttle |

---

## 5. Sync process

```
1  Read last-sync timestamp for the entity type
2  Fetch remote changes since that timestamp
3  Apply changes to Room (transactionally per batch)
4  Detect conflicts — local change AND remote change on the same record
5  Resolve per policy (§6)
6  Advance the last-sync timestamp — only after 3–5 succeed
7  Emit sync status via StateFlow
```

Step 6 ordering is load-bearing. Advancing the timestamp before the writes commit
means a crash loses those changes permanently, and the next sync will not
re-fetch them.

---

## 6. Conflict resolution

**Server wins for all financial data. Unconditionally.**

The device cannot hold an authoritative balance. There is nothing to arbitrate —
the server is the ledger; the device is a view of it.

| Data | Policy |
|---|---|
| Balances, transactions, accounts | Server wins, always |
| Beneficiaries, cards | Server wins |
| Notification preferences | Last-write-wins by timestamp |
| Profile fields | Server wins; local queued edit is re-applied after sync if still pending |

Prompting the customer to resolve a balance conflict would be asking them to
arbitrate a fact. Rejected in [ADR-011](docs/adr/ADR-011-Offline-First-Sync.md).

---

## 7. Sync must survive

| Failure | Required behaviour |
|---|---|
| Process death mid-sync | Resume on relaunch; partially-applied sync is safe to re-run |
| Network interruption | Partial progress retained; resume from last committed batch |
| Server error | Back off, retry, surface status; do not advance the timestamp |
| Database write failure | Abort the batch, do not advance the timestamp, surface the error |

This requires sync state itself to be **persisted**, and every sync step to be
idempotent — re-running a partially applied batch must produce the same result.

---

## 8. Queued mutations

For `OFFLINE_QUEUEABLE` only.

- Each queued item carries a **persisted** idempotency key. Regenerating it after
  process death defeats the entire mechanism.
- WorkManager constraints: network connected; exponential backoff.
- A queued item that fails permanently must surface somewhere the user will
  actually see it — the originating screen may be long gone.
- Queue depth is bounded. An unbounded offline queue replaying days of stale
  intent is its own hazard.

---

## 9. Room schema requirements

These must be present from Phase 2. Retrofitting them is a migration, and
migrations against installed apps are `FM-ANDROID-006`.

| Requirement | Why |
|---|---|
| `last_synced_at` per entity type | Required for the staleness label and incremental fetch |
| Sync state table | Resumability across process death |
| Pending-mutation queue table | Survives process death |
| **No token columns anywhere** | Room persists cleartext ([ADR-003](docs/adr/ADR-003-Room-Local-DB.md)) |

---

## 10. Verification

| Test | Expected |
|---|---|
| Airplane mode → account list | Renders from cache with a visible last-synced label |
| Airplane mode → attempt transfer | Connection-required state; **no** work item queued |
| Kill process mid-sync → relaunch | Sync resumes; final state matches server |
| Local + remote both changed (financial) | Remote value persists |
| Network drops mid-sync → reconnect | Resumes from last committed batch, no duplicates |
| Queued profile edit → replayed twice | Single effect (idempotency proven) |

> `NOT VERIFIED — no sync implementation, WorkManager jobs, Room schema, or tests
> exist. No offline behaviour has been observed.`

---

## 11. Open items

| Item | Needed by |
|---|---|
| Foreground sync throttle interval | Phase 6 |
| Sync batch size and transaction boundary | Phase 6 |
| Permanent-failure surfacing UX | Phase 6 |
| Queue depth bound | Phase 6 |
| Room-at-rest encryption decision (SQLCipher?) | Phase 10 — currently unencrypted, listed as open risk in [THREAT-MODEL.md](THREAT-MODEL.md) |
