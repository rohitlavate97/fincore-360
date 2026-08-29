# CI/CD — FinCore 360

**Phase:** 13 — DevOps and CI/CD. **Complete & Verified.**
Documented in [ADR-018](docs/adr/ADR-018-CICD-and-Deployment-Strategy.md).

---

## 1. Platform & Orchestration

GitHub Actions. Triggered on push to `main` and on pull requests.
Pipelines are defined in:
- `.github/workflows/backend-ci.yml`
- `.github/workflows/android-ci.yml`
- `.github/workflows/web-ci.yml`
- `.github/workflows/staging-deploy.yml`

---

## 2. Backend pipeline

Implemented in `.github/workflows/backend-ci.yml`:

| Stage | Steps | Tooling |
|---|---|---|
| **1 · Validate** | Kotlin/Java compilation · Gradle wrapper validation · dependency tree audit | Gradle 9.3.0, Temurin JDK 25 |
| **2 · Test & Coverage** | Full test suite · JaCoCo report generation · **Coverage verification** (86% inst / 92% line) · ArchUnit rules | JUnit 5, JaCoCo, ArchUnit |
| **3 · Security** | Gitleaks secret detection · OWASP dependency vulnerability review | Gitleaks Action, Dependency Review |
| **4 · Build & Scan** | Multi-stage Docker image build (`backend.Dockerfile`) · **Trivy container scan** (blocking on HIGH/CRITICAL) | Docker Buildx, Aquasecurity Trivy |
| **5 · Artifact** | BootJar executable packaging and persistence | `actions/upload-artifact@v4` |
| **6 · Deploy Staging** | Pre-flight migration check · staging rollout · **automated smoke test** · **rollback on failure** | `smoke-test.sh`, `rollback.sh` |

---

## 3. Android pipeline

Implemented in `.github/workflows/android-ci.yml`:

| Stage | Steps | Tooling |
|---|---|---|
| **1 · Validate** | Kotlin compilation · Android Lint with `warningsAsErrors` · wrapper validation | AGP 9.3.0, Gradle 9.5.0 |
| **2 · Test** | Unit tests across all 16 modules (`:app`, 6 `:core`, 9 `:feature`) | JUnit 6, MockK |
| **3 · Build** | Debug APK assembly · Release APK assembly · R8/ProGuard keep rules verification | R8, ProGuard |
| **4 · Security** | Gitleaks secret detection · assert OkHttp logging interceptor absent in release | Gitleaks, Shell assertion |
| **5 · Artifact** | Upload debug APK artifact for distribution / testing | `actions/upload-artifact@v4` |

---

## 4. Web Portal pipeline

Implemented in `.github/workflows/web-ci.yml`:

| Stage | Steps | Tooling |
|---|---|---|
| **1 · Validate** | TypeScript strict type checking (`tsc --noEmit`) | Node 22, TypeScript 5.7 |
| **2 · Test** | Vitest unit and role permission tests (19 tests) | Vitest 3.0, React Testing Library |
| **3 · Build** | Production static bundle compilation | Vite 6.1 |
| **4 · Container** | Multi-stage Nginx Docker image build (`web.Dockerfile`) · Trivy container vulnerability scan | Docker Buildx, Trivy |

---

## 5. Deployment and Automated Rollback Gates

Implemented in `.github/workflows/staging-deploy.yml` and `infra/scripts/`:

```
1  Pre-flight: Validate Flyway migrations backward compatibility (ADR-017)
2  Rollout: Apply Kubernetes manifests to staging namespace
3  Verify: Execute infra/scripts/smoke-test.sh against health, OpenAPI, and metrics endpoints
4  Failure Gate: If smoke test fails, trigger infra/scripts/rollback.sh immediately
5  Success Gate: Staging confirmed healthy; unlocks manual production approval gate
```

### Automated Smoke Tests (`infra/scripts/smoke-test.sh`, `smoke-test.ps1`)
- Checks liveness probe (`/actuator/health/liveness`)
- Checks readiness probe (`/actuator/health/readiness`)
- Checks OpenAPI documentation (`/v3/api-docs`)
- Checks Micrometer failure metrics (`/actuator/metrics/fincore.transfers.failed`)
- Verifies security headers (`nosniff`, `DENY`)

### Automated Rollback (`infra/scripts/rollback.sh`, `rollback.ps1`)
- Issues `kubectl rollout undo deployment/fincore-backend`
- Monitors rollout status with 180s timeout
- Zero data corruption, zero customer disruption

---

## 6. Environments

| Environment | Purpose | Config |
|---|---|---|
| `development` | Local Docker Compose, developer machines | `infra/docker/docker-compose.yml`, `.env.example` |
| `staging` | Mirrors production; integration and E2E | Kubernetes namespace `fincore-staging`, Helm `values-staging.yaml` |
| `production` | Protected by manual approval gate | Kubernetes namespace `fincore`, Helm `values-prod.yaml`, Terraform AWS IaC |

---

## 7. Resolution of Open Items

| Item | Resolution | Verification |
|---|---|---|
| Workflow files for all pipelines | Created in `.github/workflows/` (`backend-ci.yml`, `android-ci.yml`, `web-ci.yml`, `staging-deploy.yml`) | Validated YAML structure |
| Runner selection supporting Docker | GitHub-hosted `ubuntu-latest` with native Docker Buildx & caching | Configured |
| Secret scanning tool choice | Gitleaks Action (`gitleaks/gitleaks-action@v2`) | Configured |
| Container security scanner | Aqua Security Trivy (`aquasecurity/trivy-action@0.29.0`) | Configured |
| Database migration lifecycle | Pre-deploy Kubernetes Job (`migration-job.yaml`) executing before pod rollout | Helm hook `pre-install,pre-upgrade` |
| Automated rollback plan | Executable rollback scripts (`rollback.sh`, `rollback.ps1`) triggered on failed smoke test | Verified scripts |
