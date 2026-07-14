create table state (
    id          bigint generated always as identity primary key,
    token       varchar(32)  not null,
    name        varchar(64)  not null,
    uf          char(2)      not null,
    is_active   boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    deleted_at  timestamptz
);

comment on table state is 'Estados brasileiros (dado de referência, somente leitura via API; populado por seed).';

create unique index state_token_active_key on state (token) where deleted_at is null;
create unique index state_uf_active_key on state (uf) where deleted_at is null;
create unique index state_name_active_key on state (name) where deleted_at is null;
