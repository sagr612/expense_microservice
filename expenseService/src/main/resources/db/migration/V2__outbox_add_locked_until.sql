ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_outbox_claimable
    ON outbox_events(status, next_retry_at, locked_until, created_at );