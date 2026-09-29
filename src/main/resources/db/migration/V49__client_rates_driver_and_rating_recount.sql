-- O cliente avalia o motorista (UC12, #158), mas o bundle CLIENT nunca recebeu
-- create_driver_rating: só o admin conseguia criar uma avaliação. O seeder só monta
-- o bundle quando ele ainda não existe, então ambientes já inicializados dependem
-- desta migration.
--
-- Idempotente: só adiciona se ainda não estiver no bundle.

update role_permissions
set permissions = permissions || '["create_driver_rating"]'::jsonb
where name = 'CLIENT'
  and not permissions @> '["create_driver_rating"]'::jsonb;

-- driver.rating saiu do total acumulado de dois jeitos: apagar a última avaliação
-- gravava 5.00 no lugar de "sem avaliações", e o seeder criava avaliação sem
-- recalcular a média. Recalcula todos os motoristas a partir das avaliações que
-- existem. Motorista sem avaliação fica com null, não com 5.00 nem 0.00.
--
-- Vínculos removidos (soft delete) ficam de fora, como no AVG do DriverRatingService,
-- que navega por um ClientDriverModel com @SoftDelete.

update driver motorista
set rating = (
    select round(avg(avaliacao.rating), 2)
    from driver_rating avaliacao
    join client_driver vinculo on vinculo.id = avaliacao.client_driver_id
    where vinculo.driver_id = motorista.id
      and vinculo.deleted_at is null
);
