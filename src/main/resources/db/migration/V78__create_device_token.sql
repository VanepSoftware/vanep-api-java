-- FCM registration tokens of each user's devices. A user may have several devices, and a
-- device moves to whoever signs in on it last, so fcm_token is unique among live rows.

create table device_token (
    id           bigint        generated always as identity primary key,
    user_id      bigint        not null references users (id),
    fcm_token    varchar(1024) not null,
    platform     varchar(16)   not null,
    created_at   timestamptz   not null default now(),
    last_seen_at timestamptz   not null default now(),
    deleted_at   timestamptz
);

create unique index device_token_fcm_token_active_key
    on device_token (fcm_token) where deleted_at is null;

create index device_token_user_idx
    on device_token (user_id) where deleted_at is null;
