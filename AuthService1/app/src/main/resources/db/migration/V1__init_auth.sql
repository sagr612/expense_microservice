-- =====================================================================
-- AuthService — initial schema
-- Covers: users, user_roles (element collection), tokens (refresh tokens),
-- outbox_events.
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
    user_id      VARCHAR(100) PRIMARY KEY,
    username     VARCHAR(100) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20)  NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ
);

-- @ElementCollection Set<UserRole> maps to this join table.
CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(100) NOT NULL,
    role    VARCHAR(50)  NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles(user_id);

CREATE TABLE IF NOT EXISTS tokens (
    id           BIGSERIAL   PRIMARY KEY,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    expiry_date  TIMESTAMPTZ NOT NULL,
    user_id      VARCHAR(100) UNIQUE,
    CONSTRAINT fk_tokens_user
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);


CREATE INDEX IF NOT EXISTS idx_tokens_expiry ON tokens(expiry_date);

CREATE TABLE IF NOT EXISTS outbox_events (
    id            VARCHAR(36) PRIMARY KEY,
    aggregate_id  VARCHAR(100),
    event_type    VARCHAR(100),
    payload       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published     BOOLEAN     NOT NULL DEFAULT FALSE
    );

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON outbox_events(created_at)
    WHERE published = FALSE;
