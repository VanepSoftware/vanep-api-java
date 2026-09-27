delete from driver_rating where deleted_at is not null;

drop index idx_driver_rating_driver_client_unique;

alter table driver_rating drop column deleted_at;

create unique index idx_driver_rating_driver_client_unique
    on driver_rating (driver_id, client_id);
