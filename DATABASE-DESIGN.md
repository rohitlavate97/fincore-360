# DATABASE DESIGN — FinCore 360

**Phase:** Complete & Audited — All migrations (V1 through V5) active and verified against real PostgreSQL.
See [PROJECT-STATUS.md](PROJECT-STATUS.md) and [AUDIT.md](AUDIT.md).

Governed by [ADR-007](docs/adr/ADR-007-PostgreSQL-Primary-DB.md),
[ADR-012](docs/adr/ADR-012-Monetary-Representation.md),
[ADR-017](docs/adr/ADR-017-Flyway-Migrations.md).

---

## 1. Universal column conventions

Every table:

| Column | Type | Rule |
|---|---|---|
| `id` | `UUID` | Primary key. **Not** a sequential integer — sequential IDs leak record volume and permit enumeration attacks. |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | Always timezone-aware |
| `updated_at` | `TIMESTAMPTZ NOT NULL` | Maintained by the application |
| `version` | `BIGINT` | JPA `@Version` — optimistic lock, where appropriate |

`TIMESTAMPTZ`, never `TIMESTAMP`. A naive timestamp in a financial system is a
defect waiting for a daylight-saving boundary.

### Financial columns

| Concern | Type |
|---|---|
| Monetary amount | `NUMERIC(19,4)` |
| Currency | `CHAR(3)` — ISO 4217 |

`NUMERIC` is exact decimal arithmetic. `REAL`/`DOUBLE PRECISION` are **banned**
for money at the schema level, not merely by convention
([ADR-012](docs/adr/ADR-012-Monetary-Representation.md)).

Amount and currency are always stored together. A bare amount is meaningless.

---

## 2. Balance model

Two balances, deliberately distinct:

| Balance | Meaning |
|---|---|
| **Ledger balance** | Sum of all settled transactions |
| **Available balance** | Ledger balance minus pending holds |

Transfers validate against **available**, not ledger. Validating against ledger
permits spending money that is already committed to a pending transfer.

**Storage approach:** maintain an explicit balance column, updated under a
pessimistic row lock, with the transaction ledger as the reconcilable source of
truth. The alternative — deriving the balance by summing the ledger on every read
— is correct but scans an ever-growing table on the hottest query in the system.

**Consequence accepted:** an explicit balance can drift from the ledger if any
write path bypasses the locked update. A periodic reconciliation job comparing
`SUM(ledger)` against the balance column is therefore required, and its absence
is a real gap until Phase 5.

---

## 3. Concurrency control

| Situation | Mechanism |
|---|---|
| Balance debit/credit | **Pessimistic** — `SELECT … FOR UPDATE` on the account row |
| Ordinary entity update | **Optimistic** — `version` column |
| Concurrent duplicate idempotency key | Unique constraint + row lock |

### Deterministic lock ordering — required before Phase 5

Two transfers in opposite directions between the same pair of accounts will
**deadlock** if each locks its own source account first:

```
Transfer 1 (A → B):  lock A ... wait for B
Transfer 2 (B → A):  lock B ... wait for A     ← deadlock
```

**Rule:** any operation locking more than one account locks rows in ascending
`account_id` order, regardless of which is source and which is destination.

```sql
SELECT * FROM accounts
 WHERE id IN (:sourceId, :destinationId)
 ORDER BY id
   FOR UPDATE;
```

This is not optional and not a micro-optimisation. Without it, opposing transfers
between two accounts deadlock under load — an intermittent production failure
that is hard to reproduce and easy to misdiagnose.

`PLANNED — not implemented.`

---

## 4. Core tables

Illustrative shape. Column lists are not final; no DDL has been written.

### `accounts`

```
id                UUID PK
customer_id       UUID FK → customers(id)
account_number    VARCHAR UNIQUE          -- simulated
account_type      VARCHAR                 -- CHECKING | SAVINGS
status            VARCHAR                 -- ACTIVE | FROZEN | CLOSED
currency          CHAR(3)
ledger_balance    NUMERIC(19,4) NOT NULL
available_balance NUMERIC(19,4) NOT NULL
version           BIGINT
created_at        TIMESTAMPTZ
updated_at        TIMESTAMPTZ
```

