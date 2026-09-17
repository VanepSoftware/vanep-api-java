create table driver_document (
    id                 bigint generated always as identity primary key,
    token              varchar(32)  not null,
    driver_id          bigint       not null references driver (id),
    document_type      varchar(50)  not null,
    file_url           varchar(512) not null,
    expires_at         date,
    status             varchar(20)  not null default 'PENDING',
    review_method      varchar(30),
    external_check_id  varchar(64),
    rejection_reason   varchar(255),
    reviewed_by        bigint       references users (id),
    reviewed_at        timestamptz,
    notified_at        timestamptz,
    is_active          boolean      not null default true,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    deleted_at         timestamptz
);

comment on table driver_document is 'Documentos anexados pelos motoristas para validação.';

create unique index driver_document_token_active_key on driver_document (token) where deleted_at is null;
create index idx_driver_document_driver_active on driver_document (driver_id) where deleted_at is null;