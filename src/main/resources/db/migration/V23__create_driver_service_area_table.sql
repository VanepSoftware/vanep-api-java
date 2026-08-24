-- Região onde o motorista atua. Dado PÚBLICO: aparece na busca do cliente.
--
-- Repare no que NÃO existe aqui: rua, número, CEP, complemento. A garantia de
-- privacidade do D1 é do schema, não de disciplina — não há o que vazar porque
-- a coluna não existe. Endereço residencial mora em `address`, e só o dono lê.

create table driver_service_area (
    id          bigint generated always as identity primary key,
    token       varchar(32) not null,
    driver_id   bigint      not null references driver (id),
    city_id     bigint      not null references city (id),
    district_id bigint      references district (id),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz
);

comment on table driver_service_area is
    'Regiões atendidas por um motorista. Só nós da árvore — nunca logradouro.';
comment on column driver_service_area.district_id is
    'Nulo = a cidade inteira. A validação D8 decide quando isso é permitido.';

create unique index driver_service_area_token_active_key
    on driver_service_area (token) where deleted_at is null;

-- Mesma região cadastrada duas vezes pelo mesmo motorista é ruído na busca.
-- NULLS NOT DISTINCT porque district_id nulo ("cidade inteira") também não
-- pode repetir — sem isso o caso mais amplo seria o único desprotegido.
create unique index driver_service_area_unique_active_key
    on driver_service_area (driver_id, city_id, district_id)
    nulls not distinct
    where deleted_at is null;

-- Índices da busca por contenção (D4): o filtro entra por cidade e distrito.
create index driver_service_area_city_idx
    on driver_service_area (city_id) where deleted_at is null;
create index driver_service_area_district_idx
    on driver_service_area (district_id) where deleted_at is null;
