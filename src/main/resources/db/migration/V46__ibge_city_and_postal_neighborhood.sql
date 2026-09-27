alter table city add column ibge_code varchar(7);

create unique index city_ibge_code_active_key
    on city (ibge_code) where deleted_at is null;

comment on column city.ibge_code is
    'IBGE municipality code (7 digits). Identity of the city catalog.';

alter table address add column neighborhood varchar(128);

comment on column address.neighborhood is
    'Free-text neighborhood from the postal form. Not a district tree node.';

drop index if exists city_google_place_id_active_key;
drop index if exists state_google_place_id_active_key;

alter table city drop column google_place_id;
alter table state drop column google_place_id;
