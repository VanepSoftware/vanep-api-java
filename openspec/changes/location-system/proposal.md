## Why

A localização da Vanep hoje é um catálogo fictício, não um sistema. `driver.city` é `varchar(255)` de texto livre que nem aponta para a tabela `city`; `driver.service_areas` é um `jsonb` de `List<String>` sem semântica alguma. As tabelas `state`/`city`/`school` são populadas por seeders com dados de exemplo (3 cidades, 2 escolas fictícias) e não escalam — o Brasil tem 5.570 municípios e ~180 mil escolas. Não existe endpoint de busca: `GET /api/drivers` apenas pagina todos os motoristas.

Consequência: o cliente não consegue encontrar um motorista que atenda o trajeto do dependente, e o motorista não consegue declarar onde trabalha. É o núcleo do produto e ele não existe. Prioridade: **Alta** — bloqueia o MVP do app.

Além disso, os dois conceitos de endereço estão misturados: o endereço **residencial** (privado, preciso — rua/número/CEP) e a **região de atuação** do motorista (público, impreciso — um nó da árvore geográfica) hoje competem pela mesma modelagem.

## What Changes

**Árvore geográfica única e lazy**
- Nova tabela `district` com auto-FK (`parent_id`), permitindo profundidade variável: Taguatinga → QNL 5 → Conjunto J. Sem profundidade fixa em código.
- `country → state → city → district` deixa de ser seedada e passa a nascer **sob demanda** dos `addressComponents` retornados pelo Google Places. Seeders de state/city/school/address são removidos.
- `country` continua curado (carrega moeda, DDI e locale, que o Google não fornece) e casa com o componente `country` do Google pelo `shortText` (ISO `BR`).

**Regra central de normalização**
- Nunca persistir o place escolhido pelo usuário; persistir sempre o nó derivado dos `addressComponents` daquele place. Motorista e cliente passam pela mesma normalização e caem no mesmo nó por construção.

**Separação dos dois tipos de relação com a geografia**
- `address` (PRIVADO): endereço residencial de client, driver, assistant, dependent e school. Ganha `district_id` FK (substitui o `district varchar(128)` solto), `google_place_id`, e as FKs faltantes de `school.address_id` / `dependent.address_id`; `assistant` ganha `address_id`.
- `driver_service_area` (PÚBLICO, tabela nova): `driver_id` + `city_id` (obrigatório) + `district_id` (nullable = cidade inteira). **Não possui colunas de rua ou número** — vazamento de endereço residencial por essa tabela é estruturalmente impossível.
- Declarar a cidade inteira só é permitido onde a política curada permite: `state.requires_district` (27 linhas, seed `DF`/`SP` = true) com override nullable em `city.requires_district`. A regra **não** olha quantos distritos já existem na árvore — isso tornaria o mesmo cadastro válido ou inválido conforme o relógio (ver D8).

**Busca do cliente por origem + destino**
- `GET /api/drivers/search` recebe `originPlaceId` e `destinationPlaceId` (destino é qualquer place, não obrigatoriamente escola).
- Match por contenção na árvore: o motorista aparece se sua área cobre **ambos** os pontos. Um motorista cadastrado em "Brasília" casa com uma busca em "QNL 5 Conjunto J" porque o nó é ancestral. Um cadastrado em "Brasília" (sem distrito) cobre a cidade inteira.
- Busca ampla (só cidade) lista todos os motoristas daquela cidade, independente do distrito.

**Escola**
- `school` vira registro magro criado a partir de um place do Google (`google_place_id` único, `name`, `city_id`, `district_id`). `cnpj`, `phone` e `email` são removidos — não existem na base do Google e a Vanep não os possui.
- `POST /api/schools/resolve` (não `GET`: a operação faz `findOrCreate`, é escrita), idempotente por `google_place_id` e com rate limit por usuário.

**Onboarding**
- `GET /api/user/me` passa a expor `onboarding.pendingSteps` como enum (`PERSONAL_ADDRESS`, `SERVICE_AREA`), permitindo ao mobile bloquear o acesso até o cadastro estar completo, sem lógica por papel no cliente.

**Limpeza (sem dados em produção — corte limpo, sem backfill)**
- Remoção de `driver.city` e `driver.service_areas`.
- Remoção dos POST/PUT/DELETE de `CityController` (a fonte passa a ser o Google).

**Fora de escopo:**
- Renderização de mapa (decisão: apenas busca por texto).
- Raio de atuação / distância / roteirização (Routes API, Distance Matrix).
- PostGIS, geometria, polígonos de bairro.
- Proxy de autocomplete no backend — o autocomplete roda no cliente com chave restrita e session token.
- Frontend e mobile (esta change é backend-only).
- Migração de dados legados (não há dados reais em produção).

## Capabilities

### New Capabilities

- `geography-tree`: árvore `country → state → city → district` construída sob demanda a partir dos `addressComponents` do Google Places, com resolução de âncora por normalização de nome.
- `personal-address`: endereço residencial privado, criado a partir de um place, vinculado à árvore e a todos os papéis.
- `driver-service-area`: declaração pública das regiões onde o motorista trabalha, por nó da árvore.
- `driver-location-search`: busca de motoristas por origem + destino com match por contenção na árvore.
- `school-resolution`: resolução de um place do Google em uma `school` persistida e magra, idempotente por `google_place_id`.
- `location-onboarding`: exposição dos passos pendentes de cadastro em `GET /api/user/me`.

### Modified Capabilities

- `country-crud`: `country` deixa de ser raiz de um catálogo curado completo e passa a ser o único nível curado de uma árvore lazy; casamento por ISO code com o componente do Google.

## Impact

- **Bloqueio externo (fase 0):** as chaves do Google Maps Platform (Places API New + Geocoding API) precisam ser criadas e restritas manualmente no Google Cloud Console antes de qualquer implementação. Nenhuma fase de código pode começar antes disso.
- **Código:** novo feature package `br.com.vanep.district`; novo `br.com.vanep.driverservicearea`; novo `br.com.vanep.places` (client HTTP do Google + resolver da árvore); refatoração de `address`, `school`, `city`, `state`, `driver`; extensão de `UserMeResponseDTO`.
- **Schema:** migrations Flyway a partir de `V20` (última aplicada: `V19`). Nenhuma migration existente é editada (constituição, regra 2).
- **Deps:** cliente HTTP para o Places (`RestClient` do Spring, sem SDK adicional); cache em memória (Caffeine) para `Place Details` por `placeId`, **ignorado quando a requisição traz `sessionToken`**, para que a sessão de autocomplete feche e entre no SKU de sessão (D5).
- **Config:** `vanep.google.places.api-key`, `vanep.google.places.base-url`, `vanep.google.geocoding.enabled` — todos via env (`.env.example`), nunca hardcoded (constituição, regra 1/3).
- **Cobertura declarada:** a v1 vale para DF e capital de SP. Abrir praça nova exige fixtures reais daquela praça e reconferência da tabela `types` → nível (R1) — não é rollout de configuração.
- **Mensagens:** novas keys de MessageSource para erros de resolução de place, região inválida e onboarding incompleto (EN + pt-BR).
- **Auth:** `PUT /api/user/me/address` e `/api/drivers/me/service-areas` com `isAuthenticated()`; busca autenticada; leituras de geografia autenticadas. Ownership via `@sec` quando aplicável (regra 21).
- **Testes:** unit (resolver da árvore, policy de match) + slice MockMvc; o client do Google é mockado — nenhum teste chama a API real.
- **Delivery:** fases empilhadas conforme constituição 35–43; fase 0 é bloqueio humano, fase 1 é spike de derisking, fases 2+ são código.
