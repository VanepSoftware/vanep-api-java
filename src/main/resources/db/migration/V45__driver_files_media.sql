alter table driver_cnh add column photo_media_id bigint;
alter table driver_document add column file_media_id bigint;

alter table driver_cnh
    add constraint fk_driver_cnh_photo_media
    foreign key (photo_media_id) references media_file (id);

alter table driver_document
    add constraint fk_driver_document_file_media
    foreign key (file_media_id) references media_file (id);

alter table driver_cnh drop column photo_url;
alter table driver_document drop column file_url;
