CREATE TABLE
    users (
        id BIGSERIAL PRIMARY KEY,
        token VARCHAR(200) UNIQUE,
        type VARCHAR(20) NOT NULL,
        name VARCHAR(150) NOT NULL,
        email VARCHAR(100) NOT NULL UNIQUE,
        username VARCHAR(50) NOT NULL UNIQUE,
        password VARCHAR(255) NOT NULL,
        cpf VARCHAR(25) NOT NULL UNIQUE,
        phone VARCHAR(30),
        address_id INT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        deleted_at TIMESTAMPTZ NULL
    );

CREATE
OR REPLACE FUNCTION trigger_set_timestamp () RETURNS TRIGGER AS '
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
' LANGUAGE plpgsql;

CREATE TRIGGER set_timestamp_users BEFORE
UPDATE ON users FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp ();