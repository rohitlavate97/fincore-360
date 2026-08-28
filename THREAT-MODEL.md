# THREAT MODEL — FinCore 360

**Phase:** 0 — initial model. **No mitigation is implemented.**
Reviewed in full in Phase 10.

> `NOT VERIFIED — this is a paper exercise against a system that does not exist.
> Every mitigation below is "planned". Nothing has been tested or attacked.`

---

## 1. Scope and assumptions

**In scope:** Android client, web portal, API gateway, backend monolith,
PostgreSQL, Redis, Kafka, CI/CD pipeline.

**Assumptions:**

- The **client is attacker-controlled.** Android and web code, storage, and
  traffic are all inspectable and modifiable. No client-side check is a control.
- The network is hostile. TLS is assumed but pinning is not present before
  Phase 10.
- All data is fictional. A breach costs credibility, not money.
- One developer, no separate security team. Review is self-review, which is a
  genuine weakness of this model.

---

## 2. Trust boundaries

```
┌──────────────────────────────────────────────────┐
│ UNTRUSTED — attacker-controlled                  │
│   Android app · web browser · device storage     │
└──────────────────────┬───────────────────────────┘
                       │  ◄── BOUNDARY 1: TLS, authN, authZ, validation
┌──────────────────────┴───────────────────────────┐
│ SEMI-TRUSTED — our code, hostile input           │
│   API gateway · Spring filters · controllers     │
└──────────────────────┬───────────────────────────┘
                       │  ◄── BOUNDARY 2: service-layer authZ + ownership
┌──────────────────────┴───────────────────────────┐
│ TRUSTED — invariants enforced                    │
│   Application services · domain · persistence    │
└──────────────────────┬───────────────────────────┘
                       │  ◄── BOUNDARY 3: least-privilege DB user, constraints
┌──────────────────────┴───────────────────────────┐
│ DATA — PostgreSQL · Redis · Kafka                │
└──────────────────────────────────────────────────┘
```

**Boundary 2 is the one that matters most.** Boundary 1 controls are standard and
widely implemented. Boundary 2 — "is this actor entitled to *this* resource?" —
is where real financial breaches happen, and it is enforced per-endpoint with
nothing structural to catch an omission.

---

## 3. STRIDE analysis

### Spoofing

| Threat | Mitigation | Residual risk |
|---|---|---|
| Stolen access token replayed | 15-minute lifetime; RS256 signature | **Valid for up to 15 minutes. Cannot be revoked.** Accepted. |
| Stolen refresh token replayed | Rotation on use; reuse detection revokes the device family | Window until the legitimate client next refreshes |
| Credential stuffing | Rate limiting (fail closed), account lockout, MFA simulation | Distributed low-rate attacks evade per-IP limits |
| Forged JWT | RS256 — verifiers hold only the public key | Private key compromise is total. Key rotation procedure is undefined. |
| Session fixation | Tokens minted server-side only | — |

### Tampering

| Threat | Mitigation | Residual risk |
|---|---|---|
| Modified request body (amount, account) | Server-side validation; ownership check; domain invariants | Depends on every endpoint doing the check |
| Altered JWT claims (role escalation) | Signature verification | — |
| Audit record alteration | **Database trigger** rejecting UPDATE/DELETE | DBA-level access bypasses it. Accepted for simulation. |
| Modified APK / repackaging | Not addressed | **Open.** No integrity attestation, no root detection. |
| MITM | TLS | **No certificate pinning before Phase 10.** |

### Repudiation

| Threat | Mitigation | Residual risk |
|---|---|---|
| "I did not authorise that transfer" | Append-only audit with actor, role-at-time, IP, correlation ID | Audit write inside the transaction adds latency; outside it risks loss (outbox, Phase 7) |
| Lost audit event on broker failure | Transactional outbox | **Unimplemented. Currently a real gap.** |

### Information disclosure

| Threat | Mitigation | Residual risk |
|---|---|---|
| **IDOR — reading another customer's account** | Service-layer ownership check; 404 rather than 403 for existence-sensitive lookups; per-endpoint tests | **Highest-likelihood real vulnerability.** One forgotten check on one new endpoint. |
| Secrets or PII in logs | Field allowlists, structured logging, no token/PII fields | A careless `log.debug(request)` defeats it |
| Stack traces in responses | Global exception handler, fixed error contract | — |
| Sequential ID enumeration | UUID primary keys everywhere | — |
| Cached financial data on a lost device | Keystore for tokens; `FLAG_SECURE`; backup exclusion | **Room data is not encrypted at rest.** Open. |
| Verbose Android logging in release | Logging interceptor excluded by build type | Enforcement is a build config that can regress |

### Denial of service

| Threat | Mitigation | Residual risk |
|---|---|---|
| Credential brute force | Rate limiting, lockout | Lockout itself enables targeted account-denial |
| Request flooding | Gateway rate limiting | No WAF or upstream DDoS protection |
| **Lock contention / deadlock on hot accounts** | Deterministic lock ordering | **Ordering rule not yet specified.** Real risk at Phase 5. |
| Connection pool exhaustion | Pool sizing, tight transaction scope | `FM-BACKEND-001`. Untested. |
| Unbounded idempotency table growth | Scheduled purge | Purge job unimplemented |

### Elevation of privilege

| Threat | Mitigation | Residual risk |
|---|---|---|
| Role escalation via request manipulation | Roles come from the signed token, never from the request | — |
| Client-side guard bypass | Server-side enforcement; guards are navigation only | — |
| Admin action without permission | Role **and** explicit permission required | Permission model unimplemented |
| Service method reached without authZ | Check at service layer, not controller | Depends on discipline; ArchUnit cannot verify semantics |
| Over-privileged database user | Least-privilege DB user planned | Unimplemented |

---

## 4. Top risks, ranked

Ranked by likelihood × impact for *this* system, not generically.

| # | Risk | Why ranked here |
|---|---|---|
| 1 | **IDOR on a new endpoint** | Highest likelihood by far. Every endpoint is a fresh chance to forget the ownership check, and nothing structural prevents it. Impact: cross-customer financial data. |
| 2 | **Lost audit events (dual write)** | Designed but unimplemented. A transfer with no audit record is unprovable. |
| 3 | **Deadlock on opposing transfers** | Ordering rule not yet specified; will manifest as intermittent failures under load. |
| 4 | Refresh-token race causing mass logouts | Reuse detection plus a non-single-flight client is self-inflicted denial of service |
| 5 | Secrets or PII reaching logs | One careless log line; long-lived and widely readable |
| 6 | Private signing key compromise | Low likelihood, total impact. No rotation procedure exists. |
| 7 | Unencrypted cached financial data on device | Requires physical access; real for a lost phone |

---

## 5. Explicitly out of scope

Stated so their absence is not mistaken for an oversight:

- Real payment rail and banking integration security
- Regulatory compliance (PCI-DSS, PSD2, SOC 2, GDPR erasure)
- Physical and datacentre security
- Insider threat and privileged-access management
- Nation-state adversaries
- Supply-chain attacks beyond dependency scanning
- **Right-to-erasure vs append-only audit.** These genuinely conflict. Anything
  beyond simulation would need pseudonymisation at write time or
  crypto-shredding. Not addressed.

---

## 6. Review schedule

| When | What |
|---|---|
| Phase 3 | Re-examine after authentication exists |
| Phase 5 | Re-examine after transfers and concurrency exist |
| Phase 10 | **Full review** — OWASP checklist run for real, penetration testing simulation |
| Every new endpoint | Ownership check confirmed and tested |
