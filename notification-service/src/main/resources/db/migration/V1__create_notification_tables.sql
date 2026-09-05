-- Idempotency table — prevents duplicate notifications
CREATE TABLE processed_events (
                                  event_id        VARCHAR(255)    PRIMARY KEY,
                                  event_type      VARCHAR(100)    NOT NULL,
                                  processed_at    TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Notification log — audit trail of every notification sent
-- Useful for debugging "I didn't receive the email" complaints
CREATE TABLE notification_log (
                                  id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
                                  event_id        VARCHAR(255)    NOT NULL,
                                  event_type      VARCHAR(100)    NOT NULL,
                                  recipient_email VARCHAR(255)    NOT NULL,
                                  subject         VARCHAR(500)    NOT NULL,
                                  status          VARCHAR(20)     NOT NULL DEFAULT 'SENT',
                                  error_message   TEXT,
                                  sent_at         TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_log_event
    ON notification_log(event_id);

CREATE INDEX idx_notification_log_recipient
    ON notification_log(recipient_email, sent_at DESC);