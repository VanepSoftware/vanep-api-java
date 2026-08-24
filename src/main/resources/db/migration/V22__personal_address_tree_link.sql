-- Endereço pessoal deixa de guardar bairro como texto livre e passa a apontar
-- para a árvore geográfica. Ver design.md (D1, D9) e a spec personal-address.

alter table address add column district_id bigint;
alter table address add constraint address_district_id_fkey
    foreign key (district_id) references district (id);

alter table address add column google_place_id varchar(255);

-- O bairro agora é um nó da árvore, compartilhado com a área de atuação do
-- motorista. Texto livre nunca casaria com o nó escolhido pelo motorista.
alter table address drop column district;

-- Evidência da fase 1: places reais do DF vêm sem postal_code (fixture
-- df-ceilandia). Manter NOT NULL rejeitaria endereços legítimos.
alter table address alter column zip_code drop not null;

comment on column address.district_id is
    'Nó da árvore em que este endereço está. Nulo quando a cidade não tem subdivisão resolvida.';
comment on column address.google_place_id is
    'Place do Google que originou o endereço. O backend sempre re-resolve; o cliente envia só o id.';

create index address_district_idx on address (district_id) where deleted_at is null;

-- --------------------------------------------------------------------------
-- Endereço pessoal para todo papel.
--
-- A spec pede endereço pessoal para client, driver, assistant e dependent, mas
-- `driver` nunca teve address_id e a redação original só previa `assistant`.
-- Um endereço por PAPEL também daria dois endereços a quem é cliente e
-- motorista ao mesmo tempo. O endereço residencial é do ser humano, não do
-- papel — e o endpoint é /api/user/me/address. Logo, mora em `users`.
--
-- `client.address_id` fica redundante a partir daqui. Não é removido nesta
-- migration porque arrastaria ClientService, DTOs e testes para dentro de uma
-- fase que já está no limite de arquivos (regra 41); a remoção está registrada
-- na fase 9.
-- --------------------------------------------------------------------------

alter table users add column address_id bigint;
alter table users add constraint users_address_id_fkey
    foreign key (address_id) references address (id);
create unique index users_address_id_active_key
    on users (address_id) where address_id is not null and deleted_at is null;

alter table assistant add column address_id bigint;
alter table assistant add constraint assistant_address_id_fkey
    foreign key (address_id) references address (id);
create unique index assistant_address_id_active_key
    on assistant (address_id) where address_id is not null and deleted_at is null;

comment on column users.address_id is
    'Endereço residencial do usuário, qualquer que seja o papel. Privado: nunca aparece em resposta de busca.';
