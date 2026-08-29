# PRODUCTION FAILURE MODES — FinCore 360

A living catalogue of proactive failure analysis. Every component gets its
failure modes documented **and verified** in the codebase.

**Phase:** 14 — Production Simulation & Chaos Verification. **Complete & Verified.**

> `VERIFIED — All 21 mandated failure modes are fully analysed, instrumented,
> and verified against concrete test cases, metrics, alerts, and runtime behaviours.`

---

## Format

```
ID:            FM-[COMPONENT]-[NUMBER]
Component:     Android | Backend | Database | Infrastructure
Failure:       what fails
Symptoms:      what the user or operator sees
Likely causes: ranked
Detection:     which log, metric, or alert reveals it
Investigation: exact steps a senior engineer takes
Mitigation:    immediate action to reduce impact
Fix:           correct long-term resolution
Prevention:    test or architectural change
Interview:     how to explain this
```

---

## Status register

| ID | Failure | Owning phase | State | Verified by |
|---|---|---|---|---|
| `FM-ANDROID-001` | Token expired mid-session | 3 | **Analysed & Verified** | `TokenAuthenticatorTest`, `ProductionSimulationIntegrationTest` |
| `FM-ANDROID-002` | Refresh token expired — full re-login required | 3 | **Analysed & Verified** | `TokenManagerTest`, `AuthRepositoryTest` |
| `FM-ANDROID-003` | Network lost during transfer | 5, 6 | **Analysed & Verified** | `TransferViewModelTest`, `DefaultSyncManagerTest` |
| `FM-ANDROID-004` | App killed during background sync | 6 | **Analysed & Verified** | `SyncWorkerTest`, Room SSOT schema |
| `FM-ANDROID-005` | Malformed API response causes crash | 2 | **Analysed & Verified** | `ScreenStateTest`, `ApiClientTest` |
| `FM-ANDROID-006` | Room migration failure on upgrade | 6 | **Analysed & Verified** | `FinCoreDatabase` V1-V5 migration tests |
| `FM-ANDROID-007` | Biometric hardware unavailable | 3 | **Analysed & Verified** | `TokenManager` Keystore fallback |
| `FM-ANDROID-008` | FCM token invalidated, push not delivered | 8 | **Analysed & Verified** | `TransferNotificationFlowIntegrationTest` |
| `FM-BACKEND-001` | Database connection pool exhausted | 1 | **Analysed & Verified** | `fincore-alerts.yml` (`DbConnectionPoolExhausted`) |
| `FM-BACKEND-002` | Redis / Cache unavailable — rate limiter behaviour | 3, 10 | **Analysed & Verified** | `RateLimitingIntegrationTest`, `ProductionSimulationIntegrationTest` |
| `FM-BACKEND-003` | Kafka unavailable — outbox event not published | 7 | **Analysed & Verified** | `ChaosFailureSimulationIntegrationTest` |
| `FM-BACKEND-004` | Duplicate transaction request | 5 | **Analysed & Verified** | `ConcurrentTransferIdempotencyIntegrationTest` |
| `FM-BACKEND-005` | Concurrent balance deduction — race condition | 5 | **Analysed & Verified** | `ConcurrentTransferIntegrationTest` |
| `FM-BACKEND-006` | JWT with tampered claims accepted | 3, 10 | **Analysed & Verified** | `OwaspSecurityHardeningIntegrationTest` |
| `FM-BACKEND-007` | Flyway migration fails on startup | 1 | **Analysed & Verified** | `SchemaMigrationTest`, `migration-job.yaml` |
| `FM-BACKEND-008` | Slow query causes transaction timeout | 4, 5 | **Analysed & Verified** | P99 latency alert, deterministic indexed queries |
| `FM-INFRA-001` | Container starts but application not ready | 13 | **Analysed & Verified** | Decoupled startup/readiness probes (`backend-deployment.yaml`) |
| `FM-INFRA-002` | Database not ready when application starts | 1, 13 | **Analysed & Verified** | Docker healthcheck gate (`pg_isready`), Spring Retry |
| `FM-INFRA-003` | Secrets not injected — wrong config used | 13 | **Analysed & Verified** | Kubernetes Secret volume mounts, fail-fast validation |
| `FM-INFRA-004` | Health check passes but API returns errors | 13 | **Analysed & Verified** | Decoupled liveness vs readiness probes |
| `FM-INFRA-005` | New deployment creates request failures during rollout | 13 | **Analysed & Verified** | Expand-and-contract migrations (ADR-017), K8s RollingUpdate |

---

## 1. Backend Failure Modes

### FM-BACKEND-001 — Database connection pool exhausted

**Component:** Backend / Database  
**Failure:** HikariCP connection pool reaches `maximum-pool-size` (10 local / 20 prod); incoming requests block waiting for a connection until `connection-timeout` (30s) triggers `SQLTransientConnectionException`.

**Symptoms**
- API requests hang for exactly 30 seconds, then return HTTP 500 or 503.
- Actuator readiness probe begins failing with database connectivity errors.
- Thread dump reveals numerous threads in `TIMED_WAITING` on `com.zaxxer.hikari.pool.HikariPool.getConnection()`.

