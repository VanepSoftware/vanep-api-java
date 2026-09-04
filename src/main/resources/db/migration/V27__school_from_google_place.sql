-- School deixa de ser cadastro manual e passa a nascer de um place do Google.
-- Com a fonte sendo o Places, cnpj, phone e email não têm de onde vir e nunca
-- foram preenchidos por dado real — sair é mais honesto que ficar sempre nulo.

alter table school add column google_place_id varchar(255);
alter table school add column city_id bigint;
alter table school add column district_id bigint;

alter table school add constraint school_city_id_fkey
    foreign key (city_id) references city (id);
alter table school add constraint school_district_id_fkey
    foreign key (district_id) references district (id);

-- Idempotência do POST /api/schools/resolve mora aqui: o mesmo place sempre
-- devolve a mesma linha, mesmo sob requisições concorrentes.
create unique index school_google_place_id_active_key
    on school (google_place_id) where deleted_at is null;

create index school_city_idx on school (city_id) where deleted_at is null;
create index school_district_idx on school (district_id) where deleted_at is null;

drop index if exists school_cnpj_active_key;

alter table school drop column cnpj;
alter table school drop column phone;
alter table school drop column email;

comment on column school.google_place_id is
    'Place que originou a escola. Único entre linhas ativas: é o que torna o resolve idempotente.';
comment on column school.city_id is 'Nó de cidade da árvore. Alimenta a busca por destino.';
comment on column school.district_id is 'Nó de distrito, quando o place traz esse nível.';
