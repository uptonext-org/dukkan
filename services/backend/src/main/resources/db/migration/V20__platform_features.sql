-- Functions platform admins switch on and off for everyone (portal "Funktionen"). A missing row
-- means the default of the feature in the code (Feature.defaultEnabled).
CREATE TABLE platform_feature (
    feature_key varchar(60) PRIMARY KEY,
    enabled     boolean     NOT NULL,
    updated_at  timestamptz NOT NULL,
    updated_by  uuid
);
