
create table client (
    id          bigint generated always as identity primary key,
    token       varchar(32) not null unique,
    user_id     bigint      not null unique references users (id),
    photo       varchar(255),
    rating      numeric(3, 2),
    address_id  bigint,
    is_active   boolean     not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz
);

comment on table client is 'Perfil de cliente (responsável). 1:1 com users.';

create table driver (
    id                      bigint generated always as identity primary key,
    token                   varchar(32) not null unique,
    user_id                 bigint      not null unique references users (id),
    photo                   varchar(255),
    rating                  numeric(3, 2),
    bio                     text,
    cnpj                    varchar(32),
    experience_years        integer,
    city                    varchar(255),
    base_price              numeric(12, 2) not null,
    work_start_time         time,
    work_end_time           time,
    work_days               jsonb,
    wait_tolerance_minutes  integer,
    service_areas           jsonb,
    approval_status         varchar(16) not null default 'PENDING',
    is_active               boolean     not null default true,
    is_available            boolean     not null default false,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    deleted_at              timestamptz
);

comment on table driver is 'Perfil de motorista. 1:1 com users. Recebe propostas só após aprovação.';
comment on column driver.cnpj is 'RN13: não precisa ser único (pode ser de terceiro).';
