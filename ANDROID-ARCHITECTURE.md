# ANDROID ARCHITECTURE — FinCore 360

**Phase:** Complete & Audited — Jetpack Compose BOM 2026.08.00, Hilt 2.60.1, Room 2.8.4, and 13 Clean Architecture modules.
See [PROJECT-STATUS.md](PROJECT-STATUS.md) and [AUDIT.md](AUDIT.md).

Governed by ADRs [001](docs/adr/ADR-001-Compose-over-Views.md),
[002](docs/adr/ADR-002-Clean-Architecture.md),
[003](docs/adr/ADR-003-Room-Local-DB.md),
[004](docs/adr/ADR-004-Retrofit-Networking.md),
[005](docs/adr/ADR-005-Hilt-DI.md),
[011](docs/adr/ADR-011-Offline-First-Sync.md).

---

## 1. Layers

```
        Compose UI          ← renders state, holds no logic
             │
        ViewModel           ← one StateFlow<ScreenState<T>>, maps user intent
             │
        Use Case            ← one business action each
             │
   Repository (interface)   ← domain layer owns the interface
             │
      ┌──────┴──────┐
      │             │
    Room         Retrofit
   (local)       (remote)
      │             │
      └──────┬──────┘
             │
     Sync / cache strategy  ← Remote → Repository → Room → UI
```

**Rules:**

- Use cases depend on repository **interfaces**, never implementations.
- ViewModels never call Retrofit or Room directly.
- The UI layer contains no business logic.
- Every ViewModel exposes exactly **one** `StateFlow<ScreenState<T>>`.
- The UI reads from Room. Always. A network response updates the UI only by
  being written to Room first.

---

## 2. Screen state model

Every screen models **all** states. Scattered booleans (`isLoading`, `hasError`,
`isEmpty`) are banned — they permit contradictory combinations that the type
system should make unrepresentable.

```kotlin
sealed class ScreenState<out T> {
    object Loading : ScreenState<Nothing>()
    data class Success<T>(val data: T) : ScreenState<T>()
    object Empty : ScreenState<Nothing>()
    data class Error(val type: ErrorType, val message: String) : ScreenState<Nothing>()
}

enum class ErrorType {
    NETWORK, UNAUTHORIZED, FORBIDDEN, NOT_FOUND,
    CONFLICT, SERVER, TIMEOUT, UNKNOWN
}
```

`Empty` is distinct from `Success(emptyList())` deliberately — "no accounts yet"
and "your filter matched nothing" are different screens.

Composables render state with an exhaustive `when`, so an unhandled state is a
compile error rather than a blank screen.

---

## 3. Module graph

```
:app                    entry point, DI setup, navigation host
:core:common            utilities, extensions, constants
:core:network           OkHttp, Retrofit, interceptors
:core:database          Room, DAOs, entities, migrations
:core:security          Keystore, biometric, encryption
:core:ui                design system, shared Composables
:core:testing           fakes, fixtures, test utilities

:feature:auth           login, MFA, biometric
:feature:dashboard      home, summary, quick actions
:feature:accounts       account list, account detail
:feature:transactions   transaction list, transaction detail
:feature:transfer       transfer flow, confirmation, receipt
:feature:beneficiaries  beneficiary management
:feature:cards          card management
:feature:notifications  notification centre
:feature:profile        profile, security settings, preferences
```

**Boundary rule:** feature modules depend on `:core:*` modules **only**. A
feature module never depends on another feature module. Cross-feature navigation
goes through Navigation Compose with a shared `NavGraph`.

This is what makes ADR-002's dependency rule structural rather than
aspirational — `:feature:accounts` cannot import Retrofit types because it does
not depend on `:core:network`'s implementation surface.

---

## 4. Networking

Cross-cutting concerns are interceptors in `:core:network`, never call-site code
([ADR-004](docs/adr/ADR-004-Retrofit-Networking.md)).

| Interceptor | Responsibility |
|---|---|
| `CorrelationIdInterceptor` | UUID per request → `X-Correlation-ID`, logged |
| `AuthInterceptor` | Attach current access token |
| `TokenAuthenticator` | 401 → **single-flight** refresh → retry |
| `RetryInterceptor` | Backoff + jitter, allowlisted codes only |
| `LoggingInterceptor` | Redacting; **absent from release builds** |

### HTTP outcome handling — every case explicit

