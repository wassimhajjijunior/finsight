CREATE TABLE outbox_events (
                               id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                               aggregate_id    VARCHAR(255) NOT NULL,
                               aggregate_type  VARCHAR(100) NOT NULL,
                               event_type      VARCHAR(100) NOT NULL,
                               payload         JSONB        NOT NULL,
                               published       BOOLEAN      NOT NULL DEFAULT FALSE,
                               created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
                               published_at    TIMESTAMP
);

-- Relay queries for unpublished events ordered by creation time
CREATE INDEX idx_outbox_unpublished
    ON outbox_events(published, created_at)
    WHERE published = false;