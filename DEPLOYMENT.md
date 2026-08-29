# DEPLOYMENT — FinCore 360

**Phase:** 13 — DevOps and CI/CD. **Complete & Verified.**
Documented in [ADR-018](docs/adr/ADR-018-CICD-and-Deployment-Strategy.md).

---

## 1. Local — Docker Compose Stack

Full orchestration defined in [`infra/docker/docker-compose.yml`](infra/docker/docker-compose.yml):

```bash
cd infra/docker
docker compose up -d
```

| Service | Image / Build Context | Port | Health Check |
|---|---|---|---|
| `postgres` | `postgres:18.6-alpine` | `5432` | `pg_isready -U fincore -d fincore` |
| `backend` | `infra/docker/backend.Dockerfile` | `8080` | `wget http://localhost:8080/actuator/health/readiness` |
| `web` | `infra/docker/web.Dockerfile` | `3000` (mapped to `80`) | `wget http://localhost:80/health` |
| `prometheus` | `prom/prometheus:v2.54.0` | `9090` | Internal Prometheus health |
| `grafana` | `grafana/grafana:11.1.0` | `3001` (mapped to `3000`) | Internal Grafana health |

---

## 2. Configuration & Secrets Management

- Injected via environment variables or Kubernetes Secrets / ConfigMaps.
- `.env.example` lists every required environment variable with documented defaults.
- Production secrets managed via external Key Management Services (AWS KMS, HashiCorp Vault).
- Database credentials and RS256 JWT key pairs are never committed to version control.

---

## 3. Container Images & Security Hardening

### Backend Image ([`infra/docker/backend.Dockerfile`](infra/docker/backend.Dockerfile))
- Multi-stage build: `eclipse-temurin:25-jdk-alpine` builder → `eclipse-temurin:25-jre-alpine` runtime.
- Runs as unprivileged user `fincore` (non-root).
- JVM memory container-aware limits: `-XX:MaxRAMPercentage=75.0` and `-XX:+ExitOnOutOfMemoryError`.
- Readiness probe wired directly into image definition.
- Scanned with Aqua Security Trivy in CI pipeline.

### Web Image ([`infra/docker/web.Dockerfile`](infra/docker/web.Dockerfile))
- Multi-stage build: `node:22-alpine` builder → `nginx:1.27-alpine` runtime.
- Runs as unprivileged user `nginx`.
- Hardened [`nginx.conf`](infra/docker/nginx.conf) with strict OWASP security headers (CSP, HSTS, X-Frame-Options DENY, nosniff, Referrer-Policy, Permissions-Policy).
- Reverse proxy forwards `/api/` and `/actuator/` requests to `backend:8080` with correlation ID propagation.

---

## 4. Kubernetes Manifests ([`infra/k8s/`](infra/k8s/))

Production manifests validated against Kubernetes 1.31+ specifications:

| Manifest | Purpose | Security & Resilience |
|---|---|---|
| [`namespace.yaml`](infra/k8s/namespace.yaml) | `fincore` namespace | `pod-security.kubernetes.io/enforce: restricted` |
| [`backend-deployment.yaml`](infra/k8s/backend-deployment.yaml) | Backend Deployment (3 replicas) | RollingUpdate (`maxSurge: 1, maxUnavailable: 0`), non-root, read-only root |
| [`web-deployment.yaml`](infra/k8s/web-deployment.yaml) | Web Deployment (2 replicas) | RollingUpdate, non-root `nginx`, read-only root |
| [`hpa.yaml`](infra/k8s/hpa.yaml) | Horizontal Pod Autoscaler | 3 to 10 replicas based on CPU (70%) and Memory (80%) |
| [`ingress.yaml`](infra/k8s/ingress.yaml) | NGINX Ingress Controller | TLS termination, path routing (`/api` → backend, `/` → web) |
| [`migration-job.yaml`](infra/k8s/migration-job.yaml) | Flyway Pre-Deploy Job | Runs migrations in isolated container prior to pod rollout |
| [`network-policy.yaml`](infra/k8s/network-policy.yaml) | Zero-Trust Network Policy | Default deny ingress; allows strictly ingress → web/backend and backend → DB |

### Critical Health Probe Decoupling Invariant ([OBSERVABILITY.md](OBSERVABILITY.md) §4)
- **Startup Probe (`/actuator/health/liveness`)**: Delays failure counts until JVM has finished JIT compilation and startup.
- **Readiness Probe (`/actuator/health/readiness`)**: Verifies PostgreSQL connection; safely removes pod from routing endpoints if DB is momentarily unreachable.
- **Liveness Probe (`/actuator/health/liveness`)**: Verifies ONLY the process lifecycle; **strictly decoupled from external databases** to prevent catastrophic cascading restart storms.

---

## 5. Helm Chart ([`infra/helm/fincore-360/`](infra/helm/fincore-360/))

Parameterized package for multi-environment deployments:
- `values.yaml`: Base development configurations.
- `values-staging.yaml`: Staging cluster environment overrides.
- `values-prod.yaml`: Production HA overrides (3+ replicas, multi-AZ, TLS certs, strict autoscaling policies).
- Pre-install and pre-upgrade hooks for automated schema migration (`migration-job.yaml`).

---

## 6. Cloud Infrastructure as Code ([`infra/terraform/`](infra/terraform/))

AWS Enterprise Multi-AZ topology:
- Dedicated VPC with public, private app, and private database subnets across 3 Availability Zones.
- Managed Amazon EKS cluster with private worker node group.
- Amazon RDS Multi-AZ PostgreSQL 16 instance encrypted at rest with AWS KMS Customer Managed Keys.
- Versioned, encrypted Amazon S3 bucket with strict public access blocks for immutable audit log archives.

---

## 7. Deployment Procedure & Automated Rollback

```
1  CI Green across all stages (Backend, Android, Web)
2  Pre-deploy Flyway schema migration job executes
3  Zero-downtime rolling update deployed to Staging
4  Automated Smoke Test Suite executes (infra/scripts/smoke-test.sh)
5  Automated Rollback Gate:
   ├── IF Smoke Test FAILS → infra/scripts/rollback.sh executed automatically
   └── IF Smoke Test PASSES → Staging healthy, ready for manual approval gate
6  Manual approval gate for Production deployment
```

---

## 8. Resolution of Open Items

| Item | Resolution | Verification |
|---|---|---|
| Dockerfile + Compose for full stack | `backend.Dockerfile`, `web.Dockerfile`, `docker-compose.yml`, Prometheus, Grafana | Verified file definitions |
| `.env.example` with every required variable | Updated with DB, App, Web, and Observability ports/credentials | Verified `.env.example` |
| Kubernetes manifests and Helm charts | `infra/k8s/` (12 manifests) and `infra/helm/fincore-360/` (14 templates/values) | Syntax & schema verified |
| Database migrations in pipeline | Decoupled pre-deploy Kubernetes Job (`migration-job.yaml`) | Verified Helm hook |
| Staging smoke test suite | `infra/scripts/smoke-test.sh` and `smoke-test.ps1` | Functional HTTP assertion suite |
| Automated rollback procedure | `infra/scripts/rollback.sh` and `rollback.ps1` | `kubectl rollout undo` rollback engine |
