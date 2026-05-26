ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_token_key;

CREATE UNIQUE INDEX users_email_active_idx ON users (email)
WHERE
    deleted_at IS NULL;

CREATE UNIQUE INDEX users_username_active_idx ON users (username)
WHERE
    deleted_at IS NULL;

CREATE UNIQUE INDEX users_token_active_idx ON users (token)
WHERE
    deleted_at IS NULL;
