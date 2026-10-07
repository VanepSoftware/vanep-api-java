alter table driver_rating add column client_driver_id bigint;

insert into client_driver (token, client_id, driver_id, status)
select substr(replace(gen_random_uuid()::text, '-', ''), 1, 25),
       pares.client_id,
       pares.driver_id,
       'ACTIVE'
from (select distinct client_id, driver_id from driver_rating) as pares
where not exists (
    select 1
    from client_driver existente
    where existente.client_id = pares.client_id
      and existente.driver_id = pares.driver_id
      and existente.deleted_at is null
);

update driver_rating avaliacao
set client_driver_id = vinculo.id
from client_driver vinculo
where vinculo.client_id = avaliacao.client_id
  and vinculo.driver_id = avaliacao.driver_id
  and vinculo.deleted_at is null;

alter table driver_rating alter column client_driver_id set not null;

alter table driver_rating
    add constraint fk_driver_rating_client_driver
    foreign key (client_driver_id) references client_driver (id);

drop index idx_driver_rating_driver_client_unique;

create unique index idx_driver_rating_link_unique
    on driver_rating (client_driver_id);

alter table driver_rating drop column client_id;
alter table driver_rating drop column driver_id;

comment on column driver_rating.client_driver_id is
    'Vínculo avaliado. O par cliente-motorista vive só em client_driver.';
