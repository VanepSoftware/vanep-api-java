create table dependent (
    id          bigint generated always as identity primary key,
    token       varchar(32)  not null,
    client_id   bigint       not null references client (id),
    school_id   bigint,
    address_id  bigint,
    name        varchar(255) not null,
    birth_date  date,
    gender      varchar(16),
    document    varchar(64),
    phone       varchar(32),
    email       varchar(255),
    is_self     boolean      not null default false,
    is_default  boolean      not null default false,
    shift       varchar(16)  not null default 'MORNING',
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    deleted_at  timestamptz
);

create unique index dependent_token_active_key
    on dependent (token) where deleted_at is null;

create unique index dependent_document_active_key
    on dependent (document) where deleted_at is null;
