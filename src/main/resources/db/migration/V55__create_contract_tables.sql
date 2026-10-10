-- A contract is the agreement between a client and a driver; one item per
-- dependent. The document (PDF, signatures) is a separate table in a later
-- change, so a contract created by an admin is a contract without a document,
-- not a contract with empty columns. Later changes link to the contract from
-- their own tables; these core tables do not grow new columns.

create table contract (
    id                     bigint        generated always as identity primary key,
    token                  varchar(32)   not null,
    client_driver_id       bigint        not null references client_driver (id),
    status                 varchar(24)   not null,
    starts_on              date          not null,
    ends_on                date          not null,
    total_amount           numeric(12,2) not null,
    installments           smallint      not null,
    due_day                smallint      not null,
    supersedes_contract_id bigint        references contract (id),
    created_at             timestamptz   not null default now(),
    updated_at             timestamptz   not null default now(),
    deleted_at             timestamptz,
    constraint contract_period_check check (ends_on > starts_on),
    constraint contract_total_amount_check check (total_amount > 0),
    constraint contract_installments_check check (installments between 1 and 12),
    -- Up to 28 so that every month, February included, has the due day.
    constraint contract_due_day_check check (due_day between 1 and 28)
);

comment on table contract is
    'Agreement of a client_driver link. The twelve-month cap on the period is enforced by the service, not here.';
comment on column contract.status is
    'ContractStatus. ENDED, TERMINATED, CANCELLED and SUPERSEDED are terminal; deleted_at is an administrative correction, not an ending.';
comment on column contract.total_amount is
    'Total amount of the contract, split into installments; this is what is billed. contract_item.monthly_amount is the listed price per dependent.';
comment on column contract.supersedes_contract_id is
    'Contract this one replaces (amendment). Null for an original contract.';

create unique index contract_token_active_key
    on contract (token) where deleted_at is null;

create unique index contract_client_driver_active_key
    on contract (client_driver_id) where status = 'ACTIVE' and deleted_at is null;

create index contract_client_driver_idx
    on contract (client_driver_id) where deleted_at is null;

create index contract_signed_starts_on_idx
    on contract (starts_on) where status = 'SIGNED' and deleted_at is null;

create index contract_active_ends_on_idx
    on contract (ends_on) where status = 'ACTIVE' and deleted_at is null;

create table contract_item (
    id                     bigint        generated always as identity primary key,
    token                  varchar(32)   not null,
    contract_id            bigint        not null references contract (id),
    dependent_id           bigint        not null references dependent (id),
    school_id              bigint        not null references school (id),
    pickup_city_id         bigint        not null references city (id),
    pickup_zip_code        varchar(8),
    pickup_street          varchar(255)  not null,
    pickup_number          varchar(16),
    pickup_complement      varchar(128),
    pickup_neighborhood    varchar(128),
    pickup_district_id     bigint        references district (id),
    pickup_google_place_id varchar(255),
    monthly_amount         numeric(12,2) not null,
    schedule_id            bigint        not null references schedule (id),
    created_at             timestamptz   not null default now(),
    updated_at             timestamptz   not null default now(),
    deleted_at             timestamptz,
    constraint contract_item_monthly_amount_check check (monthly_amount > 0)
);

comment on table contract_item is
    'One dependent in a contract: school, pickup address and schedule as agreed.';
comment on column contract_item.pickup_street is
    'The pickup_* columns are a deliberate copy of the dependent address, not a reference: editing the address later must not change what was agreed.';

create unique index contract_item_token_active_key
    on contract_item (token) where deleted_at is null;

create index contract_item_contract_idx
    on contract_item (contract_id) where deleted_at is null;

create index contract_item_dependent_idx
    on contract_item (dependent_id) where deleted_at is null;

create unique index contract_item_schedule_id_active_key
    on contract_item (schedule_id) where deleted_at is null;
