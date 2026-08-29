-- FinCore 360 — double-entry ledger schema (M-7)
--
-- Immutable double-entry bookkeeping ledger: records debit and credit movements
-- with running balance snapshot for point-in-time reconstruction and reconciliation.

CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY,
    transaction_id  UUID           NOT NULL REFERENCES transactions(id),
    account_id      UUID           NOT NULL REFERENCES accounts(id),
    direction       VARCHAR(6)     NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    running_balance NUMERIC(19, 4) NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ledger_direction_check CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_ledger_account_created ON ledger_entries(account_id, created_at DESC, id DESC);
CREATE INDEX idx_ledger_transaction ON ledger_entries(transaction_id);