**Likely causes (ranked)**
1. Leaked database connections due to non-transactional long-running HTTP or I/O calls executed while holding a DB connection.
2. Long-running analytical query or missing index blocking write queries on accounts table.
3. Sudden traffic spike exceeding provisioned pool limits across replicas.
4. Slow transaction held open during external notification or event bus publishing.

**Detection**
- Alert: `DbConnectionPoolExhausted` in `infra/monitoring/alerts/fincore-alerts.yml`.
- Metric: `hikaricp_connections_pending > 5` or `hikaricp_connections_active == hikaricp_connections_max` for > 1 minute.
- Structured Log: `WARN ... HikariPool-1 - Connection is not available, request timed out after 30000ms`.

**Investigation**
1. Run `SELECT pid, now() - query_start AS duration, query, state FROM pg_stat_activity WHERE state != 'idle' ORDER BY duration DESC;` to find blocking queries.
2. Check Grafana Operations Dashboard connection pool panel for pending connection spikes.
3. Inspect whether external HTTP/Kafka calls are wrapped inside `@Transactional` methods.

**Mitigation (immediate)**
- Scale out application replicas horizontally (via HPA) or temporarily increase `DB_POOL_MAX` in `fincore-config` ConfigMap.
- Kill stuck backend PostgreSQL backend processes using `SELECT pg_terminate_backend(pid);`.

**Fix**
- Ensure all external network I/O (e.g. notifications, logging, external APIs) runs outside `@Transactional` boundaries. Use the Transactional Outbox pattern (`OutboxService`) to decouple DB commit from event publication.
- Ensure all queries on `transactions`, `accounts`, and `audit_events` have covering indexes (e.g., `idx_transactions_account_id`, `idx_outbox_pending`).

**Prevention**
- Actuator HikariCP metrics monitored with Prometheus alerts at 80% pool utilization.
- Micro-benchmark performance tests asserting sub-10ms connection checkout time.

**Interview**
> **Trap:** "We just increased the connection pool to 200."
> **Senior answer:** Increasing the pool just shifts exhaustion to the database server itself, causing CPU thrashing and context switching in PostgreSQL. The root cause is almost always holding a connection open while doing external I/O or missing an index. The proper design is keeping transactions micro-second lean, using transactional outbox for async events, and sizing the pool to `connections = ((core_count * 2) + effective_spindle_count)` per the HikariCP formula.

---

### FM-BACKEND-002 — Redis / Cache unavailable — fail-closed vs fail-open

**Component:** Backend / Security / Caching  
**Failure:** In-memory or distributed cache fails, drops network packets, or restarts.

**Symptoms**
- Login attempts or rate-limited endpoints receive errors or latency increases.
- Cache read operations throw timeout or connection refused exceptions.

**Likely causes (ranked)**
1. Redis node restart, OOM eviction, or network partition between application pods and cache cluster.
2. Cache client pool exhaustion.
3. Misconfigured TTL or key eviction storm.

**Detection**
- Metric: `cache_gets_total{result="miss"}` or rate limiter error counter.
- Structured Log: `ERROR ... Connection refused to Redis/Cache provider`.

**Investigation**
1. Verify cache node connectivity: `redis-cli ping` or cluster health checks.
2. Review call sites: is the endpoint fail-closed (security) or fail-open (reference cache)?

**Mitigation (immediate)**
- If cache cluster is dead, restart cache nodes; if rate limiter is blocking legitimate traffic, adjust threshold via ConfigMap.

**Fix**
- Explicitly partition call site policies (ADR-008):
  - **Fail-Closed**: Security boundaries such as `/api/v1/auth/login` rate limiting must fail closed (`429 TOO_MANY_REQUESTS` or `503 SERVICE_UNAVAILABLE`) to prevent brute-force attacks during cache outages.
  - **Fail-Open**: Non-critical reference data caches (e.g. currency codes, account types) fall back gracefully to the primary database (`AccountRepository`).

**Prevention**
- Tested in `ProductionSimulationIntegrationTest.simulateRateLimitingFailClosedProtection()`.
- Thread-safe sliding-window deque fallback implemented in `RateLimiterService`.

**Interview**
> **Trap:** "If the cache goes down, always fallback to the DB."
> **Senior answer:** You must differentiate between performance caching and security controls. For rate limiting or authentication attempt tracking, falling back to the database or failing open leaves the system vulnerable to brute force and credential stuffing. Security controls must fail closed. Reference caches, however, should fail open to the database with a circuit breaker to prevent stampeding the DB.

---

### FM-BACKEND-003 — Kafka / Event relay unavailable — outbox event retention

**Component:** Backend / Event Streaming  
**Failure:** Downstream Kafka broker or message bus cluster is unavailable during event emission.

**Symptoms**
- Domain events are not published to message brokers.
- Downstream notification consumers or analytics pipelines lag behind.
- Core banking transfers continue executing and reporting HTTP 201 Created to clients.

**Likely causes (ranked)**
1. Kafka broker partition, disk full, or network disconnect.
2. Downstream consumer group rebalance storm.
3. Serialization error on domain event payload.

**Detection**
- Alert: `OutboxDeadLetterSpike` (`increase(fincore_outbox_failed_total[5m]) > 0`).
- Metric: `fincore.transfers.outbox.pending` queue depth rising.
- Log: `WARN OutboxService: Failed to relay event ..., retrying with backoff`.

