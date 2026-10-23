-- Which version of the platform terms (AGB) and privacy notice an account accepted, and when
-- (dukkan.platform.terms-version). NULL: accepted nothing (before acceptance was required).
ALTER TABLE app_user ADD COLUMN terms_version varchar(40);
ALTER TABLE app_user ADD COLUMN terms_accepted_at timestamptz;
