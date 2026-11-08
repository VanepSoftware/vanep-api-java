alter table client_driver
    add column origin varchar(16),
    add column blocked_by bigint references users (id),
    add column blocked_at timestamptz;

-- The client search is the only way a link could be created before this column.
update client_driver
set origin = 'CLIENT_SEARCH';

alter table client_driver
    alter column origin set not null;

-- BLOCKED links written by the PATCH that V60 removed have no blocker, so no party could ever
-- unblock them: they get the status their contracts give (the LinkStatusPolicy rule). Removed
-- links are included because the checks below cover every row.
update client_driver link
set status = case
        when exists (
            select 1
            from contract
            where contract.client_driver_id = link.id
              and contract.deleted_at is null
              and contract.status in ('SIGNED', 'ACTIVE', 'SUSPENDED')) then 'ACTIVE'
        when exists (
            select 1
            from contract
            where contract.client_driver_id = link.id
              and contract.deleted_at is null) then 'INACTIVE'
        else 'PENDING'
    end,
    updated_at = now()
where link.status = 'BLOCKED';

alter table client_driver
    add constraint client_driver_blocked_by_check
        check ((status = 'BLOCKED') = (blocked_by is not null)),
    add constraint client_driver_blocked_at_check
        check ((status = 'BLOCKED') = (blocked_at is not null));

comment on column client_driver.origin is
    'LinkOrigin: CLIENT_SEARCH or DRIVER_INVITE. Written once when the link is created and never updated, because the commission is charged by origin.';
comment on column client_driver.blocked_by is
    'User who blocked the link; only that user can unblock it. Set exactly when status is BLOCKED.';
comment on column client_driver.blocked_at is
    'When the link was blocked. Set exactly when status is BLOCKED.';
