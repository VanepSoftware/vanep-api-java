create table family_invite (
    id                    bigint      generated always as identity primary key,
    token                 varchar(32) not null,
    code                  varchar(16) not null,
    driver_id             bigint      not null references driver (id),
    unlinked_passenger_id bigint      references unlinked_passenger (id),
    sent_at               timestamptz not null,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    deleted_at            timestamptz
);

comment on table family_invite is
    'Link a driver shares so a guardian joins the app. With unlinked_passenger_id it invites the family of that student; without it, it is the driver''s link for the parents'' group. It does not expire: it is read days later in a group chat.';
comment on column family_invite.code is
    'Public identifier in the invite URL. Resending keeps the code, so links already shared keep working.';

create unique index family_invite_token_active_key
    on family_invite (token) where deleted_at is null;

create unique index family_invite_code_active_key
    on family_invite (code) where deleted_at is null;

create unique index family_invite_passenger_active_key
    on family_invite (unlinked_passenger_id)
    where unlinked_passenger_id is not null and deleted_at is null;

create unique index family_invite_driver_group_active_key
    on family_invite (driver_id)
    where unlinked_passenger_id is null and deleted_at is null;

create table family_match (
    id                    bigint      generated always as identity primary key,
    token                 varchar(32) not null,
    unlinked_passenger_id bigint      not null references unlinked_passenger (id),
    dependent_id          bigint      not null references dependent (id),
    family_invite_id      bigint      not null references family_invite (id),
    status                varchar(16) not null,
    responded_at          timestamptz,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    deleted_at            timestamptz
);

comment on table family_match is
    'Suggestion that a dependent a guardian registered is an unlinked passenger of the driver. Only the driver confirms (merge) or dismisses it.';
comment on column family_match.status is 'FamilyMatchStatus: PENDING, CONFIRMED or DISMISSED.';

create unique index family_match_token_active_key
    on family_match (token) where deleted_at is null;

create unique index family_match_pending_passenger_key
    on family_match (unlinked_passenger_id) where status = 'PENDING' and deleted_at is null;

create index family_match_dependent_idx
    on family_match (dependent_id) where deleted_at is null;
