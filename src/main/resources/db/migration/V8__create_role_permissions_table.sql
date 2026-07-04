create table role_permissions (
    id          bigint generated always as identity primary key,
    token       varchar(32)  not null unique,
    name        varchar(64)  not null unique,
    permissions jsonb        not null default '[]',
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    deleted_at  timestamptz
);

alter table roles
    add column role_permissions_id bigint unique,
    add column role_name varchar(16) unique;

alter table roles
    add constraint fk_roles_role_permissions foreign key (role_permissions_id) references role_permissions (id);
