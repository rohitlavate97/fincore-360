# PERFORMANCE — FinCore 360

**Phase:** 0.

> `NOT VERIFIED — nothing has been measured. There are no benchmarks, no load
> tests, and no profiling data. This document contains zero performance claims,
> deliberately.`

This file stays almost empty until Phase 12. Populating it with predicted numbers
would be exactly the fabrication the project exists to avoid — a predicted P99 is
not a P99.

---

## 1. What will be measured

Recorded now so the instrumentation is designed for it, not retrofitted.

### Backend

| Metric | Why |
|---|---|
| P50 / P95 / P99 latency per endpoint | Averages hide the tail where failures live |
| Transfer throughput under contention | The interesting number is with concurrent transfers on the **same** account |
| Lock wait time and deadlock count | Directly measures the [DATABASE-DESIGN.md](DATABASE-DESIGN.md) §3 hazard |
| Connection pool utilisation | Leading indicator for `FM-BACKEND-001` |
| Query plans on large tables | Every query touching `transactions` or `audit_events` |
| Idempotency overhead | The extra write + lookup on every mutation ([ADR-010](docs/adr/ADR-010-Idempotency-Strategy.md)) |
| `BigDecimal` vs primitive arithmetic cost | The accepted cost in [ADR-012](docs/adr/ADR-012-Monetary-Representation.md) — worth quantifying |

### Android

| Metric | Why |
|---|---|
| Cold and warm start time | |
| Frame rate on the transaction list under scroll | The recomposition cost accepted in [ADR-001](docs/adr/ADR-001-Compose-over-Views.md) |
| Sync duration and failure rate | |
| Room query time on a large history | |
| APK/AAB size | |

---

## 2. Design decisions with known performance cost

Accepted deliberately. Listed so they are not later "discovered" as regressions.

| Decision | Cost | Why accepted |
|---|---|---|
| `BigDecimal` / `NUMERIC` | Slower and allocating vs primitives | Correctness is not negotiable for money |
| Pessimistic row locks on balances | **Serialises throughput on hot accounts** | Application locks do not span replicas |
| Idempotency record per mutation | Extra write + lookup on the hot path | Prevents duplicate transfers |
| Room write-then-observe | Extra hop before the UI updates | Single source of truth, offline reads |
| Audit write inside the transaction | Adds latency to every transfer | Losing audit records is worse |
| Three model representations (Android) | Mapping cost | Layer independence ([ADR-002](docs/adr/ADR-002-Clean-Architecture.md)) |

---

## 3. Performance requirements

Not yet defined. Defining targets before there is a system to measure produces
numbers chosen for how they sound.

Targets will be set in Phase 12 from an observed baseline.

---

## 4. Known optimisation opportunities — not yet needed

Recorded, not implemented. Premature optimisation against unmeasured code is how
correctness gets traded for nothing.

| Opportunity | Trigger |
|---|---|
| Keyset pagination on transaction history | **Already decided** — offset is incorrect here, not merely slow ([DATABASE-DESIGN.md](DATABASE-DESIGN.md) §6) |
| Covering indexes for the history query | When `EXPLAIN` shows heap fetches dominating |
| Read replicas for audit and reporting queries | When auditor queries affect transactional latency |
| Audit table partitioning by month | When volume demands it |
| Baseline Profiles (Android) | When cold start is measured and unacceptable |
| Redis caching of reference data | When the read path is measured as hot |

Each of these is a hypothesis. None will be implemented without a measurement
that justifies it.

---

## 5. Results

`PLANNED — not implemented.`

Benchmark results, load test output, and profiling data go here in Phase 12,
with the methodology and the environment recorded alongside every number. A
figure without its conditions is not a measurement.
