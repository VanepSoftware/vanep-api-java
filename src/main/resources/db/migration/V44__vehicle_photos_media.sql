alter table vehicle add column photo_front_media_id bigint;
alter table vehicle add column photo_side_media_id bigint;
alter table vehicle add column photo_document_media_id bigint;

alter table vehicle
    add constraint fk_vehicle_photo_front_media
    foreign key (photo_front_media_id) references media_file (id);

alter table vehicle
    add constraint fk_vehicle_photo_side_media
    foreign key (photo_side_media_id) references media_file (id);

alter table vehicle
    add constraint fk_vehicle_photo_document_media
    foreign key (photo_document_media_id) references media_file (id);

alter table vehicle drop column photo_front_url;
alter table vehicle drop column photo_side_url;
alter table vehicle drop column photo_document_url;