### `transactions`

```
id                  UUID PK
idempotency_key     UUID                  -- see idempotency_keys
source_account_id   UUID FK
dest_account_id     UUID FK
type                VARCHAR               -- DEPOSIT | WITHDRAWAL | TRANSFER
                                          -- | PAYMENT | REVERSAL | REFUND
status              VARCHAR               -- PENDING | PROCESSING | COMPLETED
                                          -- | FAILED | CANCELLED | REVERSED
amount              NUMERIC(19,4) NOT NULL
currency            CHAR(3) NOT NULL
created_by          UUID                  -- actor
correlation_id      UUID
created_at          TIMESTAMPTZ
updated_at          TIMESTAMPTZ
```

Status transitions are validated in the domain layer, not by a check constraint —
the rule is a state machine, not a value range
([BACKEND-ARCHITECTURE.md](BACKEND-ARCHITECTURE.md) §5).

### `idempotency_keys`

```
id                UUID PK
key               UUID NOT NULL
user_id           UUID NOT NULL
endpoint          VARCHAR NOT NULL
state             VARCHAR               -- IN_PROGRESS | COMPLETE
response_status   INT
response_body     JSONB
expires_at        TIMESTAMPTZ NOT NULL
created_at        TIMESTAMPTZ

UNIQUE (key, user_id, endpoint)         -- ← the concurrency control
```

The unique constraint is the mechanism, not a data-quality nicety. Two concurrent
requests with the same key: one insert wins, the other fails and reads the
winner's row ([ADR-010](docs/adr/ADR-010-Idempotency-Strategy.md)).

Scoping by `user_id` prevents one customer replaying another's response.

A scheduled purge of expired keys is **required** — otherwise this table grows
without bound on the hot path of every mutation.

### `audit_events` — append-only

```
event_id        UUID PK
event_type      VARCHAR NOT NULL
actor_id        UUID
actor_role      VARCHAR              -- role AT TIME OF ACTION
resource_type   VARCHAR
resource_id     UUID
outcome         VARCHAR              -- SUCCESS | FAILURE
reason          TEXT
ip_address      INET
user_agent      TEXT
correlation_id  UUID
timestamp       TIMESTAMPTZ NOT NULL
```

`actor_role` is denormalised deliberately. Roles change; a record that cannot be
interpreted years later is not an audit trail.

**Append-only enforcement — database level:**

```sql
CREATE OR REPLACE FUNCTION audit_immutable() RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_no_update BEFORE UPDATE ON audit_events
  FOR EACH ROW EXECUTE FUNCTION audit_immutable();
CREATE TRIGGER audit_no_delete BEFORE DELETE ON audit_events
  FOR EACH ROW EXECUTE FUNCTION audit_immutable();
```

Application-level rules alone are insufficient: an attacker who controls the
application would then also control the log of what they did
([ADR-014](docs/adr/ADR-014-RBAC-Authorization.md)).

Partitioning by month is planned **only if volume demands it** — premature
otherwise.

### `refresh_tokens`

```
id           UUID PK
user_id      UUID NOT NULL
device_id    VARCHAR NOT NULL
token_hash   VARCHAR NOT NULL       -- HASHED, never plaintext
expires_at   TIMESTAMPTZ NOT NULL
revoked_at   TIMESTAMPTZ
created_at   TIMESTAMPTZ

UNIQUE (user_id, device_id)         -- one active token per device
```

Stored hashed. A plaintext refresh token table is a credential dump waiting to
happen ([ADR-013](docs/adr/ADR-013-JWT-Auth-Model.md)).

### `outbox_events` — Phase 7 & C-4 / H-8

Required by [ADR-009](docs/adr/ADR-009-Kafka-Async-Events.md) to solve the dual
write between the database commit and the Kafka publish. Written inside the
business transaction; relayed asynchronously using `SELECT ... FOR UPDATE SKIP LOCKED`:

