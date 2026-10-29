-- Whether the account has a password its owner knows (B-51). Accounts created by Apple or Google
-- sign-in get a random one; only those may be deleted without the password. Existing accounts with
-- a linked provider keep today's behaviour (no password asked), every other account needs it.
ALTER TABLE app_user ADD COLUMN password_set boolean NOT NULL DEFAULT true;
UPDATE app_user u SET password_set = false WHERE EXISTS (SELECT 1 FROM user_identity i WHERE i.user_id = u.id);
