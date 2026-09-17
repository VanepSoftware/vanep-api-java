-- HUB do vínculo cliente-motorista. Proposta, contrato, avaliações e chat
-- penduram aqui: nenhum deles pertence ao cliente nem ao motorista sozinho,
-- e sim ao par.

create table client_driver (
    id         bigint      generated always as identity primary key,
    token      varchar(32) not null,
    client_id  bigint      not null references client (id),
    driver_id  bigint      not null references driver (id),
    status     varchar(16) not null default 'PENDING',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

comment on table client_driver is
    'Vínculo entre um cliente e um motorista. Um vínculo ativo por par.';
comment on column client_driver.status is
    'INACTIVE encerra a relação e continua visível; deleted_at é remoção administrativa e some.';

create unique index client_driver_token_active_key
    on client_driver (token) where deleted_at is null;

-- Parcial porque um vínculo removido pelo admin não pode travar aquele par
-- para sempre: sem o WHERE, o restore viraria a única saída de um bloqueio
-- que ele deveria evitar.
create unique index client_driver_pair_active_key
    on client_driver (client_id, driver_id) where deleted_at is null;

create index client_driver_client_idx on client_driver (client_id) where deleted_at is null;
create index client_driver_driver_idx on client_driver (driver_id) where deleted_at is null;
