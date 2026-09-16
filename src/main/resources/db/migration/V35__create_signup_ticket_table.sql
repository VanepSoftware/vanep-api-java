create table signup_ticket (
    id           bigint generated always as identity primary key,
    ticket_hash  varchar(64)  not null unique,
    provider     varchar(16)  not null,
    provider_uid varchar(255) not null,
    email        varchar(255) not null,
    name         varchar(255),
    expires_at   timestamptz  not null,
    consumed_at  timestamptz,
    created_at   timestamptz  not null default now()
);

create index idx_signup_ticket_expires_at on signup_ticket (expires_at);
