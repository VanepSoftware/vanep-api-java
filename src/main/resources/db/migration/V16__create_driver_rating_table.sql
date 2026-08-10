create table driver_rating (
    id          bigint generated always as identity primary key,
    token       varchar(32) not null unique,
    driver_id   bigint not null references driver(id),
    client_id   bigint not null references client(id),
    rating      numeric(3, 2) not null check (rating >= 1.00 and rating <= 5.00),
    comment     text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz
);

comment on table driver_rating is 'Avaliações registradas por clientes para motoristas.';

create unique index idx_driver_rating_driver_client_unique
    on driver_rating (driver_id, client_id)
    where deleted_at is null;
