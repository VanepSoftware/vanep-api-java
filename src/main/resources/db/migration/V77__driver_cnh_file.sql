-- The CNH file may be a PDF (the digital CNH), so it is no longer a photo. Stored objects keep
-- their object_key; only the column and its foreign key change name. The driver sends only the
-- file: the license's data is filled by the reviewer on approval, and binds only approved CNHs.
alter table driver_cnh rename column photo_media_id to file_media_id;
alter table driver_cnh rename constraint fk_driver_cnh_photo_media to fk_driver_cnh_file_media;

comment on column driver_cnh.file_media_id is 'The reviewed file of the CNH: an image or a PDF.';

alter table driver_cnh
    alter column registration_number drop not null,
    alter column category drop not null,
    alter column issue_date drop not null,
    alter column valid_until drop not null;

alter table driver_cnh
    add constraint driver_cnh_approved_data_check
        check (status <> 'APPROVED'
               or (registration_number is not null and category is not null
                   and issue_date is not null and valid_until is not null));

comment on column driver_cnh.registration_number is 'Filled by the reviewer on approval; one driver per number (exclusion constraint), nulls ignored.';
