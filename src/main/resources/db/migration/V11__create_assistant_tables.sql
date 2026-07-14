create table assistant (
    id                  bigint generated always as identity primary key,
    token               varchar(32)  not null,
    user_id             bigint       not null references users (id),
    driver_id           bigint       references driver (id),
    status              varchar(16)  not null default 'UNLINKED',
    verification_status varchar(16)  not null default 'PENDING',
    photo               varchar(255),
    activated_at        timestamptz,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    deleted_at          timestamptz
);

comment on table assistant is 'Perfil de assistente de bordo. 1:1 com users; vínculo com motorista via assistant_invite.';

create unique index assistant_token_active_key on assistant (token) where deleted_at is null;
create unique index assistant_user_id_active_key on assistant (user_id) where deleted_at is null;

create table assistant_invite (
    id            bigint generated always as identity primary key,
    token         varchar(32)  not null,
    link_token_hash varchar(64)  not null,
    driver_id     bigint       not null references driver (id),
    assistant_id  bigint       not null references assistant (id),
    status        varchar(16)  not null default 'PENDING',
    expires_at    timestamptz  not null,
    responded_at  timestamptz,
    created_at    timestamptz  not null default now(),
    deleted_at    timestamptz
);

comment on table assistant_invite is 'Convite endereçado motorista→assistente; hash do token do link do e-mail em link_token_hash.';

create unique index assistant_invite_token_active_key on assistant_invite (token) where deleted_at is null;
create unique index assistant_invite_link_token_hash_active_key on assistant_invite (link_token_hash) where deleted_at is null;
create index idx_assistant_invite_driver on assistant_invite (driver_id);
create index idx_assistant_invite_assistant on assistant_invite (assistant_id);
