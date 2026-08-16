-- =====================================================================
-- userService — initial schema
-- Translates JPA entities sagar.userService.entities.UserInfo and
-- sagar.userService.entities.OutboxEvent into an authoritative DDL.
-- After applying, spring.jpa.hibernate.ddl-auto is set to 'validate' so
-- Hibernate NEVER mutates the schema — Flyway owns it.
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
    id                       VARCHAR(36)  PRIMARY KEY,
    user_id                  VARCHAR(100) NOT NULL UNIQUE,
    first_name               VARCHAR(100) NOT NULL,
    last_name                VARCHAR(100) NOT NULL,
    phone_number             BIGINT       NOT NULL UNIQUE,
    email                    VARCHAR(255) NOT NULL UNIQUE,
    profile_pic              VARCHAR(500),
    profile_image_public_id  VARCHAR(255),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ
    );

-- helpful lookup indexes (already implied by UNIQUE, but explicit for docs)
CREATE INDEX IF NOT EXISTS idx_users_email        ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone_number ON users(phone_number);

-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS outbox_events (
    id            VARCHAR(36) PRIMARY KEY,
    aggregate_id  VARCHAR(100),
    event_type    VARCHAR(100),
    payload       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published     BOOLEAN     NOT NULL DEFAULT FALSE
    );

-- the poller does: WHERE published = false ORDER BY created_at ASC LIMIT 50
-- Partial index makes that scan cheap even when the outbox has millions of rows
-- of already-published events.
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON outbox_events(created_at)
    WHERE published = FALSE;
