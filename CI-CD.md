# CI/CD — FinCore 360

**Phase:** 0 — design intent. **No pipeline exists. Nothing has ever run.**
Built in Phase 13.

> `NOT VERIFIED — there is no CI configuration, no workflow file, and no
> pipeline run. No stage below has executed.`

---

## 1. Platform

GitHub Actions. Triggered on push to `main` and on every pull request.

---

## 2. Backend pipeline

| Stage | Steps |
|---|---|
| **1 · Validate** | Kotlin compilation · Ktlint · dependency conflict check |
| **2 · Test** | Unit · repository (Testcontainers) · controller (MockMvc) · integration (Testcontainers) · **security tests** · ArchUnit |
| **3 · Security** | OWASP Dependency Check · secret detection · static analysis (Detekt / SonarQube) |
| **4 · Build** | Gradle build · Docker image · container scan (Trivy) |
| **5 · Artifact** | Push to container registry |
| **6 · Deploy** | Staging → smoke tests → **manual approval gate** → production → post-deploy health check → **rollback on health check failure** |

Stage 2 requires a Docker daemon for Testcontainers — a real constraint on runner
selection, and the usual reason this stage is quietly replaced with H2. It must
not be.

---

## 3. Android pipeline

| Stage | Steps |
|---|---|
| **1 · Validate** | Kotlin compilation · Lint with `warningsAsErrors` · dependency licence check |
| **2 · Test** | Unit tests · integration tests (Robolectric) |
| **3 · Build** | Debug + release APK · R8/ProGuard verification |
| **4 · Security** | Dependency vulnerability scan · **secret detection** · assert the logging interceptor is absent from release |
| **5 · Artifact** | AAB for distribution (Play Store simulation) |

R8 verification matters beyond size: a missing keep rule on a serialised type
produces a release-only crash that debug builds never show.

---

## 4. Gates

A pull request cannot merge unless:

- [ ] Compilation succeeds on both pipelines
- [ ] All test categories pass — including failure-scenario and security tests
- [ ] No secret detected
- [ ] No new high or critical dependency vulnerability
- [ ] ArchUnit boundary rules pass
- [ ] `PROJECT-STATUS.md` updated
- [ ] Any `NOT VERIFIED` items listed explicitly in the PR description

The last two are process, not tooling, and are the ones that decay first.

---

## 5. Environments

| Environment | Purpose | Config |
|---|---|---|
| `development` | Local Docker Compose, developer machines | Local `.env`, never committed |
| `staging` | Mirrors production; integration and E2E | Injected secrets |
| `production` | Protected by manual approval gate | Injected secrets, reviewed before each deploy |

**Rules:**

- Never hardcode environment-specific URLs or config
- Never commit secrets — placeholder plus secrets manager
- **Each environment has its own database.** Never shared, and never a copy of
  another environment's data without scrubbing.
- Production configuration is reviewed before every deployment

---

## 6. Migrations in the pipeline

Per [ADR-017](docs/adr/ADR-017-Flyway-Migrations.md):

- CI runs all migrations from empty against Testcontainers PostgreSQL
- Migrations are tested against production-like data before deploying
- Rollback scripts are **executed** in CI, not merely present — an untested
  rollback script is not a rollback plan
- Migrations must be **backward compatible**: during a rolling deploy the old and
  new application versions share one schema, so a dropped or renamed column
  breaks the version still running. Expand-and-contract across two releases.

---

## 7. Open items

| Item | Needed by |
|---|---|
| Workflow files for both pipelines | Phase 13 (minimal build+test earlier, Phase 1) |
| Runner selection supporting Docker-in-Docker | Phase 1 |
| Secret scanning tool choice | Phase 1 |
| Container registry | Phase 13 |
| Signing keys for release artifacts | Phase 13 |
| Whether migrations run at startup or as a deploy job | Phase 13 |
