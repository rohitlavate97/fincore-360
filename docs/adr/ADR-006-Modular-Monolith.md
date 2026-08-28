# ADR-006: Modular monolith for the backend, not microservices

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 1

---

## Context

The backend covers seven domains — identity, accounts, transactions, payments,
customer, notifications, audit. The obvious "enterprise" instinct is a service
per domain. The obvious counter-force is that a money transfer must debit one
account and credit another **atomically**, and that is a single database
transaction in a monolith versus a saga with compensating transactions across
services.

There is exactly one team (one engineer). No domain has an independent scaling or
deployment requirement that has been demonstrated.

## Decision

Build a **modular monolith**: one deployable Spring Boot application, internally
partitioned into domain modules with enforced boundaries.

```
com.fincore/
├── identity/       ├── api/            controllers, DTOs
├── accounts/       ├── application/    use cases, services
├── transactions/   ├── domain/         entities, value objects, domain services
├── payments/       └── infrastructure/ JPA repositories, adapters
├── customer/
├── notifications/
├── audit/
└── shared/         error · security · pagination · correlation
```

**Boundary rules:**

- Modules communicate through application service interfaces only.
- No JPA repository is accessed across a module boundary.
- A shared database schema is acceptable in this phase.

**Extraction rule.** A module becomes an independent service only when at least
one of these is *demonstrated*, not anticipated: independent scaling need,
independent deployment need, separate team ownership, genuinely different data
model. The extraction gets its own ADR before any code moves.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Microservice per domain | A transfer would span an accounts service and a transactions service, forcing a saga with compensating transactions and eventual consistency on *balances*. That is a large correctness cost bought with no scaling benefit at this size. |
| Single-package layered monolith (`controllers/`, `services/`, `repositories/`) | Layer-first packaging lets any service call any repository. Boundaries erode invisibly, and the "extract later" option quietly disappears. |
| Modular monolith with a schema per module | Defensible, and closer to a future split. Rejected for now because cross-module foreign keys and a single transaction across accounts + audit are wanted while the domain model is still moving. |

## Consequences

### Positive

- Debit, credit, and audit-record insert commit in **one** ACID transaction.
- One thing to build, deploy, run locally, and trace. Local development is
  `docker compose up`, not seven containers plus a service mesh.
- Boundaries are still explicit, so extraction stays possible if it is ever
  justified.

### Negative — what this costs us

- **Boundaries are enforced by discipline, not by the network.** A developer
  *can* inject another module's repository; nothing physically stops them. This
  must be caught in review or by an architecture test (ArchUnit) — otherwise the
  design decays into the layered monolith rejected above.
- The whole application scales as a unit. If notifications become hot, identity
  scales with them.
- One deployment means one blast radius: a bad release takes down everything.
- Shared schema means a careless migration can affect every module.

### Neutral / follow-on work

- ArchUnit tests are the practical enforcement mechanism and should land in
  Phase 1 alongside the module skeleton, not later.

## Verification

- ArchUnit test: no class in module A references a `*.infrastructure.*` type in
  module B.
- Integration test: a transfer's debit, credit, and audit insert roll back
  together on induced failure.

> `NOT VERIFIED — no backend code, modules, or ArchUnit rules exist. The boundary
> rule is currently unenforced text.`

## Interview notes

**The trap:** "We used microservices for scalability." Asked *what* needed to
scale independently, and the answer is usually nothing measured.

**The senior answer:** Start with the transaction boundary, not the org chart. A
transfer must debit and credit atomically; in a monolith that is one database
transaction, and across services it is a saga with compensating actions and a
window where money exists in neither account. You take on that complexity when
something forces you to — independent scaling, independent deployment, separate
team ownership, or a genuinely different data model — and you write down which
one before you split. The honest cost of my choice is that my module boundaries
are enforced by ArchUnit and code review rather than by a network, and those can
be bypassed.
