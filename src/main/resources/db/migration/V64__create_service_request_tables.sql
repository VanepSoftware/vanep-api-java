-- A service request is a client asking a driver for a quote: which dependents
-- and, per school day, whether the van is needed on the way to school, on the
-- way back, or both. The driver answers with a proposal (V64). The end of a
-- request is a status, never a removal: these tables have no deleted_at.

create table service_request (
    id                 bigint        generated always as identity primary key,
    token              varchar(32)   not null,
    client_driver_id   bigint        not null references client_driver (id),
    status             varchar(16)   not null,
    desired_start_date date          not null,
    notes              varchar(1000),
    decline_reason     varchar(500),
    expires_at         timestamptz   not null,
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now()
);

comment on table service_request is
    'Quote request of a client to a driver. At most one open request per client_driver pair.';
comment on column service_request.status is
    'ServiceRequestStatus. OPEN waits for the driver to send a proposal, until expires_at; ANSWERED waits for the client to answer the SENT proposal, until its valid_until. CONVERTED, DECLINED, EXPIRED and WITHDRAWN are terminal.';
comment on column service_request.expires_at is
    'Deadline of an OPEN request; renewed when the request goes back from ANSWERED to OPEN.';
comment on column service_request.decline_reason is 'Optional reason given by the driver on DECLINED.';

create unique index service_request_token_key
    on service_request (token);

create unique index service_request_client_driver_open_key
    on service_request (client_driver_id) where status in ('OPEN', 'ANSWERED');

create index service_request_open_expires_at_idx
    on service_request (expires_at) where status = 'OPEN';

create index service_request_client_driver_idx
    on service_request (client_driver_id);

create table service_request_item (
    id                 bigint      generated always as identity primary key,
    service_request_id bigint      not null references service_request (id),
    dependent_id       bigint      not null references dependent (id),
    school_id          bigint      not null references school (id),
    pickup_city_id     bigint      not null references city (id),
    snapshot           jsonb       not null,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint service_request_item_snapshot_check
        check (snapshot ?& array['version', 'dependent', 'school', 'pickup'])
);

comment on table service_request_item is
    'One dependent in a service request.';
comment on column service_request_item.snapshot is
    'What the driver priced, as it was at sending time: dependent name and birth date, school name, pickup city, state and neighborhood. Written once and shown as is, so editing, removing or restoring the dependent, or removing the school, never changes nor hides the request. There is no street, number or zip code on purpose: the exact address only reaches the driver with the contract.';
comment on column service_request_item.school_id is
    'dependent_id, school_id and pickup_city_id are taken at sending time and never change: they are for navigation and for the re-check on accept, never for display.';

create index service_request_item_request_idx
    on service_request_item (service_request_id);

create index service_request_item_dependent_idx
    on service_request_item (dependent_id);

create table service_request_day (
    id                      bigint      generated always as identity primary key,
    service_request_item_id bigint      not null references service_request_item (id),
    weekday                 varchar(16) not null,
    school_shift            varchar(16) not null,
    needs_outbound          boolean     not null,
    needs_return            boolean     not null,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    -- A day with no leg is not asked for: it is the absence of a row.
    constraint service_request_day_leg_check check (needs_outbound or needs_return)
);

comment on table service_request_day is
    'What the client asks for each weekday: which legs need the van. The route shift and the boarding window of each leg are the driver''s answer and live in the proposal schedule.';
comment on column service_request_day.weekday is 'java.time.DayOfWeek name (MONDAY to SUNDAY).';
comment on column service_request_day.school_shift is
    'SchoolShift copied from dependent_school_day at sending time, the period the driver priced.';

create unique index service_request_day_weekday_key
    on service_request_day (service_request_item_id, weekday);
