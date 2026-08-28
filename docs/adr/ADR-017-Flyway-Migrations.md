# ADR-017: Flyway for database migrations

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 1

---

## Context

The master prompt requires "Flyway or Liquibase (justify in ADR)". The schema
carries constraints that are the actual enforcement of several other decisions —
`NUMERIC(19,4)` monetary columns (ADR-012), the audit table's append-only trigger
(ADR-014), partial and composite indexes (`DATABASE-DESIGN.md`). Those are
PostgreSQL-specific SQL, not portable abstractions.

Hibernate's `ddl-auto` is not a candidate. Letting an ORM mutate a schema
containing financial data is how production data is lost.

## Decision

Use **Flyway** with versioned, sequential, plain-SQL migrations.

**Rules:**

- Every schema change goes through a migration. **No manual schema change on any
  environment, ever** — including local, because a schema that only exists on one
  machine is a migration that was never written.
- Migrations are **immutable once merged.** A mistake is corrected by a new
  migration, never by editing an applied one — Flyway checksums applied
  migrations and will refuse to start otherwise.
- `spring.jpa.hibernate.ddl-auto=validate` in every environment. Hibernate may
  *verify* that entities match the schema; it may never modify it.
- A rollback script accompanies every non-trivial migration.
- Migrations are tested against production-like data before deployment, and
  verified in CI via Testcontainers.
- Migrations that rewrite large tables must be designed to avoid long locks —
  `CREATE INDEX CONCURRENTLY`, and adding a `NOT NULL` column in the
  add/backfill/constrain sequence rather than in one statement.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| **Liquibase** | More capable: XML/YAML changelogs, native rollback support, database-agnostic changesets, preconditions. Rejected because its main advantage — abstraction over multiple database engines — is worth nothing here (PostgreSQL is fixed by ADR-007), while the abstraction obscures the PostgreSQL-specific DDL that carries our guarantees. Plain SQL is directly reviewable; a YAML changeset for an append-only trigger is not. |
| Hibernate `ddl-auto=update` | Non-deterministic, no version history, no review, and it silently alters schemas holding financial data. Never acceptable. |
| Hand-run SQL scripts | No ordering guarantee, no record of what was applied where, no CI verification. |

## Consequences

### Positive

- Migrations are plain PostgreSQL SQL — reviewable in a pull request by anyone
  who reads SQL, with no changelog dialect in between.
- Flyway's schema history table makes the applied state of every environment
  explicit and auditable.
- Startup fails loudly on checksum mismatch, so environment drift is caught at
  deploy rather than discovered later.

### Negative — what this costs us

- **No native rollback.** Flyway Community has no `undo`; rollback scripts are
  hand-written and, unless they are actually exercised, are fiction. Liquibase
  would have given this for free, and it is the real cost of this choice.
- Migrations run at application startup by default, which couples deploy time to
  migration time. A slow migration delays or fails startup (`FM-BACKEND-007`),
  and with multiple replicas starting together, Flyway's locking serialises them.
- SQL is PostgreSQL-specific; changing engine would mean rewriting migrations.
  Accepted — that change is not planned.
- Backward-compatible migration discipline is now mandatory for zero-downtime
  deploys: during a rolling release, old and new application versions run against
  the *same* schema, so a migration that drops or renames a column breaks the
  version still running. Expand-and-contract across two releases is required, and
  this is easy to forget.

### Neutral / follow-on work

- Whether migrations run at startup or as a separate deploy step should be
  revisited in Phase 13; the Kubernetes answer is usually an init container or a
  job, not application startup.

## Verification

- CI runs all migrations from empty against a Testcontainers PostgreSQL instance.
- Schema assertion tests: monetary columns are `NUMERIC(19,4)`; the audit trigger
  rejects `UPDATE`/`DELETE`.
- `ddl-auto=validate` proves entities and schema agree at startup.
- A rollback script is exercised in CI, not merely present.

> `NOT VERIFIED — no migrations, schema, or CI exist. The rollback-script
> weakness described above is inherent to Flyway Community and is accepted, not
> mitigated.`

## Interview notes

**The trap:** "We use Flyway for migrations." True and uninteresting. The
follow-up that exposes depth is: what happens during a rolling deployment?

**The senior answer:** The tool matters less than the discipline around it. Two
things do the work. First, `ddl-auto=validate` — Hibernate verifies that entities
match the schema and is never permitted to change it, because an ORM altering a
schema that holds balances is how you lose data. Second, migrations must be
**backward compatible**, because during a rolling deploy the old and new
application versions run against the same schema simultaneously. Dropping a
column in the same release that stops using it will break every pod still running
the old version. The fix is expand-and-contract: add and backfill in release N,
stop writing the old column in N, drop it in N+1. And I would name Flyway's
genuine weakness unprompted — Community has no automatic rollback, so undo
scripts are hand-written, and a rollback script that has never been executed is
not a rollback plan.
