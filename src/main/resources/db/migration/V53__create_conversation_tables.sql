-- Chat between a client and a driver. A conversation belongs to the (client, driver)
-- pair, not to client_driver: it starts before any link exists and outlives it.

create table conversation (
    id              bigint      generated always as identity primary key,
    token           varchar(32) not null,
    client_id       bigint      not null references client (id),
    driver_id       bigint      not null references driver (id),
    last_message_at timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    deleted_at      timestamptz
);

create unique index conversation_token_active_key
    on conversation (token) where deleted_at is null;

create unique index conversation_pair_active_key
    on conversation (client_id, driver_id) where deleted_at is null;

create index conversation_driver_recent_idx
    on conversation (driver_id, last_message_at desc) where deleted_at is null;

create table conversation_message (
    id              bigint        generated always as identity primary key,
    token           varchar(32)   not null,
    conversation_id bigint        not null references conversation (id),
    sender          varchar(16)   not null,
    body            varchar(2000) not null,
    sent_at         timestamptz   not null default now(),
    deleted_at      timestamptz
);

create unique index conversation_message_token_active_key
    on conversation_message (token) where deleted_at is null;

create index conversation_message_history_idx
    on conversation_message (conversation_id, sent_at desc) where deleted_at is null;

-- The seeder only builds a bundle when it does not exist yet, so environments that
-- are already initialized get the new permissions here. Idempotent.

update role_permissions
set permissions = permissions || '["create_conversation"]'::jsonb
where name in ('CLIENT', 'ADMIN')
  and not permissions @> '["create_conversation"]'::jsonb;

update role_permissions
set permissions = permissions || '["list_driver_conversations"]'::jsonb
where name in ('DRIVER', 'ADMIN')
  and not permissions @> '["list_driver_conversations"]'::jsonb;
