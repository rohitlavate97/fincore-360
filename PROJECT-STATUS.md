# PROJECT STATUS — FinCore 360

> This file is the single source of truth for what actually exists.
> It is updated after every significant change.
> If a capability is not listed under COMPLETED with a verification method,
> it does not exist.

**Phase:** 6 — Offline and Sync
**Last updated:** 2026-08-28

---

## COMPLETED

| Item | Verified by |
|---|---|
| Repository skeleton (`android/`, `backend/`, `web/`, `infra/`, `docs/`) | Directory listing |
| `.gitignore` — secrets, build output, IDE, Terraform state excluded | File review |
| `.editorconfig` — encoding, line endings, indent per language | File review |
| Documentation set — all 20 root documents from the master prompt created | File listing |
| ADR framework — 17 ADRs recorded (`docs/adr/`) + template | File listing |
| `PRODUCTION-FAILURE-MODES.md` — 21 mandated failure mode IDs registered; 3 fully analysed | File review |
| Architecture decisions for backend language, serialization, migration tool | ADR-015, ADR-016, ADR-017 |
| Cross-document link integrity | **134 internal links checked, 0 broken** (script run 2026-08-28) |

### Phase 1 — Backend Foundation

Toolchain versions verified against official sources on 2026-08-28, not assumed:
**Spring Boot 4.1.1 · Kotlin 2.3.21 · Gradle 9.3.0 · JDK 25 (Temurin 25.0.3)**.

| Item | Verified by |
|---|---|
| Gradle build — Boot 4.1.1 + Kotlin 2.3.21 + JDK 25 toolchain | `./gradlew clean build` → **BUILD SUCCESSFUL** |
| Full test suite — 32 tests, 0 failures, 0 errors | JUnit XML reports, clean build 2026-08-28 |
| Application starts against real PostgreSQL | `ApplicationStartupTest.contextLoads` PASSED |
| `GET /actuator/health` returns 200 `UP` | `ApplicationStartupTest` PASSED |
| Liveness and readiness probes distinct and responding | `ApplicationStartupTest` PASSED |
| Correlation ID generated when absent, echoed when supplied | 2 tests PASSED |
| Error contract returned for unknown paths (not a framework page) | `ApplicationStartupTest` PASSED |
| Error responses leak no stack trace or internal class name | `ApplicationStartupTest` PASSED |
| OpenAPI spec generated and served at `/v3/api-docs` | `ApplicationStartupTest` PASSED |
| Flyway migrations apply cleanly from an empty database | `SchemaMigrationTest` PASSED |
| **Every monetary column is `NUMERIC(19,4)`** (ADR-012) | `SchemaMigrationTest` PASSED |
| **No floating-point column exists anywhere** (ADR-012) | `SchemaMigrationTest` PASSED |
| **`audit_events` rejects `UPDATE` and `DELETE`** via DB trigger (ADR-014) | 2 tests PASSED |
| `available_balance` cannot go negative (DB constraint) | `SchemaMigrationTest` PASSED |
| Hibernate `ddl-auto=validate` — entities match schema, ORM cannot alter it | Context loads, `ApplicationStartupTest` |
| `Money`: exact decimal arithmetic, scale 4, `compareTo` equality | `MoneyTest` — 9 PASSED |
| **Money serialises as a JSON string, never a number** (ADR-012) | `MoneySerializationTest` — 4 PASSED |
| ArchUnit boundary rules active (no float fields, layer/module rules) | `ArchitectureTest` — 5 PASSED |
| Structured JSON logging | Boot 4 built-in; configured via `logging.structured.format.console` |

### Phase 2 — Android Foundation

Toolchain versions verified against official sources on 2026-08-28, not assumed:
**AGP 9.3.0 · Gradle 9.5.0 · Kotlin 2.3.21 · JDK 25 / Java 17 toolchain · Jetpack Compose BOM 2026.08.00 · Hilt 2.60.1 · Room 2.8.4 · Retrofit 3.0.0 · OkHttp 5.5.0 · Navigation Compose 2.10.0 · JUnit 6.1.3 · MockK 1.14.11**.

