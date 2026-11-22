-- The van's documents get a table owned by the van, and driver_document keeps only the person's
-- documents. Nothing is moved: no app ever sent a van document or a vehicle document photo.
-- The residence proof and the free type go too: no approval rule reads them.
-- The driver sends only the file; the reviewer fills the number and the expiry on approval, so
-- the data columns are nullable and the checks bind only approved documents.

create table vehicle_document (
    id                bigint generated always as identity primary key,
    token             varchar(32)  not null,
    vehicle_id        bigint       not null references vehicle (id),
    document_type     varchar(50)  not null,
    document_number   varchar(64),
    file_media_id     bigint       references media_file (id),
    expires_at        date,
    status            varchar(20)  not null default 'PENDING',
    review_method     varchar(30),
    external_check_id varchar(64),
    rejection_reason  varchar(255),
    reviewed_by       bigint       references users (id),
    reviewed_at       timestamptz,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    deleted_at        timestamptz,
    constraint vehicle_document_type_check
        check (document_type in ('CRLV', 'VEHICLE_INSPECTION', 'SCHOOL_VEHICLE_AUTHORIZATION')),
    constraint vehicle_document_approved_expiry_check
        check (status <> 'APPROVED' or expires_at is not null),
    constraint vehicle_document_authorization_number_check
        check (document_type <> 'SCHOOL_VEHICLE_AUTHORIZATION' or status <> 'APPROVED'
               or document_number is not null)
);

create unique index vehicle_document_token_active_key
    on vehicle_document (token) where deleted_at is null;
create index vehicle_document_vehicle_type_idx
    on vehicle_document (vehicle_id, document_type) where deleted_at is null;

comment on table vehicle_document is
    'Documents of a van (CRLV, semiannual inspection, school transport authorization, CTB art. 136), with a history: a van keeps every inspection, and the approved one with the latest expiry counts.';
comment on column vehicle_document.document_number is
    'Filled by the reviewer on approval; required for an approved SCHOOL_VEHICLE_AUTHORIZATION, the number the contract cites.';
comment on column vehicle_document.expires_at is
    'Filled by the reviewer on approval; required once approved.';

alter table driver_document add column document_number varchar(64);

comment on column driver_document.document_number is
    'Filled by the reviewer on approval; required for an approved MUNICIPAL_AUTHORIZATION, the number the contract cites.';

-- An approved municipal authorization had no number before: it goes back to review.
update driver_document
set status = 'PENDING', reviewed_by = null, reviewed_at = null, review_method = null,
    external_check_id = null, rejection_reason = null
where document_type = 'MUNICIPAL_AUTHORIZATION'
  and status = 'APPROVED';

alter table driver_document
    add constraint driver_document_authorization_number_check
    check (document_type <> 'MUNICIPAL_AUTHORIZATION' or status <> 'APPROVED'
           or document_number is not null);

update driver_document
set deleted_at = now()
where deleted_at is null
  and document_type in ('CRLV', 'VEHICLE_INSPECTION', 'PROFILE_PHOTO', 'VEHICLE_PHOTO',
                        'RESIDENCE_PROOF', 'OTHER');

alter table driver_document
    add constraint driver_document_type_check
    check (deleted_at is not null
           or document_type in ('MUNICIPAL_AUTHORIZATION', 'SCHOOL_DRIVER_COURSE', 'CRIMINAL_RECORD'));

alter table vehicle drop constraint fk_vehicle_photo_document_media;
alter table vehicle drop column photo_document_media_id;

-- With vanep.seed.enabled=false the seeder never syncs the ADMIN bundle (see V59).
update role_permissions
set permissions = permissions || '["list_vehicle_documents"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["list_vehicle_documents"]'::jsonb;

update role_permissions
set permissions = permissions || '["show_vehicle_document"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["show_vehicle_document"]'::jsonb;

update role_permissions
set permissions = permissions || '["update_vehicle_document"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["update_vehicle_document"]'::jsonb;

update role_permissions
set permissions = permissions || '["delete_vehicle_document"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["delete_vehicle_document"]'::jsonb;

update role_permissions
set permissions = permissions || '["restore_vehicle_document"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["restore_vehicle_document"]'::jsonb;