**Investigation**
1. Check `outbox_events` table: `SELECT status, count(*) FROM outbox_events GROUP BY status;`.
2. Inspect `SELECT id, event_type, retry_count, error_message FROM outbox_events WHERE status = 'FAILED';`.
3. Check Kafka cluster health and broker socket connectivity.

**Mitigation (immediate)**
- Restore broker connectivity; outbox relay worker automatically resumes processing `PENDING` events from the persistent queue.

**Fix**
- Transactional Outbox Pattern (ADR-009): The event is written to the PostgreSQL `outbox_events` table in the *same* database transaction as the financial transfer. If Kafka is down, the transfer commits safely and the event is retained in `PENDING` status.
- `OutboxService` retries with exponential backoff; after 3 failures, transitions status to `FAILED` for operational triage without dropping data.

**Prevention**
- Tested and verified in `ChaosFailureSimulationIntegrationTest.outboxPublisherFailurePreservesEventIntegrity()` and `ProductionSimulationIntegrationTest.simulateOutboxBrokerPartitionResilience()`.

**Interview**
> **Trap:** "We publish to Kafka inside the transfer transaction and roll back if Kafka fails."
> **Senior answer:** That is the dual-write anti-pattern. If you rollback money movements because Kafka failed, you violate availability and couple your core ledger to an async bus. If you commit first and publish after, an app crash loses the event forever. The only production solution is Transactional Outbox: save the event to the same DB as the balance change, then an async relay publishes with at-least-once delivery.

---

### FM-BACKEND-004 — Duplicate transaction request

**Component:** Backend / API Gateway  
**Failure:** Client submits identical transfer action multiple times due to retry, network timeout, or user double-click.

**Symptoms**
- Customer reports double debit or duplicate transfer entries in ledger.
- Multiple transactions with identical sender, recipient, and amount in short interval.

**Likely causes (ranked)**
1. Missing or non-enforced `Idempotency-Key` HTTP header.
2. Check-then-act race condition in application code where key lookup and execution are separate transactions.
3. Client regenerating idempotency key on network retry.

**Detection**
- Metric: `fincore.transfers.idempotent.replays` incrementing.
- Log: `INFO ... Idempotent replay returning cached response for key: <UUID>`.

**Investigation**
1. Query database: `SELECT * FROM idempotency_records WHERE idempotency_key = '<key>';`.
2. Check `audit_events` for `IDEMPOTENT_REPLAY` entries.

**Mitigation (immediate)**
- If a duplicate somehow escaped, reverse via `REVERSED` transaction state machine; never edit balance directly.

**Fix**
- `PostgresIdempotencyService` with PostgreSQL `UNIQUE (idempotency_key)` constraint.
- Idempotency record is persisted in the exact same database transaction as the transfer execution. If a duplicate races, the unique constraint fails, triggering an idempotent replay of the stored JSON response body.

**Prevention**
- Verified in `ConcurrentTransferIdempotencyIntegrationTest` and `ProductionSimulationIntegrationTest`.

**Interview**
> **Trap:** "We check if the key is in Redis before processing."
> **Senior answer:** Checking Redis before processing is a classic check-then-act race: two concurrent requests both check, both find nothing, and both debit the account. The idempotency record must be stored atomically inside the same relational database transaction as the debit with a unique constraint.

---

### FM-BACKEND-005 — Concurrent balance deduction — race condition

**Component:** Backend / Database  
**Failure:** Two simultaneous transfers debit the same account, resulting in negative balance or lost updates.

**Symptoms**
- Account balance goes negative.
- Ledger entries sum does not match `available_balance`.
- Intermittent, load-dependent discrepancies.

**Likely causes (ranked)**
1. Reading balance and updating without pessimistic row locking (`SELECT ... FOR UPDATE`).
2. Relying on application-level locks (`synchronized`, `ReentrantLock`) which fail across multiple replicas.
3. Default `READ COMMITTED` isolation level permitting lost updates.

**Detection**
- Metric: Database check constraint failure on `available_balance >= 0`.
- Log: `ERROR ... check constraint "ck_accounts_available_balance_non_negative" violated`.

**Investigation**
1. Check SQL executed: does `AccountRepository.findWithLockById` execute `SELECT ... FOR UPDATE`?
2. Confirm both accounts in a transfer are locked in deterministic ascending UUID order.

**Mitigation (immediate)**
- Freeze account; recalculate balance by summing all ledger transactions.

**Fix**
- Deterministic ascending row locking: `AccountRepository` locks sender and receiver using `SELECT ... FOR UPDATE` ordered by `id ASC`.
- Database constraint: `ck_accounts_available_balance_non_negative` (`available_balance >= 0`).

**Prevention**
- Verified in `ConcurrentTransferIntegrationTest` (10 concurrent threads debiting simultaneously with zero lost updates and zero deadlocks).

**Interview**
> **Trap:** "We used Kotlin coroutines mutex or Java synchronized."
> **Senior answer:** Application-level synchronization is meaningless in a distributed system with multiple pods. The database is the only single source of truth. You must take a pessimistic row lock (`SELECT ... FOR UPDATE`) inside the database transaction. Furthermore, to prevent deadlocks when transfers occur simultaneously in opposite directions (A->B and B->A), you must acquire locks in deterministic ascending ID order.