| Item | Verified by |
|---|---|
| Multi-module Gradle build — 16 modules (:app, 6 :core, 9 :feature) | `./gradlew.bat projects` → **BUILD SUCCESSFUL** (16 modules resolved) |
| Debug APK build and packaging | `./gradlew.bat assembleDebug` → **BUILD SUCCESSFUL** (`app-debug.apk` built) |
| Full Android unit test suite | `./gradlew.bat test` → **BUILD SUCCESSFUL** (all 16 modules passing) |
| Centralized version catalog (`gradle/libs.versions.toml`) | AGP 9.3.0, Gradle 9.5.0, Compose BOM 2026.08.00, Hilt 2.60.1 |
| Jetpack Compose UI architecture & theme system (`:core:ui`) | `FinCoreTheme`, Material 3 dynamic coloring, typography |
| Generic `ScreenStateContainer` composable pattern | Verified in `AccountsScreen` and `TransferScreen` |
| Sealed `ScreenState<T>` and `ErrorType` model (`:core:common`) | `ScreenStateTest` unit tests PASSED |
| Dagger Hilt dependency injection wiring | `@HiltAndroidApp`, `@AndroidEntryPoint`, KSP code generation PASSED |
| Navigation Compose graph with bottom navigation bar | `FinCoreNavGraph`, `FinCoreBottomBar`, `ScreenTest` PASSED |
| Retrofit 3.0.0 + OkHttp 5.5.0 + CorrelationIdInterceptor (`:core:network`) | `NetworkModule` compilation & Hilt injection verified |
| Room database with KSP code generation (`:core:database`) | `FinCoreDatabase`, `SyncMetadataEntity`, `SyncMetadataDao` verified |
| TokenManager security interface & in-memory provider (`:core:security`) | `SecurityModule` compilation & Hilt injection verified |
| Testing infrastructure with `MainDispatcherRule` (`:core:testing`) | JUnit Jupiter extension compilation verified |
| 9 feature module stubs (`:feature:*`) | All compile with proper `:core` dependencies |

### Phase 3 — Authentication (Backend & Android)

| Item | Verified by |
|---|---|
| Database migration V2 (`users`, `refresh_tokens`, FKs, indexes) | `SchemaMigrationTest`, `UserRepositoryTest` PASSED |
| User & RefreshToken JPA entities with Role and UserStatus enums | `UserRepositoryTest` PASSED |
| Asymmetric RS256 JWT access token minting (15-min expiry, claims) | `JwtTokenServiceTest` PASSED (2/2 tests) |
| Rotating opaque refresh token with SHA-256 and token reuse detection | `RefreshTokenServiceTest` PASSED (2/2 tests) |
| Spring Security filter chain with OAuth2 Resource Server & Nimbus JWT | `SecurityRbacTest` PASSED (4/4 tests) |
| Strict RBAC: 401 on missing auth, 403 on role mismatch | `SecurityRbacTest` PASSED |
| Auth REST endpoints (`/register`, `/login`, `/refresh`, `/logout`) | `AuthControllerIntegrationTest` PASSED (3/3 tests) |
| 5-attempt account lockout (15-min lock) with persistent tracking | `AuthControllerIntegrationTest` PASSED |
| Immutable audit logging on all auth events with correlation ID | `AuthControllerIntegrationTest`, `AuditLogRepository` PASSED |
| Android Keystore AES-256 GCM hardware-backed encrypted storage | `TokenManagerTest` PASSED (3/3 tests) |
| OkHttp AuthInterceptor attaching Bearer token to authenticated routes | `AuthInterceptorTest` PASSED (2/2 tests) |
| OkHttp TokenAuthenticator single-flight refresh with Mutex queuing | `TokenAuthenticatorTest` PASSED (2/2 tests) |
| Retrofit AuthApi, AuthRepository, Login/Logout/CheckAuth UseCases | `LoginUseCaseTest`, `AuthRepositoryTest` PASSED (4/4 tests) |
| Material 3 LoginScreen composable & LoginViewModel with ScreenState | `LoginViewModelTest` PASSED (3/3 tests) |
| App navigation route guards & bottom navigation bar suppression | `ScreenTest` PASSED, `./gradlew.bat assembleDebug` PASSED |

### Phase 4 — Accounts (Backend & Android)

