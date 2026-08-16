ALTER TABLE outbox_events
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN last_error     TEXT,
    ADD COLUMN status         VARCHAR(20) NOT NULL DEFAULT 'PENDING';

UPDATE outbox_events
SET status = 'PUBLISHED'
WHERE published = TRUE;

CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox_events(status, next_retry_at, created_at);

DROP INDEX IF EXISTS idx_outbox_unpublished;
