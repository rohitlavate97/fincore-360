-- FinCore 360 — notifications schema (ADR-008, Phase 8)
--
-- Stores customer notifications generated from asynchronous domain events
-- and system alerts, with deep links to affected domain entities.

CREATE TABLE notifications (
    id            UUID PRIMARY KEY,
    customer_id   UUID         NOT NULL REFERENCES customers (id),
    title         VARCHAR(200) NOT NULL,
    body          TEXT         NOT NULL,
    type          VARCHAR(50)  NOT NULL,
    deep_link_uri VARCHAR(500),
    status        VARCHAR(20)  NOT NULL DEFAULT 'UNREAD',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at       TIMESTAMPTZ,
    CONSTRAINT notifications_type_check CHECK (type IN ('TRANSACTION_ALERT', 'SECURITY_ALERT', 'SYSTEM')),
    CONSTRAINT notifications_status_check CHECK (status IN ('UNREAD', 'READ'))
);

CREATE INDEX idx_notifications_customer ON notifications (customer_id, status, created_at DESC);