| Code | Behaviour |
|---|---|
| 200 | Parse, write to Room |
| 401 | Refresh token → retry once → logout if refresh fails |
| 403 | Authorization error. **Do not retry.** |
| 404 | Not-found state |
| 409 | Conflict — meaningful message; for transfers this means *pending*, not failed |
| 422 | Parse field errors, display in form |
| 429 | Rate limited — show cooldown. **Do not retry immediately.** |
| 500 | Log, generic message |
| 502/503/504 | Infrastructure — "try again" |
| No network | Offline state from cache |
| Timeout | Timeout state, offer retry |

### Retry policy

- Retry on: network error, 502, 503, 504 — **idempotent requests only**
- Never retry: 400, 401, 403, 404, 409, 422, 500
- Exponential backoff **with jitter**, maximum 3 attempts
- Never retry a non-idempotent operation without an idempotency key

**The 401 race.** Five in-flight requests when the token expires produce five
401s. Five refresh calls would each present a refresh token that the first has
already rotated and invalidated — the server sees token reuse, treats it as
theft, and logs the user out. OkHttp's `Authenticator` gives the serialisation
point: one refresh runs, the rest queue on its result.

---

## 5. Offline and sync

Per-operation classification ([ADR-011](docs/adr/ADR-011-Offline-First-Sync.md)),
detail in [OFFLINE-SYNC.md](OFFLINE-SYNC.md).

| Class | Offline behaviour |
|---|---|
| `ONLINE_ONLY` — transfer, payment, balance changes | Blocked, explicit state. **Never queued.** |
| `OFFLINE_READ` — accounts, history, beneficiaries, cards | Served from Room **with a last-synced label** |
| `OFFLINE_QUEUEABLE` — profile, preferences | WorkManager queue; must already be idempotent |

A cached balance shown without its last-synced time is misleading, not merely
imprecise. The label is a correctness requirement.

---

## 6. Security

| Item | Rule |
|---|---|
| Access token | Android Keystore-backed encrypted store |
| Refresh token | Android Keystore-backed encrypted store |
| `SharedPreferences` | **Never** for tokens — unencrypted |
| Room | **Never** for tokens — persisted cleartext |
| DataStore | Non-sensitive preferences only |
| `FLAG_SECURE` | On every screen showing account numbers, balances, or card details |
| Exported components | `exported=false` on all Activity/Service/Receiver unless external access is intentional |
| Auto-backup | Sensitive data excluded via explicit backup rules |
| Logging interceptor | Enforced absent from release by build type, not by memory |
| Certificate pinning | Phase 10. **Not claimed before then.** |

---

## 7. Testing

Detail in [TESTING.md](TESTING.md).

| Level | Tooling | Scope |
|---|---|---|
| Unit | JUnit, MockK | ViewModels, use cases, retry logic, error mapping — fake repositories |
| Integration | Robolectric / device | Room DAOs, repository with fake remote, WorkManager |
| UI | Compose Testing | **All four `ScreenState` branches per screen**, interactions, navigation |
| E2E | Espresso / UI Automator | Login → accounts → transfer → receipt; token expiry; offline → sync |

### Mandated failure scenarios

| Scenario | Failure mode |
|---|---|
| 401 → refresh → retry → succeeds | `FM-ANDROID-001` |
| 401 → refresh fails → logged out | `FM-ANDROID-002` |
| App killed mid-transfer → status correct on restart | `FM-ANDROID-003` |
| Network lost during sync → resumes on reconnect | `FM-ANDROID-004` |
| Malformed API response → error state, **not** a crash | `FM-ANDROID-005` |

---

## 8. Legacy knowledge — document, do not build with

Android Views, XML layouts, `RecyclerView`, Fragment lifecycle. Covered in
[INTERVIEW-GUIDE.md](INTERVIEW-GUIDE.md) because choosing Compose does not
excuse not knowing what it replaced.

---

## 9. Open items

| Item | Needed by |
|---|---|
| Idempotency key generation **and persistence** on the client | Phase 5 |
| Last-sync timestamp columns in the Room schema | Phase 2 — retrofitting is a migration |
| Sync resumability state model | Phase 6 |
| Design system tokens in `:core:ui` | Phase 2 |
| Dependency versions | Phase 2 — verify against official compatibility docs |

> `NOT VERIFIED — nothing in this document has been built, compiled, or run.`
