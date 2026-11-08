-- A avaliação do motorista passa a ser imutável para o autor (#158): o PUT
-- /api/driver-ratings/{token} deixou de existir e update_driver_rating saiu do
-- PermissionEnum. O DataSeeder só ressincroniza o bundle ADMIN quando o seed está
-- ligado, o que não é o padrão fora do ambiente local; esta migration tira a
-- permissão órfã de qualquer bundle que ainda a tenha.
--
-- Idempotente: o operador "-" em jsonb remove o elemento se existir e ignora se não.

update role_permissions
set permissions = permissions - 'update_driver_rating'
where permissions @> '["update_driver_rating"]'::jsonb;
