create table oauth_account (
    id            bigint generated always as identity primary key,
    token         varchar(32)  not null unique,
    user_id       bigint       not null references users (id),
    provider      varchar(16)  not null,
    provider_uid  varchar(255) not null,
    email         varchar(255),
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);

create unique index uq_oauth_account_provider_uid on oauth_account (provider, provider_uid);
create unique index uq_oauth_account_user_provider on oauth_account (user_id, provider);

comment on table oauth_account is 'Vínculo de provedores OAuth (Google/Apple) a uma conta.';
comment on column oauth_account.provider_uid is 'id do usuário no provedor.';
