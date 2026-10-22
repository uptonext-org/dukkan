-- B-51: deleted (anonymised) accounts are marked, so late events (e.g. order notifications still in
-- the outbox) skip them.
ALTER TABLE app_user ADD COLUMN deleted_at timestamptz;
