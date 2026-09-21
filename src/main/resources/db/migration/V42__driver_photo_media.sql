alter table driver add column photo_media_id bigint;

alter table driver
    add constraint fk_driver_photo_media
    foreign key (photo_media_id) references media_file (id);

alter table driver drop column photo;

comment on column driver.photo_media_id is
    'Foto do motorista. O dono aponta para a mídia; a mídia não conhece o dono.';
