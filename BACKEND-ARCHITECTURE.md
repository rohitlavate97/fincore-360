# BACKEND ARCHITECTURE — FinCore 360

**Phase:** Complete & Audited — Spring Boot 4.1.1, Kotlin 2.3.21, and JDK 25.
See [PROJECT-STATUS.md](PROJECT-STATUS.md) and [AUDIT.md](AUDIT.md).

Governed by [ADR-006](docs/adr/ADR-006-Modular-Monolith.md) (modular monolith)
and [ADR-015](docs/adr/ADR-015-Backend-Language-Kotlin.md) (Kotlin).

---

## 1. Package structure

```
com.fincore/
├── identity/
│   ├── api/              controllers, request/response DTOs
│   ├── application/      use cases, application services
│   ├── domain/           entities, value objects, domain services
│   └── infrastructure/   JPA repositories, external adapters
├── accounts/             ← same four-layer shape
├── transactions/
├── payments/
├── customer/
├── notifications/
├── audit/
└── shared/
    ├── error/            global exception handler, error contract
    ├── security/         JWT filter, RBAC config, method security
    ├── pagination/       page/size/sort utilities, allowlists
    ├── money/            Money value object, Jackson converters
    └── correlation/      correlation ID filter, MDC propagation
```

Every domain module repeats the same four layers. Consistency here is what makes
the codebase navigable at fifteen modules.

---

## 2. Layer responsibilities

| Layer | Contains | Must not |
|---|---|---|
| `api` | Controllers, DTOs, `@PreAuthorize`, request validation | Contain business rules |
| `application` | Use cases, transaction boundaries (`@Transactional`), **ownership checks**, orchestration | Contain persistence details |
| `domain` | Entities, value objects, the transaction state machine, domain exceptions | Depend on Spring or JPA annotations where avoidable |
| `infrastructure` | JPA repositories, Kafka producers, external adapters | Be referenced from another module |

**The transaction boundary is the application layer.** Not the controller (too
early — validation would be inside it) and not the repository (too late — a
multi-step operation would not be atomic).

---

## 3. Module boundary rules

1. Modules communicate through **application service interfaces** only.
2. No JPA repository is accessed across a module boundary.
3. Cross-module calls go through the interface, not the implementation.
4. A shared database schema is acceptable in the monolith phase.
5. `shared/` may be depended on by everything; it depends on nothing.

**Enforcement.** These rules are text until ArchUnit tests exist. That is the
single largest architectural risk in Phase 1 — the monolith degrades into a
layered ball of mud silently and without any individual bad commit.

Required ArchUnit rules (Phase 1):

```
no class in ..identity.. may access ..accounts.infrastructure..   (and all pairs)
no class in ..domain.. may depend on ..infrastructure..
no class annotated @Controller may be accessed by ..application..
no field of type double or float in any financial type
```

---

## 4. Request pipeline

```
HTTP request
  │
  ├─ CorrelationIdFilter      read or generate X-Correlation-ID → MDC
  ├─ RateLimitFilter          login / refresh / transfer endpoints
  ├─ JwtAuthenticationFilter  validate RS256 signature, populate SecurityContext
  │                           ↑ positioned BEFORE UsernamePasswordAuthenticationFilter
  ├─ Controller               @PreAuthorize role check, bean validation
  ├─ Application service      ownership check → idempotency → @Transactional
  ├─ Domain                   invariants, state machine
  └─ Infrastructure           JPA, locks
      │
GlobalExceptionHandler → error contract (see API-DESIGN.md)
```

**Filter ordering notes** (each has bitten real systems):

- `OPTIONS` (CORS preflight) must be permitted **before** the JWT filter, or every
  cross-origin request fails at preflight.
- The refresh-token endpoint is **excluded** from the JWT filter — it is reached
  precisely when the access token is invalid.
- CORS is configured in Spring Security, not via `@CrossOrigin` alone;
  annotation-only CORS does not cover the security filter chain.
