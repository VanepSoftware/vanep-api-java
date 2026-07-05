create table vehicle (
    id                  bigint generated always as identity primary key,
    token               varchar(32)  not null,
    driver_id           bigint       not null references driver (id),
    plate               varchar(10)  not null,
    brand               varchar(100) not null,
    model               varchar(100) not null,
    manufacture_year    integer      not null,
    color               varchar(50)  not null,
    capacity            integer      not null,
    photo_front_url     varchar(255),
    photo_side_url      varchar(255),
    photo_document_url  varchar(255),
    is_active           boolean      not null default true,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    deleted_at          timestamptz
);

comment on table vehicle is 'Veículos cadastrados por motoristas.';

create unique index vehicle_token_active_key on vehicle (token) where deleted_at is null;
create unique index vehicle_plate_active_key on vehicle (plate) where deleted_at is null;
