-- FinCore 360 — Users and Authentication schema (Phase 3)

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(320) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    roles           VARCHAR(255) NOT NULL DEFAULT 'ROLE_CUSTOMER',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    customer_id     UUID         REFERENCES customers(id),
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'LOCKED', 'SUSPENDED'))
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email    ON users (email);
CREATE INDEX idx_users_customer ON users (customer_id);

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
