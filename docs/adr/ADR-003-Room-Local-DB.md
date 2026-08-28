# ADR-003: Room as the Android local database and single source of truth

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 2 (foundation), 6 (offline-first)

---

## Context

The client must serve account lists and transaction history while offline, and
the UI must not flicker between cached and fresh data when a sync completes.
That requires a local store that the UI observes continuously, plus schema
migration support — a banking app that loses cached data or crashes on upgrade is
a support incident.

## Decision

Use **Room** as the local database, and make it the **single source of truth for
all UI data**. Data flows in exactly one direction:

```
Remote → Repository → Room → UI
```

The UI never reads from Retrofit. A network response is written to Room; the UI
updates because it is observing Room, not because the call returned.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Raw SQLite + `SQLiteOpenHelper` | No compile-time query verification, no Flow observation, hand-written cursor mapping. Room's codegen is exactly this with the bugs removed. |
| DataStore / Proto DataStore for everything | Key-value, not relational. Transaction history needs paged, filtered, sorted queries over thousands of rows. Reserved for non-sensitive preferences instead. |
| SQLDelight | Legitimate contender — SQL-first with compile-time verification, and better if KMP were on the table. Rejected because it is not, and Room has first-party Paging 3 and Compose integration. |
| In-memory cache, no persistence | Fails the offline requirement outright and loses everything on process death. |

## Consequences

### Positive

- Offline reads are the default behaviour, not a special path — there is no
  "am I online?" branch in the UI.
- One `Flow` per query means sync results propagate to the UI automatically.
- Room migrations are testable against real schema JSON.

### Negative — what this costs us

- **Migrations are now a permanent obligation.** Every entity change needs a
  migration and a migration test. A missed one is `FM-ANDROID-006` — a crash
  loop on app upgrade that the user cannot escape without reinstalling.
- Cached financial data on the device is a data-exposure surface. Balances and
  account numbers land in a file on disk.
- Write-then-observe adds latency versus rendering the network response directly.

### Neutral / follow-on work

- **Tokens are never stored in Room.** Room persists cleartext. Access and
  refresh tokens go to the Android Keystore-backed store — see ADR-013.
- Cached account data must be excluded from Android auto-backup.

## Verification

- Room migration tests run every schema version pair.
- Airplane-mode test: account list and transaction history render from cache.
- Assertion that no token-bearing entity exists in the Room schema.

> `NOT VERIFIED — no database, entities, DAOs, or migrations exist. No offline
> behaviour has been observed.`

## Interview notes

**The trap:** "We cache API responses in Room." Caching is not the point, and
describing it that way invites the follow-up you will fail — what happens when
cache and network disagree.

**The senior answer:** Room is the *single source of truth*, which is a stronger
claim than caching. The UI observes Room and only Room, so there is exactly one
place where cache-versus-network conflicts get resolved (the repository) instead
of one per screen. The cost is real: a write-then-observe hop on every load, a
migration obligation forever, and financial data sitting in a file on the device
— which is why tokens explicitly do *not* live there.
