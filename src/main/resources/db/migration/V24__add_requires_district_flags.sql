-- D8: "pode declarar esta cidade inteira como área de atuação?" é fato curado,
-- não resposta do Google. Fica no estado, com override opcional por cidade.
--
-- Só as colunas. Quais UFs exigem distrito é dado curado e vive num lugar só,
-- o StateSeeder — uma linha de state criada depois desta migration não seria
-- alcançada por um UPDATE escrito aqui.

alter table state add column requires_district boolean not null default false;
alter table city add column requires_district boolean;

comment on column state.requires_district is
    'Curado: as cidades deste estado exigem granularidade abaixo da cidade na área de atuação do motorista.';
comment on column city.requires_district is
    'Override do flag do estado. Nulo = herda de state.requires_district.';
