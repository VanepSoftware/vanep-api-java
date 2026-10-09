-- With vanep.seed.enabled=false (the default outside the local profile) the seeder
-- never syncs the ADMIN bundle, so this migration grants the contract permissions there.
--
-- Idempotent: each permission is appended only if the bundle does not have it yet.

update role_permissions
set permissions = permissions || '["list_contracts"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["list_contracts"]'::jsonb;

update role_permissions
set permissions = permissions || '["show_contract"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["show_contract"]'::jsonb;

update role_permissions
set permissions = permissions || '["create_contract"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["create_contract"]'::jsonb;

update role_permissions
set permissions = permissions || '["update_contract"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["update_contract"]'::jsonb;

update role_permissions
set permissions = permissions || '["delete_contract"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["delete_contract"]'::jsonb;

update role_permissions
set permissions = permissions || '["restore_contract"]'::jsonb
where name = 'ADMIN'
  and not permissions @> '["restore_contract"]'::jsonb;
