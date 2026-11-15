-- The seeder never rewrites an existing CLIENT bundle, so initialized environments lose it here.

update role_permissions
set permissions = permissions - 'create_conversation'
where permissions @> '["create_conversation"]'::jsonb;

insert into conversation (token, client_id, driver_id)
select substr(replace(gen_random_uuid()::text, '-', ''), 1, 25),
       pairs.client_id,
       pairs.driver_id
from (select distinct link.client_id, link.driver_id
      from service_request request
      join client_driver link on link.id = request.client_driver_id) as pairs
where not exists (
    select 1
    from conversation existing
    where existing.client_id = pairs.client_id
      and existing.driver_id = pairs.driver_id
      and existing.deleted_at is null
);
