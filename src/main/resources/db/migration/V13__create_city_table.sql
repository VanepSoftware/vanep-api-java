create table city (
    id          bigint generated always as identity primary key,
    token       varchar(32)  not null,
    state_id    bigint       not null references state (id),
    name        varchar(128) not null,
    is_active   boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    deleted_at  timestamptz
);

comment on table city is 'Cidades atendidas pela Vanep. Pertence a um estado (state).';

create unique index city_token_active_key on city (token) where deleted_at is null;
create unique index city_name_state_active_key on city (name, state_id) where deleted_at is null;
