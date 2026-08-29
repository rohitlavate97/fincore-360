# FinCore 360 — Codebase Audit

**Audited:** 2026-08-29
**Scope:** `backend/` (Spring Boot 4.1.1 / Kotlin), `android/` (Compose / Hilt), `web/` (React 19 / Vite), `infra/` (Docker, K8s, Helm, Terraform), `.github/workflows/`, root documentation set
**Method:** full source read of all 218 Kotlin, 18 TSX/TS, 25 YAML, 4 SQL and 4 HCL files; backend compile verified (`./gradlew compileKotlin compileTestKotlin` → exit 0)

---

## 1. Verdict

The **architecture is genuinely strong** and the **design documentation is exceptional** — the concurrency model (deterministic `SELECT … FOR UPDATE` ordering), the idempotency design (unique constraint as the concurrency primitive), `NUMERIC(19,4)` money, the DB-enforced append-only audit trigger, and the Terraform hardening are all better than what most production systems ship with.

The gap is **between what the documents claim and what the code does**. Several load-bearing mechanisms are declared, tested in isolation, and then never wired into a running system. Two of them mean the platform, as configured today, cannot run correctly on more than one instance; one means an entire compliance guarantee is silently void.

| Area | Grade | One-line assessment |
|---|---|---|
| Domain & concurrency design | **A** | Locking order, idempotency, money precision are correct and enforced at the DB |
| Database schema & migrations | **A−** | Excellent constraints and triggers; missing double-entry ledger and purge jobs |
| Documentation | **A / D** | Best-in-class depth; but README, ADR-008/009 and PROJECT-STATUS contradict the code |
| Backend security | **C** | Ephemeral JWT keys, fail-open authorization default, spoofable rate limiting |
| Backend runtime completeness | **D** | Outbox relay and retention jobs are never scheduled — dead code paths in prod |
| Observability | **B−** | Good metrics/logging; alerts and dashboards exist; web "observability" page is fabricated |
| Web portal | **D** | Cannot log in against the real backend; ships a client-side role-spoofing helper |
| Android | **C−** | Clean architecture, but full request/response body logging in release builds |
| Infrastructure | **B** | Terraform is well hardened; K8s/Helm use `:latest` and expose actuator publicly |
| CI/CD | **D** | Deploy stages are `echo` statements; every security gate is set to never fail |
| Test suite | **B−** | 139 backend tests, real PostgreSQL, good breadth; blind to the highest-severity bugs |

**Headline count:** 6 Critical · 11 High · 14 Medium · 9 Low

---

## 2. Severity legend

| Level | Meaning |
|---|---|
| **CRITICAL** | Breaks correctness, security, or multi-instance operation in production |
| **HIGH** | Real defect or material risk; would fail a serious code review |
| **MEDIUM** | Correctness/maintainability problem; fix before scaling the codebase |
| **LOW** | Cleanup, consistency, polish |

---

# 3. CRITICAL findings

---

### C-1 — JWT signing keys are regenerated in memory on every startup

**Where:** `backend/src/main/kotlin/com/fincore/shared/security/JwtConfig.kt:20`

```kotlin
private val keyPair: KeyPair = generateRsaKey()   // new RSA key per JVM process
```

**What's wrong.** Each application instance generates its own RSA keypair at boot. Nothing loads a key from configuration, and no JWKS endpoint is published.

**Why it matters.**
- With `replicas: 3` (`infra/k8s/backend-deployment.yaml:9`), instance A cannot verify a token minted by instance B. Roughly **two-thirds of all authenticated requests fail with 401** behind a round-robin service.
- Every restart, rolling deploy, or pod eviction invalidates all outstanding access tokens.
- `infra/k8s/secret.yaml` already defines `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` — the secrets exist and are simply never read. The infrastructure and the code contradict each other.

**How to change it.**

1. Add the key properties to `application.yml`, with **no default** so a missing key fails startup loudly (this is the policy `infra/docker/.env.example` already states):

```yaml
fincore:
  security:
    jwt:
      private-key: ${JWT_PRIVATE_KEY}
      public-key: ${JWT_PUBLIC_KEY}
      key-id: ${JWT_KEY_ID:fincore-signing-key-1}
```

2. Rewrite `JwtConfig` to load the injected PEM rather than generate one:

```kotlin
@Configuration
class JwtConfig(
    @Value("\${fincore.security.jwt.private-key}") private val privateKeyPem: String,
    @Value("\${fincore.security.jwt.public-key}")  private val publicKeyPem: String,
    @Value("\${fincore.security.jwt.key-id}")      private val keyId: String,
) {
    private val publicKey: RSAPublicKey  by lazy { RsaKeyReader.readPublic(publicKeyPem) }
    private val privateKey: RSAPrivateKey by lazy { RsaKeyReader.readPrivate(privateKeyPem) }

    @Bean
    fun jwtEncoder(): JwtEncoder {
        val rsaKey = RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build()
        return NimbusJwtEncoder(ImmutableJWKSet(JWKSet(rsaKey)))
    }

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withPublicKey(publicKey).build().apply {
            setJwtValidator(DelegatingOAuth2TokenValidator(
                JwtValidators.createDefault(),
                JwtIssuerValidator(JwtTokenService.ISSUER),
                JwtClaimValidator<List<String>>("aud") { it?.contains("fincore-api") == true },  // see C-2
            ))
        }
}
```

3. Add a test profile that generates an ephemeral pair (so tests keep working), e.g. a `@TestConfiguration` in `EmbeddedPostgresSupport` supplying the two properties.

4. Add a regression test proving cross-instance verification: mint with one `JwtConfig` instance, verify with a second built from the same PEM.

5. For rotation, publish `/.well-known/jwks.json` with a `kid` per key and keep the previous public key in the JWK set for one access-token lifetime.

---

### C-2 — Default authorization rule is `permitAll()` (fail-open)

**Where:** `backend/src/main/kotlin/com/fincore/shared/security/SecurityConfig.kt:73`

```kotlin
it.anyRequest().permitAll()
```

**What's wrong.** Every endpoint is enumerated explicitly, and anything not on the list is public. `@PreAuthorize` on controllers is the only remaining defence.

**Why it matters.** A new controller added under a path not in that hardcoded list is publicly reachable the moment it merges — a silent failure, not a loud one. The security posture depends on a developer remembering to edit an unrelated file. In a banking system the default must be deny.

**How to change it.**

```kotlin
.authorizeHttpRequests {
    it.requestMatchers(
        "/actuator/health/**", "/actuator/info",     // see C-3 — narrow the actuator surface
        "/api/v1/auth/login", "/api/v1/auth/register",
        "/api/v1/auth/refresh", "/api/v1/auth/logout",
    ).permitAll()
    it.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
        .hasRole("ADMIN")                             // see M-5
    it.anyRequest().authenticated()                   // deny by default
}
```

Add an integration test asserting that an unmapped path (`/api/v1/anything`) returns 401, so the fail-closed default cannot regress.

Add an audience claim while you are here — `JwtTokenService.createAccessToken` should call `.audience(listOf("fincore-api"))`, matched by the validator in C-1.

---

### C-3 — Actuator (including Prometheus metrics) is unauthenticated and publicly proxied

**Where:** `SecurityConfig.kt:63` (`"/actuator/**"` → `permitAll`), `infra/docker/nginx.conf:52` (`location /actuator/` → proxied), `infra/helm/fincore-360/values.yaml` (ingress path `/actuator` → backend)

