CREATE TABLE IF NOT EXISTS telegram_notification_outbox (
    event_id VARCHAR(128) PRIMARY KEY,
    chat_id VARCHAR(64) NOT NULL,
    message_text VARCHAR(4000) NOT NULL,
    state VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL,
    lease_until TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    last_failure_kind VARCHAR(32)
);

CREATE INDEX IF NOT EXISTS idx_telegram_outbox_due
    ON telegram_notification_outbox (state, next_attempt_at, created_at);