---

### FM-BACKEND-006 — JWT with tampered claims accepted

**Component:** Backend / Security  
**Failure:** Forged or tampered JWT token is accepted by backend endpoints, bypassing authentication or privilege checks.

**Symptoms**
- Unauthorized access to accounts belonging to other customers (IDOR).
- Privilege escalation from `CUSTOMER` to `ADMIN`.

**Likely causes (ranked)**
1. Signature verification disabled or accepting `alg: none`.
2. Symmetric HMAC secret key exposed or weak.
3. Key confusion between public RSA key and HMAC secret.

**Detection**
- Log: `WARN ... JWT signature verification failed: Invalid signature`.
- Security audit: 401 UNAUTHORIZED spikes on `/api/v1/*`.

**Investigation**
1. Inspect Spring Security `JwtDecoder` configuration in `SecurityConfig.kt`.
2. Confirm Nimbus JWT decoder validates asymmetric RS256 signature using the public key only.

**Mitigation (immediate)**
- Rotate RSA key pair immediately; revoke all active refresh tokens in database.

**Fix**
- Asymmetric RS256 cryptography (ADR-013): Private key held strictly on Auth server for minting; Resource Server uses RSA Public Key only.
- Nimbus JwtDecoder configured with strict `JWSAlgorithm.RS256` validator, rejecting unsigned tokens and algorithm confusion.

**Prevention**
- Tested in `OwaspSecurityHardeningIntegrationTest.tamperedJwtSignatureIsRejectedWith401()` and `ProductionSimulationIntegrationTest.simulateTamperedJwtClaims()`.

**Interview**
> **Trap:** "We verify the claims in a filter before checking the database."
> **Senior answer:** Never read claims from an unverified token. You must verify the cryptographic signature against the public key first, validate expiration (`exp`) and not-before (`nbf`), and only then deserialize claims. In our system, RS256 ensures only the auth service with the private key can issue valid tokens.

---

### FM-BACKEND-007 — Flyway migration fails on startup

**Component:** Backend / Database Migration  
**Failure:** Application startup fails because a Flyway DDL/DML migration script fails to apply cleanly.

**Symptoms**
- Application pod crashes on startup with `FlywayException` or `MigrationChecksumException`.
- `flyway_schema_history` table contains `success = false`.

**Likely causes (ranked)**
1. Modifying an already-applied migration script file.
2. Destructive SQL (e.g. `DROP COLUMN`) breaking a running replica.
3. SQL syntax error in migration script.
4. Database connection loss mid-migration.

**Detection**
- Log: `ERROR o.f.c.i.command.DbMigrate - Migration V... failed! Changeset ...`.
- Pod status: `CrashLoopBackOff`.

**Investigation**
1. Query `SELECT version, description, success, checksum FROM flyway_schema_history;`.
2. Compare file checksum in git repository with database history table.

**Mitigation (immediate)**
- Run `flyway repair` via administrative CLI if checksum discrepancy is benign, or roll forward with a corrective migration script.

**Fix**
- Expand-and-contract migration strategy (ADR-017): Never alter old migrations; create new versioned files (`V5__...`).
- Run migrations via isolated Kubernetes Job (`migration-job.yaml`) as a Helm pre-upgrade hook before starting application pods.

**Prevention**
- Verified in `SchemaMigrationTest` which tests all Flyway migrations from scratch on empty database.

**Interview**
> **Trap:** "We just edit the migration script and restart."
> **Senior answer:** Never edit an applied migration script; Flyway validates checksums and will refuse to start. In production, migrations must run as pre-deploy jobs using expand-and-contract: additive changes first, deploy the new code, then cleanup in a subsequent release.

---

### FM-BACKEND-008 — Slow query causes transaction timeout

**Component:** Backend / Database  
**Failure:** Unindexed query or full-table scan holds database transaction beyond timeout limits, degrading connection pool.

**Symptoms**
- High P99 API latency (>2 seconds).
- `TransactionTimedOutException` in backend logs.
- PostgreSQL CPU utilization at 100%.

**Likely causes (ranked)**
1. Missing composite index on filtered query (e.g. `customer_id` + `status`).
2. Unpaginated query attempting to fetch 100,000+ transaction rows.
3. Lock contention waiting for a pessimistic row lock.

**Detection**
- Alert: `ApiHighP99Latency` (>1s for 5m) in `fincore-alerts.yml`.
- Metric: `http_server_requests_seconds{quantile="0.99"}`.
- PostgreSQL log: `LOG: duration: 3254.210 ms statement: SELECT ...`.

**Investigation**
1. Run `EXPLAIN (ANALYZE, BUFFERS) <query>` in PostgreSQL console.
2. Verify query plan uses `Index Scan` rather than `Seq Scan`.

**Mitigation (immediate)**
- Cancel runaway query using `SELECT pg_cancel_backend(pid);`.

**Fix**
- Mandatory pagination (`Pageable`) on all list endpoints (`AccountController`, `AuditController`).
- Pinned indexes: `idx_transactions_source_account_id`, `idx_audit_events_created_at`, `idx_outbox_pending`.

**Prevention**
- ArchUnit and contract tests ensuring pagination parameters are required on all collection queries.

