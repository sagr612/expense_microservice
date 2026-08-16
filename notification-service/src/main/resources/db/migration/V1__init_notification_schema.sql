CREATE TABLE IF NOT EXISTS notifications (
    id          VARCHAR(36)  PRIMARY KEY,
    user_id     VARCHAR(100) NOT NULL ,
    title       VARCHAR(255) NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id) WHERE is_read = FALSE;

CREATE TABLE IF NOT EXISTS push_tokens (
    id          VARCHAR(36)  PRIMARY KEY,
    user_id     VARCHAR(100) NOT NULL UNIQUE,
    expo_token  VARCHAR(255) NOT NULL UNIQUE,
    updated_at  TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id       VARCHAR(100) PRIMARY KEY,
    consumer_group VARCHAR(100) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_processed_at ON processed_events(processed_at);
