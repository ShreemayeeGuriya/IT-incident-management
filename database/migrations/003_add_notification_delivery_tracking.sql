-- Track idempotent notification events and bounded simulated-email retries.
BEGIN;

ALTER TABLE notifications
    ADD COLUMN event_key VARCHAR(160),
    ADD COLUMN delivery_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_delivery_error TEXT,
    ADD CONSTRAINT chk_notifications_delivery_attempts
        CHECK (delivery_attempts >= 0);

CREATE UNIQUE INDEX uq_notifications_event_key
    ON notifications(event_key)
    WHERE event_key IS NOT NULL;

COMMIT;