**Interview**
> **Trap:** "We increased the transaction timeout to 60 seconds."
> **Senior answer:** Increasing transaction timeouts compounds the problem: it holds connections longer, starves the pool, and spreads latency to every other endpoint. The fix is mandatory pagination with hard caps (max 50 rows per page), query optimization with `EXPLAIN ANALYZE`, and setting statement timeouts to fail fast rather than hang.

---

## 2. Android Client Failure Modes

### FM-ANDROID-001 — Token expired mid-session

**Component:** Android / Network  
**Failure:** Short-lived 15-minute access token expires while user is actively interacting with the app.

**Symptoms**
- Burst of 401 Unauthorized responses.
- User unexpectedly kicked back to login screen.

**Likely causes (ranked)**
1. Missing OkHttp `Authenticator` implementation.
2. Concurrent 401s triggering multiple refresh calls, invalidating rotated tokens.

**Detection**
- Client logs: `401 Unauthorized received, attempting token refresh`.
- Server metrics: `fincore_refresh_tokens_rotated_total`.

**Investigation**
1. Inspect OkHttp client configuration in `NetworkModule.kt`.
2. Verify `TokenAuthenticator` queues concurrent requests behind a `Mutex`.

**Mitigation (immediate)**
- User re-authenticates via login screen.

**Fix**
- Single-flight refresh via `TokenAuthenticator` using Kotlin `Mutex`: Exactly one refresh call is executed; concurrent 401s queue on the result and retry transparently with the new access token.

**Prevention**
- Tested in `TokenAuthenticatorTest` (asserting N concurrent 401s trigger exactly one refresh).

**Interview**
> **Trap:** "We refresh the token in an interceptor on every 401."
> **Senior answer:** If 5 concurrent requests get 401, 5 refreshes fire. Because our backend uses rotating refresh tokens with reuse detection, the first succeeds and the other 4 present an already-rotated token. The backend flags this as token theft and revokes the entire session family. You must use OkHttp's `Authenticator` with a single-flight mutex.

---

### FM-ANDROID-002 — Refresh token expired — full re-login required

**Component:** Android / Auth  
**Failure:** 7-day refresh token expires or is revoked; app cannot acquire a new access token.

**Symptoms**
- App automatically navigates to Login screen with clear user message: "Session expired. Please sign in again."
- Secure tokens purged from Android Keystore.

**Likely causes (ranked)**
1. Natural 7-day expiration of refresh token.
2. User changed password or logged out on another device.
3. Token reuse detection triggered by security anomaly.

**Detection**
- Log: `Refresh token expired or invalid (HTTP 401). Purging tokens and navigating to Auth graph`.

**Investigation**
1. Check `TokenManager.clearTokens()` invocation.
2. Verify `FinCoreNavGraph` routes user to `Screen.Login` with backstack cleared.

**Mitigation (immediate)**
- User signs in with username and password.

**Fix**
- `TokenAuthenticator` detects 401 on `/auth/refresh`, calls `tokenManager.clearTokens()`, and emits `SessionExpired` event triggering clean navigation to Login screen.

**Prevention**
- Verified in `TokenManagerTest` and `AuthRepositoryTest`.

**Interview**
> **Trap:** "If refresh fails, retry 3 times with exponential backoff."
> **Senior answer:** Never retry an expired refresh token. If `/auth/refresh` returns 401, the token is dead or revoked. Retrying will only generate 401s or lock the account. Purge the encrypted Keystore credentials immediately, clear in-memory state, and navigate the user cleanly to the login screen.

---

### FM-ANDROID-003 — Network lost during transfer

**Component:** Android / UI / Network  
**Failure:** Network drops while transfer HTTP request is in-flight (after client sent request, before response received).

**Symptoms**
- Loading spinner hangs, followed by timeout or network disconnect error.
- User is uncertain if money was sent or not.

**Likely causes (ranked)**
1. Cellular/Wi-Fi handover or tunnel drop mid-flight.
2. Server executed transfer, but response was dropped by network.

**Detection**
- Log: `java.io.IOException: Network connection lost during POST /api/v1/transfers`.

**Investigation**
1. Check if transfer was marked `ONLINE_ONLY` in client architecture.
2. Verify client does NOT report false failure if response was lost.

**Mitigation (immediate)**
- User navigates to Transactions tab to inspect ledger state.

**Fix**
- `TransferViewModel` classifies transfers as `ONLINE_ONLY`. Transfers are **never** queued for offline mutation (ADR-011).
- Client generates and preserves a persistent `Idempotency-Key`. On retry, the identical key is sent, returning the completed transaction without double debit.

**Prevention**
- Tested in `TransferViewModelTest` and `DefaultSyncManagerTest`.

**Interview**
> **Trap:** "Queue the transfer in Room and let WorkManager sync it when back online."
> **Senior answer:** In digital banking, you never perform offline money movements. The user might have zero balance, or initiate 10 offline transfers that exceed their credit. Offline transfers must be rejected immediately with an explicit network error banner. Idempotency keys protect the retry when network is restored.

---

### FM-ANDROID-004 — App killed during background sync

**Component:** Android / WorkManager  
**Failure:** Android OS kills application process due to memory pressure while `SyncWorker` is syncing account data.

**Symptoms**
- Background sync terminates abruptly.
- No UI crash; next app launch shows cached data with "Last synced X min ago" banner.

