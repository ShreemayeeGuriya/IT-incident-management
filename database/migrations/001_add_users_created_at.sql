-- Safe, additive migration for existing databases. The default backfills
-- existing users and applies to future rows; no user data is removed.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
