-- B-39b: email verification. Accounts from before this migration count as verified; accounts
-- created through Apple/Google are verified by the provider.
ALTER TABLE app_user ADD COLUMN email_verified_at timestamptz;
UPDATE app_user SET email_verified_at = created_at;

CREATE TABLE email_verification_token (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash bytea       NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    CONSTRAINT uq_email_verification_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_email_verification_token_user ON email_verification_token (user_id);
CREATE INDEX ix_email_verification_token_expires ON email_verification_token (expires_at);
