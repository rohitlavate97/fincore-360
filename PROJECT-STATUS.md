# PROJECT STATUS — FinCore 360

> This file is the single source of truth for what actually exists.
> It is updated after every significant change.
> If a capability is not listed under COMPLETED with a verification method,
> it does not exist.

**Phase:** 1 — Backend Foundation
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
| **Full test suite — 32 tests, 0 failures, 0 errors** | JUnit XML reports, clean build 2026-08-28 |
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

---

## IN PROGRESS

Phase 1 is functionally complete and verified except for the Docker-dependent
criterion below. Awaiting review before Phase 2.

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
| No CI pipeline | Phase 13 owns CI; a minimal build+test workflow is worth adding sooner | Phase 2 or 3 — all current results are from one machine |

---

## NOT VERIFIED

Phase 1 produced real, executed results; those are listed under COMPLETED with
the test that proves each. Everything in this section is **not** yet backed by
execution.

| Item | What needs verification |
|---|---|
| ADRs beyond 007, 012, 014, 015, 016, 017 | Those six now have partial code backing (schema constraints, `Money`, audit trigger, Kotlin/Jackson/Flyway in use). The rest still record *intent* only — no code exists to validate them against. |
| All 21 failure modes | Registered as IDs with no investigation content. Each is filled in as its owning component is built. |
| Every failure-mode stub's `Detection` / `Investigation` / `Fix` | Cannot be written honestly before the component exists and has been made to fail on purpose. |

### Phase 1 — what remains unverified

| Item | What needs verification |
|---|---|
| **Docker image** | `backend.Dockerfile` has never been built. Multi-stage layout, non-root user, and healthcheck are design intent only. |
| **Docker Compose stack** | `docker-compose.yml` has never been run. Service startup ordering, the `pg_isready` healthcheck gate, and container networking are all unexercised (`FM-INFRA-001`, `FM-INFRA-002`). |
| **PostgreSQL 18.6 specifically** | Tests run against the embedded server's PostgreSQL binary. The Compose file pins `postgres:18.6-alpine`; these have not been cross-checked. |
| Concurrency and locking | No `SELECT … FOR UPDATE` code exists yet — Phase 5. The non-negative balance *constraint* is verified; the *locking strategy* is not. |
| Security posture | No authentication, authorization, or security tests exist — Phase 3. Nothing has been scanned. |
| Performance | Nothing measured. No benchmark, no load test, no query plan reviewed. |
| CI | No pipeline exists. All results above are from local runs on one machine. |
| Structured JSON log **output** | The config is set, but no test asserts the emitted log line shape or that `correlationId` appears in it. Only the response header is tested. |

---

## PHASE LEDGER

| Phase | Name | State | Exit criteria |
|---|---|---|---|
| 0 | Architecture and Foundation | **Complete** — approved 2026-08-28 | Documentation complete, reviewed, approved |
| 1 | Backend Foundation | **Complete except Compose** — app starts, health 200, 32/32 tests pass; Docker criterion blocked | App starts, `/actuator/health` returns 200, tests pass, Compose stack up |
| 2 | Android Foundation | Not started | App builds, navigation works, Hilt injects |
| 3 | Authentication | Not started | Login E2E, refresh tested, 401 on anon, 403 on wrong role |
| 4 | Accounts | Not started | Paginated API, Android renders all four screen states |
| 5 | Transactions and Concurrency | Not started | Idempotency test passes; concurrent transfer preserves balance integrity |
| 6 | Offline and Sync | Not started | Cached data offline; sync restores correct state |
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