**What's wrong.** `management.endpoints.web.exposure.include: health,info,prometheus,metrics` combined with `permitAll` and a public ingress path means anyone on the internet can read `/actuator/prometheus` and `/actuator/metrics`.

**Why it matters.** The metrics are business metrics: `fincore.transfers.initiated`, `fincore.transfers.failed{reason=…}`, transfer duration percentiles, HikariCP pool saturation, JVM internals. That is a free transaction-volume feed and a live capacity-planning oracle for an attacker planning a resource-exhaustion attack.

**How to change it.**

1. Move the management endpoints to a separate, non-ingressed port:

```yaml
management:
  server:
    port: 9090            # never exposed through the public ingress
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

2. In `SecurityConfig`, permit **only** the probes: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`. Require `hasRole('ADMIN')` for `/actuator/prometheus` and `/actuator/metrics`.
3. Delete the `location /actuator/ { … }` block from `infra/docker/nginx.conf` and the `/actuator` path from the Helm ingress `hosts[].paths`.
4. Point the Prometheus scrape config and the K8s `prometheus.io/port` annotation at `9090`; the existing `NetworkPolicy` already restricts that traffic to the `monitoring` namespace.

---

### C-4 — The transactional outbox is never relayed

**Where:** `backend/src/main/kotlin/com/fincore/shared/outbox/OutboxService.kt:45`

**What's wrong.** `relayPendingEvents()` exists, is well written, and is called **only from tests**. There is no `@EnableScheduling` and no `@Scheduled` anywhere in `backend/src/main` (verified by grep).

**Why it matters.** Every `TRANSFER_COMPLETED` row is written to `outbox_events` with `status = PENDING` and stays there forever. `TransactionEventListener` never fires. **No customer ever receives a notification.** The `notifications` table stays empty in production while every notification test passes, because the tests invoke the relay by hand.

A second defect is latent in the same method: `findByStatusOrderByCreatedAtAsc` takes no row locks, so once you *do* schedule it, three replicas will each claim the same batch and publish every event three times.

**How to change it.**

1. Add a locking query to `OutboxEventRepository`:

```kotlin
@Query(
    value = """
        SELECT * FROM outbox_events
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
    """,
    nativeQuery = true,
)
fun claimPendingBatch(batchSize: Int): List<OutboxEvent>
```

`FOR UPDATE SKIP LOCKED` is what makes the relay safe to run on every replica simultaneously — each instance takes a disjoint batch.

2. Use it in `relayPendingEvents` in place of `findByStatusOrderByCreatedAtAsc`.

3. Add the scheduler (a new file, `shared/outbox/OutboxRelayScheduler.kt`):

```kotlin
@Component
@ConditionalOnProperty("fincore.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler(private val outboxService: OutboxService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${fincore.outbox.relay.interval-ms:2000}")
    fun relay() {
        runCatching { outboxService.relayPendingEvents() }
            .onFailure { log.error("Outbox relay cycle failed", it) }
    }
}
```

4. Add `@EnableScheduling` to `FinCoreApplication`, and set `fincore.outbox.relay.enabled: false` in the test profile so tests keep driving the relay deterministically.

5. Add exponential backoff — currently `markFailed()` retries on the next cycle with no delay, so a poison event is retried three times within six seconds and then dead-lettered. Add a `next_attempt_at TIMESTAMPTZ` column in a new migration `V5` and filter on `next_attempt_at <= now()`.

6. Add a metric and alert for relay lag:

```kotlin
Gauge.builder("fincore.outbox.pending", outboxEventRepository) { it.countByStatus(PENDING).toDouble() }
     .register(meterRegistry)
```

with a Prometheus alert firing when `fincore_outbox_pending > 100 for 5m`.

---

### C-5 — Audit records silently drop `ip_address` and `user_agent`

**Where:** `backend/src/main/kotlin/com/fincore/shared/audit/AuditLogRepository.kt:38-55`

```kotlin
fun append(… ipAddress: String?, userAgent: String?, …) {
    val sql = """
        INSERT INTO audit_events (
            event_id, event_type, actor_id, actor_role, resource_type,
            resource_id, outcome, reason, correlation_id, timestamp
        ) VALUES ( … )                      // ip_address and user_agent are absent
    """
```

**What's wrong.** The method signature accepts `ipAddress` and `userAgent`. The schema has `ip_address INET` and `user_agent TEXT`. `findEvents` selects both and `AuditEventResponse` returns both. The `INSERT` writes neither. Both parameters are accepted and thrown away.

**Why it matters.** Every audit row in the database has `ip_address = NULL`. Failed-login forensics, session-hijack investigation and "where was this transfer initiated from" are all impossible. The API returns `null` and looks like an empty field rather than a bug, so nobody notices. For a system whose `THREAT-MODEL.md` and `SECURITY.md` lean on the audit trail as the primary detective control, this voids the control.

`AuditControllerIntegrationTest.kt:81` passes `ipAddress = "127.0.0.1"` and then asserts only `eventType` and `actorRole` — the bug is invisible to the test suite.

**How to change it.**

```kotlin
val sql = """
    INSERT INTO audit_events (
        event_id, event_type, actor_id, actor_role, resource_type,
        resource_id, outcome, reason, ip_address, user_agent,
        correlation_id, timestamp
    ) VALUES (
        :eventId, :eventType, :actorId, :actorRole, :resourceType,
        :resourceId, :outcome, :reason, CAST(:ipAddress AS INET), :userAgent,
        :correlationId, now()
    )
""".trimIndent()

jdbcClient.sql(sql)
    // …
    .param("ipAddress", ipAddress)      // CAST handles the INET conversion and NULLs
    .param("userAgent", userAgent?.take(1000))
    .update()
```

Then strengthen the test so it can never regress:

```kotlin
.andExpect(jsonPath("$.items[0].ipAddress").value("127.0.0.1"))
.andExpect(jsonPath("$.items[0].userAgent").value("JUnit"))
```

Note `remoteAddr` is the load balancer's IP behind an ingress — resolve the real client IP from a **trusted** `X-Forwarded-For` (see C-6) and pass that instead.

---

### C-6 — Rate limiting is per-instance, unbounded in memory, and trivially bypassed

**Where:** `shared/security/ratelimit/RateLimiterService.kt`, `RateLimitingFilter.kt:79-85`

Three distinct defects in one component:

**(a) Spoofable key.** `extractClientIp` trusts `X-Forwarded-For` unconditionally. Any client can send a different `X-Forwarded-For` on each request and get an unlimited number of login attempts — the brute-force control (`LOGIN_LIMIT = 10`) is bypassed with one header.

**(b) Per-instance state.** The `ConcurrentHashMap` is local to the JVM. With 3 replicas the real limit is 30/min, and it resets on every deploy.

**(c) Unbounded growth.** Keys are never evicted, only their timestamp deques are trimmed. One entry per distinct IP accumulates for the process lifetime — a slow memory leak that becomes fast under (a), since an attacker chooses the cardinality.

**Why it matters.** These compound: an attacker spoofs `X-Forwarded-For` to defeat login throttling *and* to grow the map without bound at the same time.

**How to change it.**

*(a) — trust only the proxy hop you control.* Configure Spring's forwarded-header handling and read the resolved address:

```yaml
server:
  forward-headers-strategy: NATIVE     # trusts the container/ingress, not the client
```