- CSRF is disabled — the API is stateless and token-based, with no cookie-borne
  ambient authority on the API surface. The web portal's `HttpOnly` refresh cookie
  is `SameSite`-restricted and used only against the refresh endpoint. Recorded
  here because "CSRF disabled" must never be an unexplained line in a config file.

---

## 5. Transaction state machine

Lives in `transactions/domain`. Transitions are validated in the domain layer;
an invalid transition throws a domain exception, never a generic error.

| From | Permitted to |
|---|---|
| `PENDING` | `PROCESSING`, `CANCELLED` |
| `PROCESSING` | `COMPLETED`, `FAILED` |
| `COMPLETED` | `REVERSED` (within reversal window) |
| `CANCELLED` / `FAILED` / `REVERSED` | terminal |

Modelled with Kotlin sealed types so exhaustiveness is compiler-checked
([ADR-015](docs/adr/ADR-015-Backend-Language-Kotlin.md)).

---

## 6. The transfer use case

The one flow where every cross-cutting mechanism meets. Ordering is
load-bearing.

```kotlin
// Illustrative shape only — no implementation exists.
@Transactional
fun transfer(command: TransferCommand, actor: Actor): TransferResult {
    // 1. Ownership — before any data is read or returned
    // 2. Idempotency — new key? replay stored response? in progress → 409
    // 3. Lock accounts in DETERMINISTIC order (by account ID) — deadlock defence
    // 4. Validate available balance (not ledger balance)
    // 5. Debit, credit
    // 6. Persist transaction (state machine validated)
    // 7. Persist idempotency record   ── same transaction as 5
    // 8. Persist audit record         ── same transaction as 5
    // 9. Persist outbox event         ── same transaction as 5 (Phase 7)
}
```

Steps 7–9 sharing the transaction with step 5 is the entire point. Any of them
moved outside it becomes a lost record on failure.

**Deadlock hazard.** Two transfers in opposite directions between accounts A and
B will deadlock if each locks its own source first. Locking by sorted account ID
prevents it. The specific ordering rule must be written into
[DATABASE-DESIGN.md](DATABASE-DESIGN.md) before Phase 5.

---

## 7. Spring Security configuration

Per [ADR-013](docs/adr/ADR-013-JWT-Auth-Model.md) and
[ADR-014](docs/adr/ADR-014-RBAC-Authorization.md):

- CORS configured in the security filter chain, restricted origins
- `OPTIONS` permitted pre-authentication
- JWT filter before `UsernamePasswordAuthenticationFilter`
- Refresh endpoint excluded from the JWT filter
- Method-level security enabled (`@EnableMethodSecurity`)
- Authorization at **both** controller (`@PreAuthorize`) and service layer
- CSRF disabled — justified in §4
- Rate limiting on login, token refresh, and transfer endpoints

`PLANNED — not implemented.`

---

## 8. Testing approach

Detail in [TESTING.md](TESTING.md). Backend-specific shape:

| Level | Tooling | Scope |
|---|---|---|
| Unit | JUnit, MockK | Domain logic, state machine, use cases — no Spring context |
| Repository | Testcontainers | Real PostgreSQL: queries, pagination, migrations |
| Controller | MockMvc slice | Status codes, contract shape, 401/403 per endpoint |
| Integration | Testcontainers | Full flows, concurrency, idempotency, induced failures |
| Architecture | ArchUnit | Module boundaries, banned types |

---

## 9. Open items

| Item | Needed by |
|---|---|
| Deterministic lock ordering rule | Phase 5 |
| Transactional outbox table + relay design | Phase 7 |
| ArchUnit rule set | Phase 1 |
| `Money` value object + Jackson converter | Phase 1 |
| Rate limit thresholds and fail-open/fail-closed policy per endpoint | Phase 3 |
| Dependency versions | Phase 1 — verify against official compatibility docs |

> `NOT VERIFIED — nothing in this document has been built, compiled, or run.`