| Item | Verified by |
|---|---|
| Account JPA entity with NUMERIC(19,4) balances & @JdbcTypeCode(Types.CHAR) | `AccountRepositoryTest` PASSED |
| AccountRepository with pagination and customer-scoped lookup | `AccountRepositoryTest` PASSED (2/2 tests) |
| AccountService with unique number generation & scale-4 money strings | `AccountServiceTest` PASSED (3/3 tests) |
| AccountController: GET /api/v1/accounts (paginated), POST, GET /{id} | `AccountControllerIntegrationTest` PASSED (3/3 tests) |
| Strict ownership check returning 404 on unowned accounts (no oracle) | `AccountControllerIntegrationTest` PASSED |
| All 53 backend tests passing cleanly across all modules | `./gradlew.bat test` → **BUILD SUCCESSFUL** (53 tests green) |
| Room AccountEntity, AccountDao, and DB version 2 in :core:database | `:core:database:test` PASSED |
| Retrofit AccountApi, AccountRepositoryImpl (Room SSOT), and UseCases | `AccountRepositoryTest`, `GetAccountsUseCaseTest` PASSED (3/3 tests) |
| Material 3 AccountsScreen and AccountsViewModel with all 4 ScreenStates | `AccountsViewModelTest` PASSED (4/4 tests) |
| Navigation Compose integration in :app with real AccountsScreen | `./gradlew.bat test` and `assembleDebug` → **BUILD SUCCESSFUL** |

### Phase 5 — Transactions and Concurrency (Backend & Android)

| Item | Verified by |
|---|---|
| IdempotencyKeyRecord & IdempotencyKeyRepository with JSONB body | `IdempotencyServiceTest` PASSED (5/5 tests) |
| IdempotencyService with IN_PROGRESS lock, COMPLETE replay, 409 conflict | `IdempotencyServiceTest` PASSED |
| Transaction entity and domain state machine with transitionTo validation | `TransactionStateMachineTest` PASSED (6/6 tests) |
| AccountRepository with deterministic `ORDER BY id ASC FOR UPDATE` deadlock defense | `TransactionRepositoryTest` PASSED |
| TransferService implementing 8-step load-bearing flow & ArchUnit boundary compliance | `TransferServiceTest` PASSED (3/3 tests) |
| TransferController: POST /transfers (mandatory Idempotency-Key), GET /transactions/{id} | `TransferControllerIntegrationTest` PASSED (4/4 tests) |
| **Exit Criterion 1**: Concurrent bidirectional transfers preserve balance integrity | `ConcurrentTransferIntegrationTest` PASSED |
| **Exit Criterion 2**: Concurrent idempotency race executes once, no double debit | `ConcurrentTransferIntegrationTest` PASSED |
| Full backend test suite passing cleanly across all modules | `./gradlew.bat test` → **BUILD SUCCESSFUL** (67 tests green) |
| Room TransactionEntity, TransactionDao, and DB version 3 in :core:database | `:core:database:test` PASSED |
| Retrofit TransferApi, TransferRepositoryImpl, and ExecuteTransferUseCase | `TransferRepositoryTest`, `ExecuteTransferUseCaseTest` PASSED |
| Material 3 TransferScreen and TransferViewModel with validation & state feedback | `TransferViewModelTest` PASSED (2/2 tests) |
| Retrofit TransactionsApi, TransactionRepositoryImpl, and TransactionsViewModel | `TransactionRepositoryTest`, `TransactionsViewModelTest` PASSED |
| Navigation Compose integration in :app (Transfer route, Dashboard quick-action) | `./gradlew.bat test` & `assembleDebug` → **BUILD SUCCESSFUL** |

### Phase 6 — Offline and Sync (Android)

| Item | Verified by |
|---|---|
| Room PendingMutationEntity, PendingMutationDao, and DB version 4 | `:core:database:test` PASSED |
| SyncMetadataDao with observeSyncMetadata Flow for reactive staleness | `:core:database:test` PASSED |
| NetworkMonitor interface and ConnectivityManagerNetworkMonitor (NET_CAPABILITY_INTERNET) | `NetworkMonitorTest` PASSED |
| SyncManager interface and SyncStatus enum in :core:common.sync | `:core:common:test` PASSED |
| DefaultSyncManager with server-wins conflict resolution & 30s throttle | `DefaultSyncManagerTest` PASSED (3/3 tests) |
| WorkManager SyncWorker (Hilt entry point) & SyncWorkScheduler | `SyncWorkerTest` PASSED, `assembleDebug` PASSED |
| Material 3 AccountsScreen displaying offline mode banner and last-synced timestamp | `AccountsViewModelTest` PASSED |
| Material 3 TransferScreen & ViewModel enforcing ONLINE_ONLY transfer blocking | `TransferViewModelTest` PASSED |
| **Exit Criterion 1**: Cached data renders offline from Room SSOT with timestamp | `OfflineSyncIntegrationTest` PASSED |
| **Exit Criterion 2**: Sync restores correct state when connectivity restored | `OfflineSyncIntegrationTest` PASSED |
| Full test suite passing across all 16 Android modules and debug APK | `./gradlew.bat test` & `assembleDebug` → **BUILD SUCCESSFUL** |