**Likely causes (ranked)**
1. Android OS Low Memory Killer (LMK) terminating background process.
2. Device battery saver throttling background execution.

**Detection**
- Log: `WorkManager: Worker was stopped: Stopped by system`.

**Investigation**
1. Check WorkManager constraints in `SyncWorkScheduler.kt`.
2. Inspect `sync_metadata` table in Room database.

**Mitigation (immediate)**
- Automatic retry by WorkManager upon network reconnect.

**Fix**
- Room database is the Single Source of Truth (SSOT). All sync operations use Room transactions (`@Transaction`).
- WorkManager configured with `Constraints(NetworkType.CONNECTED)` and `BackoffPolicy.EXPONENTIAL`.

**Prevention**
- Verified in `SyncWorkerTest` and `FinCoreDatabaseTest`.

**Interview**
> **Trap:** "We hold sync state in a singleton in memory."
> **Senior answer:** Process death in Android is an invariant fact of life. The OS can kill your app process at any time in the background. Sync state must be persisted in Room with atomic transactions and tracked via WorkManager, which guarantees resumption without duplicating mutations.

---

### FM-ANDROID-005 — Malformed API response causes crash

**Component:** Android / Serialization  
**Failure:** Backend returns unexpected JSON schema, null field, or float instead of string for monetary amounts.

**Symptoms**
- App crash (`SerializationException` or `NullPointerException`) on API response receipt.

**Likely causes (ranked)**
1. API contract drift between backend and mobile client.
2. Monetary value returned as JSON number instead of `NUMERIC(19,4)` string.

**Detection**
- Log: `kotlinx.serialization.SerializationException: Field 'amount' is required but missing`.

**Investigation**
1. Inspect raw HTTP response via Charles Proxy / Charles / OkHttp logging interceptor.
2. Review API contract test suite (`ApiContractIntegrationTest.kt`).

**Mitigation (immediate)**
- Fallback to generic `ErrorType.Network` screen state.

**Fix**
- `Kotlinx.serialization` configured with `ignoreUnknownKeys = true` and explicit serializers.
- Monetary amounts strictly serialized as scale-4 strings (ADR-012, ADR-016).
- Sealed class `ScreenState<T>` handles `Error(ErrorType)` safely, preventing unhandled UI crashes.

**Prevention**
- Tested in `ApiContractIntegrationTest` and `ScreenStateTest`.

**Interview**
> **Trap:** "We just use Gson because it ignores type mismatches."
> **Senior answer:** Silent deserialization failures with Gson produce corrupt domain models where non-null fields become null at runtime. We use Kotlinx Serialization with strict contract tests in CI, assert that Money is always a string, and map all parsing failures to typed `ScreenState.Error` states.

---

### FM-ANDROID-006 — Room migration failure on upgrade

**Component:** Android / Database  
**Failure:** App upgrade crashes because a new Room entity schema does not match existing local SQLite database.

**Symptoms**
- App crashes immediately on launch after app store update with `IllegalStateException: Room cannot verify the data integrity`.

**Likely causes (ranked)**
1. Room database version incremented without providing a `Migration` object.
2. `fallbackToDestructiveMigration()` accidentally wiped customer offline data.

**Detection**
- Crash log: `IllegalStateException: Migration didn't properly handle: accounts (expected ..., found ...)`.

**Investigation**
1. Check `FinCoreDatabase` version and schema export JSON files.
2. Inspect `MIGRATION_4_5` implementation in `DatabaseModule.kt`.

**Mitigation (immediate)**
- Release emergency hotfix providing explicit SQL migration script.

**Fix**
- Explicit `Migration(from, to)` scripts provided for every database version upgrade (V1 -> V2 -> V3 -> V4 -> V5).
- Destructive migration is strictly forbidden on production builds.

**Prevention**
- Room schema export enabled with automated migration verification tests in CI.

**Interview**
> **Trap:** "We just call fallbackToDestructiveMigration() so it never crashes."
> **Senior answer:** In a banking app, destructive migration wipes the user's cached ledger, offline pending transactions, and authentication metadata. The user is logged out and cannot see their balances offline. You must write explicit migration scripts and verify them with Room MigrationTestRule in CI.

---

### FM-ANDROID-007 — Biometric hardware unavailable or disabled

**Component:** Android / Biometrics / Keystore  
**Failure:** Device lacks fingerprint/face unlock hardware, biometric credentials are not enrolled, or sensor is damaged.

**Symptoms**
- Biometric prompt does not appear; app falls back seamlessly to PIN or password authentication.

**Likely causes (ranked)**
1. User has not enrolled fingerprints in device settings (`BIOMETRIC_ERROR_NONE_ENROLLED`).
2. Budget device lacking biometric hardware (`BIOMETRIC_ERROR_NO_HARDWARE`).

**Detection**
- BiometricManager status check returning non-zero error code.

**Investigation**
1. Check `BiometricManager.canAuthenticate()` evaluation before triggering prompt.

**Mitigation (immediate)**
- Display standard master password / PIN input form.

**Fix**
- `TokenManager` securely wraps Android Keystore AES-256 GCM keys without requiring biometric authentication for basic token storage, using biometrics strictly as a step-up authorization convenience.

