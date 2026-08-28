# ADR-013: JWT access tokens with rotating opaque refresh tokens

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 3

---

## Context

Authentication must serve three clients (Android, web portal, admin UI) against a
stateless backend, survive token theft with a bounded blast radius, and support
logout that actually revokes access.

The central tension: stateless JWTs cannot be revoked before expiry. A stolen
access token is valid until it expires no matter what the server does. The design
must therefore bound that window rather than pretend it does not exist.

## Decision

**Two token types with deliberately different properties.**

**Access token — JWT**

| Property | Value |
|---|---|
| Lifetime | 15 minutes |
| Signature | **RS256** (asymmetric) |
| Claims | `sub`, `roles`, `jti`, `exp`, `iat`, `iss` |
| Storage — Android | Android Keystore-backed encrypted store |
| Storage — web | **In memory only.** Never `localStorage`. |
| Revocable | No — mitigated by the 15-minute lifetime |

**Refresh token — opaque**

| Property | Value |
|---|---|
| Format | Opaque random token, **not** a JWT — carries no readable claims |
| Lifetime | 7 days |
| Storage — server | Database row: hash, expiry, user, device |
| Storage — Android | Android Keystore-backed encrypted store |
| Storage — web | `HttpOnly; Secure; SameSite` cookie |
| Scope | One active token per device |
| Rotation | **Rotated on every use** — the presented token is invalidated |
| Revocable | Yes — delete the row |

**Reuse detection.** Because rotation invalidates the old token, presentation of
an already-used refresh token means either replay or theft. The correct response
is to **revoke the entire token family for that device**, forcing re-login. A
stolen refresh token therefore survives at most until the legitimate client next
refreshes.

**Client-side refresh race.** Multiple simultaneous 401s must trigger exactly one
refresh; the rest queue on the shared result. Success → all retry with the new
token. Failure → all fail and the user is logged out. See ADR-004.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Server-side sessions (opaque ID + session store) | Trivially revocable, which is a genuine advantage. Rejected because it makes the session store a hard dependency of every authenticated request (see ADR-008), and multi-client support gets harder. |
| Long-lived access token, no refresh token | A stolen token is valid for its whole lifetime with no revocation path. Exactly the risk this design bounds. |
| **HS256** symmetric signing | Every verifier needs the signing secret, so any service that validates a token can also mint one. RS256 lets verifiers hold only the public key. |
| JWT refresh tokens | A JWT refresh token is self-contained and therefore not revocable — which defeats the point of having one. Opaque + database row is what makes revocation possible. |
| Access token in `localStorage` (web) | Readable by any XSS payload. In-memory plus an `HttpOnly` refresh cookie limits XSS to the current page lifetime. |
| Refresh token without rotation | No reuse detection. A stolen refresh token then yields access for its full 7 days, silently. |

## Consequences

### Positive

- Ordinary request validation is a signature check — no database lookup, so auth
  does not depend on a session store being up.
- The blast radius of a stolen access token is capped at 15 minutes.
- Rotation plus reuse detection turns refresh-token theft into a detectable
  event rather than a silent compromise.
- RS256 means a verifier cannot forge tokens.

### Negative — what this costs us

- **Access tokens genuinely cannot be revoked.** A locked or deleted user keeps
  working for up to 15 minutes. For genuinely urgent revocation a `jti` denylist
  is required, and that reintroduces a lookup on every request — the cost the
  stateless model was avoiding. This is a real, accepted gap, not a solved one.
- Rotation makes the refresh endpoint a correctness hazard: two concurrent
  refreshes with the same token look identical to theft. The single-flight client
  behaviour (ADR-004) is what prevents self-inflicted logouts, and it must
  actually work.
- Key management (RS256 private key storage, rotation, `kid` handling) is now an
  operational responsibility.
- One refresh token per device requires a device identity concept, which is more
  state to model and to get wrong.
- Refresh tokens stored server-side are credentials at rest — they must be stored
  **hashed**, never in plaintext.

### Neutral / follow-on work

- Key rotation procedure belongs in `SECURITY.md` and `DEPLOYMENT.md`.
- Android must never store either token in Room or `SharedPreferences` (ADR-003).

## Verification

- Expired access token → `401`, never `500` (`FM-BACKEND-006` neighbour case).
- Tampered signature → `401`.
- Refresh rotation: the old token fails after one use.
- Reuse detection: presenting a consumed refresh token revokes the device family.
- Concurrency: N simultaneous 401s produce exactly one refresh call.
- Storage assertion: no token appears in `SharedPreferences`, Room, or
  `localStorage`.

> `NOT VERIFIED — no authentication code, key material, or tests exist. Reuse
> detection and the single-flight refresh are design claims that have never been
> executed or raced.`

## Interview notes

**The trap:** "JWTs are stateless, so we don't need a database for auth." Then:
how do you log someone out? The honest answer is that you cannot revoke a
stateless access token, and pretending otherwise is the tell.

**The senior answer:** Use two token types because they have different jobs. The
access token is a short-lived JWT — stateless validation, no database on the hot
path, and its irrevocability is bounded to 15 minutes by design rather than
wished away. The refresh token is deliberately **opaque and stored server-side**,
because that is what makes revocation possible at all; a JWT refresh token would
be unrevocable and defeat its own purpose. Rotate it on every use, and treat
presentation of an already-consumed token as theft — revoke the whole device
family. The subtlety worth raising unprompted: rotation creates a race on the
client. Five in-flight requests get 401 together, five refreshes fire, four
present a token the first already invalidated, and your reuse detection logs the
user out. So the client must single-flight refresh — the server-side security
control and the client-side concurrency behaviour are one design, not two.
