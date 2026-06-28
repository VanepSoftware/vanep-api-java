create table users (
    id                  bigint generated always as identity primary key,
    token               varchar(32)  not null unique,
    type                varchar(16)  not null,
    role_id             bigint,
    country_id          bigint,
    name                varchar(255) not null,
    email               varchar(255) not null unique,
    username            varchar(255) unique,
    password            varchar(255),
    document            varchar(64)  not null unique,
    phone               varchar(32),
    birth_date          date,
    gender              varchar(16),
    verified            boolean      not null default false,
    terms_accepted_at   timestamptz,
    last_name_change_at  timestamptz,
    last_login_at        timestamptz,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    deleted_at          timestamptz
);

create index idx_users_type on users (type);

comment on table users is 'Conta. Dona de toda a autenticação (local + OAuth via oauth_account).';
comment on column users.password is 'Nullable: contas só-OAuth não têm senha local.';
comment on column users.document is 'Documento nacional de identificação (BR: CPF, PT: NIF, etc.).';
