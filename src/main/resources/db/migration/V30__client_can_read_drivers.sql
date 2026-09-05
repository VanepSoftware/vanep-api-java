-- Cliente precisa enxergar motorista: a home lista motoristas e a busca abre o
-- detalhe de um deles. Sem list_drivers e show_driver o papel CLIENT tomava 403
-- em ambas, e a tela de "sugestões" nunca funcionou para cliente nenhum.
--
-- O seeder só cria o bundle quando ele ainda não existe, então ambientes já
-- semeados não seriam alcançados por ele. Esta migration é idempotente: só
-- acrescenta o que estiver faltando.

update role_permissions
set permissions = permissions || '["list_drivers"]'::jsonb
where name = 'CLIENT'
  and not permissions @> '["list_drivers"]'::jsonb;

update role_permissions
set permissions = permissions || '["show_driver"]'::jsonb
where name = 'CLIENT'
  and not permissions @> '["show_driver"]'::jsonb;