```
id             UUID PK
event_type     VARCHAR(100) NOT NULL
aggregate_type VARCHAR(50) NOT NULL
aggregate_id   UUID NOT NULL
payload        JSONB NOT NULL
status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
retry_count    INT NOT NULL DEFAULT 0
created_at     TIMESTAMPTZ NOT NULL
processed_at   TIMESTAMPTZ
```

### `ledger_entries` — V5 & M-7 (Double-Entry Ledger)

Immutable double-entry book of accounts capturing balanced debits and credits for all settled transactions:

```
id              UUID PK
transaction_id  UUID NOT NULL REFERENCES transactions(id)
account_id      UUID NOT NULL REFERENCES accounts(id)
amount          NUMERIC(19,4) NOT NULL
currency        CHAR(3) NOT NULL
direction       VARCHAR(10) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT'))
running_balance NUMERIC(19,4) NOT NULL
created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
```

Zero-sum invariant verified by reconciliation integration tests:
`SUM(amount WHERE direction = 'DEBIT') == SUM(amount WHERE direction = 'CREDIT')`

---

## 5. Indexing

Rules:

- Indexes are defined **explicitly**. ORM defaults are never relied upon.
- Composite indexes are ordered by selectivity, high → low.
- Covering indexes for read-heavy paths.
- An `EXPLAIN` plan is reviewed for every query touching a large table.

Verified index set:

| Table | Index | Serves |
|---|---|---|
| `transactions` | `(source_account_id, created_at DESC)` | Paginated history — the hottest read |
| `transactions` | `(correlation_id)` | Tracing a request end to end |
| `audit_events` | `(actor_id, timestamp DESC)` | Auditor queries |
| `audit_events` | `(resource_type, resource_id, timestamp DESC)` | "What happened to this account?" |
| `idempotency_keys` | `(expires_at)` partial, `WHERE state = 'COMPLETE'` | Purge job |
| `accounts` | `(customer_id)` | Account list |
| `ledger_entries` | `(account_id, created_at DESC)` | Account running balance reconciliation |
| `ledger_entries` | `(transaction_id)` | Double-entry zero-sum invariant audits |

> `VERIFIED — all indexes defined via Flyway DDL and validated in schema tests.`

---

## 6. Pagination

Offset pagination (`page`, `size`) is acceptable for admin screens.

**Keyset (cursor) pagination is required for transaction history.** `OFFSET`
degrades linearly — the database still walks the skipped rows — and it produces
duplicate or skipped records when new transactions arrive between page requests.
On a list ordered by newest-first, which is exactly the transaction list, that is
guaranteed to happen.

```sql
-- keyset, ordered by (created_at DESC, id DESC)
WHERE (created_at, id) < (:lastCreatedAt, :lastId)
ORDER BY created_at DESC, id DESC
LIMIT :size
```

Sort and filter fields are restricted by an explicit **allowlist**. Accepting an
arbitrary column name from a query parameter is SQL injection with extra steps.

---

## 7. Migrations

Per [ADR-017](docs/adr/ADR-017-Flyway-Migrations.md):

- Flyway, versioned, sequential, plain SQL
- **No manual schema change on any environment** — including local
- Migrations are **immutable once merged**; fix forward with a new migration
- `ddl-auto=validate` everywhere — Hibernate verifies, never modifies
- A rollback script accompanies every non-trivial migration, and is exercised in
  CI rather than merely present
- **Backward compatible during rolling deploys.** Old and new application
  versions run against the same schema simultaneously, so a dropped or renamed
  column breaks the version still running. Expand-and-contract across two
  releases.
- Large-table changes avoid long locks: `CREATE INDEX CONCURRENTLY`; add
  `NOT NULL` columns via add → backfill → constrain, never in one statement.

---

## 8. Open items

| Item | Needed by |
|---|---|
| Full DDL for all tables | Phase 1 |
| Balance reconciliation job (ledger vs balance column) | Phase 5 |
| Idempotency key purge job | Phase 5 |
| Outbox table + relay | Phase 7 |
| Audit partitioning decision | When volume demands it |
| Real `EXPLAIN` plans replacing §5 predictions | Phase 4 |