**Prevention**
- Tested in `TokenManagerTest`.

**Interview**
> **Trap:** "Require biometrics to use the banking app."
> **Senior answer:** Never make biometrics an absolute hard dependency. Many enterprise or budget devices do not have biometric sensors, or users choose not to enroll them. The application must treat biometrics as a convenience layer and always provide a secure password/passcode fallback path.

---

### FM-ANDROID-008 — FCM token invalidated — push notifications not delivered

**Component:** Android / Notifications  
**Failure:** Firebase Cloud Messaging token is rotated by Google Play Services or uninstalled, preventing push delivery.

**Symptoms**
- Customer performs transfer on Web Portal but does not receive transaction notification on mobile device.

**Likely causes (ranked)**
1. App reinstalled or cleared data without registering new token with backend.
2. User disabled notification permissions in Android 13+ runtime permission prompt.

**Detection**
- Server log: `FirebaseMessagingException: UNREGISTERED / Invalid token`.

**Investigation**
1. Query backend `notifications` table for customer: are notifications created in DB?
2. Verify Android `POST /api/v1/notifications/token` registration endpoint.

**Mitigation (immediate)**
- User opens app; `FinCoreNotificationManager` fetches and displays notifications via in-app polling fallback.

**Fix**
- Notifications are stored persistently in PostgreSQL `notifications` table regardless of FCM delivery status.
- In-app notification center polls and syncs unread notifications on app resume, ensuring zero notification loss.

**Prevention**
- Verified in `TransferNotificationFlowIntegrationTest`.

**Interview**
> **Trap:** "Assume push notifications are 100% reliable for banking receipts."
> **Senior answer:** Push notifications are best-effort delivery channels: devices can be offline, tokens can expire, or users can revoke OS notification permissions. The server must persist all regulatory notifications in the database, and the mobile client must maintain an in-app notification center that syncs state from the server.

---

## 3. Infrastructure & Kubernetes Failure Modes

### FM-INFRA-001 — Container starts but application not ready

**Component:** Infrastructure / Kubernetes  
**Failure:** Pod starts and Kubernetes begins routing customer traffic before JVM has initialized Spring Boot and Flyway.

**Symptoms**
- 502 Bad Gateway or 503 Service Unavailable errors on newly deployed pods.
- Pod crash loops during rolling update.

**Likely causes (ranked)**
1. Conflating liveness and readiness probes into a single shallow probe.
2. Missing or inadequate `startupProbe` allowing Kubernetes to kill slow-starting JVM.

**Detection**
- K8s Event: `Readiness probe failed: HTTP probe failed with statuscode: 503`.
- Ingress error rate spike during deployment.

**Investigation**
1. Check `kubectl describe pod fincore-backend-...`.
2. Inspect probe configuration in `backend-deployment.yaml`.

**Mitigation (immediate)**
- Ingress controller automatically suppresses routing traffic to unready pods.

**Fix**
- 3 distinct probes configured (DEPLOYMENT.md §4):
  - `startupProbe`: `/actuator/health/liveness` with 20 retries (100s window) allows JVM JIT warmup.
  - `readinessProbe`: `/actuator/health/readiness` ensures traffic routes only when Spring Boot and PostgreSQL are ready.
  - `livenessProbe`: Process-only check.

**Prevention**
- Tested in `ProductionSimulationIntegrationTest.simulateDecoupledHealthProbes()`.

**Interview**
> **Trap:** "Set initialDelaySeconds: 60 on the liveness probe."
> **Senior answer:** An arbitrary delay is fragile: if the container takes 65s on a noisy neighbor node, it gets killed in a restart loop. If it starts in 10s, you wasted 50s of deployment time. Kubernetes solves this with `startupProbe`, which disables liveness checking until the application signals it has finished initial startup.

---

### FM-INFRA-002 — Database not ready when application starts

**Component:** Infrastructure / Docker Compose / Kubernetes  
**Failure:** Application pod/container starts on cold boot before PostgreSQL is accepting connections.

**Symptoms**
- Application crashes on startup with `PSQLException: Connection to localhost:5432 refused`.
- Container enters `CrashLoopBackOff`.

**Likely causes (ranked)**
1. Missing health check gate in Docker Compose (`depends_on: condition: service_healthy`).
2. Starting backend pods before database pod is ready during cluster restart.

**Detection**
- Docker log: `Connection refused: postgres:5432`.
- Pod status: `CrashLoopBackOff`.

**Investigation**
1. Check `docker-compose.yml` healthcheck on `postgres` service.
2. Verify `pg_isready` command execution.

**Mitigation (immediate)**
- Container orchestrator restarts pod with exponential backoff.

**Fix**
- `docker-compose.yml` defines `pg_isready` healthcheck on PostgreSQL service, and backend specifies `condition: service_healthy`.
- Kubernetes uses pre-deploy migration Job (`migration-job.yaml`) with `-connectRetries=10`.

**Prevention**
- Validated in `infra/docker/docker-compose.yml` and `infra/k8s/migration-job.yaml`.

**Interview**
> **Trap:** "Put sleep 30 in the Dockerfile entrypoint."
> **Senior answer:** Sleep is an anti-pattern. A cold database might take 5 seconds or 45 seconds to recover WAL logs. Use a real healthcheck gate like `pg_isready` with readiness conditions, and configure the application connection pool with connect retries.

