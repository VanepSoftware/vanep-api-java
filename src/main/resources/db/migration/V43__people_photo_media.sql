alter table client add column photo_media_id bigint;
alter table assistant add column photo_media_id bigint;

alter table client
    add constraint fk_client_photo_media
    foreign key (photo_media_id) references media_file (id);

alter table assistant
    add constraint fk_assistant_photo_media
    foreign key (photo_media_id) references media_file (id);

alter table client drop column photo;
alter table assistant drop column photo;
