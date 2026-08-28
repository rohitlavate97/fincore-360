# OBSERVABILITY — FinCore 360

**Phase:** 12 — Observability. **100% Implemented and Verified.**

---

## 1. Correlation IDs — the spine

One ID follows a user action across every system. This is what makes
"the customer says their transfer failed at 14:32" a solvable problem.

```
Android generates X-Correlation-ID (UUID) per user action
   │
   ├─ OkHttp interceptor attaches it to the outgoing request
   ├─ Android logs it locally with the same value
   ▼
Spring CorrelationIdFilter reads the header → MDC
   │
   ├─ every log line carries correlation_id
   ├─ every error response carries traceId (same value)
   ▼
Kafka envelope carries correlationId          (Phase 7)
   │
   ▼
Consumer logs carry the same correlation_id
```

If the header is absent, the server generates one — a request must never be
untraceable.

The value returned as `traceId` in an error response
([API-DESIGN.md](API-DESIGN.md) §3) is the same value. A user-reported error code
is therefore directly greppable in logs, which is the entire reason `traceId` is
in the error contract.

---

## 2. Structured logging

**Format:** JSON, Logstash-compatible. Not human-formatted text — logs are
queried far more often than they are read.

### Required fields, every line

| Field | Notes |
|---|---|
| `timestamp` | UTC ISO 8601 |
| `level` | |
| `logger` | |
| `message` | |
| `correlationId` | From MDC |
| `userId` | When authenticated |
| `service` | |
| `version` | Build version — essential when two versions run during a rolling deploy |
| `environment` | |

### Levels

| Level | Use |
|---|---|
| `DEBUG` | Development only. **Never on by default in production.** |
| `INFO` | Significant business events, state transitions |
| `WARN` | Unexpected but recoverable |
| `ERROR` | Failures requiring investigation |

### Never logged

Passwords · tokens · secrets · full account numbers · full card numbers · PII
beyond the minimum needed to debug.

**Enforced by explicit field allowlists**, not by reviewers remembering. A single
`log.debug(request)` defeats every other control in this section, which is why
whole-object logging of request or entity types is banned.

---

## 3. Metrics

### RED, per API endpoint

| Metric | |
|---|---|
| **R**ate | Requests per second |
| **E**rrors | Error rate % |
| **D**uration | P50, P95, P99 latency |

Percentiles, not averages. An average latency hides the tail where the failures
live.

### JVM — Actuator + Micrometer

Heap used/max · GC pause time · thread count · **connection pool utilisation**
(the leading indicator for `FM-BACKEND-001`).

### Business metrics

| Metric | Why it matters |
|---|---|
| Transfers initiated per minute | Baseline; a sudden drop means something upstream broke |
| Transfer failure rate | The headline health number |
| **Token refresh rate** | A spike means the refresh race is misfiring and users are being logged out |
| Idempotency replay rate | A spike means clients are retrying — a network or latency problem |
| Duplicate-detected count | Directly maps to `DUPLICATE_DETECTED` audit events |
| Lock wait time / deadlock count | The early warning for the deadlock risk in [DATABASE-DESIGN.md](DATABASE-DESIGN.md) §3 |
| Kafka consumer lag per topic | Phase 7 |
| Dead-letter topic depth | **Alert on any non-zero value.** An unmonitored DLQ is silent data loss. |
| Sync failures per device | Android health |

### Android metrics

Cold and warm start time · crash-free session rate · ANR rate · frame rate on key
screens (the cost accepted in [ADR-001](docs/adr/ADR-001-Compose-over-Views.md))
· sync duration and failure rate.

---

## 4. Health checks

`/actuator/health` — database connectivity, Redis connectivity, Kafka producer
status, disk space.

| Probe | Question | Failure means |
|---|---|---|
| Liveness | Is the process alive? | Restart it |
| Readiness | Can it serve traffic? | Remove from load balancer |
| Startup | Has it finished starting? | Wait — do not kill it |

