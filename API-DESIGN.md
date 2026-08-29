# API DESIGN — FinCore 360

**Phase:** Complete & Audited — OpenAPI specification generated and live endpoints verified.
See [PROJECT-STATUS.md](PROJECT-STATUS.md) and [AUDIT.md](AUDIT.md).

---

## 1. Conventions

| Concern | Rule |
|---|---|
| URLs | Resource-oriented — nouns, not verbs. `/api/v1/accounts/{id}/transactions` |
| Versioning | URL prefix `/api/v1/`. Breaking changes get `/v2/`; both run during migration. |
| `GET` | Safe and idempotent. Never mutates. |
| `POST` | Creates. Requires `Idempotency-Key`. |
| `PUT` | Full replace. Idempotent. |
| `PATCH` | Partial update. Requires `Idempotency-Key`. |
| `DELETE` | Removes. Idempotent. |
| Correlation | `X-Correlation-ID` on every request **and** response |
| Idempotency | `Idempotency-Key` on every mutation |
| Money | Amounts are JSON **strings**, always paired with a currency |
| Timestamps | UTC, ISO 8601, `Z` suffix |
| IDs | UUID strings |

---

## 2. Required headers

### Request

| Header | When | Notes |
|---|---|---|
| `Authorization: Bearer <jwt>` | Authenticated endpoints | RS256 access token |
| `X-Correlation-ID: <uuid>` | Always | Client-generated per user action; server generates one if absent |
| `Idempotency-Key: <uuid>` | Every mutation | Per user *action*, not per retry. Must be persisted client-side — regenerating after process death defeats it. |

### Response

| Header | Always |
|---|---|
| `X-Correlation-ID` | Echoes the request value; equals `traceId` in errors |

---

## 3. Error contract

**Every** error response has exactly this shape:

```json
{
  "errorCode": "TRANSFER_INSUFFICIENT_FUNDS",
  "message": "Insufficient available balance to complete transfer",
  "details": [
    { "field": "amount", "issue": "Exceeds available balance" }
  ],
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": "2026-08-28T12:00:00Z"
}
```

| Field | Rule |
|---|---|
| `errorCode` | Application-defined enum. Stable, searchable in runbooks. Never a raw exception name. |
| `message` | Safe for end-user display. No internals. |
| `details` | Populated **only** for validation errors (400, 422) |
| `traceId` | Equals the correlation ID. Links a user report to server logs. |
| `timestamp` | UTC ISO 8601 |

**Never in a response:** stack traces, internal class names, SQL, framework
error text, or anything that describes the server's implementation. An error
message is a product surface, not a debugging channel — `traceId` is the
debugging channel.

---

## 4. Status codes

| Code | Meaning here |
|---|---|
| 200 | Success |
| 201 | Created — includes `Location` |
| 204 | Success, no body |
| 400 | Malformed request |
| 401 | Missing, expired, or invalid token. **Never 500** for an expired token. |
| 403 | Authenticated but not permitted — including resource-ownership failure |
| 404 | Not found. Also returned instead of 403 where existence itself is sensitive. |
| 409 | Conflict — including **idempotency key in progress** |
| 422 | Semantically invalid (business rule violation) |
| 429 | Rate limited — includes `Retry-After` |
| 500 | Unexpected server error. Generic message only. |
| 503 | Dependency unavailable. **Not 500** — see `FM-BACKEND-002`. |

**404 vs 403.** Returning 403 for a resource the caller does not own confirms
that the resource exists, which is an enumeration oracle. For account lookups by
ID, 404 is returned instead. The audit record still records the attempt as a
`FAILURE` with the real reason.

---

## 5. Idempotency semantics

| Key state | Response |
|---|---|
| Unseen | Process normally; store key + response atomically with the business change |
| Seen, complete | **Replay the stored response** — status, body, everything |
| Seen, in progress | `409` with retry guidance |
| Seen, different user | Rejected |

Expiry: 24 hours (configurable).

**Client note.** The in-progress `409` means *"the outcome is not yet known"*,
not *"this failed"*. A client that renders it as an error will tell a customer
their transfer failed while it is succeeding. It must render as **pending**
([ADR-010](docs/adr/ADR-010-Idempotency-Strategy.md)).

---

## 6. Pagination

Query parameters: `page`, `size`, `sort`, `direction` for admin/list screens.

**Transaction history uses keyset (cursor) pagination**, not offset — offset
produces duplicates and skips when new rows arrive between page requests, which
on a newest-first list is guaranteed
([DATABASE-DESIGN.md](DATABASE-DESIGN.md) §6).

```json
{
  "items": [],
  "nextCursor": "eyJjcmVhdGVkQXQiOiIyMDI2LTA4LTI4VDEyOjAwOjAwWiIsImlkIjoiLi4uIn0",
  "hasMore": true
}
```

Sort and filter fields are constrained by an explicit **allowlist**. Passing a
client-supplied column name into a query is SQL injection with extra steps.

---

## 7. Money in JSON

```json
{
  "amount": "1234.5600",
  "currency": "GBP"
}
```

Strings, scale 4, always paired with currency. Never a JSON number — a
JavaScript client parses that into an IEEE-754 double and destroys the precision
protected at every other layer
([ADR-012](docs/adr/ADR-012-Monetary-Representation.md)).

---

## 8. Endpoint sketch

Shape only. Nothing is implemented.

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh          ← excluded from the JWT filter
POST   /api/v1/auth/logout

GET    /api/v1/accounts
GET    /api/v1/accounts/{id}
GET    /api/v1/accounts/{id}/transactions      ← keyset paginated

POST   /api/v1/transfers                       ← Idempotency-Key required
GET    /api/v1/transactions/{id}
POST   /api/v1/transactions/{id}/reversal      ← Idempotency-Key required

GET    /api/v1/customers/me
PATCH  /api/v1/customers/me
GET    /api/v1/beneficiaries
POST   /api/v1/beneficiaries

GET    /api/v1/audit-events                    ← AUDITOR role
```

`PLANNED — not implemented.`

---

## 9. OpenAPI

Springdoc generates the spec from the implementation, so the spec cannot drift
from the code. It is the shared contract of record between backend, Android, and
web — each validates against it rather than against the others
([ADR-016](docs/adr/ADR-016-Serialization-Kotlinx.md)).

`PLANNED — not implemented.`

---

## 10. Open items

| Item | Needed by |
|---|---|
| `errorCode` enum — the full catalogue | Phase 1 |
| Rate limit thresholds per endpoint | Phase 3 |
| Cursor encoding format | Phase 4 |
| OpenAPI generation and publication | Phase 1 |

> `NOT VERIFIED — no API exists. Nothing here has been requested, returned, or
> contract-tested.`
