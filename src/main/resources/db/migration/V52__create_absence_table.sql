-- Day-of absence for one dependent on one van link. One active row per
-- (dependent, date, leg). trip_id is optional so a guardian can report
-- before that leg's trip exists.

create table absence (
    id               bigint      generated always as identity primary key,
    token            varchar(32) not null,
    client_driver_id bigint      not null references client_driver (id),
    dependent_id     bigint      not null references dependent (id),
    trip_id          bigint      references trip (id),
    absence_date     date        not null,
    leg              varchar(16) not null,
    source           varchar(16) not null,
    reason           varchar(255),
    notified_at      timestamptz,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    deleted_at       timestamptz
);

comment on table absence is
    'Absence of a dependent on one route leg of one service day. BOTH is never stored.';
comment on column absence.trip_id is
    'Filled when the matching trip exists; stays null when the guardian reports before start.';
comment on column absence.leg is
    'OUTBOUND is home to school; RETURN is school to home. UI BOTH becomes two rows.';

create unique index absence_token_active_key
    on absence (token) where deleted_at is null;

-- Partial so a soft-deleted report does not lock that leg for the rest of the day.
create unique index absence_dependent_date_leg_active_key
    on absence (dependent_id, absence_date, leg) where deleted_at is null;

create index absence_client_driver_idx
    on absence (client_driver_id) where deleted_at is null;

create index absence_dependent_idx
    on absence (dependent_id) where deleted_at is null;

create index absence_trip_idx
    on absence (trip_id) where deleted_at is null;

create index absence_date_idx
    on absence (absence_date) where deleted_at is null;