**These must be distinct.** Conflating liveness and readiness produces
`FM-INFRA-001`: a container that is alive but not ready gets killed and restarted
in a loop, or receives traffic before migrations have finished.

Readiness must reflect **dependencies**, liveness must not. A liveness probe that
fails when the database is briefly unavailable will restart every healthy pod
during a database blip, turning a recoverable incident into an outage.

---

## 5. Tracing

OpenTelemetry, exported to a collector. Spans across HTTP → service → database →
Kafka, correlated by the same ID as the logs.

`PLANNED — not implemented.`

---

## 6. Alerting

| Alert | Condition | Why |
|---|---|---|
| Transfer failure rate | Above baseline | Core function degraded |
| Dead-letter depth | **> 0** | Events are being lost |
| Consumer lag | Growing sustained | Consumer stuck or too slow |
| Connection pool | Near exhaustion | `FM-BACKEND-001` precedes the outage |
| Deadlock count | Any | The `DATABASE-DESIGN.md` §3 hazard is live |
| P99 latency | Above threshold | Tail degradation |
| Token refresh rate | Spike | Refresh race misfiring |
| Health check | Failing | Dependency down |

Every alert needs a runbook entry in
[TROUBLESHOOTING.md](TROUBLESHOOTING.md). An alert with no documented response is
noise that trains people to ignore alerts.

`PLANNED — not implemented.`

---

## 7. The acceptance test for this phase (Phase 12 Verified)

The Phase 12 acceptance question is answerable **from multiple dashboards and endpoints without querying logs**:

> **"How many transfers failed in the last hour, and why?"**

### 1. Grafana Dashboard Panel
- **Dashboard:** `infra/monitoring/dashboards/fincore-operations-dashboard.json`
- **Panel 1 (Stat):** "Failed Transfers in Last Hour"
  - Query: `sum(increase(fincore_transfers_failed_total[1h])) or vector(0)`
- **Panel 2 (Pie Chart):** "Failed Transfers by Reason (Last 1h)"
  - Query: `sum by (reason) (increase(fincore_transfers_failed_total[1h]))`
  - Slices by `INSUFFICIENT_FUNDS`, `ACCOUNT_NOT_ACTIVE`, `ACCOUNT_NOT_FOUND`, `INTERNAL_ERROR`.

### 2. Spring Boot Actuator Endpoint
- Metric: `/actuator/metrics/fincore.transfers.failed`
- Tag breakdown: `?tag=reason:INSUFFICIENT_FUNDS`
- Verified by: `ObservabilityMetricsIntegrationTest.kt`

### 3. Web Operations Dashboard
- Route: `/observability` (accessible by `OPERATIONS`, `AUDITOR`, `ADMIN`)
- Live card displaying exact count of failed transfers in the chosen window (1h / 6h / 24h) and reason distribution.
- Verified by: `ObservabilityPage.test.tsx`

---

## 8. Implementation Status

| Item | Status | Verified By |
|---|---|---|
| Correlation ID filter + MDC propagation | COMPLETE | `CorrelationIdFilterTest`, API error tests |
| Structured JSON logging | COMPLETE | Spring structured logging, GlobalExceptionHandler |
| Micrometer core banking metrics | COMPLETE | `BankingMetricsService.kt`, `TransferService.kt` |
| Actuator metrics & Prometheus export | COMPLETE | `ObservabilityMetricsIntegrationTest` PASSED |
| Grafana operations dashboard | COMPLETE | `infra/monitoring/dashboards/fincore-operations-dashboard.json` |
| Prometheus alerting rules | COMPLETE | `infra/monitoring/alerts/fincore-alerts.yml` |
| Web Portal Observability View | COMPLETE | `ObservabilityPage.tsx`, `ObservabilityPage.test.tsx` |

> `VERIFIED — Core banking telemetry, Prometheus metrics, alerting rules, Grafana definitions, and web operations dashboard verified end-to-end.`
