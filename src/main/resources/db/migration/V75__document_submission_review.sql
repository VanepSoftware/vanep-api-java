-- The columns of the new review on driver_document, vehicle_document and driver_cnh (version,
-- structured reason, superseded date, notice, last data correction) and the CNH reviewed like the
-- documents. The rule of
-- one PENDING submission per owner and type, and a code on every rejection, come with the service
-- that enforces them (V76).

create extension if not exists btree_gist;

alter table driver_document
    add column version               bigint      not null default 0,
    add column rejection_reason_code varchar(30),
    add column superseded_at         timestamptz,
    add column data_corrected_by     bigint      references users (id),
    add column data_corrected_at     timestamptz;

alter table vehicle_document
    add column version               bigint      not null default 0,
    add column rejection_reason_code varchar(30),
    add column superseded_at         timestamptz,
    add column notified_at           timestamptz,
    add column data_corrected_by     bigint      references users (id),
    add column data_corrected_at     timestamptz;

alter table driver_cnh
    add column status                varchar(20) not null default 'PENDING',
    add column review_method         varchar(30),
    add column external_check_id     varchar(64),
    add column rejection_reason      varchar(255),
    add column rejection_reason_code varchar(30),
    add column reviewed_by           bigint      references users (id),
    add column reviewed_at           timestamptz,
    add column notified_at           timestamptz,
    add column version               bigint      not null default 0,
    add column superseded_at         timestamptz,
    add column data_corrected_by     bigint      references users (id),
    add column data_corrected_at     timestamptz;

update driver_document set rejection_reason_code = 'OTHER' where status = 'REJECTED';
update vehicle_document set rejection_reason_code = 'OTHER' where status = 'REJECTED';

-- A renewed CNH keeps its number, so the number is unique per driver, not per row.
drop index driver_cnh_driver_active_key;
drop index driver_cnh_registration_active_key;
alter table driver_cnh
    add constraint driver_cnh_registration_one_driver_excl
    exclude using gist (registration_number with =, driver_id with <>)
    where (deleted_at is null);

alter table driver_document
    add constraint driver_document_rejection_reason_code_check
        check (rejection_reason_code in ('ILLEGIBLE', 'EXPIRED', 'DATA_MISMATCH', 'WRONG_TYPE',
                                         'INCOMPLETE', 'NOT_ELIGIBLE', 'OTHER'));

alter table vehicle_document
    add constraint vehicle_document_rejection_reason_code_check
        check (rejection_reason_code in ('ILLEGIBLE', 'EXPIRED', 'DATA_MISMATCH', 'WRONG_TYPE',
                                         'INCOMPLETE', 'NOT_ELIGIBLE', 'OTHER'));

alter table driver_cnh
    add constraint driver_cnh_status_check
        check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    add constraint driver_cnh_rejection_reason_code_check
        check (rejection_reason_code in ('ILLEGIBLE', 'EXPIRED', 'DATA_MISMATCH', 'WRONG_TYPE',
                                         'INCOMPLETE', 'NOT_ELIGIBLE', 'OTHER'));

alter table driver add column rejection_reason_code varchar(30);
update driver set rejection_reason_code = 'OTHER' where approval_status = 'REJECTED';
alter table driver
    add constraint driver_rejection_reason_code_check
    check (rejection_reason_code in ('DATA_MISMATCH', 'NOT_ELIGIBLE', 'DUPLICATE_ACCOUNT', 'OTHER'));

-- With vanep.seed.enabled=false the seeder never syncs the ADMIN bundle (see V59).
update role_permissions
set permissions = permissions || '["review_documents"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["review_documents"]'::jsonb;

comment on column driver_document.version is 'Optimistic lock: a decision carries the version the reviewer saw.';
comment on column driver_document.rejection_reason_code is 'Structured reason of a rejection; rejection_reason is the optional message.';
comment on column driver_document.superseded_at is 'Set when another submission of the same owner and type replaced this one; it then keeps only the decision.';
comment on column vehicle_document.version is 'Optimistic lock: a decision carries the version the reviewer saw.';
comment on column vehicle_document.rejection_reason_code is 'Structured reason of a rejection; rejection_reason is the optional message.';
comment on column vehicle_document.superseded_at is 'Set when another submission of the same owner and type replaced this one; it then keeps only the decision.';
comment on column vehicle_document.notified_at is 'When the driver was e-mailed about the rejection.';
comment on column driver_cnh.status is 'Review of the CNH: only an APPROVED one is a credential.';
comment on column driver_cnh.version is 'Optimistic lock: a decision carries the version the reviewer saw.';
comment on column driver_cnh.superseded_at is 'Set when a renewed CNH was approved; it then keeps only the decision.';
comment on column driver_cnh.notified_at is 'When the driver was e-mailed about the rejection.';
comment on column driver.rejection_reason_code is 'Structured reason of the onboarding rejection; rejection_reason is the message.';

comment on column driver_document.data_corrected_by is 'The reviewer who last corrected the data of the approved submission (D25).';
comment on column vehicle_document.data_corrected_by is 'The reviewer who last corrected the data of the approved submission (D25).';
comment on column driver_cnh.data_corrected_by is 'The reviewer who last corrected the data of the approved CNH (D25).';
