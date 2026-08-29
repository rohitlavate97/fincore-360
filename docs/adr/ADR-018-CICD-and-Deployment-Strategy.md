# ADR-018: CI/CD Pipeline, Multi-Environment Containerization, and Deployment Strategy

## Status

Accepted

## Date

2026-08-29

## Context

FinCore 360 is an enterprise digital banking platform comprising a Spring Boot 4 / Kotlin modular backend, a 16-module Jetpack Compose Android client, and a React 19 / TypeScript operations web portal. 

In financial services platforms, deployment and continuous integration are subject to stringent regulatory, availability, and safety constraints:
1. **Zero-Downtime Rolling Deployments**: During a rolling update, older and newer application pods run simultaneously against the database. Backward incompatible schema changes (e.g., dropping or renaming columns abruptly) cause fatal runtime errors (`FM-INFRA-005`).
2. **Cascading Failure Prevention (Probe Decoupling)**: In Kubernetes, if an application's liveness probe checks downstream external dependencies (such as PostgreSQL), a transient database hiccup or failover causes Kubernetes to restart every healthy pod simultaneously, transforming a recoverable dependency latency spike into a total platform outage.
3. **Least Privilege & Container Hardening**: Images must not run as root, must use multi-stage builds to exclude compilation toolchains, must drop all Linux capabilities, and must enforce read-only root filesystems.
4. **Automated Verification and Fast Rollback**: Deployments must pass automated post-rollout smoke tests against live health and metrics endpoints, and must trigger immediate, automated zero-downtime rollbacks if smoke tests fail.

## Decision

We have established a comprehensive DevOps and continuous delivery architecture across four integrated pillars:

### 1. Multi-Pipeline GitHub Actions Workflows (`.github/workflows/`)
- **Backend Pipeline (`backend-ci.yml`)**: 6 stages:
  - `Stage 1 · Validate`: Gradle wrapper verification, Kotlin compilation, dependency conflict check.
  - `Stage 2 · Test & Coverage`: Full JUnit 5 test execution, JaCoCo report generation, and automated coverage threshold enforcement (86% instruction, 92% line coverage).
  - `Stage 3 · Security Analysis`: Gitleaks secret detection, OWASP dependency vulnerability review.
  - `Stage 4 · Build & Container Scan`: Docker Buildx multi-stage build, Trivy vulnerability scanning (blocking on CRITICAL/HIGH severity).
  - `Stage 5 · Artifact Packaging`: Production BootJar packaging and artifact persistence.
  - `Stage 6 · Staging Deploy & Smoke Test`: Automated deployment to staging cluster, pre-flight migration check, and live smoke test gate.
- **Android Pipeline (`android-ci.yml`)**: 5 stages:
  - Validate (Lint with `warningsAsErrors`), Test (unit tests across all 16 modules), Build (Debug & Release APK with R8/ProGuard verification), Security (Secret detection, release logging interceptor audit), Artifact (APK/AAB upload).
- **Web Portal Pipeline (`web-ci.yml`)**: 4 stages:
  - Validate & Typecheck (Node 22, `tsc --noEmit`), Vitest suite (19 tests), Vite production build, Nginx container scan with Trivy.
- **Staging Deployment & Rollback Gate (`staging-deploy.yml`)**:
  - Pre-flight Flyway backward-compatibility validation.
  - Deployment rollout execution.
  - Automated smoke test execution (`infra/scripts/smoke-test.sh`).
  - Automated zero-downtime rollback execution (`infra/scripts/rollback.sh`) upon test failure.

### 2. Multi-Stage Production Containerization (`infra/docker/`)
- **Backend Image (`backend.Dockerfile`)**:
  - Multi-stage build (`eclipse-temurin:25-jdk-alpine` builder → `eclipse-temurin:25-jre-alpine` runtime).
  - Unprivileged user `fincore` (UID/GID 10001).
  - Container-aware memory allocation (`-XX:MaxRAMPercentage=75.0`, `-XX:+ExitOnOutOfMemoryError`).
  - Health check wired to `/actuator/health/readiness`.
- **Web Image (`web.Dockerfile`)**:
  - Multi-stage build (`node:22-alpine` builder → `nginx:1.27-alpine` runtime).
  - Unprivileged user `nginx`.
  - Hardened Nginx configuration (`nginx.conf`) with OWASP security headers (CSP, HSTS, X-Frame-Options DENY, nosniff, Referrer-Policy, Permissions-Policy).
  - Reverse proxy pass for `/api/` and `/actuator/` with `X-Correlation-ID` forwarding.
- **Docker Compose Stack (`docker-compose.yml`)**:
  - Complete local orchestration: PostgreSQL 18.6, Backend, Web Portal, Prometheus 2.54, Grafana 11.1 with pre-provisioned banking dashboards.

### 3. Production Kubernetes Manifests & Helm Chart (`infra/k8s/`, `infra/helm/`)
- Pod Security Standards enforced: `pod-security.kubernetes.io/enforce: restricted`.
- **Decoupled Probes**:
  - `startupProbe`: `/actuator/health/liveness` (allows up to 100s initialization without pod termination).
  - `readinessProbe`: `/actuator/health/readiness` (checks PostgreSQL; removes pod from endpoint routing if DB is unreachable).
  - `livenessProbe`: `/actuator/health/liveness` (strictly decoupled from PostgreSQL; checks process health only to prevent cascading restart storms).
- Multi-replica Deployment (3 replicas) with RollingUpdate (`maxSurge: 1`, `maxUnavailable: 0`).
- HorizontalPodAutoscaler (`hpa.yaml`) scaling 3 to 10 replicas based on CPU/memory utilization.
- Zero-trust NetworkPolicy segmenting ingress/egress.
- Pre-deploy Flyway migration Job hook.
- Parameterized Helm chart with environment-specific overrides (`values-staging.yaml`, `values-prod.yaml`).

### 4. Infrastructure as Code (`infra/terraform/`)
- Modular AWS production topology: dedicated VPC, public/private/database subnets across 3 Availability Zones, NAT Gateways, EKS managed cluster, Multi-AZ KMS-encrypted RDS PostgreSQL, and versioned/encrypted S3 audit log archive.

## Consequences

### Positive
- **Guaranteed Zero-Downtime Rollouts**: RollingUpdate with backward-compatible migrations ensures old and new versions coexist safely during deployments.
- **Cascading Outage Immunity**: Strictly decoupled liveness probes ensure database latency or transient failovers do not trigger pod restart loops.
- **Automated Quality and Security Gates**: Secret scanning, container vulnerability scanning, and automated coverage verification run on every commit.
- **Instant Rollback Safety**: Automated smoke tests detect deployment defects immediately and trigger zero-downtime rollbacks before customer traffic is impacted.

### Trade-offs
- Maintaining both raw Kubernetes manifests (for Kustomize/GitOps) and Helm charts requires synchronizing configuration changes across both templates.