```kotlin
private fun extractClientIp(request: HttpServletRequest): String {
    // With forward-headers-strategy=NATIVE, remoteAddr is already the resolved
    // client address for trusted proxies. Never parse the raw header here.
    return request.remoteAddr ?: "unknown"
}
```

Set `use-forwarded-headers: "true"` on the nginx ingress controller and keep `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;` (already correct in `nginx.conf:38`) so only the ingress can append.

*(b) — make the limiter shared.* `docs/adr/ADR-008-Redis-Cache-Sessions.md` already chose Redis for exactly this; it was never implemented (see H-9). Introduce it behind the existing interface:

```kotlin
interface RateLimiter { fun tryAcquire(key: String, limit: Int, windowSeconds: Long): RateLimitResult }

@Service
@ConditionalOnProperty("fincore.ratelimit.backend", havingValue = "redis")
class RedisRateLimiter(private val redis: StringRedisTemplate) : RateLimiter {
    // Sliding window via ZSET + Lua for atomicity:
    //   ZREMRANGEBYSCORE key -inf (now-window); ZCARD key; ZADD key now uuid; EXPIRE key window
}
```

Keep the in-memory implementation as the `local` backend for tests and single-node dev.

*(c) — bound the map* in the local implementation regardless, so it is safe as a fallback:

```kotlin
private val requestWindows = Caffeine.newBuilder()
    .maximumSize(100_000)
    .expireAfterAccess(Duration.ofMinutes(10))
    .build<String, ConcurrentLinkedDeque<Long>>()
```

*Also:* move `LOGIN_LIMIT` / `TRANSFER_LIMIT` out of `companion object` constants into `@ConfigurationProperties`, so limits are tunable during an incident without a redeploy.

---

# 4. HIGH findings

---

### H-1 — Failure audit entries and failure outbox events are rolled back and lost

**Where:** `transactions/application/TransferService.kt:170-205`

**What's wrong.** `executeTransfer` is a single `@Transactional` method. Inside `catch`, it appends `TRANSFER_FAILED` to the audit log and records a `TRANSFER_FAILED` outbox event — then rethrows. `AuditLogRepository` uses the ambient `JdbcClient` transaction and `OutboxService.recordEvent` is `Propagation.MANDATORY`. **Both writes are rolled back with the transaction.**

**Why it matters.** The audit trail records that transfers were *initiated* and *completed*, but never that one *failed*. Every insufficient-funds attempt, every frozen-account attempt, every internal error is invisible to the audit trail — precisely the events fraud monitoring cares about. `PRODUCTION-FAILURE-MODES.md` treats these as recorded; they are not.

**How to change it.** Failure logging must survive the rollback, which means a separate transaction.

1. Add a suspend-and-write variant to `AuditLogRepository`:

```kotlin
@Transactional(propagation = Propagation.REQUIRES_NEW)
fun appendIndependently(/* same parameters */) = append(/* … */)
```

2. In `TransferService`'s `catch` block, call `appendIndependently(...)` instead of `append(...)`.

3. For the failure event, do not use the outbox — an outbox row is by design part of the committing transaction. Either drop the `TRANSFER_FAILED` outbox write (the audit record is the durable trail) or persist it through its own `REQUIRES_NEW` service so it is genuinely committed.

4. Add the missing test — this is the single most valuable test to add to the suite:

```kotlin
@Test
fun `failed transfer still records TRANSFER_FAILED in the audit trail`() {
    // source account with 10.00, attempt a transfer of 1000.00
    assertThrows<InsufficientFundsException> { transferService.executeTransfer(cmd) }
    val events = auditLogRepository.findEvents(correlationId = corrId)
    assertTrue(events.any { it.eventType == "TRANSFER_FAILED" && it.outcome == "FAILURE" })
}
```

---

### H-2 — Web portal cannot log in against the real backend

**Where:** `web/src/context/AuthContext.tsx:56-59` vs `backend/.../identity/api/dto/AuthDtos.kt:29-38`

**What's wrong.** The backend's `LoginRequest` declares `deviceId` as `@field:NotBlank`. The web client posts `{ username, password }` only.

**Why it matters.** Every real login returns **400 VALIDATION_FAILED**. The portal has never been exercised end-to-end against the backend; the web tests mock `fetch`, so nothing catches it.

**How to change it.** Give the browser a stable device identity and send it:

```ts
// web/src/services/deviceId.ts
export function getDeviceId(): string {
  const KEY = 'fincore_device_id'
  let id = localStorage.getItem(KEY)
  if (!id) { id = crypto.randomUUID(); localStorage.setItem(KEY, id) }
  return id
}
```

```ts
const response = await apiClient.post<AuthResponse>('/api/v1/auth/login', {
  username, password, deviceId: getDeviceId(),
})
```

Apply the same to the refresh call added in H-3. `localStorage`, not `sessionStorage`, is correct here: the device identity must outlive the session or every new tab counts as a new device and silently revokes the previous refresh token (`RefreshTokenService.createRefreshToken` overwrites per `(userId, deviceId)`).

Then add a genuine end-to-end check — a Playwright spec, or at minimum a contract test asserting the web login payload satisfies the backend's `LoginRequest` schema.

---

### H-3 — No token refresh on the web; sessions die after 15 minutes

**Where:** `web/src/services/apiClient.ts`

**What's wrong.** Access tokens expire in 15 minutes (`JwtTokenService.ACCESS_TOKEN_EXPIRY_MINUTES`). `apiClient` has no 401 interceptor and never calls `/api/v1/auth/refresh`, even though the refresh token is stored in the session. The Android client does this correctly via `TokenAuthenticator` — the web client simply never got the equivalent.

**How to change it.** Add a single-flight refresh to `ApiClient`:

```ts
private refreshInFlight: Promise<void> | null = null

private async refreshToken(): Promise<void> {
  // Collapse concurrent 401s into one refresh call.
  if (!this.refreshInFlight) {
    this.refreshInFlight = (async () => {
      const res = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: this.refresh, deviceId: getDeviceId() }),
      })
      if (!res.ok) throw new Error('refresh_failed')
      const body = await res.json()
      this.setToken(body.accessToken)
      this.setRefresh(body.refreshToken)   // rotation — the old one is now invalid
    })().finally(() => { this.refreshInFlight = null })
  }
  return this.refreshInFlight
}
```

In `request()`, on `response.status === 401` and not already retried: `await this.refreshToken()`, then replay the request once. On refresh failure, clear the session and redirect to `/login`.

The backend rotates refresh tokens and revokes the whole device family on reuse (`RefreshTokenService:63`), so the single-flight guard is **required** — two parallel refreshes would trip reuse detection and log the user out entirely.

---

### H-4 — `mockLoginAs` ships client-side role spoofing to production

**Where:** `web/src/context/AuthContext.tsx:78-90`

**What's wrong.** `mockLoginAs(role)` fabricates a session with any role and a fake bearer token, and is exported through the real `AuthContext` used by the real app.

**Why it matters.** Anyone can open the console and grant themselves `ADMIN` in the UI. The backend still rejects the fake token, so this is not a data breach — but it exposes admin-only navigation, audit screens and controls, is an obvious finding in any security review, and makes the portal's own tests meaningless as evidence of RBAC.

**How to change it.** Remove `mockLoginAs` from the production context entirely.

1. Delete it from `AuthContextType` and `AuthProvider`.
2. For tests, wrap components in a test-only provider that seeds `user` directly:

```tsx
// web/src/test/renderWithAuth.tsx
export function renderWithAuth(ui: React.ReactNode, session: UserSession | null) {
  return render(<AuthContext.Provider value={buildTestValue(session)}>{ui}</AuthContext.Provider>)
}
```

3. Update `RolePermissionMatrix.test.tsx` and `ProtectedRoute.test.tsx` to use it.
4. If you want a demo mode, gate it on `import.meta.env.DEV` so Vite tree-shakes it out of the production bundle — but deleting it is better.

---

### H-5 — Android logs full request and response bodies in release builds

**Where:** `android/core/network/src/main/kotlin/com/fincore/core/network/di/NetworkModule.kt:53-55`

```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY      // unconditional
}
```

**What's wrong.** `Level.BODY` logs every header and every request/response body to logcat, in all build types. That includes the `Authorization: Bearer …` header, plaintext passwords on `POST /auth/login`, refresh tokens, account numbers and balances.

**Why it matters.** On Android, logcat is readable by ADB and by crash/analytics SDKs. This writes credentials and PII to a durable, exportable log on the device. The `android-ci.yml` job that supposedly guards this —

```yaml
- name: Assert No Logging Interceptor in Release Source
  run: grep -rn "HttpLoggingInterceptor.Level.BODY" android/core/network || true
```

— ends in `|| true`, so it prints the violation and passes. The check is decorative and the violation is live.

**How to change it.**

1. Enable `buildConfig` and expose a debug flag in `android/core/network/build.gradle.kts`:

```kotlin
android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("boolean", "NETWORK_LOGGING", "false")
    }
    buildTypes {
        debug   { buildConfigField("boolean", "NETWORK_LOGGING", "true") }
        release { buildConfigField("boolean", "NETWORK_LOGGING", "false") }
    }
}
```

2. Gate the interceptor and never log bodies even in debug:

```kotlin
.apply {
    if (BuildConfig.NETWORK_LOGGING) {
        addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC     // method, URL, status, timing only
            redactHeader("Authorization")
            redactHeader("Cookie")
        })
    }
}
```

3. Make the CI gate real — drop `|| true` and invert it:

```yaml
- name: Assert no body-level HTTP logging
  run: |
    if grep -rn "Level.BODY" android/ --include=*.kt; then
      echo "::error::HttpLoggingInterceptor.Level.BODY is forbidden"; exit 1
    fi
```

---

### H-6 — Android has no certificate pinning and a hardcoded base URL

**Where:** `NetworkModule.kt:29` (`BASE_URL = "https://api.fincore.com/"`), `provideOkHttpClient`

**What's wrong.** No `CertificatePinner`, no per-flavour endpoint configuration, no explicit timeouts.

**Why it matters.** A banking client on a hostile network (public Wi-Fi, corporate MITM proxy, a device with a user-installed CA) is trivially interceptable. And with the URL compiled in, the app cannot be pointed at staging without editing source.

**How to change it.**

```kotlin
buildTypes {
    debug   { buildConfigField("String", "API_BASE_URL", "\"https://api-staging.fincore.com/\"") }
    release { buildConfigField("String", "API_BASE_URL", "\"https://api.fincore.com/\"") }
}
```

```kotlin
private val certificatePinner = CertificatePinner.Builder()
    .add("api.fincore.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")  // leaf
    .add("api.fincore.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")  // backup — required
    .build()

OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(60, TimeUnit.SECONDS)
    // …
```

Always pin a **backup** key; pinning a single certificate bricks every installed app when that certificate is rotated. Add a `network_security_config.xml` with `cleartextTrafficPermitted="false"` and reference it from the manifest as a second layer.

---

### H-7 — CI/CD deploy and security gates do not do anything

**Where:** `.github/workflows/backend-ci.yml`, `staging-deploy.yml`, `android-ci.yml`

Four separate problems:

| Gate | Current behaviour | Effect |
|---|---|---|
| Trivy container scan | `exit-code: '0'` (both workflows) | CRITICAL CVEs print and pass |
| Android R8 verification | `assembleRelease --dry-run \|\| ./gradlew assembleDebug` | Falls back to a debug build; verifies nothing |
| Logging-interceptor check | `grep … \|\| true` | Always passes (see H-5) |
| Staging deploy | `echo "Simulating kubectl apply…"` | No deployment occurs; the job reports success |

The smoke test compounds it: `backend-ci.yml` runs `smoke-test.sh http://localhost:8080` on a runner where nothing is listening, so it fails, triggers `rollback.sh`, and `exit 1`s. **The `deploy-staging` job fails on every push to `main`.**

**How to change it.**

1. Make the scanners blocking:

```yaml
- uses: aquasecurity/trivy-action@0.29.0
  with:
    image-ref: fincore-backend:${{ github.sha }}
    exit-code: '1'
    severity: 'CRITICAL,HIGH'
    ignore-unfixed: true
```

2. Make the R8 check real — `./gradlew assembleRelease` with a debug-keystore signing config for CI, and no `||` fallback.
3. Either implement the deploy properly (`azure/setup-kubectl` → `kubectl apply -k infra/k8s/` → `kubectl rollout status --timeout=5m`) **or delete the simulated jobs and say in `CI-CD.md` that deployment is designed but not wired**. A job that prints "Simulating rollout" and reports green is worse than no job.
4. Run the smoke test against a service the workflow actually starts:

```yaml
- run: docker compose -f infra/docker/docker-compose.yml up -d --wait backend
- run: ./infra/scripts/smoke-test.sh http://localhost:8080
```

5. Add the gates that are missing entirely: CodeQL (`github/codeql-action`) for Kotlin and TypeScript, and OWASP Dependency-Check or `gradle dependencyCheckAnalyze` with a CVSS-7 failure threshold.

---

### H-8 — `GlobalExceptionHandler` maps ordinary domain rejections to HTTP 500

**Where:** `shared/error/GlobalExceptionHandler.kt`

**What's wrong.** Only `DomainException` and three Spring exceptions are handled; everything else falls into `handleUnexpected` → `500 INTERNAL_ERROR`. But several reachable, entirely-expected conditions do not throw `DomainException`:

| Trigger | Exception | Correct status | Actual |
|---|---|---|---|
| `sourceAccountId == destinationAccountId` (`AccountService:104`) | `IllegalArgumentException` | 400 | **500** |
| Amount with >4 decimal places (`TransferService:56`, `setScale(…, UNNECESSARY)`) | `ArithmeticException` | 400 | **500** |
| Bad UUID in a path variable | `MethodArgumentTypeMismatchException` | 400 | **500** |
| DB CHECK violation (`available_balance >= 0`) | `DataIntegrityViolationException` | 409 | **500** |
| Lock wait timeout under contention | `CannotAcquireLockException` | 409/503 | **500** |

**Why it matters.** A customer transferring to their own account gets "An unexpected error occurred". Every one of these increments the 5xx error-rate SLO and pages an on-call engineer for a user input error. `API-DESIGN.md §3` promises a different contract than the code delivers.

**How to change it.**

