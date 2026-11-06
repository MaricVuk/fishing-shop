-- V10: background jobs (Phase 4, ADR 0006). The table is both the outbox and the queue: a job is
-- inserted in the same transaction as the order, and a worker claims it with FOR UPDATE SKIP LOCKED.
-- There is no RUNNING status on purpose: a job being worked on is a locked row, so a worker that dies
-- releases it with its connection.

CREATE TABLE background_jobs (
    id              BIGSERIAL    PRIMARY KEY,
    type            VARCHAR(40)  NOT NULL,
    order_id        BIGINT       NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    CONSTRAINT chk_background_jobs_type CHECK (type IN ('ORDER_CONFIRMATION_EMAIL')),
    CONSTRAINT chk_background_jobs_status CHECK (status IN ('PENDING', 'DONE', 'DEAD')),
    CONSTRAINT chk_background_jobs_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_background_jobs_error_length CHECK (char_length(last_error) <= 2000),
    CONSTRAINT chk_background_jobs_finished CHECK ((status = 'PENDING') = (finished_at IS NULL)),
    CONSTRAINT chk_background_jobs_dead_error CHECK (status <> 'DEAD' OR last_error IS NOT NULL),
    CONSTRAINT uq_background_jobs_order_type UNIQUE (order_id, type)
);

CREATE INDEX idx_background_jobs_due ON background_jobs (next_attempt_at, id) WHERE status = 'PENDING';
