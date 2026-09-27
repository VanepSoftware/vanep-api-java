create table media_file (
    id            bigint       generated always as identity primary key,
    token         varchar(32)  not null,
    provider      varchar(20)  not null default 'LOCAL',
    object_key    varchar(512) not null,
    mime_type     varchar(100) not null,
    size_bytes    bigint       not null,
    original_name varchar(255),
    visibility    varchar(20)  not null default 'PRIVATE',
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    deleted_at    timestamptz
);

comment on table media_file is
    'Metadado de arquivo binário. Quem aponta para cá é o dono, nunca o contrário.';
comment on column media_file.provider is
    'Onde o byte está. Por linha, para permitir migração em lote sem downtime.';
comment on column media_file.object_key is
    'Chave do objeto no provider, nunca a URL. Derivada no servidor.';

create unique index media_file_token_active_key
    on media_file (token) where deleted_at is null;

create unique index media_file_object_key_active_key
    on media_file (object_key) where deleted_at is null;
