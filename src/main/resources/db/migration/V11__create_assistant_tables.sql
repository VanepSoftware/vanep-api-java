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

comment on table assistant is 'Perfil de assistente de bordo. 1:1 com users; vínculo opcional com driver.';

create unique index assistant_token_active_key on assistant (token) where deleted_at is null;
create unique index assistant_user_id_active_key on assistant (user_id) where deleted_at is null;

create table driver_link_code (
    id                       bigint generated always as identity primary key,
    driver_id                bigint       not null references driver (id),
    code_hash                varchar(64)  not null,
    status                   varchar(16)  not null default 'ACTIVE',
    expires_at               timestamptz  not null,
    consumed_at              timestamptz,
    consumed_by_assistant_id bigint       references assistant (id),
    created_at               timestamptz  not null default now()
);

comment on table driver_link_code is 'Código aberto de vínculo motorista-assistente (TTL 24h, uso único).';

create unique index driver_link_code_code_hash_key on driver_link_code (code_hash);
create index driver_link_code_driver_id_idx on driver_link_code (driver_id);
