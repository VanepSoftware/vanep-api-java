-- Execução diária da rota de um motorista (UC08). É a âncora do bloco de
-- operação: checklist_entry, absence, stop_change_request e route penduram aqui.

create table trip (
    id           bigint      generated always as identity primary key,
    token        varchar(32) not null,
    driver_id    bigint      not null references driver (id),
    service_date date        not null,
    shift        varchar(16) not null,
    status       varchar(16) not null default 'SCHEDULED',
    started_at   timestamptz,
    finished_at  timestamptz,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    deleted_at   timestamptz
);

comment on table trip is
    'Rota do dia de um motorista. Uma linha ativa por (motorista, data, turno).';
comment on column trip.service_date is
    'Dia DE SERVIÇO, derivado do relógio do servidor em America/Sao_Paulo — nunca do dispositivo.';
comment on column trip.status is
    'CANCELLED é rota que não vai acontecer; deleted_at é remoção administrativa. São coisas distintas.';

create unique index trip_token_active_key
    on trip (token) where deleted_at is null;

-- A idempotência de "iniciar rota" é este índice, não um check-then-insert no
-- serviço: entre a consulta e o insert cabe outra requisição, e o duplo toque
-- criaria duas trips. O serviço tenta inserir e relê a linha vencedora.
--
-- Parcial porque uma trip removida pelo admin não pode travar o motorista de
-- iniciar a rota do mesmo dia e turno de novo.
create unique index trip_driver_service_date_shift_active_key
    on trip (driver_id, service_date, shift) where deleted_at is null;

create index trip_driver_idx on trip (driver_id) where deleted_at is null;
create index trip_service_date_idx on trip (service_date) where deleted_at is null;
