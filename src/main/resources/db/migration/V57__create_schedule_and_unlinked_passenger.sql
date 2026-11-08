-- A schedule is the set of (weekday, leg) slots a passenger rides. Every owner
-- (contract_item, unlinked_passenger, and later proposal_item and
-- service_request_item) points to its own schedule; there is no polymorphic
-- owner column on the slot, so every reference keeps a real foreign key.

create table schedule (
    id         bigint      generated always as identity primary key,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

comment on table schedule is
    'Weekly schedule of one owner. Never shared: each owner table has a partial unique index on schedule_id.';

create table schedule_slot (
    id           bigint      generated always as identity primary key,
    schedule_id  bigint      not null references schedule (id),
    weekday      varchar(16) not null,
    leg          varchar(16) not null,
    shift        varchar(16) not null,
    window_start time        not null,
    window_end   time,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    deleted_at   timestamptz,
    constraint schedule_slot_window_check check (window_end is null or window_end > window_start)
);

comment on column schedule_slot.weekday is 'java.time.DayOfWeek name (MONDAY to SUNDAY).';
comment on column schedule_slot.leg is 'OUTBOUND is home to school; RETURN is school to home.';
comment on column schedule_slot.shift is
    'Operation shift of the trip this slot rides. Explicit, never derived from the time: a full-time student has an OUTBOUND MORNING slot and a RETURN AFTERNOON slot.';
comment on column schedule_slot.window_end is 'Optional: a return usually has only the school exit time.';

create unique index schedule_slot_weekday_leg_active_key
    on schedule_slot (schedule_id, weekday, leg) where deleted_at is null;

-- Route passengers are found from the owner down to its schedule, so the
-- lookup index starts at schedule_id rather than at the weekday.
create index schedule_slot_weekday_shift_idx
    on schedule_slot (schedule_id, weekday, shift) where deleted_at is null;

create table unlinked_passenger (
    id           bigint       generated always as identity primary key,
    token        varchar(32)  not null,
    driver_id    bigint       not null references driver (id),
    name         varchar(255) not null,
    school_id    bigint       not null references school (id),
    school_shift varchar(16)  not null,
    address_id   bigint       not null references address (id),
    notes        varchar(500),
    schedule_id  bigint       not null references schedule (id),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    deleted_at   timestamptz
);

comment on table unlinked_passenger is
    'Student a driver already transports, registered by the driver for the route only: no guardian, no contract, no notification. Holds only what the route needs.';
comment on column unlinked_passenger.address_id is
    'Pickup address, an owned row of address: at most one active owner per address (same rule as V20).';

create unique index unlinked_passenger_token_active_key
    on unlinked_passenger (token) where deleted_at is null;

create index unlinked_passenger_driver_idx
    on unlinked_passenger (driver_id) where deleted_at is null;

create unique index unlinked_passenger_address_id_active_key
    on unlinked_passenger (address_id) where deleted_at is null;

create unique index unlinked_passenger_schedule_id_active_key
    on unlinked_passenger (schedule_id) where deleted_at is null;
