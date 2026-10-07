-- Administrador precisa da permissão approve_driver para aprovar ou rejeitar
-- motoristas submetidos ao onboarding. Em ambientes já inicializados (ou com
-- vanep.seed.enabled=false), esta migration garante a permissão de forma idempotente.

update role_permissions
set permissions = permissions || '["approve_driver"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["approve_driver"]'::jsonb;
