# FinCore 360

Enterprise digital banking **simulation** platform — Kotlin/Jetpack Compose Android client, Spring Boot modular monolith, PostgreSQL, Redis, Kafka, React admin portal.

> **This is not a real financial product.**
> It processes no real money, integrates no banking or payment rails, and uses only fictional users, accounts, balances, and transactions. It exists to demonstrate how a real enterprise engineering organization designs, implements, tests, secures, deploys, observes, and maintains a large-scale financial platform.

---

## Project Status

| Phase | Description | Status | Verification |
|---|---|---|---|
| **Phase 0** | Architecture & ADRs | **Complete** | 20 root architecture docs, 18 ADRs, full threat model |
| **Phase 1** | Backend Foundation | **Complete** | Spring Boot 4.1.1, Flyway, PostgreSQL, NUMERIC(19,4) precision |
| **Phase 2** | Android Foundation | **Complete** | AGP 9.3.0, Compose, Hilt, Room, 16 modules |
| **Phase 3** | Authentication & RBAC | **Complete** | External RSA PEM keys, JWT token rotation, lockout, RBAC matrix |
| **Phase 4** | Customer & Account Domain | **Complete** | Zero initial balance enforcement, admin cash deposits, account lifecycle |
| **Phase 5** | Transaction & Idempotency Engine | **Complete** | Pessimistic locking, UUID idempotency with expiry purge, double-entry ledger |
| **Phase 6** | Outbox Pattern & Event Relay | **Complete** | SKIP LOCKED batch claiming, scheduled relay, atomicity |
| **Phase 7** | Audit Trail & Compliance | **Complete** | Append-only audit trigger, IP/UserAgent capture, rollback survival |
| **Phase 8** | Notifications Domain | **Complete** | Deep links, unread counters, transactional outbox dispatch |
| **Phase 9** | React Web Operations Portal | **Complete** | React 19, TypeScript, role-based nav, single-flight token refresh |
| **Phase 10** | Security Hardening & Rate Limiting | **Complete** | Deny-by-default, secured Actuator/Swagger, bounded sliding window |
| **Phase 11** | Infrastructure & CI/CD Pipelines | **Complete** | Docker, Helm, K8s ingress, blocking Trivy scans, real R8 verification |
| **Phase 12** | Production Simulation & Audits | **Complete** | Chaos failure simulations, double-entry ledger reconciliation |
| **Audit** | Comprehensive Security Audit Remediation | **Complete** | 100% remediated across C-1..C-6, H-1..H-11, M-1..M-13 |

See [PROJECT-STATUS.md](PROJECT-STATUS.md) and [AUDIT.md](AUDIT.md) for detailed audit findings and verification logs.

---

## Quickstart & Local Setup

> 📖 **Complete Step-by-Step Guide:** See [LOCAL-SETUP.md](LOCAL-SETUP.md) for full instructions on running the entire platform via Docker Compose or standalone development services.

### Prerequisites
- **JDK 25** (Temurin 25.0.3 LTS recommended)
- **Node.js 22** & npm
- **Docker & Compose** (PostgreSQL, Prometheus, Grafana)
- **Android SDK** (API Level 36 targetSdk)

### 1. Backend Service
```bash
cd backend
./gradlew test
./gradlew bootRun
```
*Executes all unit, integration, double-entry ledger reconciliation, and failure simulation tests against embedded PostgreSQL, and runs on `http://localhost:8080`.*

### 2. Web Portal
```bash
cd web
npm install
npm test
npm run dev
```
*Runs on `http://localhost:5173` with Vite hot-reload and automated backend proxying.*

### 3. Android Application
```bash
cd android
./gradlew test
./gradlew assembleDebug
```
*Compiles all 13 modules, runs unit test suites, and packages APK.*

---

## System Architecture