```kotlin
@ExceptionHandler(IllegalArgumentException::class, ArithmeticException::class)
fun handleIllegalArgument(ex: RuntimeException): ResponseEntity<ErrorResponse> {
    log.warn("Rejected request: {}", ex.message)
    return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "The request was not valid")
}

@ExceptionHandler(MethodArgumentTypeMismatchException::class)
fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException) =
    respond(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, "Parameter '${ex.name}' has an invalid format")

@ExceptionHandler(DataIntegrityViolationException::class)
fun handleIntegrity(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
    log.error("Data integrity violation", ex)   // full detail to the log, never to the client
    return respond(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "The request conflicts with the current state")
}

@ExceptionHandler(CannotAcquireLockException::class, QueryTimeoutException::class)
fun handleContention(ex: Exception) =
    respond(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR, "The service is busy; please retry")
```

Better still, prevent the first two at the boundary instead of catching them:

```kotlin
// TransferDtos.kt
@field:Digits(integer = 15, fraction = 4, message = "amount supports at most 4 decimal places")
val amount: BigDecimal,
```

and add a class-level `@AssertTrue` that `sourceAccountId != destinationAccountId`, so both become clean 400s with field-level `details`.

---

### H-9 — ADR-008 (Redis) and ADR-009 (Kafka) describe infrastructure that does not exist

**Where:** `docs/adr/ADR-008-Redis-Cache-Sessions.md`, `docs/adr/ADR-009-Kafka-Async-Events.md`, `README.md:3`

**What's wrong.** Two accepted ADRs and the README's opening line commit to Redis and Kafka. Neither appears anywhere: no dependency, no client, no Docker service, no K8s manifest. Rate limiting is in-memory (C-6); the event bus is `ApplicationEventPublisher` in the same JVM.

**Why it matters.** An ADR is a record of a decision that was *made and acted on*. Two ADRs describing infrastructure that was never built means a reader cannot trust any of the other seventeen — including the ones that *are* faithfully implemented and are the strongest part of this project.

**How to change it.** Pick one, per ADR:

- **Implement it** — Redis is the right fix for C-6 and already justified.
- **Or change the status.** Edit the front matter to `Status: Accepted — Not Yet Implemented (target: Phase 15)` and add a "Current state" section naming the in-process stand-in and its limitations. Then correct `README.md:3` to describe what runs today.

The second option costs ten minutes and restores the integrity of the whole ADR set.

---

### H-10 — `Money` is a fully-tested value object that no production code uses

**Where:** `shared/money/Money.kt`; entities at `accounts/domain/Account.kt:44-50` and `transactions/domain/Transaction.kt`

**What's wrong.** `Money` is well designed, has 13 passing tests, and is referenced **zero** times outside its own package (verified by grep). `Account.ledgerBalance` / `availableBalance` and `Transaction.amount` are bare `BigDecimal`, and `setScale(4, RoundingMode.UNNECESSARY)` is hand-repeated at eight call sites in `AccountService` and `TransferService`.

Two consequences follow:
- The currency-matching invariant `Money` enforces structurally is re-implemented ad hoc as a string comparison in `AccountService:127`.
- Every one of those `setScale(…, UNNECESSARY)` calls throws `ArithmeticException` on unexpected input — the 500 in H-8.

Separately, `Money.kt:3-4` imports `com.fasterxml.jackson.annotation.*` (Jackson 2) while the application's `ObjectMapper` is `tools.jackson` (Jackson 3, per `build.gradle.kts`). `RateLimitingFilter.kt:3` does the same and constructs a *second*, unconfigured Jackson 2 `ObjectMapper`. The tests pass today, but the app is running two Jackson lineages and relying on cross-version annotation compatibility.

**How to change it.**

1. Make `Money` an `@Embeddable` and use it in the entities:

```kotlin
@Embeddable
class Money private constructor(
    @Column(name = "amount", precision = 19, scale = 4) val amount: BigDecimal,
    @JdbcTypeCode(Types.CHAR) @Column(name = "currency", length = 3) val currency: String,
)
```

```kotlin
@AttributeOverrides(
    AttributeOverride(name = "amount",   column = Column(name = "available_balance")),
    AttributeOverride(name = "currency", column = Column(name = "currency")),
)
@Embedded var availableBalance: Money
```

2. Replace every hand-rolled `setScale`/currency check in `AccountService.executeTransferBalances` with `Money` operators — `sourceAccount.availableBalance -= amount` then carries the currency check for free.
3. Normalise Jackson: change `Money.kt` to `tools.jackson.annotation.*`, and in `RateLimitingFilter` **inject** the Spring-managed `tools.jackson.databind.ObjectMapper` rather than constructing one.
4. Add an ArchUnit rule so this cannot drift back:

```kotlin
noClasses().that().resideInAPackage("com.fincore..")
    .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
    .because("this application runs Jackson 3 (tools.jackson)")
```

---

### H-11 — Any customer can create an account with an arbitrary opening balance

**Where:** `accounts/api/AccountController.kt:78-97`, `accounts/api/dto/AccountDtos.kt:19-20`

```kotlin
@PostMapping
@PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
fun createAccount(/* … */ @Valid @RequestBody request: CreateAccountRequest /* … */)
```

with `initialDeposit` validated only by `@DecimalMin("0.0000")` — no upper bound, no authorization distinction.

**What's wrong.** An authenticated customer can `POST /api/v1/accounts` with `initialDeposit: 999999999.0000`, then transfer the balance to any other account. Money is created from nothing, and it passes every existing check: the account is `ACTIVE`, the currency matches, `available_balance >= 0` holds, and the transfer is fully audited as legitimate.

**Why it matters.** In a simulation this is harmless; as a design statement it is the one thing a banking reviewer will not forgive, because opening balances are a funding operation and funding is never a self-service customer capability.

**How to change it.**

1. Customers open accounts with a **zero** balance:

```kotlin
data class CreateAccountRequest(
    val accountType: AccountType = AccountType.CHECKING,
    @field:NotBlank @field:Size(min = 3, max = 3) val currency: String = "GBP",
)
```

2. Move funding to a separate, privileged endpoint that produces a `DEPOSIT` transaction — the `TransactionType.DEPOSIT` enum value already exists and is unused:

```kotlin
@PostMapping("/{id}/deposits")
@PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
fun deposit(@PathVariable id: UUID, @RequestHeader("Idempotency-Key") key: String,
            @Valid @RequestBody request: DepositRequest, @AuthenticationPrincipal jwt: Jwt)
```

so every credit has a transaction row, an idempotency key, and an audit entry — the same guarantees transfers already have.

3. Add the regression test: a `ROLE_CUSTOMER` token posting a non-zero `initialDeposit` must get 400 or 403, and a customer calling the deposit endpoint must get 403.

---

# 5. MEDIUM findings

---

### M-1 — Refresh tokens have no absolute session lifetime

**Where:** `identity/application/RefreshTokenService.kt:88`

Each rotation resets `expiresAt` to `now + 7 days`. A client refreshing every 14 minutes stays authenticated forever; the "7-day expiry" only limits *inactivity*.

**Fix.** Add an absolute cap alongside the sliding one — a new column in migration `V5`:

```sql
ALTER TABLE refresh_tokens ADD COLUMN absolute_expires_at TIMESTAMPTZ;
UPDATE refresh_tokens SET absolute_expires_at = created_at + INTERVAL '30 days';
ALTER TABLE refresh_tokens ALTER COLUMN absolute_expires_at SET NOT NULL;
```

Set it once at creation, never on rotation, and reject in `rotateRefreshToken` when `Instant.now() > token.absoluteExpiresAt` — return `RefreshResult.Expired` so the user re-authenticates.

---

### M-2 — Token-reuse detection revokes one device, not the account

**Where:** `RefreshTokenService.kt:63-70`

