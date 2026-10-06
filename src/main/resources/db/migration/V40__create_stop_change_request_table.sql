-- Solicitação de alteração de parada de desembarque no meio da rota (UC22, RN-19).
-- Vinculada à trip do dia e ao aluno (dependent), preservando o contrato original.

create table stop_change_request (
    id                     bigint      generated always as identity primary key,
    token                  varchar(32) not null,
    contract_id            bigint,
    dependent_id           bigint      not null references dependent (id),
    trip_id                bigint      not null references trip (id),
    service_date           date        not null,
    requested_by_user_id   bigint      not null references users (id),
    new_dropoff_address_id bigint      not null references address (id),
    reason                 varchar(255),
    status                 varchar(16) not null default 'PENDING',
    responded_by_user_id   bigint      references users (id),
    responded_at           timestamptz,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    deleted_at             timestamptz
);

comment on table stop_change_request is
    'Solicitação de ajuste de parada no meio da rota. Vinculada a uma trip e um aluno.';
comment on column stop_change_request.contract_id is
    'Vínculo futuro com contract (#41). Nullable enquanto contract não existe.';
comment on column stop_change_request.status is
    'PENDING, APPROVED, REJECTED, EXPIRED ou CANCELLED.';

create unique index stop_change_request_token_active_key
    on stop_change_request (token) where deleted_at is null;

-- Garante no nível de banco no máximo uma solicitação PENDING ativa por dependente na mesma viagem:
create unique index stop_change_request_pending_dependent_trip_active_key
    on stop_change_request (dependent_id, trip_id)
    where status = 'PENDING' and deleted_at is null;

create index stop_change_request_trip_idx
    on stop_change_request (trip_id) where deleted_at is null;

create index stop_change_request_dependent_idx
    on stop_change_request (dependent_id) where deleted_at is null;

create index stop_change_request_requested_by_idx
    on stop_change_request (requested_by_user_id) where deleted_at is null;

create index stop_change_request_service_date_idx
    on stop_change_request (service_date) where deleted_at is null;
