-- RN12: um cliente tem no maximo um dependente padrao ativo.
-- Limpa duplicatas existentes (mantem a mais antiga) antes de criar o indice.
update dependent d
   set is_default = false
 where d.is_default
   and d.deleted_at is null
   and d.id <> (
       select min(o.id)
         from dependent o
        where o.client_id = d.client_id
          and o.is_default
          and o.deleted_at is null);

create unique index dependent_default_per_client_key
    on dependent (client_id) where is_default and deleted_at is null;
