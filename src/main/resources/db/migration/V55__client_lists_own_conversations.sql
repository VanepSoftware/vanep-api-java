-- The client gets a chat tab listing their conversations, so the CLIENT bundle needs the
-- list permission the DRIVER bundle already has. Idempotent.

update role_permissions
set permissions = permissions || '["list_client_conversations"]'::jsonb
where name in ('CLIENT', 'ADMIN')
  and not permissions @> '["list_client_conversations"]'::jsonb;