On detecting a replayed token the service calls `revokeAllByUserIdAndDeviceId(...)` — it revokes the compromised device family only. But `deviceId` is chosen by the client, so an attacker who stole a token simply presents a different `deviceId` and keeps their own session alive.

**Fix.** Reuse detection is a strong signal of credential theft; escalate it to the account:

```kotlin
refreshTokenRepository.revokeAllByUserId(token.userId)
auditLogRepository.appendIndependently(
    eventType = "TOKEN_REUSE_DETECTED", actorId = token.userId,
    outcome = "FAILURE", reason = "All sessions revoked", /* … */
)
notificationService.createSecurityAlert(token.userId, "Unusual sign-in activity detected")
```

Also `previousTokenHash` retains only **one** generation, so a replay two rotations old falls through to `findByTokenHash` → `Invalid` rather than `ReuseDetected`. Keep a small ring of previous hashes, or a separate `revoked_token_hashes` table with a TTL.

---

### M-3 — No retention or cleanup jobs; three tables grow without bound

**Where:** `idempotency_keys`, `refresh_tokens`, `outbox_events`

`V1__baseline_schema.sql:100` says *"Supports the expiry purge job"* and creates `idx_idempotency_expires`. **There is no purge job.** `IdempotencyKeyRecord.expiresAt` is written and never read. Published outbox rows are never archived; revoked refresh tokens are never deleted.

`idempotency_keys` grows by one row per mutation *and stores the full response body as JSONB* — it is the fastest-growing table in the system and the one on the hot path of every transfer.

**Fix.** Add a scheduled retention component (alongside the outbox scheduler from C-4):

```kotlin
@Component
class RetentionScheduler(private val jdbcClient: JdbcClient) {

    @Scheduled(cron = "\${fincore.retention.cron:0 0 3 * * *}")   // 03:00 daily
    @SchedulerLock(name = "retention", lockAtMostFor = "10m")     // ShedLock — one instance only
    fun purge() {
        jdbcClient.sql("DELETE FROM idempotency_keys WHERE expires_at < now()").update()
        jdbcClient.sql("DELETE FROM refresh_tokens  WHERE expires_at < now() - INTERVAL '30 days'").update()
        jdbcClient.sql("""
            DELETE FROM outbox_events
            WHERE status = 'PUBLISHED' AND published_at < now() - INTERVAL '7 days'
        """).update()
    }
}
```

Note `audit_events` must be **excluded** — the `audit_events_immutable()` trigger rejects `DELETE` by design (`V1:150`). Archive it to the S3 bucket Terraform already provisions (`aws_kms_key.s3_audit`) via a partition-detach strategy rather than deleting rows.

Add ShedLock (`net.javacrumbs.shedlock`) so the job runs once per cluster, not once per replica.

---

### M-4 — Four Android feature modules are empty shells that the build still compiles

**Where:** `android/feature/{dashboard,beneficiaries,cards,profile}/`

Each has a `build.gradle.kts`, an `AndroidManifest.xml`, and a single `.gitkeep`. `settings.gradle.kts` includes all four. `PROJECT-STATUS.md` counts them toward "16 modules"; `android-ci.yml` advertises "Unit Tests (16 Modules)".

`:feature:dashboard` is a declared bottom-navigation destination with no screen behind it.

**Fix.** Choose per module:

- **Implement** `:feature:dashboard` — it is reachable from `FinCoreBottomBar` and its absence is user-visible.
- **Remove** `:feature:beneficiaries`, `:feature:cards`, `:feature:profile`: delete the directories, drop the three `include(...)` lines from `settings.gradle.kts`, and correct the module count in `PROJECT-STATUS.md`, `README.md` and the `android-ci.yml` job name to the true figure.

Empty modules add build-graph cost and configuration time for nothing, and inflate a metric the documentation reports as evidence.

---

### M-5 — Swagger UI and the OpenAPI spec are public in every environment

**Where:** `SecurityConfig.kt:65-68`

`/v3/api-docs/**` and `/swagger-ui/**` are `permitAll` unconditionally, and the Helm ingress routes `/api` publicly.

**Fix.** Disable in production and require authentication elsewhere:

```yaml
# application-prod.yml
springdoc:
  api-docs.enabled: false
  swagger-ui.enabled: false
```

```kotlin
it.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
  .hasRole("ADMIN")
```

---

### M-6 — `CorrelationIdFilter` accepts arbitrary client input into the log stream

**Where:** `shared/correlation/CorrelationIdFilter.kt:41`

```kotlin
val correlationId = request.getHeader(HEADER)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
```

The header is placed in the MDC, echoed in the response, and returned as `traceId` in error bodies — with no length limit and no format check. A client can send a 100 KB header, or embed newlines and ANSI escapes to forge log entries. Downstream, `TransferService:44` and `AuthService` both `runCatching { UUID.fromString(it) }` and silently fall back to a random UUID when parsing fails, so a malformed inbound ID quietly breaks the correlation chain it exists to preserve.

**Fix.** Validate at the boundary, so everything downstream can trust it:

```kotlin
private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

val correlationId = request.getHeader(HEADER)
    ?.trim()
    ?.takeIf { it.length <= 36 && UUID_RE.matches(it) }
    ?: UUID.randomUUID().toString()
```

Also restore the previous MDC value rather than removing it unconditionally, so nested/async dispatches do not lose context:

```kotlin
val previous = MDC.get(MDC_KEY)
MDC.put(MDC_KEY, correlationId)
try { filterChain.doFilter(request, response) }
finally { if (previous != null) MDC.put(MDC_KEY, previous) else MDC.remove(MDC_KEY) }
```

---

### M-7 — There is no double-entry ledger; balances are mutated in place

**Where:** `accounts/domain/Account.kt`, `AccountService.executeTransferBalances`

Money exists only as a mutable column on `accounts`, updated by `-=` and `+=`. The `transactions` table records that a transfer happened but is not a ledger: there is no per-account posting, no running balance, no debit/credit pairing.

**Why it matters.** You cannot answer "what was this balance on 3 August?", cannot prove that the sum of all postings equals the sum of all balances, and cannot reverse a posting without another in-place mutation. `TransactionType.REVERSAL` and `REFUND` are declared in the schema with no mechanism behind them.

**Fix.** This is a design change, not a patch, and is the highest-value addition to the whole platform. Add a `ledger_entries` table in a new migration:

```sql
CREATE TABLE ledger_entries (
    id             UUID PRIMARY KEY,
    transaction_id UUID           NOT NULL REFERENCES transactions (id),
    account_id     UUID           NOT NULL REFERENCES accounts (id),
    direction      VARCHAR(6)     NOT NULL,     -- DEBIT | CREDIT
    amount         NUMERIC(19,4)  NOT NULL,
    currency       CHAR(3)        NOT NULL,
    balance_after  NUMERIC(19,4)  NOT NULL,     -- running balance at posting time
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ledger_direction_check CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_amount_positive CHECK (amount > 0)
);
CREATE INDEX idx_ledger_account_time ON ledger_entries (account_id, created_at DESC, id DESC);
```

Write exactly two rows per transfer inside the existing transaction, keep `accounts.available_balance` as the fast-read projection, and add a reconciliation test:

```kotlin
@Test
fun `sum of ledger entries equals account balance for every account`() {
    // SELECT account_id,
    //        SUM(CASE WHEN direction='CREDIT' THEN amount ELSE -amount END) AS computed
    // FROM ledger_entries GROUP BY account_id
    // -> assert equals accounts.available_balance for each
}
```

