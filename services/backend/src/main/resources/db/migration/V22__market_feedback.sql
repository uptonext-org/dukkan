-- Customer feedback to a market (merchant point 3). The merchant switches receiving it on or off;
-- no row: off.
CREATE TABLE market_feedback_setting (
    market_id  uuid PRIMARY KEY REFERENCES market (id) ON DELETE CASCADE,
    enabled    boolean     NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE market_feedback (
    id         uuid PRIMARY KEY,
    market_id  uuid          NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    -- Deleted with the account (AccountService); the merchant sees the first name only.
    user_id    uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    rating     smallint,
    message    varchar(1000) NOT NULL,
    created_at timestamptz   NOT NULL,
    CONSTRAINT ck_market_feedback_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);
CREATE INDEX ix_market_feedback_market ON market_feedback (market_id, created_at DESC);
CREATE INDEX ix_market_feedback_user ON market_feedback (user_id);