---

## IN PROGRESS

Phase 6 is 100% complete and fully verified. Ready for Phase 7 (Audit and Events).

---

## BLOCKED

| Item | Blocked by |
|---|---|
| Phase 1 criterion *"Docker Compose brings up full stack"* | **No Docker, WSL, or Podman on the development machine.** `docker-compose.yml` and `backend.Dockerfile` are written but have never been executed. |
| Testcontainers-based repository/integration tests | Same. Substituted with real embedded PostgreSQL — see TESTING.md §2. |

---

## KNOWN ISSUES

| Issue | Impact | Workaround |
|---|---|---|
| `prompt.txt.txt` is an empty stray file at repo root | Cosmetic only | Delete once confirmed unneeded |
| Gradle 9.7.1 is current but the Kotlin plugin 2.3.21 supports only up to 9.3.0 | Pinned to 9.3.0 deliberately | Revisit when KGP's supported window moves |
| `backend/.gitkeep` is now redundant | Cosmetic | Remove |

---

## TECHNICAL DEBT

| Item | Why deferred | When to address |
|---|---|---|
| Architecture diagrams are ASCII in `ARCHITECTURE.md` | Rendered diagrams add tooling before there is a system to diagram | Phase 12, alongside observability dashboards |
| Test DB is embedded PostgreSQL, not Testcontainers | No Docker on the development machine | When Docker is available — one file changes (`EmbeddedPostgresSupport.kt`) |
| No CI pipeline | Phase 13 owns CI; a minimal build+test workflow is worth adding sooner | All current results are from one machine |

---

## NOT VERIFIED

| Item | What needs verification |
|---|---|
| **Docker image** | `backend.Dockerfile` has never been built. Multi-stage layout, non-root user, and healthcheck are design intent only. |
| **Docker Compose stack** | `docker-compose.yml` has never been run. Service startup ordering, the `pg_isready` healthcheck gate, and container networking are all unexercised (`FM-INFRA-001`, `FM-INFRA-002`). |
| **PostgreSQL 18.6 specifically** | Tests run against the embedded server's PostgreSQL binary. The Compose file pins `postgres:18.6-alpine`; these have not been cross-checked. |
| Performance | Nothing measured. No benchmark, no load test, no query plan reviewed. |

---

## PHASE LEDGER

| Phase | Name | State | Exit criteria |
|---|---|---|---|
| 0 | Architecture and Foundation | **Complete** — approved 2026-08-28 | Documentation complete, reviewed, approved |
| 1 | Backend Foundation | **Complete except Compose** — app starts, health 200, 32/32 tests pass; Docker criterion blocked | App starts, `/actuator/health` returns 200, tests pass, Compose stack up |
| 2 | Android Foundation | **Complete** — verified 2026-08-28 | App builds (`app-debug.apk`), navigation works, Hilt injects, 16 modules green |
| 3 | Authentication | **Complete** — verified 2026-08-28 | Login E2E, refresh, lockout, reuse detection, 401 anon, 403 role, Keystore, single-flight, M3 LoginScreen |
| 4 | Accounts | **Complete** — verified 2026-08-28 | Paginated API, Android renders all four screen states, Room SSOT, 53 backend tests green |
| 5 | Transactions and Concurrency | **Complete** — verified 2026-08-28 | Idempotency test passes; concurrent transfer preserves balance integrity |
| 6 | Offline and Sync | **Complete** — verified 2026-08-28 | Cached data offline; sync restores correct state |
| 7 | Audit and Events | Not started | Transfer audit trail complete initiation → completion |
| 8 | Notifications | Not started | Notification received, tap deep-links to correct transaction |
| 9 | Web Portal | Not started | Each role sees only permitted screens; API 403 on violation |
| 10 | Security Hardening | Not started | OWASP checklist confirmed or risk-accepted in writing |
| 11 | Comprehensive Testing | Not started | CI green across all test categories |
| 12 | Observability | Not started | "How many transfers failed in the last hour?" answerable from a dashboard |
| 13 | DevOps and CI/CD | Not started | Full pipeline green; staging deploy successful |
| 14 | Production Simulation | Not started | System behaves as documented in the failure modes catalog |

