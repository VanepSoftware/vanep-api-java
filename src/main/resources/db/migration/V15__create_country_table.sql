create table country (
    id          bigint generated always as identity primary key,
    token       varchar(32)  not null,
    name        varchar(128) not null,
    iso_code    varchar(2)   not null,
    phone_code  varchar(16)  not null,
    currency    varchar(3)   not null,
    locale      varchar(16),
    is_active   boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    deleted_at  timestamptz
);

comment on table country is 'Países atendidos. Define moeda, DDI e locale. Raiz da hierarquia geográfica.';
comment on column country.iso_code is 'ISO 3166-1 alpha-2: BR, US, PT';
comment on column country.phone_code is 'DDI, ex: +55';
comment on column country.currency is 'ISO 4217: BRL, USD, EUR';
comment on column country.locale is 'ex: pt-BR — formata datas/documentos/moeda';

create unique index country_token_active_key on country (token) where deleted_at is null;
create unique index country_name_active_key on country (name) where deleted_at is null;
create unique index country_iso_code_active_key on country (iso_code) where deleted_at is null;

insert into country (token, name, iso_code, phone_code, currency, locale) 
values ('brasil', 'Brasil', 'BR', '+55', 'BRL', 'pt-BR');

alter table state add column country_id bigint;

update state set country_id = (select id from country where name = 'Brasil');

alter table state alter column country_id set not null;

alter table state add constraint fk_state_country foreign key (country_id) references country (id);

drop index if exists state_uf_active_key;
create unique index state_country_uf_active_key on state (country_id, uf) where deleted_at is null;
