create table client_rating (
    id          bigint generated always as identity primary key,
    token       varchar(32) not null unique,
    driver_id   bigint not null references driver(id),
    client_id   bigint not null references client(id),
    rating      numeric(3, 2) not null check (rating >= 1.00 and rating <= 5.00),
    comment     text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

comment on table client_rating is 'Avaliações registradas por motoristas para clientes.';

create unique index idx_client_rating_driver_client_unique
    on client_rating (driver_id, client_id);
