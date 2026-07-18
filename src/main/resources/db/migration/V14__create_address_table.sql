create table address (
    id          bigint generated always as identity primary key,
    token       varchar(32)  not null,
    city_id     bigint       not null references city (id),
    zip_code    varchar(8)   not null,
    street      varchar(255) not null,
    number      varchar(16),
    complement  varchar(128),
    district    varchar(128),
    is_active   boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    deleted_at  timestamptz
);

comment on table address is 'Endereços da plataforma. Referenciado por client.address_id, dependent.address_id e school.address_id.';
comment on column address.zip_code is 'CEP somente dígitos (8 caracteres).';
comment on column address.number is 'Texto: aceita "s/n" e números com letra.';

create unique index address_token_active_key on address (token) where deleted_at is null;
