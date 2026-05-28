DROP TRIGGER IF EXISTS set_timestamp_users ON users;

DROP TABLE IF EXISTS users;

DROP FUNCTION IF EXISTS trigger_set_timestamp ();

DROP TABLE IF EXISTS flyway_test;
