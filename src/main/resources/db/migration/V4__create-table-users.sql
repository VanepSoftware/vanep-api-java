CREATE TYPE user_type AS ENUM ('CLIENT', 'DRIVER', 'ADMIN');

CREATE TABLE
    users (
        id SERIAL PRIMARY KEY,
        token VARCHAR NOT NULL UNIQUE,
        type user_type NOT NULL,
        name VARCHAR(150) NOT NULL,
        email VARCHAR(100) NOT NULL UNIQUE,
        username VARCHAR(50) NOT NULL UNIQUE,
        password VARCHAR(255) NOT NULL,
        cpf VARCHAR(25) NOT NULL,
        verified BOOLEAN,
        phone VARCHAR(35),
        created_at TIMESTAMP,
        updated_at TIMESTAMP,
        deleted_at TIMESTAMP
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
