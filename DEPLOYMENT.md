# DEPLOYMENT — FinCore 360

**Phase:** 0 — design intent. **Nothing is deployable.** Built in Phases 1 and 13.

> `NOT VERIFIED — no Dockerfile, Compose file, manifest, or deployment has ever
> been created or run.`

---

## 1. Local — Docker Compose

Target for the end of Phase 1: `docker compose up` brings up the backend and
PostgreSQL, migrations run, and `/actuator/health` returns 200.

Services: backend · PostgreSQL · Redis (Phase 3) · Kafka (Phase 7).

`PLANNED — not implemented.` Verified commands are written here once they have
actually been run, not before.

---

## 2. Configuration

**Rules:**

- Environment variables injected at deploy. **Never hardcoded, never committed.**
- No environment-specific URL in source.
- Placeholders in `.env.example`; real values from a secrets manager.
- Production configuration reviewed before every deployment.

`FM-INFRA-003` is what happens when this is violated: secrets are not injected,
the application silently falls back to a default, and it runs against the wrong
database. Startup must **fail** on a missing required secret rather than fall
back — a fallback that works is worse than a crash, because nobody notices.

---

## 3. Container images

- Multi-stage builds — no build toolchain in the runtime image
- Non-root user
- Explicit base image tags, never `latest`
- Trivy scan in CI, gating the push
- Health check defined in the image

`PLANNED — not implemented.`

---

## 4. Kubernetes — Phase 13

Deployment, Service, ConfigMap, Secret, HPA, Ingress. Helm charts for
environment parameterisation.

**Probes must be distinct** ([OBSERVABILITY.md](OBSERVABILITY.md) §4):

| Probe | Reflects |
|---|---|
| Startup | Application has finished starting — do not kill it yet |
| Readiness | Dependencies are reachable — safe to receive traffic |
| Liveness | Process is alive — **must not** check dependencies |

A liveness probe that fails on a brief database outage restarts every healthy pod
and converts a recoverable incident into an outage. This is the single most
common Kubernetes mistake in this shape of system.

`PLANNED — not implemented.`

---

## 5. Deployment procedure

```
1  CI green on all stages
2  Deploy to staging
3  Smoke tests against staging
4  Manual approval gate
5  Deploy to production (rolling)
6  Post-deployment health check
7  Rollback automatically on health check failure
```

### Rolling deploy constraint

During step 5, **old and new application versions run simultaneously against one
schema.** Therefore:

- Migrations must be backward compatible — expand-and-contract across two
  releases ([ADR-017](docs/adr/ADR-017-Flyway-Migrations.md))
- API changes must be additive within a version
- `FM-INFRA-005` is the failure this prevents: requests failing during rollout
  because the old version hits a schema it no longer matches

---

## 6. Rollback

| Layer | Procedure |
|---|---|
| Application | Redeploy the previous image tag |
| Schema | Run the migration's rollback script — **but** see below |
| Configuration | Revert and redeploy |

**The honest limitation.** Flyway Community has no automatic undo, so rollback
scripts are hand-written, and a script that has never been executed is not a
rollback plan. CI must actually run them. Further, a schema rollback after data
has been written under the new schema may be **impossible without data loss** —
which is the real reason migrations are expand-and-contract rather than
destructive. Rolling *forward* is usually the correct response to a bad
migration.

---

## 7. Android release

Signing keys in a secrets manager, never in the repository. R8/ProGuard verified.
AAB produced by CI. Play Store distribution is simulated only — this app is not
published.

---

## 8. Open items

| Item | Needed by |
|---|---|
| Dockerfile + Compose for backend and PostgreSQL | Phase 1 |
| `.env.example` with every required variable | Phase 1 |
| Fail-fast on missing required secrets | Phase 1 |
| Kubernetes manifests and Helm charts | Phase 13 |
| Migrations at startup vs a deploy job | Phase 13 |
| Smoke test suite for staging | Phase 13 |
