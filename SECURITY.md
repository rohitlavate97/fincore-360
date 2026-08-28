# SECURITY — FinCore 360

**Phase:** 0 — design intent. **No security control is implemented.**

> `NOT VERIFIED — nothing in this document has been built, tested, scanned, or
> reviewed against a running system. No claim here should be read as an assertion
> that FinCore 360 is secure. It currently has no code to secure.`

---

## 1. Authentication

Per [ADR-013](docs/adr/ADR-013-JWT-Auth-Model.md).

### Access token — JWT

| Property | Value |
|---|---|
| Lifetime | 15 minutes |
| Algorithm | **RS256** — asymmetric; verifiers hold only the public key |
| Claims | `sub`, `roles`, `jti`, `exp`, `iat`, `iss` |
| Android storage | Keystore-backed encrypted store |
| Web storage | **Memory only.** Never `localStorage`. |
| Revocable | **No** — bounded to 15 minutes by design |

### Refresh token — opaque

| Property | Value |
|---|---|
| Format | Opaque random. Not a JWT — carries no readable claims |
| Lifetime | 7 days |
| Server storage | Database row, **hashed**, with expiry + user + device |
| Android storage | Keystore-backed encrypted store |
| Web storage | `HttpOnly; Secure; SameSite` cookie |
| Rotation | **On every use** — the presented token is invalidated |
| Revocable | Yes — delete the row |

**Reuse detection.** Presentation of an already-consumed refresh token indicates
replay or theft. Response: revoke the entire token family for that device, forcing
re-login.

**Known accepted gap.** A locked or deleted user retains access for up to 15
minutes because a stateless JWT cannot be revoked. Closing this requires a `jti`
denylist checked on every request, which reintroduces the per-request lookup the
stateless model avoids. Not implemented; revisit only against a real requirement.

### Password policy

Complexity, history, and expiry simulation; account lockout after repeated
failures. Lockout counters live in Redis
([ADR-008](docs/adr/ADR-008-Redis-Cache-Sessions.md)).

`PLANNED — not implemented.`

---

## 2. Authorization

Per [ADR-014](docs/adr/ADR-014-RBAC-Authorization.md).

**Two checks, both required, on every request:**

1. **Role** — does this role permit this operation?
2. **Resource ownership** — is this actor entitled to *this specific resource*?

Check 2 is the one that is skipped, and skipping it is textbook IDOR.

**Both live at the service layer.** A controller annotation is bypassed by any
other caller into that service — a scheduled job, a Kafka consumer, a second
controller. The service layer is the last common chokepoint.

### Role matrix

| Role | Intent |
|---|---|
| `CUSTOMER` | Self-service on **their own** accounts only |
| `SUPPORT_AGENT` | Read customer data to assist with issues |
| `OPERATIONS` | Transaction monitoring, operational tasks |
| `AUDITOR` | Read-only audit log access |
| `ADMIN` | User management, system configuration |

Admin operations require role **and** explicit permission — role alone is too
coarse for destructive actions.

The full role → permission matrix is maintained here as the single reference.

`PLANNED — not implemented.`

### Client-side guards are not a security boundary

Android route guards and React route protection are navigation aids. The backend
denies unauthorised requests regardless of what any client did. The client is
attacker-controlled and is never trusted for a security decision.

---

## 3. Transport and API surface

| Control | Rule |
|---|---|
| TLS | Required everywhere. No plaintext HTTP in any environment. |
| CORS | Configured in the Spring Security filter chain with restricted origins. `@CrossOrigin` alone does not cover the security chain. |
| CSRF | Disabled — the API is stateless and token-based with no ambient cookie authority on the API surface. The web refresh cookie is `SameSite`-restricted and scoped to the refresh endpoint. Documented so this is never an unexplained config line. |
| Security headers | HSTS, `X-Content-Type-Options`, `X-Frame-Options`/`frame-ancestors`, CSP |
| Rate limiting | Login, token refresh, transfer endpoints |
| Certificate pinning (Android) | Phase 10. **Not claimed before then.** |

### Rate limiter degradation policy

This is easy to get backwards, so it is written down:

| Endpoint | Redis unavailable → |
|---|---|
| `/auth/login` | **Fail closed** — reject. Losing brute-force protection on an auth endpoint is worse than refusing logins. |
| `/auth/refresh` | **Fail closed** |
| `/transfers` | **Fail closed** |
| Reference-data cache | Fail open — serve from source |

---

## 4. OWASP controls