That single test is the strongest correctness statement this codebase could make.

---

### M-8 — K8s and Helm deploy `:latest`

**Where:** `infra/k8s/backend-deployment.yaml:38` (`image: fincore-backend:latest`, `imagePullPolicy: IfNotPresent`), `infra/helm/fincore-360/values.yaml` (`tag: "latest"` for backend and web)

`:latest` with `IfNotPresent` means a rolling update may not pull at all, replicas can run different builds, and rollback has no target.

**Fix.**

```yaml
image: "{{ .Values.backend.image.repository }}:{{ .Values.backend.image.tag }}"
imagePullPolicy: IfNotPresent
```

with `tag` set from CI to `${{ github.sha }}`, or better a digest (`@sha256:…`). Add to `values-prod.yaml` a required-value guard: `tag: {{ required "backend.image.tag must be set explicitly" .Values.backend.image.tag }}`.

---

### M-9 — No PodDisruptionBudget or anti-affinity for a 3-replica service

**Where:** `infra/k8s/` (no PDB), `backend-deployment.yaml` (no `topologySpreadConstraints`)

A node drain can evict all three backend pods simultaneously. `maxUnavailable: 0` protects rolling updates but not voluntary disruptions.

**Fix.** Add `infra/k8s/pdb.yaml`:

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata: { name: fincore-backend-pdb, namespace: fincore }
spec:
  minAvailable: 2
  selector:
    matchLabels: { app.kubernetes.io/name: fincore-backend }
```

and spread across zones in the pod spec:

```yaml
topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: DoNotSchedule
    labelSelector:
      matchLabels: { app.kubernetes.io/name: fincore-backend }
```

Also set `automountServiceAccountToken: false` — the backend never calls the K8s API.

---

### M-10 — Helm ingress serves a banking application over plain HTTP

**Where:** `infra/helm/fincore-360/values.yaml`

```yaml
annotations:
  nginx.ingress.kubernetes.io/ssl-redirect: "false"
tls: []
```

Meanwhile `SecurityConfig` sets HSTS with `max-age=31536000`, which is meaningless without TLS.

**Fix.** In `values-prod.yaml` (and staging):

```yaml
ingress:
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
  tls:
    - hosts: [ "banking.fincore360.internal" ]
      secretName: fincore-tls
```

Keep `ssl-redirect: "false"` only in the local `values.yaml`.

---

### M-11 — `application.yml` falls back to a default database password

**Where:** `backend/src/main/resources/application.yml:7-9`

```yaml
password: ${DB_PASSWORD:fincore}
```

`infra/docker/.env.example` states the policy explicitly: *"A missing required secret must make the application FAIL TO START rather than fall back to a default: a fallback that works is worse than a crash, because nobody notices it ran against the wrong database (FM-INFRA-003)."* The configuration does the opposite of its own documented rule.

**Fix.** Keep developer-friendly defaults in a `dev` profile only, and make the base configuration strict:

```yaml
# application.yml — no defaults
datasource:
  url: ${DB_URL}
  username: ${DB_USERNAME}
  password: ${DB_PASSWORD}
```

```yaml
# application-dev.yml — explicit, opt-in
datasource:
  url: jdbc:postgresql://localhost:5432/fincore
  username: fincore
  password: fincore