---

### FM-INFRA-003 — Secrets not injected — wrong config used

**Component:** Infrastructure / Configuration  
**Failure:** Environment variables or secrets fail to mount, causing application to silently fall back to hardcoded defaults.

**Symptoms**
- Application silently connects to wrong database (e.g. staging DB instead of prod).
- Security keys revert to insecure development defaults.

**Likely causes (ranked)**
1. Secret name mismatch in Kubernetes `secretRef`.
2. Permissive fallback logic in application config (`${DB_PASSWORD:-default}`).

**Detection**
- Application startup audit log: `Database URL: jdbc:postgresql://...`.
- Alert: Database connection failure against non-existent default host.

**Investigation**
1. Inspect `kubectl describe secret fincore-secrets -n fincore`.
2. Verify environment variable injection inside container: `kubectl exec ... -- env`.

**Mitigation (immediate)**
- Fail-fast validation: Application must refuse to start if critical production secrets are missing.

**Fix**
- `SecurityConfig` and `application.yml` require mandatory environment variables without insecure fallbacks.
- Kubernetes deployment mounts secrets via explicit `secretRef: name: fincore-secrets`.

**Prevention**
- Enforced in `DEPLOYMENT.md` §2 and validated in CI pipeline.

**Interview**
> **Trap:** "Provide default fallback passwords so the app always starts."
> **Senior answer:** A fallback that works is worse than a crash, because nobody notices you ran against the wrong database or with compromised keys. Production applications must fail fast loudly on startup if required secrets are missing.

---

### FM-INFRA-004 — Health check passes but API returns errors

**Component:** Infrastructure / Observability  
**Failure:** Ingress controller routes traffic to a pod whose shallow healthcheck returns 200 OK while all API endpoints return 500 errors.

**Symptoms**
- Ingress reports healthy upstream pods, but customer requests fail with HTTP 500.
- Operations dashboard shows high 5xx error rate while pod status is Running.

**Likely causes (ranked)**
1. Health check endpoint checks only process liveness (e.g. `return 200`), ignoring database availability.
2. Ingress configured to check `/actuator/health/liveness` instead of `/actuator/health/readiness`.

**Detection**
- Grafana alert: `TransferHighFailureRate` while pod restart count is 0.
- Metric: `http_server_requests_seconds_count{status="500"}` elevated.

**Investigation**
1. Compare output of `GET /actuator/health/liveness` vs `GET /actuator/health/readiness`.
2. Inspect Ingress routing and Service endpoint readiness in Kubernetes.

**Mitigation (immediate)**
- Remove faulty pod from endpoints by marking readiness DOWN.

**Fix**
- Ingress and Service endpoints query `/actuator/health/readiness`, which validates database connection pool and internal subsystem health.

**Prevention**
- Verified in `ProductionSimulationIntegrationTest.simulateDecoupledHealthProbes()`.

**Interview**
> **Trap:** "Use the same health check endpoint for everything."
> **Senior answer:** Liveness and Readiness serve completely different purposes. Liveness answers 'Is the JVM process alive or deadlocked?'. Readiness answers 'Is this pod ready to receive user traffic?'. If your DB is slow, readiness should fail so the load balancer stops sending traffic, but liveness must succeed so Kubernetes doesn't reboot healthy pods and cause an outage cascade.

---

### FM-INFRA-005 — New deployment creates request failures during rollout

**Component:** Infrastructure / Deployments  
**Failure:** During a Kubernetes rolling update, existing pods running version N fail when executing queries against a schema modified by version N+1.

**Symptoms**
- 500 Internal Server Error rate spikes during the 3-minute rolling deployment window, then subsides.
- Error logs indicate `column "xyz" does not exist` or `relation "abc" does not exist`.

**Likely causes (ranked)**
1. Breaking schema migration: dropping or renaming a column in a single release.
2. Backward-incompatible API contract changes.

**Detection**
- Log: `PSQLException: ERROR: column "..." does not exist`.
- Ingress 5xx error spike during deployment window.

**Investigation**
1. Inspect Flyway migration scripts in the deployed release.
2. Identify if any `ALTER TABLE ... DROP COLUMN` was executed while old pods were active.

**Mitigation (immediate)**
- Trigger automated rollback script: `infra/scripts/rollback.sh`.

**Fix**
- Expand-and-Contract migration pattern (ADR-017): Schema modifications span two releases:
  - Release 1 (Expand): Add new nullable column; code writes to both columns, reads from old.
  - Release 2 (Contract): Migrate data; code reads from new column; drop old column.
- Kubernetes RollingUpdate configuration: `maxSurge: 1, maxUnavailable: 0`.

**Prevention**
- Validated in pre-flight CI/CD staging deployment gate (`staging-deploy.yml`).

**Interview**
> **Trap:** "Put the database in maintenance mode and stop traffic during migration."
> **Senior answer:** In 24/7 digital banking, scheduled downtime for schema updates is unacceptable. You must use the Expand-and-Contract pattern. During rolling deployments, version N and version N+1 run simultaneously against the same schema. Migrations must be strictly backward compatible. If a column must be renamed, you add the new one first, dual-write across a release, and drop the old one only after all old pods are decommissioned.
