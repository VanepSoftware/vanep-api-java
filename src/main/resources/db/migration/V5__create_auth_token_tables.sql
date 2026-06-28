create table email_verification_token (
    id          bigint generated always as identity primary key,
    user_id     bigint       not null references users (id),
    token_hash  varchar(64)  not null unique,
    expires_at  timestamptz  not null,
    consumed_at timestamptz,
    created_at  timestamptz  not null default now()
);

create index idx_email_verification_user on email_verification_token (user_id);

create table password_reset_token (
    id          bigint generated always as identity primary key,
    user_id     bigint       not null references users (id),
    token_hash  varchar(64)  not null unique,
    expires_at  timestamptz  not null,
    consumed_at timestamptz,
    created_at  timestamptz  not null default now()
);

create index idx_password_reset_user on password_reset_token (user_id);
