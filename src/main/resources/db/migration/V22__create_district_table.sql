-- Árvore geográfica: district de profundidade variável (auto-FK).
-- Ver openspec/changes/location-system/design.md (D7, D11 e risco R2).

create table district (
    id              bigint generated always as identity primary key,
    token           varchar(32)  not null,
    city_id         bigint       not null references city (id),
    parent_id       bigint       references district (id),
    name            varchar(128) not null,
    normalized_name varchar(128) not null,
    google_place_id varchar(255),
    is_active       boolean      not null default true,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    deleted_at      timestamptz
);

comment on table district is
    'Subdivisões de uma cidade, de profundidade variável. Criadas sob demanda a partir do Google Places, nunca por seed.';
comment on column district.parent_id is
    'Distrito pai. Nulo = filho direto da cidade (ex.: Taguatinga sob Brasília).';
comment on column district.normalized_name is
    'name sem acento e em minúsculas. É por ele que o nó do motorista e o do cliente se encontram.';
comment on column district.google_place_id is
    'Opcional: addressComponents não traz place_id por componente, então só é preenchido quando o nó nasce de um place escolhido diretamente.';

create unique index district_token_active_key on district (token) where deleted_at is null;

-- R2: sem NULLS NOT DISTINCT, duas "Taguatinga" filhas diretas de Brasília (parent_id nulo)
-- passariam pelo índice sem conflito — que é justamente o caso mais comum da árvore.
-- Exige PostgreSQL 15+; o compose roda postgres:17-alpine.
create unique index district_parent_city_name_active_key
    on district (parent_id, city_id, normalized_name)
    nulls not distinct
    where deleted_at is null;

create unique index district_google_place_id_active_key
    on district (google_place_id) where deleted_at is null;

create index district_city_idx on district (city_id) where deleted_at is null;
create index district_parent_idx on district (parent_id) where deleted_at is null;