```
                                  FINCORE 360
                                       │
            ┌──────────────────────────┼──────────────────────────┐
            │                          │                          │
       ANDROID APP                 WEB PORTAL                BACKEND API
 (Jetpack Compose + Hilt)   (React 19 + TypeScript)    (Spring Boot 4.1.1 Monolith)
            │                          │                          │
         REST / TLS                 REST / TLS              ┌─────┴─────┐
            └──────────────────────────┴───────────────────►│PostgreSQL │
                                                            │ + Outbox  │
                                                            │ + Ledger  │
                                                            └───────────┘
```

### Architecture Highlights
- **Modular Monolith:** Spring Boot 4.1.1, Kotlin 2.3.21, Gradle 9.3.0, JDK 25.
- **Double-Entry Ledger:** Paired DEBIT/CREDIT entries per transaction with running balance snapshots.
- **Transactional Outbox:** Guaranteed at-least-once asynchronous event propagation using `FOR UPDATE SKIP LOCKED`.
- **Stateless Authentication:** External RSA 2048 PEM public/private key verification with absolute session lifetimes and account-wide theft revocation.
- **Idempotency Engine:** First-class `Idempotency-Key` requirement on all mutations with scheduled retention purge.
- **Fail-Closed Security:** Deny-by-default URL authorization, secured Actuator metrics, spoofing-resistant rate limiting.

---

## Repository Layout

```
fincore-360/
├── android/                        Kotlin · Compose · Clean Architecture (16 modules)
├── backend/                        Kotlin · Spring Boot 4 modular monolith & domain services
├── web/                            React 19 · TypeScript · Vite operations & compliance portal
├── infra/                          Docker, Kubernetes, Helm charts, Nginx reverse proxy
└── docs/                           Architecture specs, threat models, and 18 ADRs
```

---

## Non-Negotiable Engineering Rules

Recorded here because violating any of them silently corrupts the system:

| Rule | Why | ADR |
|---|---|---|
| Money is `BigDecimal` → `NUMERIC(19,4)` → **string** in JSON. Never `float`/`double`. | Binary floating point cannot represent decimal currency exactly | [ADR-012](docs/adr/ADR-012-Monetary-Representation.md) |
| Every state-mutating endpoint requires an `Idempotency-Key` | Retries and double-taps must not create duplicate transactions | [ADR-010](docs/adr/ADR-010-Idempotency-Strategy.md) |
| Balance contention is resolved by the **database** (`SELECT ... FOR UPDATE`), never by JVM locks | Application locks do not span replicas | [ADR-007](docs/adr/ADR-007-PostgreSQL-Primary-DB.md) |
| The audit table is append-only — no `UPDATE`, no `DELETE`, enforced by DB trigger *and* application | An audit log that can be edited is not an audit log | [ADR-014](docs/adr/ADR-014-RBAC-Authorization.md) |
| Authorization is enforced server-side at the **service** layer on every request | The client is never a security boundary | [ADR-014](docs/adr/ADR-014-RBAC-Authorization.md) |
| Passwords, tokens, secrets, PII, and account numbers never reach logs | Logs are widely readable and long-lived | [SECURITY.md](SECURITY.md) |

---

## Documentation Map

- **System Blueprint:** [ARCHITECTURE.md](ARCHITECTURE.md)
- **Status Ledger:** [PROJECT-STATUS.md](PROJECT-STATUS.md)
- **Decision Records:** [DECISIONS.md](DECISIONS.md) & [docs/adr/](docs/adr/)
- **Android Architecture:** [ANDROID-ARCHITECTURE.md](ANDROID-ARCHITECTURE.md)
- **Backend Architecture:** [BACKEND-ARCHITECTURE.md](BACKEND-ARCHITECTURE.md)
- **Database Design:** [DATABASE-DESIGN.md](DATABASE-DESIGN.md)
- **Security & Threat Model:** [SECURITY.md](SECURITY.md) · [THREAT-MODEL.md](THREAT-MODEL.md)
- **Production Failure Catalog:** [PRODUCTION-FAILURE-MODES.md](PRODUCTION-FAILURE-MODES.md)

---

## License

Educational / portfolio use. Not licensed for production financial use.
