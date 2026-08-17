CREATE TABLE IF NOT EXISTS expenses (
    id VARCHAR(36) PRIMARY KEY,
    version BIGINT,
    transaction_date DATE NOT NULL,
    external_id VARCHAR(255) NOT NULL ,
    user_id VARCHAR(255) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    type VARCHAR(50) NOT NULL,
    category VARCHAR(100) NOT NULL,
    title VARCHAR(100) NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT uk_user_external UNIQUE (user_id, external_id)
    );

CREATE TABLE IF NOT EXISTS budget (
                                      id BIGSERIAL PRIMARY KEY,
                                      user_id VARCHAR(255),
    category VARCHAR(255),
    budget_month INTEGER NOT NULL,
    budget_year INTEGER NOT NULL,
    amount NUMERIC(19,2),
    last_alert_threshold INTEGER,
    version BIGINT,
    CONSTRAINT uk_budget_user_cat_month_year UNIQUE (user_id, category, budget_month, budget_year)
    );

CREATE TABLE IF NOT EXISTS outbox_events (
                                             id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(255),
    event_type VARCHAR(255),
    payload TEXT,
    created_at TIMESTAMP,
    published BOOLEAN,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_error TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    );

CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events (status, next_retry_at, created_at);

CREATE TABLE IF NOT EXISTS processed_events (
                                                event_id VARCHAR(100) PRIMARY KEY,
    consumer_group VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_processed_at ON processed_events (processed_at);

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
