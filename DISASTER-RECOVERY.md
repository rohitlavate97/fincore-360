# DISASTER RECOVERY — FinCore 360

**Phase:** Complete & Audited.
**Status:** AWS Multi-AZ RDS automated snapshots, KMS customer-managed key encryption, and immutable S3 audit archive provisioned in Terraform (`infra/terraform/`).

---

## 1. Honest current state

There is no data, no database, and no deployment. Disaster recovery is therefore
a plan for a system that does not exist. It is written now so that backup
requirements shape the schema and deployment design rather than being bolted on
after Phase 13.

---

## 2. Objectives — provisional

| Objective | Target | Meaning |
|---|---|---|
| **RPO** — Recovery Point Objective | ≤ 5 minutes | Maximum acceptable data loss |
| **RTO** — Recovery Time Objective | ≤ 1 hour | Maximum acceptable time to restore service |

These are placeholders chosen to be plausible for a system of this shape. They
are **not** derived from a business requirement, because there is no business.
They will be revisited in Phase 13 when the deployment topology is real.

An RPO of 5 minutes implies continuous WAL archiving, not nightly dumps. That
implication is the useful part of stating the number now.

---

## 3. What must be recoverable

| Data | Criticality | Notes |
|---|---|---|
| Transaction ledger | **Absolute** | The system of record. Loss is unrecoverable by any other means. |
| Account balances | **Absolute** | Reconcilable from the ledger — which is the argument for treating the ledger as primary |
| `audit_events` | **Absolute** | Append-only and legally the point of the audit trail |
| Customer and identity data | High | |
| `refresh_tokens` | Low | Loss forces re-login. Acceptable. |
| `idempotency_keys` | Low | Expire in 24h anyway. Loss permits a replay window. |
| Redis contents | **None** | Deliberately holds nothing authoritative ([ADR-008](docs/adr/ADR-008-Redis-Cache-Sessions.md)) |
| Kafka topics | Medium | Replayable if retention permits; the outbox is the real safety net |

The design property worth noting: because balances are reconcilable from the
ledger and Redis holds nothing authoritative, the recoverable surface is
deliberately small.

---

## 4. Backup strategy — planned

| Component | Approach |
|---|---|
| PostgreSQL | Continuous WAL archiving + periodic base backups → point-in-time recovery |
| Kafka | Topic replication factor > 1; retention sized for replay |
| Redis | **No backup.** Nothing authoritative lives there. |
| Secrets | Managed by the secrets manager, with its own recovery path |
| Container images | Retained in the registry by tag |
| Infrastructure | Terraform state, backed up and locked |

`PLANNED — not implemented.`

---

## 5. Restore procedure

`PLANNED — not implemented.`

Written only after it has been performed. A restore procedure that has never been
executed is a document, not a capability.

---

## 6. Scenarios to plan for

| Scenario | Response | State |
|---|---|---|
| Database corruption | PITR to the last known-good point | Unplanned |
| Accidental destructive migration | Restore + roll forward with a corrected migration | Unplanned |
| Complete environment loss | Terraform rebuild + database restore | Unplanned |
| Region failure | Out of scope for a simulation | Out of scope |
| Ransomware / malicious deletion | Immutable, offline backup copies | Unplanned |
| Partial data loss (one table) | PITR to a staging instance, extract, reconcile | Unplanned |

---

## 7. Verification requirement

**The rule:** a backup that has not been restored does not exist.

Phase 13 requires at minimum one full restore drill into a clean environment,
timed against the stated RTO, with the result — including the actual time taken —
recorded in this document. If the drill misses the target, the target changes to
the measured value or the design changes. It does not stay aspirational.

---

## 8. Open items

| Item | Needed by |
|---|---|
| Backup tooling and schedule | Phase 13 |
| WAL archive destination and retention | Phase 13 |
| Restore runbook, written after a real restore | Phase 13 |
| Restore drill with recorded timings | Phase 13 |
| RPO/RTO revisited against real topology | Phase 13 |
