-- Ensure users.phone_number is varchar
ALTER TABLE users
ALTER COLUMN phone_number TYPE VARCHAR(20)
    USING phone_number::VARCHAR;

-- Outbox leasing column
ALTER TABLE outbox_events
    ADD COLUMN locked_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_outbox_claimable
    ON outbox_events(status, next_retry_at, locked_until, created_at);
