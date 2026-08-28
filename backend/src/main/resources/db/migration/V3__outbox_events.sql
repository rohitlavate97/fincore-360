-- FinCore 360 — outbox events schema (ADR-009, DATABASE-DESIGN.md §4)
--
-- Transactional outbox table to eliminate the dual-write problem between
-- database state commits and asynchronous event dispatching.

CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    event_type     VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    actor_id       UUID,
    correlation_id UUID         NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    retry_count    INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    CONSTRAINT outbox_status_check CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_pending ON outbox_events (status, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_correlation ON outbox_events (correlation_id);
