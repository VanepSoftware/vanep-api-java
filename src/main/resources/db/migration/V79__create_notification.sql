-- In-app notifications, listed in the app's notifications tab. Title and body are stored as
-- sent, so the list never depends on re-rendering an event that may no longer exist.

create table notification (
    id         bigint       generated always as identity primary key,
    token      varchar(32)  not null,
    user_id    bigint       not null references users (id),
    type       varchar(32)  not null,
    title      varchar(120) not null,
    body       varchar(500) not null,
    data       jsonb        not null default '{}'::jsonb,
    read_at    timestamptz,
    created_at timestamptz  not null default now()
);

create unique index notification_token_key on notification (token);

create index notification_user_recent_idx on notification (user_id, created_at desc, id desc);

create index notification_user_unread_idx on notification (user_id) where read_at is null;
