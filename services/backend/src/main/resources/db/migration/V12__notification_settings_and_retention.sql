-- B-37a: per-user switch for push messages (the inbox always fills), and indexes for the nightly
-- retention jobs on published events and read notifications.
CREATE TABLE notification_setting (
    user_id      uuid PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    push_enabled boolean     NOT NULL,
    updated_at   timestamptz NOT NULL
);
CREATE INDEX ix_outbox_event_published ON outbox_event (published_at) WHERE published_at IS NOT NULL;
CREATE INDEX ix_processed_event_at ON processed_event (processed_at);
CREATE INDEX ix_notification_read ON notification (read_at) WHERE read_at IS NOT NULL;