| Risk | Control | State |
|---|---|---|
| Injection | Parameterised queries only; no string concatenation; sort/filter fields allowlisted | Planned |
| Broken authentication | Short token lifetime, rotation, reuse detection, Keystore storage | Planned |
| Broken authorization | Server-side service-layer enforcement; ownership checks; IDOR tests per endpoint | Planned |
| Sensitive data exposure | Field-level exclusion from logs and responses; no stack traces | Planned |
| Security misconfiguration | Security headers, restricted CORS, no default credentials | Planned |
| XSS | React escapes by default; CSP headers; no `dangerouslySetInnerHTML` | Planned |
| SSRF | Outbound requests only to an explicit allowlist | Planned |
| Vulnerable dependencies | OWASP Dependency Check + Trivy in CI | Planned |
| Insufficient logging | Structured audit events for every security-sensitive action | Planned |

> `NOT VERIFIED — every row above is "planned". No control has been implemented
> or tested. The OWASP checklist is run for real in Phase 10.`

---

## 5. Data protection

### Never logged

Passwords · tokens (access or refresh) · secrets and API keys · full account
numbers · full card numbers · PII beyond the minimum needed to debug.

This is enforced by structured logging with explicit field allowlists, not by
reviewers remembering. See [OBSERVABILITY.md](OBSERVABILITY.md).

### Never in responses

Stack traces · internal class names · SQL · framework error text.

### At rest

| Data | Protection |
|---|---|
| Refresh tokens (server) | Hashed, never plaintext |
| Passwords | Adaptive hash with per-user salt (algorithm chosen in Phase 3) |
| Android tokens | Keystore-backed encrypted store |
| Android cached financial data | Room, excluded from auto-backup; `FLAG_SECURE` on displaying screens |

### Secrets management

- **No secret is ever committed.** `.gitignore` excludes `.env`, `*.pem`,
  `*.jks`, `*.keystore`, `local.properties`, `*.tfvars`.
- Placeholders plus a secrets manager. Environment variables injected at deploy,
  never hardcoded.
- Secret detection runs in CI on both pipelines.

---

## 6. Android-specific controls

| Control | Rule |
|---|---|
| Token storage | Keystore only. **Never** `SharedPreferences` (unencrypted) or Room (cleartext). |
| `FLAG_SECURE` | Every screen showing account numbers, balances, or card details |
| Exported components | `exported=false` unless external access is intentional |
| Auto-backup | Sensitive data excluded via explicit backup rules |
| Logging interceptor | Enforced absent from release builds by build type |
| Biometric | Android Biometric API gating access to Keystore material |

---

## 7. Audit

Append-only, enforced by database trigger **and** application rule
([DATABASE-DESIGN.md](DATABASE-DESIGN.md) §4). An audit log that a compromised
application can rewrite proves nothing about the compromise.

Auditable events:

| Category | Events |
|---|---|
| Authentication | `LOGIN`, `LOGOUT`, `LOGIN_FAILED`, `PASSWORD_CHANGED` |
| Account | `ACCOUNT_CREATED`, `ACCOUNT_FROZEN`, `ACCOUNT_CLOSED` |
| Transaction | `TRANSFER_INITIATED`, `TRANSFER_COMPLETED`, `TRANSFER_FAILED`, `TRANSFER_REVERSED`, `DUPLICATE_DETECTED` |
| Customer | `PROFILE_UPDATED`, `BENEFICIARY_ADDED`, `BENEFICIARY_REMOVED` |
| Admin | `ROLE_ASSIGNED`, `ROLE_REVOKED`, `USER_LOCKED`, `USER_UNLOCKED` |
| Security | `MFA_ENABLED`, `MFA_DISABLED`, `SUSPICIOUS_ACTIVITY_FLAGGED` |

Failed authorization attempts are audited as `FAILURE` with the real reason —
even where the API response is a deliberately vague 404.

---

## 8. Security testing

| Test | Expected |
|---|---|
| Unauthenticated → protected endpoint | `401` |
| Insufficient role → protected endpoint | `403` |
| Customer A → customer B's account (IDOR) | `403`/`404`, audited as failure |
| SQL injection attempt | Rejected |
| Expired token | `401`, **never** `500` |
| Tampered JWT signature | `401` |
| Consumed refresh token replayed | Device family revoked |
| Token in `SharedPreferences`/Room/`localStorage` | Assertion: absent |

Run per endpoint, every endpoint. Detail in [TESTING.md](TESTING.md).

`PLANNED — not implemented.`

---

## 9. Scope limits — stated plainly

FinCore 360 is a **simulation**. It processes no real money, integrates no
banking or payment rails, and holds only fictional data. It has not been
penetration tested, has no threat intelligence, and carries no compliance
certification. The security work here exists to demonstrate engineering
practice, not to protect real assets.

See [THREAT-MODEL.md](THREAT-MODEL.md) for threats, mitigations, and residual
risk.
