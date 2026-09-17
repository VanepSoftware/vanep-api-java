create table driver_cnh (
    id                   bigint generated always as identity primary key,
    token                varchar(32) not null,
    driver_id            bigint      not null references driver (id),
    registration_number  varchar(20) not null,
    category             varchar(5)  not null,
    issue_date           date        not null,
    valid_until          date        not null,
    first_license_date   date,
    security_number      varchar(20),
    issuing_state        varchar(2),
    photo_url            varchar(255),
    is_active            boolean     not null default true,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    deleted_at           timestamptz
);

comment on table driver_cnh is 'CNH (habilitação) dos motoristas.';

create unique index driver_cnh_token_active_key on driver_cnh (token) where deleted_at is null;
create unique index driver_cnh_driver_active_key on driver_cnh (driver_id) where deleted_at is null;
create unique index driver_cnh_registration_active_key on driver_cnh (registration_number) where deleted_at is null;
