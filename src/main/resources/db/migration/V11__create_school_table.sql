create table school (
    id          bigint generated always as identity primary key,
    token       varchar(32)  not null,
    name        varchar(255) not null,
    cnpj        varchar(32),
    phone       varchar(32),
    email       varchar(255),
    address_id  bigint,
    is_active   boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    deleted_at  timestamptz
);

comment on table school is 'Escolas atendidas pelo transporte. Referenciada por dependent.school_id.';
comment on column school.address_id is 'Sem FK até a tabela address existir (mesmo padrão de dependent).';

create unique index school_token_active_key on school (token) where deleted_at is null;
create unique index school_cnpj_active_key on school (cnpj) where deleted_at is null;
