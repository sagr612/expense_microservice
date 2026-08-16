-- Add retry-tracking fields to outbox_events for hardened publisher.
ALTER TABLE outbox_events
    ADD COLUMN attempt_count  INT         NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN last_error     TEXT,
    ADD COLUMN status         VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- Backfill: any row that was already published becomes PUBLISHED status.
UPDATE outbox_events
SET status = 'PUBLISHED'
WHERE published = TRUE;

-- Poller hot-path index (matches @Index in the entity).
CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox_events(status, next_retry_at, created_at);

-- Drop the old partial index; it targeted the boolean column and is now redundant.
DROP INDEX IF EXISTS idx_outbox_unpublished;
