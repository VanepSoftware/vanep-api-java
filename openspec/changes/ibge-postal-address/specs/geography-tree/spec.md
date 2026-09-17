## MODIFIED Requirements

### Requirement: Construção lazy da árvore a partir dos address components

O sistema SHALL construir nós de **district** sob demanda a partir dos `addressComponents` devolvidos pelo Google Place Details. O sistema MUST NOT criar linhas de `country`, `state` ou `city` a partir do Google. `country` e `state` permanecem curados; `city` é o catálogo de municípios IBGE. A criação de distritos MUST ser idempotente: resolver a mesma cadeia de distrito duas vezes MUST NOT criar nós duplicados.

O sistema MUST persistir o nó de distrito derivado dos `addressComponents` do place, e MUST NOT persistir o place selecionado pelo usuário como o próprio nó.

O sistema MUST casar o componente de cidade do Google (`administrative_area_level_2`, com fallback `locality`) a uma `city` existente por UF do `state` mais `normalized_name`. O sistema MUST NOT criar uma `city` quando esse match falhar, MUST NOT consultar tabela de alias nesta change, e MUST recusar a resolução com erro de negócio resolvido pelo MessageSource. O erro MUST incluir contexto de log suficiente (UF, nome Google da cidade, place id quando conhecido) para uma change futura de alias.

#### Scenario: Primeira resolução cria distritos sob cidade existente

- **WHEN** Brasília já existe como `city` IBGE sob DF
- **AND** o primeiro place que resolve para `[BR, DF, Brasília, Taguatinga]` é processado
- **THEN** o sistema cria o distrito Taguatinga sob essa cidade
- **AND** não insere uma nova linha de `city`

#### Scenario: Segunda resolução reusa a cadeia

- **WHEN** um segundo place que resolve para a mesma cadeia é processado
- **THEN** o sistema reusa os nós existentes de cidade e distrito
- **AND** não cria duplicatas

#### Scenario: Place selecionado é normalizado antes de persistir

- **WHEN** um usuário seleciona o place "Taguatinga Norte" e os `addressComponents` trazem o sublocality "Taguatinga"
- **THEN** o sistema ancora o nó de distrito em "Taguatinga"
- **AND** não cria um nó chamado "Taguatinga Norte"

#### Scenario: Nome de cidade Google não casa com IBGE

- **WHEN** um place resolve para UF `SP` e componente de cidade `Embu`
- **AND** nenhuma `city` sob SP tem `normalized_name` `embu`
- **THEN** o sistema recusa a resolução com erro MessageSource pt-BR
- **AND** não persiste nova `city` nem `district`

### Requirement: City e state não têm Google place id

O sistema MUST NOT persistir `google_place_id` em `city` ou `state`. Essas colunas e seus índices unique MUST ser dropados. A identidade do município é `ibge_code`; a do estado é a UF. `district`, `address` e `school` MUST manter `google_place_id`. Um Geocoding futuro MUST NOT reintroduzir um id Google unique 1:1 em `city` (aliases Google são N→1).

#### Scenario: Drop no schema

- **WHEN** a Flyway `V34` é aplicada
- **THEN** `city.google_place_id` e `state.google_place_id` não existem
- **AND** `district.google_place_id` ainda existe

#### Scenario: Distrito ainda pode rastrear um place Google

- **WHEN** um nó de distrito é persistido
- **THEN** a linha de `district` MAY armazenar `google_place_id`
- **AND** a linha pai de `city` não tem essa coluna

### Requirement: País permanece curado e casado por código ISO

O sistema SHALL manter `country` curado, porque carrega atributos de negócio (moeda, DDI, locale) que o Google não fornece. Ao resolver uma cadeia, o sistema MUST casar o componente `country` do Google pelo `shortText` (ISO 3166-1 alpha-2) contra `country.iso_code`, não pelo nome. `state` permanece curado por UF. `city` é curada por código de município IBGE, não pelo Google.

#### Scenario: País casado por código ISO

- **WHEN** uma cadeia resolve com um componente de país cujo `shortText` é `BR` e cujo `longText` é `Brazil`
- **THEN** o sistema casa a linha existente de `country` com `iso_code = 'BR'`
- **AND** não cria um país chamado "Brazil"

#### Scenario: País não suportado é recusado

- **WHEN** uma cadeia resolve para um país cujo `iso_code` não tem linha ativa de `country`
- **THEN** o sistema recusa a resolução com erro de negócio resolvido pelo MessageSource

### Requirement: Resolução de âncora somente leitura

O sistema SHALL resolver um local de busca para o nó de **district** mais profundo já existente na cadeia de ancestrais sob a cidade IBGE casada, sem criar nenhum nó. A resolução de âncora MUST ser somente leitura. Se o componente de cidade do Google não casar com uma `city` IBGE, a resolução de âncora MUST falhar com o mesmo erro de negócio da resolução de escrita — MUST NOT devolver um optional vazio que a busca renderizaria como lista vazia.

#### Scenario: Âncora para no nó mais profundo existente

- **WHEN** um place de busca resolve para `[BR, DF, Brasília, Taguatinga, QNL 5, Conjunto J]` e só existem nós até "Taguatinga"
- **THEN** o sistema devolve "Taguatinga" como âncora
- **AND** não cria "QNL 5" nem "Conjunto J"

#### Scenario: Busca não cria nós

- **WHEN** qualquer resolução de âncora roda
- **THEN** o sistema não faz insert em `state`, `city` ou `district`

#### Scenario: Cidade Google sem match falha alto na busca

- **WHEN** o componente de cidade de um place de busca não casa com uma cidade IBGE naquela UF
- **THEN** o sistema recusa a busca com erro de negócio resolvido pelo MessageSource
- **AND** não devolve uma página vazia como se não existissem motoristas
