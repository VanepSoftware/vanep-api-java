CREATE TABLE country (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token       VARCHAR(32)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    iso_code    VARCHAR(2)   NOT NULL,
    phone_code  VARCHAR(16)  NOT NULL,
    currency    VARCHAR(3)   NOT NULL,
    locale      VARCHAR(16),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

COMMENT ON TABLE country IS 'Países atendidos. Define moeda, DDI e locale. Raiz da hierarquia geográfica.';
COMMENT ON COLUMN country.iso_code IS 'ISO 3166-1 alpha-2: BR, US, PT';
COMMENT ON COLUMN country.phone_code IS 'DDI, ex: +55';
COMMENT ON COLUMN country.currency IS 'ISO 4217: BRL, USD, EUR';
COMMENT ON COLUMN country.locale IS 'ex: pt-BR — formata datas/documentos/moeda';

CREATE UNIQUE INDEX country_token_active_key ON country (token) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX country_name_active_key ON country (name) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX country_iso_code_active_key ON country (iso_code) WHERE deleted_at IS NULL;

ALTER TABLE state ADD COLUMN country_id BIGINT NOT NULL;

ALTER TABLE state ADD CONSTRAINT fk_state_country FOREIGN KEY (country_id) REFERENCES country (id);

DROP INDEX IF EXISTS state_uf_active_key;
CREATE UNIQUE INDEX state_country_uf_active_key ON state (country_id, uf) WHERE deleted_at IS NULL;
