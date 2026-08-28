-- FinCore 360 — baseline schema
--
-- Conventions (DATABASE-DESIGN.md §1):
--   UUID primary keys      — not sequential; sequential IDs leak volume and
--                            permit enumeration
--   TIMESTAMPTZ            — never naive TIMESTAMP
--   NUMERIC(19,4) for money — exact decimal arithmetic (ADR-012)
--   CHAR(3) for currency   — ISO 4217

-- ── customers ────────────────────────────────────────────────────────────
CREATE TABLE customers (
    id          UUID PRIMARY KEY,
    email       VARCHAR(320) NOT NULL UNIQUE,
    full_name   VARCHAR(200) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT customers_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

-- ── accounts ─────────────────────────────────────────────────────────────
CREATE TABLE accounts (
    id                UUID PRIMARY KEY,
    customer_id       UUID           NOT NULL REFERENCES customers (id),
    account_number    VARCHAR(34)    NOT NULL UNIQUE,
    account_type      VARCHAR(20)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    currency          CHAR(3)        NOT NULL,
    ledger_balance    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT accounts_type_check   CHECK (account_type IN ('CHECKING', 'SAVINGS')),
    CONSTRAINT accounts_status_check CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    -- The invariant the concurrency design exists to protect (ADR-007).
    -- Enforced here so no code path can bypass it.
    CONSTRAINT accounts_available_non_negative CHECK (available_balance >= 0)
);

CREATE INDEX idx_accounts_customer ON accounts (customer_id);

-- ── transactions ─────────────────────────────────────────────────────────
CREATE TABLE transactions (
    id                UUID PRIMARY KEY,
    idempotency_key   UUID,
    source_account_id UUID           REFERENCES accounts (id),
    dest_account_id   UUID           REFERENCES accounts (id),
    type              VARCHAR(20)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    currency          CHAR(3)        NOT NULL,
    created_by        UUID,
    correlation_id    UUID,
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT transactions_type_check CHECK (
        type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'PAYMENT', 'REVERSAL', 'REFUND')
    ),
    -- Which statuses exist is a value constraint and belongs here. Which
    -- TRANSITIONS are legal is a state machine and belongs in the domain layer
    -- (ARCHITECTURE.md §5) — a CHECK constraint cannot express it.
    CONSTRAINT transactions_status_check CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED')
    ),
    CONSTRAINT transactions_amount_positive CHECK (amount > 0)
);

-- Composite ordered by selectivity: the hottest read is one account's history,
-- newest first. Supports keyset pagination (DATABASE-DESIGN.md §6).
CREATE INDEX idx_transactions_source_created ON transactions (source_account_id, created_at DESC, id DESC);
CREATE INDEX idx_transactions_dest_created   ON transactions (dest_account_id, created_at DESC, id DESC);
CREATE INDEX idx_transactions_correlation    ON transactions (correlation_id);

-- ── idempotency_keys ─────────────────────────────────────────────────────
CREATE TABLE idempotency_keys (
    id              UUID PRIMARY KEY,
    key             UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    endpoint        VARCHAR(200) NOT NULL,
    state           VARCHAR(20) NOT NULL,
    response_status INT,
    response_body   JSONB,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT idempotency_state_check CHECK (state IN ('IN_PROGRESS', 'COMPLETE')),
    -- THIS is the concurrency control, not a data-quality nicety (ADR-010).
    -- Two concurrent requests with the same key: one insert wins, the other
    -- fails and reads the winner's row. Scoping by user prevents one customer
    -- replaying another's response.
    CONSTRAINT idempotency_key_unique UNIQUE (key, user_id, endpoint)
);

-- Supports the expiry purge job. Without it this table grows without bound on
-- the hot path of every mutation.
CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at);

-- ── refresh_tokens ───────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL,
    device_id   VARCHAR(200) NOT NULL,
    -- HASHED, never plaintext. A plaintext refresh token table is a credential
    -- dump waiting to happen (ADR-013).
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT refresh_tokens_device_unique UNIQUE (user_id, device_id)
);

-- ── audit_events — APPEND ONLY ───────────────────────────────────────────
CREATE TABLE audit_events (
    event_id       UUID PRIMARY KEY,
    event_type     VARCHAR(60) NOT NULL,
    actor_id       UUID,
    -- Denormalised deliberately: roles change, and a record that cannot be
    -- interpreted years later is not an audit trail.
    actor_role     VARCHAR(30),
    resource_type  VARCHAR(60),
    resource_id    UUID,
    outcome        VARCHAR(10) NOT NULL,
    reason         TEXT,
    ip_address     INET,
    user_agent     TEXT,
    correlation_id UUID,
    timestamp      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT audit_outcome_check CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_audit_actor     ON audit_events (actor_id, timestamp DESC);
CREATE INDEX idx_audit_resource  ON audit_events (resource_type, resource_id, timestamp DESC);
CREATE INDEX idx_audit_correlation ON audit_events (correlation_id);

-- Immutability enforced by the DATABASE, not only by application code.
-- An attacker who controls the application must not also control the log of
-- what they did (ADR-014).
CREATE OR REPLACE FUNCTION audit_events_immutable() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_no_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_immutable();

CREATE TRIGGER audit_events_no_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_immutable();
