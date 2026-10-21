-- B-39: refresh tokens (rotation with reuse detection), password reset tokens and linked
-- Apple/Google identities. Tokens are stored as SHA-256 hashes only.

CREATE TABLE refresh_token (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- All tokens of one sign-in share a family; reusing a rotated token revokes the family.
    family_id   uuid        NOT NULL,
    token_hash  bytea       NOT NULL,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_expires ON refresh_token (expires_at);

CREATE TABLE password_reset_token (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash bytea       NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_password_reset_token_user ON password_reset_token (user_id);

CREATE TABLE user_identity (
    provider   varchar(10)  NOT NULL,
    subject    varchar(255) NOT NULL,
    user_id    uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    email      varchar(254),
    created_at timestamptz  NOT NULL,
    PRIMARY KEY (provider, subject),
    CONSTRAINT ck_user_identity_provider CHECK (provider IN ('APPLE', 'GOOGLE'))
);
CREATE INDEX ix_user_identity_user ON user_identity (user_id);