---

## CHANGE LOG

| Date | Phase | Change |
|---|---|---|
| 2026-08-28 | 0 | Phase 0 initiated. Repo skeleton, 20 root docs, 17 ADRs, failure mode registry created. |
| 2026-08-28 | 0 | Phase 0 reviewed and approved. |
| 2026-08-28 | 1 | Backend foundation built. Boot 4.1.1 / Kotlin 2.3.21 / Gradle 9.3.0 / JDK 25, versions verified against official sources. Correlation ID filter, error contract, `Money`, baseline schema with append-only audit trigger, OpenAPI, ArchUnit rules. **32 tests, 0 failures.** Docker Compose written but unrun — no Docker on this machine. |
| 2026-08-28 | 2 | Android foundation built. AGP 9.3.0 / Gradle 9.5.0 / Kotlin 2.3.21 / Compose BOM 2026.08.00 / Hilt 2.60.1 / Room 2.8.4 / Retrofit 3.0.0 / OkHttp 5.5.0. 16-module Clean Architecture graph (:app, 6 :core, 9 :feature). ScreenState<T> model, FinCoreTheme, FinCoreNavGraph, Hilt injection verified. Build & tests green, app-debug.apk verified. |
| 2026-08-28 | 3 | Authentication built end-to-end across Backend and Android. Asymmetric RS256 JWT, rotating refresh tokens with reuse detection, 5-attempt lockout, immutable audit logging, Spring Security RBAC. Android Keystore AES-256 GCM storage, single-flight refresh authenticator with Mutex, Retrofit AuthApi/Repository/UseCases, Material 3 LoginScreen and LoginViewModel with ScreenState, Navigation route guards. All 45 backend tests and all Android tests green, debug APK built cleanly. |
| 2026-08-28 | 4 | Accounts built end-to-end across Backend and Android. Account JPA entity with NUMERIC(19,4) balances and bpchar currency type code, AccountRepository with pagination and customer-scoped queries, AccountService with unique account number generation, AccountController (paginated GET, POST, GET /{id} with 404 enumeration prevention), Room AccountEntity and AccountDao in :core:database, Retrofit AccountApi and repository with Room SSOT in :feature:accounts, Material 3 AccountsScreen and AccountsViewModel rendering all 4 ScreenStates (Loading, Success, Empty, Error), FinCoreNavGraph integration. All 53 backend tests and Android tests green, debug APK built cleanly. |
| 2026-08-28 | 5 | Transactions and Concurrency built end-to-end across Backend and Android. PostgreSQL-backed idempotency service (ADR-010), Transaction domain entity with state machine validation, deterministic ascending pessimistic account row-locking (DATABASE-DESIGN.md §3), TransferService with balance verification, TransferController requiring Idempotency-Key header, concurrent transfer test proving balance integrity and zero deadlocks, concurrent idempotency race test proving single execution and zero double-debits. Room TransactionEntity, TransactionDao, and database version 3 in :core:database. Retrofit TransferApi and TransactionsApi with Room caching SSOT. Material 3 TransferScreen, TransferViewModel, and TransactionHistoryScreen. FinCoreNavGraph integration. All 67 backend tests and all Android tests green, debug APK built cleanly. |
| 2026-08-28 | 6 | Offline and Sync built end-to-end across Android architecture. Room PendingMutationEntity, PendingMutationDao, and database version 4 in :core:database. NetworkMonitor and ConnectivityManagerNetworkMonitor registering NetworkCallback with NET_CAPABILITY_INTERNET in :core:network. SyncManager interface and SyncStatus enum in :core:common. DefaultSyncManager orchestrating Accounts & Transactions sync, ADR-011 server-wins conflict resolution, and 30-second foreground throttle. WorkManager SyncWorker (Hilt entry point) and SyncWorkScheduler with CONNECTED constraints and exponential backoff, initialized in FinCoreApplication. Material 3 AccountsScreen with offline warning banner and last-synced timestamp (ADR-011 staleness requirement). Material 3 TransferScreen and TransferViewModel enforcing ONLINE_ONLY classification (blocking offline transfers without fake success, never queued in WorkManager). All 16 Android modules green and debug APK assembled. |
