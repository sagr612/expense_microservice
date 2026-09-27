CREATE TABLE IF NOT EXISTS failed_events (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255),
    partition INTEGER,
    record_offset BIGINT,
    event_type VARCHAR(255),
    payload TEXT,
    error_message VARCHAR(1000),
    stack_trace TEXT,
    status VARCHAR(50),
    retry_count INTEGER,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);