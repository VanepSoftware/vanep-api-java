-- driver_rating carregava o par (client_id, driver_id) por conta própria, duplicando
-- o que o client_driver (V36) passou a guardar. Mesmo fato em dois lugares, livre
-- para divergir. Aqui a avaliação passa a pendurar no vínculo.

alter table driver_rating add column client_driver_id bigint;

-- Backfill. O hub nasceu na V36, então os pares já avaliados não têm vínculo.
-- DISTINCT porque uma avaliação soft-deletada repete o par de uma ativa; sem ele
-- o índice único do client_driver quebra na inserção.
-- ACTIVE, não PENDING: uma avaliação registrada é prova de que a relação
-- aconteceu; PENDING descreveria um convite que nunca houve.
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

-- Se o backfill deixou alguma avaliação órfã, é aqui que a migration para,
-- antes de destruir as colunas de origem.
alter table driver_rating alter column client_driver_id set not null;

alter table driver_rating
    add constraint fk_driver_rating_client_driver
    foreign key (client_driver_id) references client_driver (id);

-- A unicidade passa a ser por vínculo. Desvincular e revincular gera outro
-- client_driver_id, então uma nota nova é permitida e a antiga continua
-- apontando o vínculo antigo.
drop index idx_driver_rating_driver_client_unique;

create unique index idx_driver_rating_link_unique
    on driver_rating (client_driver_id)
    where deleted_at is null;

-- O par sai da tabela de avaliação. Manter as colunas manteria duas fontes para o
-- mesmo fato: uma avaliação cujo vínculo aponta a Maria e cujo client_id aponta o
-- Bruno é um estado que ninguém consegue interpretar.
alter table driver_rating drop column client_id;
alter table driver_rating drop column driver_id;

comment on column driver_rating.client_driver_id is
    'Vínculo avaliado. O par cliente-motorista vive só em client_driver.';
