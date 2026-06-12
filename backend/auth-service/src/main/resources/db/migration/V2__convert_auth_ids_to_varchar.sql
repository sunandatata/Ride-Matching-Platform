-- Convert application-generated UUID identifiers to strings for Hibernate String IDs.
DROP VIEW IF EXISTS active_refresh_tokens;

ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_fkey;
ALTER TABLE token_blacklist DROP CONSTRAINT IF EXISTS token_blacklist_user_id_fkey;
ALTER TABLE auth_audit_log DROP CONSTRAINT IF EXISTS auth_audit_log_user_id_fkey;

ALTER TABLE users ALTER COLUMN user_id DROP DEFAULT;
ALTER TABLE users ALTER COLUMN user_id TYPE VARCHAR(255) USING user_id::text;

ALTER TABLE refresh_tokens ALTER COLUMN token_id DROP DEFAULT;
ALTER TABLE refresh_tokens ALTER COLUMN token_id TYPE VARCHAR(255) USING token_id::text;
ALTER TABLE refresh_tokens ALTER COLUMN user_id TYPE VARCHAR(255) USING user_id::text;

ALTER TABLE token_blacklist ALTER COLUMN id DROP DEFAULT;
ALTER TABLE token_blacklist ALTER COLUMN id TYPE VARCHAR(255) USING id::text;
ALTER TABLE token_blacklist ALTER COLUMN user_id TYPE VARCHAR(255) USING user_id::text;

ALTER TABLE auth_audit_log ALTER COLUMN user_id TYPE VARCHAR(255) USING user_id::text;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE token_blacklist
    ADD CONSTRAINT token_blacklist_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE auth_audit_log
    ADD CONSTRAINT auth_audit_log_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL;

CREATE VIEW active_refresh_tokens AS
SELECT *
FROM refresh_tokens
WHERE revoked = FALSE
  AND expires_at > CURRENT_TIMESTAMP;
