alter table city
    add column latitude numeric(9, 6),
    add column longitude numeric(9, 6),
    add constraint city_coordinates_pair_check
        check ((latitude is null and longitude is null) or (latitude is not null and longitude is not null)),
    add constraint city_latitude_range_check check (latitude is null or latitude between -90 and 90),
    add constraint city_longitude_range_check check (longitude is null or longitude between -180 and 180);

comment on column city.latitude is 'Latitude da sede municipal segundo o catálogo IBGE 2022.';
comment on column city.longitude is 'Longitude da sede municipal segundo o catálogo IBGE 2022.';
