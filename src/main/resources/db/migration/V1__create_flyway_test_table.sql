CREATE TABLE flyway_test (
    id BIGSERIAL PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO flyway_test (label) VALUES ('flyway-smoke-test');
