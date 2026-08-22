-- Endereço deixa de ser catálogo da plataforma: cada linha address pertence a
-- no máximo um dono ativo (client, dependent ou school). Sem backfill — o
-- banco local deve ser recriado se ainda houver address_id compartilhado.

alter table client
    add constraint client_address_id_fkey
    foreign key (address_id) references address (id);

alter table dependent
    add constraint dependent_address_id_fkey
    foreign key (address_id) references address (id);

alter table school
    add constraint school_address_id_fkey
    foreign key (address_id) references address (id);

create unique index client_address_id_active_key
    on client (address_id)
    where address_id is not null and deleted_at is null;

create unique index dependent_address_id_active_key
    on dependent (address_id)
    where address_id is not null and deleted_at is null;

create unique index school_address_id_active_key
    on school (address_id)
    where address_id is not null and deleted_at is null;

comment on table address is 'Endereço exclusivo de um dono (client, dependent ou school). Não é catálogo da plataforma.';
comment on column client.address_id is 'FK para address.id. No máximo um client ativo por endereço.';
comment on column dependent.address_id is 'FK para address.id. No máximo um dependent ativo por endereço.';
comment on column school.address_id is 'FK para address.id. No máximo uma school ativa por endereço.';