```

Spring fails startup on an unresolvable placeholder, which is exactly the stated intent.

---

### M-12 — Web observability and dashboard pages display fabricated numbers

**Where:** `web/src/pages/ObservabilityPage.tsx:24-43`, `web/src/pages/DashboardPage.tsx:35`

```tsx
// Directly answers the Phase 12 Exit Criterion Question:
// "How many transfers failed in the last hour, and why?"
const failedTransfersLastHour = 3
```

The failure-reason table, percentages, and the dashboard's "UP (200)" health tile are all string literals. The page is presented — in its own comment — as satisfying an exit criterion.

**Why it matters.** A hardcoded operations dashboard is worse than none: during an incident it displays confident, wrong numbers. And the backend already emits everything needed (`fincore.transfers.failed{reason}` exists in `BankingMetricsService`).

**Fix.** Query the real data:

```ts
const { data } = useQuery({
  queryKey: ['transfer-failures', timeWindow],
  queryFn: () => apiClient.get<MetricResponse>(
    `/actuator/metrics/fincore.transfers.failed?tag=reason:${reason}`),
  refetchInterval: 15_000,
})
```

For rate-over-time, proxy a Prometheus `query_range` through an admin-only backend endpoint rather than exposing Prometheus to the browser. Until it is wired, label the page **"Sample data — not live"** in the UI.

Apply the same to `DashboardPage`: poll `/actuator/health` for the health tile.

---

### M-13 — No static analysis or formatter on any of the three codebases

**Where:** repo-wide — no ktlint, detekt, Spotless, ESLint, or Prettier configuration exists (verified by grep).

Consequences visible in the code today: a stray brace-and-annotation collision at `GlobalExceptionHandler.kt:71` (`)    @ExceptionHandler(...)` on one line), an unused `@Async` import in `TransactionEventListener.kt:9`, `dependencies {    // ...` on one line in `build.gradle.kts:59`, and fully-qualified inline class names in `SecurityConfig` where imports exist elsewhere.

**Fix.**

```kotlin
// backend/build.gradle.kts and android/build.gradle.kts
plugins { id("io.gitlab.arturbosch.detekt") version "1.23.7" }
detekt { buildUponDefaultConfig = true; allRules = false }
```

```jsonc
// web/.eslintrc.json
{ "extends": ["eslint:recommended", "plugin:@typescript-eslint/recommended",
              "plugin:react-hooks/recommended"] }
```

Add both as blocking CI stages. `detekt` alone would have caught several items in this audit.

---

### M-14 — Performance "benchmarks" are wall-clock assertions in the unit-test suite

**Where:** `test/kotlin/com/fincore/performance/PerformanceBenchmarkTest.kt`

`assertTrue(durationMs < 1000)` on a shared GitHub Actions runner is flaky by construction — a noisy neighbour fails the build with no code change. They are also not load tests: single-threaded, no concurrency, no percentiles under contention.

**Fix.** Move them out of `test` into a `jmh` source set (`me.champeau.jmh` plugin) with proper warmup, and run them on a schedule rather than per-commit. For end-to-end throughput, add a k6 or Gatling scenario driving `POST /api/v1/transfers` at a target rate and asserting p99 latency and error rate — that is what `PERFORMANCE.md` describes and what `ConcurrentTransferIntegrationTest` cannot cover.

---

# 6. LOW findings

| ID | Finding | Fix |
|---|---|---|
| **L-1** | `README.md` says Phase 3 is *"Next up"*; `PROJECT-STATUS.md` says Phase 14 complete. README claims "32/32 tests" against an actual 139. | Regenerate the README status table from `PROJECT-STATUS.md`, or reduce it to a link. Stale status tables in two places always diverge. |
| **L-2** | `NotificationController` uses `@PreAuthorize("hasRole('CUSTOMER')")` while every other controller allows `CUSTOMER or ADMIN`. Admins get 403 on notifications. | Align to `hasRole('CUSTOMER') or hasRole('ADMIN')`, or introduce a `@CustomerOrAdmin` meta-annotation so the policy lives in one place. |
| **L-3** | Unused `@Async` import in `TransactionEventListener.kt:9`; there is no `@EnableAsync`, so the listener runs synchronously inside the relay transaction. | Remove the import, or add `@EnableAsync` + `@Async` deliberately with an explicit `TaskExecutor` (and note the listener then loses the transaction). |
| **L-4** | `ArchitectureTest.DOMAIN_MODULES` lists `"payments"`, which does not exist. Harmless only because of `allowEmptyShould(true)`. | Remove it, or create the module. Rules that silently pass on absent packages erode confidence in ArchUnit. |
| **L-5** | `AccountService.generateUniqueAccountNumber` falls back after 10 collisions to a UUID-derived string containing hex letters — not a valid IBAN body, and it skips the uniqueness check. | Retry on the DB unique-constraint violation instead of pre-checking, and keep the format valid. Add a check digit if IBAN realism matters. |
| **L-6** | `backend.Dockerfile:14` — `./gradlew dependencies --no-daemon \|\| true` masks resolution failures in the cache layer. | Drop `\|\| true`. Pin base images by digest (`eclipse-temurin:25-jre-alpine@sha256:…`). |
| **L-7** | 17 `.gitkeep` files remain in directories that now have content (`android/feature/{accounts,auth,transfer,transactions,notifications}/…`, `web/.gitkeep`, `android/.gitkeep`). | `git rm` the ones whose directories are non-empty. Keep only `docs/diagrams/.gitkeep`. |
| **L-8** | `docs/diagrams/` is empty, though `ARCHITECTURE.md` and `ANDROID-ARCHITECTURE.md` describe diagrams. | Add the C4 context/container diagrams as Mermaid `.md` files — they render on GitHub and diff in review, unlike images. |
| **L-9** | Web uses inline `style={{...}}` objects throughout; no design tokens, no dark mode, no error boundary, `AuthContext` has no `isRefreshing` state. | Extract CSS modules or a small token file; add a top-level `<ErrorBoundary>` so a render error does not blank the portal. |

---

# 7. Remove

Things that should be deleted rather than fixed:

1. **`mockLoginAs`** — `web/src/context/AuthContext.tsx:78-90` (H-4). Replace with a test-only provider.
2. **`:feature:beneficiaries`, `:feature:cards`, `:feature:profile`** — `android/` (M-4). Empty modules; drop the directories and the `include(...)` lines.
3. **The simulated deploy jobs** — `staging-deploy.yml` in full, and `deploy-staging` in `backend-ci.yml` (H-7). Either implement them or delete them; a green job that ran `echo` is a false assurance.
4. **`|| true` on the Android logging check** — `android-ci.yml` (H-5). Not the step — just the escape hatch that makes it always pass.
5. **`location /actuator/` in `infra/docker/nginx.conf`** and the `/actuator` ingress path in `values.yaml` (C-3).
6. **Redundant `.gitkeep` files** (L-7).
7. **`com.fasterxml.jackson` imports** — `Money.kt`, `RateLimitingFilter.kt` (H-10). One Jackson lineage, not two.
8. **`stringData` in `infra/k8s/secret.yaml`** — even placeholders. Replace the file with a `SealedSecret` or an `ExternalSecret` referencing AWS Secrets Manager (Terraform already provisions the KMS keys), so no secret shape is ever committed.

---

# 8. Add

Capabilities the platform is missing, ordered by value:

| # | Addition | Why |
|---|---|---|
| 1 | **`ledger_entries` double-entry table + reconciliation test** (M-7) | The single strongest correctness guarantee a banking system can offer; also unlocks `REVERSAL`/`REFUND`, which are declared but unimplemented |
| 2 | **Scheduler infrastructure** — `@EnableScheduling`, outbox relay, retention purge, ShedLock (C-4, M-3) | Two designed subsystems currently never execute |
| 3 | **Redis** for shared rate limiting and idempotency lookup (C-6, H-9) | Makes horizontal scaling correct and honours ADR-008 |
| 4 | **JWKS endpoint + key rotation** (C-1) | Multi-instance operation and zero-downtime key rotation |
| 5 | **End-to-end tests** — Playwright for web, one full `login → create account → transfer → notification` path | Would have caught H-2 and C-4 immediately |
| 6 | **CodeQL + dependency scanning with failing thresholds** (H-7) | The only automated security gates that currently exist do not fail |
| 7 | **Detekt + ESLint + Spotless** (M-13) | Catches a whole class of findings in this audit automatically |
| 8 | **Alerts for the new failure modes** — `fincore_outbox_pending`, refresh-token reuse rate, 5xx rate by endpoint | `infra/monitoring/alerts/` exists; extend it to cover C-4 and M-2 |
| 9 | **`k6`/Gatling load scenario** (M-14) | `PERFORMANCE.md` describes throughput targets nothing currently measures |
| 10 | **Android release signing config + `network_security_config.xml`** (H-6) | `assembleRelease` currently produces an unsigned APK |

---

# 9. Remediation order

**Sprint 1 — make it work on more than one instance (C-1, C-4, C-5, H-1)**
External JWT keys; schedule the outbox relay with `SKIP LOCKED`; fix the audit `INSERT`; make failure auditing survive rollback. Without these, three replicas are broken, no notification is ever sent, and the audit trail is incomplete. Add the two missing tests (cross-instance JWT verification; `TRANSFER_FAILED` audit persistence) in the same change.

**Sprint 2 — close the security gaps (C-2, C-3, C-6, H-5, H-11)**
`anyRequest().authenticated()`; move actuator off the public ingress; fix the rate-limiter key, sharing and bounds; gate Android body logging; remove customer-controlled opening balances.

**Sprint 3 — make the clients real (H-2, H-3, H-4, H-6, M-12)**
Send `deviceId`; add single-flight refresh; delete `mockLoginAs`; pin certificates and externalise the base URL; wire the observability page to live metrics.

**Sprint 4 — make the pipeline mean something (H-7, M-13)**
Blocking Trivy/CodeQL/dependency gates; a real R8 check; a real smoke test against a started container; detekt and ESLint as required checks.

**Sprint 5 — correctness depth and honesty (M-7, M-3, H-9, H-10, M-1, M-2, L-1)**
Double-entry ledger with reconciliation; retention jobs; reconcile the ADRs and README with reality; adopt `Money` in the domain; absolute session lifetime and account-wide reuse revocation.

---

# 10. Verification commands

```bash
# Backend — compile and full suite (139 tests, embedded PostgreSQL)
cd backend && ./gradlew clean build

# Android — all modules
cd android && ./gradlew test lintDebug assembleDebug

# Web — typecheck, tests, production bundle
cd web && npm ci && npx tsc --noEmit && npm run test && npm run build

# Full stack locally
docker compose -f infra/docker/docker-compose.yml up --build

# Confirm C-4 (outbox never relayed) — expect zero matches
grep -rn "@Scheduled\|EnableScheduling" backend/src/main

# Confirm C-5 (audit columns dropped) — expect no ip_address in the INSERT
grep -A12 "INSERT INTO audit_events" \
  backend/src/main/kotlin/com/fincore/shared/audit/AuditLogRepository.kt

# Confirm H-2 (web login payload) — expect no deviceId
grep -n "auth/login" -A4 web/src/context/AuthContext.tsx
```

---

*Findings are ordered by severity, not by file. Every code location cited was read in full; the backend compile was verified in this session. Test-suite execution and container builds were not run here — the commands above reproduce them.*
