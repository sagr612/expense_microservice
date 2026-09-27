ALTER TABLE outbox_events
    ADD COLUMN locked_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_outbox_claimable
    ON outbox_events(status, next_retry_at, locked_until, created_at);
