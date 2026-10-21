-- B-37: transactional outbox for domain events, idempotent consumers, device tokens for push and
-- the in-app notification inbox.

CREATE TABLE outbox_event (
    id              uuid PRIMARY KEY,
    -- Insertion order; created_at alone ties for events of one transaction.
    seq             bigserial    NOT NULL,
    type            varchar(60)  NOT NULL,
    aggregate_id    uuid         NOT NULL,
    payload         jsonb        NOT NULL,
    created_at      timestamptz  NOT NULL,
    next_attempt_at timestamptz  NOT NULL,
    attempts        integer      NOT NULL DEFAULT 0,
    last_error      varchar(500),
    published_at    timestamptz
);
-- The publisher only scans what is still to send.
CREATE INDEX ix_outbox_event_pending ON outbox_event (next_attempt_at, seq) WHERE published_at IS NULL;

CREATE TABLE processed_event (
    event_id     uuid        NOT NULL,
    handler      varchar(80) NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (event_id, handler)
);

CREATE TABLE device_token (
    token        varchar(4096) PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    platform     varchar(10) NOT NULL,
    created_at   timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    CONSTRAINT ck_device_token_platform CHECK (platform IN ('IOS', 'ANDROID'))
);
CREATE INDEX ix_device_token_user ON device_token (user_id);

CREATE TABLE notification (
    id         uuid PRIMARY KEY,
    seq        bigserial    NOT NULL,
    user_id    uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind       varchar(40)  NOT NULL,
    title      varchar(200) NOT NULL,
    body       varchar(500) NOT NULL,
    order_id   uuid,
    market_id  uuid,
    created_at timestamptz  NOT NULL,
    read_at    timestamptz
);
CREATE INDEX ix_notification_user_seq ON notification (user_id, seq DESC);
