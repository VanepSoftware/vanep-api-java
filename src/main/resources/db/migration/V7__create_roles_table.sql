create table roles (
    id          bigint generated always as identity primary key,
    token       varchar(32)  not null unique,
    name        varchar(64)  not null unique,
    description text,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    deleted_at  timestamptz
);

alter table users
    add constraint fk_users_role foreign key (role_id) references roles (id);
