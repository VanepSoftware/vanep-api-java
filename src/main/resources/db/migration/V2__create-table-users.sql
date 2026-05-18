-- 1. Criação da tabela 'users'
CREATE TABLE
    users (
        id BIGSERIAL PRIMARY KEY,
        token VARCHAR(100) UNIQUE,
        types VARCHAR(20) NOT NULL,
        names VARCHAR(150) NOT NULL,
        email VARCHAR(100) NOT NULL UNIQUE,
        username VARCHAR(50) NOT NULL UNIQUE,
        passwords VARCHAR(255) NOT NULL,
        cpf VARCHAR(14) NOT NULL UNIQUE,
        phone VARCHAR(20),
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