-- A proposal is the driver's answer to a service request. It is never edited:
-- each change is a new version of the same request, and the previous SENT one
-- becomes SUPERSEDED. The end of a proposal is a status, never a removal: these
-- tables have no deleted_at.

create table proposal (
    id                    bigint        generated always as identity primary key,
    token                 varchar(32)   not null,
    client_driver_id      bigint        not null references client_driver (id),
    service_request_id    bigint        not null references service_request (id),
    version               smallint      not null,
    parent_proposal_id    bigint        references proposal (id),
    status                varchar(16)   not null,
    valid_until           timestamptz   not null,
    starts_on             date          not null,
    ends_on               date          not null,
    total_amount          numeric(12,2) not null,
    installments          smallint      not null,
    due_day               smallint      not null,
    enrollment_fee        numeric(12,2),
    adjustment_policy     varchar(32)   not null,
    cancellation_policy   varchar(32)   not null,
    vehicle_id            bigint        not null references vehicle (id),
    message               varchar(1000),
    decline_reason        varchar(500),
    accepted_at           timestamptz,
    source_contract_id    bigint        references contract (id),
    resulting_contract_id bigint        references contract (id),
    snapshot              jsonb         not null,
    created_at            timestamptz   not null default now(),
    updated_at            timestamptz   not null default now(),
    constraint proposal_version_check check (version >= 1),
    constraint proposal_period_check check (ends_on > starts_on),
    constraint proposal_total_amount_check check (total_amount > 0),
    constraint proposal_installments_check check (installments between 1 and 12),
    -- Up to 28 so that every month, February included, has the due day (same as contract).
    constraint proposal_due_day_check check (due_day between 1 and 28),
    constraint proposal_enrollment_fee_check check (enrollment_fee >= 0),
    constraint proposal_accepted_at_check check ((status = 'ACCEPTED') = (accepted_at is not null)),
    constraint proposal_resulting_contract_check check (resulting_contract_id is null or status = 'ACCEPTED'),
    constraint proposal_snapshot_check check (snapshot ?& array['version', 'vehicle'])
);

comment on table proposal is
    'Immutable, versioned answer of a driver to a service_request. Only status, accepted_at, decline_reason and resulting_contract_id are written after creation.';
comment on column proposal.status is
    'ProposalStatus. SENT is the current version, at most one per client_driver pair; ACCEPTED, SUPERSEDED, DECLINED, EXPIRED and WITHDRAWN are terminal.';
comment on column proposal.client_driver_id is
    'Derivable from the service request, kept here because the one-SENT-per-pair index needs it on this table.';
comment on column proposal.adjustment_policy is
    'ProposalAdjustmentPolicy, a closed menu. The values are provisional until the legal review.';
comment on column proposal.cancellation_policy is
    'ProposalCancellationPolicy, a closed menu. The values are provisional until the legal review.';
comment on column proposal.source_contract_id is
    'Contract this proposal amends or renews (a later phase). Null for a first agreement.';
comment on column proposal.snapshot is
    'The vehicle as offered (plate, brand, model, year, color, capacity), written once and shown as is: the driver can edit, remove and restore the vehicle, and the proposal keeps showing what the client was offered. vehicle_id is for navigation and for the re-check on accept.';
comment on column proposal.resulting_contract_id is
    'Contract generated when this proposal is accepted (a later phase). Null until then.';

create unique index proposal_token_key
    on proposal (token);

create unique index proposal_client_driver_sent_key
    on proposal (client_driver_id) where status = 'SENT';

create unique index proposal_request_version_key
    on proposal (service_request_id, version);

create index proposal_sent_valid_until_idx
    on proposal (valid_until) where status = 'SENT';

create unique index proposal_resulting_contract_key
    on proposal (resulting_contract_id) where resulting_contract_id is not null;

create index proposal_source_contract_idx
    on proposal (source_contract_id) where source_contract_id is not null;

create table proposal_item (
    id             bigint        generated always as identity primary key,
    proposal_id    bigint        not null references proposal (id),
    dependent_id   bigint        not null references dependent (id),
    monthly_amount numeric(12,2) not null,
    schedule_id    bigint        not null references schedule (id),
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now(),
    constraint proposal_item_monthly_amount_check check (monthly_amount > 0)
);

comment on table proposal_item is
    'One dependent in a proposal: price and schedule. The dependent is shown from the service_request_item copy, so a dependent removed later does not hide the proposal.';

create index proposal_item_proposal_idx
    on proposal_item (proposal_id);

create unique index proposal_item_schedule_id_key
    on proposal_item (schedule_id);

create table proposal_adjustment (
    id              bigint       generated always as identity primary key,
    proposal_id     bigint       not null references proposal (id),
    field           varchar(16)  not null,
    suggested_value varchar(100),
    note            varchar(500),
    created_at      timestamptz  not null default now(),
    constraint proposal_adjustment_content_check check (suggested_value is not null or note is not null)
);

comment on table proposal_adjustment is
    'Adjustment the client asked for on a SENT proposal. Never edited: the driver answers with the next version.';
comment on column proposal_adjustment.suggested_value is
    'Free short text ("320,00", "only outbound on Wednesdays"): each field has its own format and the driver only reads it.';

create index proposal_adjustment_proposal_idx
    on proposal_adjustment (proposal_id);
