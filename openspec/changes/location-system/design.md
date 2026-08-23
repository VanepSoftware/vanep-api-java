## Context

A geografia atual é um catálogo curado por seeders: `country`, `state`, `city`, `address` e `school` existem como tabelas, mas populadas com dados de exemplo. `driver.city` é texto livre sem FK e `driver.service_areas` é `jsonb` de strings — nenhum dos dois é consultável. `CityRepository` sequer possui `findByStateId`, então nem a cascata estado→cidade existe. `school.address_id` e `dependent.address_id` foram criados sem FK (comentado explicitamente em `V11`), e `assistant` não tem endereço.

Stakeholders: app mobile (telas de onboarding do motorista e busca do cliente), frontend web, API Vanep.

Constraints: constituição (feature packages, DTOs explícitos, tokens opacos, MessageSource pt-BR, Flyway-only, soft delete, PRs por camada), testes em H2 (sem PostGIS disponível), sem dados em produção.

---

## Goals / Non-Goals

**Goals:**

- Uma única árvore geográfica compartilhada, de profundidade variável, construída sob demanda a partir do Google Places.
- Separar endereço residencial (privado, preciso) de região de atuação (público, impreciso) sem duplicar a geografia.
- Busca do cliente por origem + destino, com match por contenção na árvore, executável em H2.
- Expor ao mobile o que falta no cadastro do usuário.

**Non-Goals:**

- Mapa, raio, distância, roteirização, PostGIS, polígonos.
- Proxy de autocomplete no backend.
- Backfill ou convivência de modelos (não há dados reais).
- Frontend e mobile.

---

## Decisions

### D1 — Uma geografia só, dois tipos de relação

A alternativa considerada foi duplicar `country`/`state`/`city` em uma hierarquia paralela dedicada ao trabalho do motorista, separando fisicamente endereço pessoal de endereço de atuação.

**Rejeitada** porque quebra o requisito central: se `work_city("Brasília")` e `personal_city("Brasília")` são linhas distintas com ids distintos, o ponto de embarque do cliente nunca casa com a área do motorista sem uma tabela de-para ou comparação por string — exatamente o `driver.city varchar(255)` que a change existe para eliminar. Também viola as regras 6 e 31 da constituição.

A separação real não é da geografia, é da **relação** com ela:

```
        ┌──────────────────────────────────────────┐
        │  GEOGRAFIA — uma só, compartilhada       │
        │  country → state → city → district ⟲     │
        └───────┬──────────────────────┬───────────┘
                │                      │
                ▼                      ▼
      ┌──────────────────┐   ┌──────────────────────┐
      │ address          │   │ driver_service_area  │
      │ PRIVADO          │   │ PÚBLICO              │
      │ rua, nº, CEP     │   │ só o nó da árvore    │
      └──────────────────┘   └──────────────────────┘
```

A garantia de privacidade fica mais forte assim: `driver_service_area` não possui coluna de logradouro, então não há o que vazar. Com hierarquia duplicada a garantia dependeria de disciplina, não do schema.

### D2 — Persistir o nó derivado, nunca o place escolhido

Regra dura, e o ponto mais frágil do desenho:

```
   motorista escolhe            cliente busca
   "Taguatinga Norte"           "qnl 5 conjunto j"
         │                            │
         ▼ Place Details              ▼ Place Details
   addressComponents            addressComponents
   [BR, DF, Brasília,           [BR, DF, Brasília,
    Taguatinga]                  Taguatinga, QNL 5, Conj J]
         │                            │
         ▼ findOrCreate               ▼ sobe até achar existente
   district = Taguatinga        âncora = Taguatinga
         └────────── mesmo nó ────────┘
```

Se o place escolhido fosse gravado direto, o motorista que selecionou "Taguatinga Norte" nunca casaria com um endereço cujos componentes resolvem para "Taguatinga". Ambos os lados atravessam a mesma normalização, então caem no mesmo nó por construção.

Efeito colateral aceito: quem escolhe "Taguatinga Norte" acaba cobrindo Taguatinga inteira.

### D3 — Escrita só no cadastro; busca é read-only

A árvore cresce quando o **motorista** cadastra uma região ou quando um **endereço/escola** é criado (`findOrCreate` sobre a cadeia de componentes, idempotente).

A **busca do cliente não escreve nada**: ela sobe a cadeia de `addressComponents` até encontrar o nó mais profundo que já existe. Isso é suficiente porque o motorista só pode ter cadastrado um nó existente. Evita write path em endpoint de leitura e evita inflar a árvore com termos de busca.

### D4 — Match por contenção, sem PostGIS

Como a profundidade é limitada e os níveis são tabelas com FK, não é necessário `ltree`, materialized path ou geometria:

```sql
-- COBERTURA (origem e destino, aplicada duas vezes)
WHERE sa.city_id = :pointCityId
  AND (sa.district_id IS NULL OR sa.district_id IN (:pointDistrictAncestors))

-- DESCOBERTA (busca ampla por cidade)
WHERE sa.city_id = :cityId
```

`pointDistrictAncestors` é o distrito da âncora mais seus ancestrais — um conjunto pequeno, resolvido em Java subindo por `parent_id`. `district_id IS NULL` significa "cidade inteira".

Consequência decisiva: são joins e `IN` comuns, que **rodam em H2**. A regra 24 da constituição fica intacta e não é preciso introduzir Testcontainers.

Alternativas rejeitadas: PostGIS + `ST_Contains` (H2 não suporta, e o Google não fornece polígono de bairro — só `viewport`); materialized path com `LIKE` (desnecessário para profundidade limitada).

### D5 — Google Places como fonte, não IBGE

Descartado usar IBGE (estados/municípios/distritos) + ViaCEP + INEP, que seriam gratuitos e sem quota. O motivo é a busca do cliente: ela precisa resolver, em uma única caixa de texto e ao vivo, formatos como `brasilia`, `brasilia taguatinga`, `qnl 5`, `qnl5 conjunto j`, `Objetivo DF`. IBGE para no distrito, ViaCEP exige o CEP e o INEP é um dump estático. Nenhum deles resolve os seis formatos.

Consequência: o autocomplete é responsabilidade do cliente (web/mobile), com chave restrita por referrer/bundle-id e **session token por caixa de busca**. Duas caixas por busca (origem + destino) = duas sessions. O backend não faz proxy de autocomplete — proxiar adicionaria latência a cada tecla sem ganho.

O backend chama apenas `Place Details`, e nunca confia em `addressComponents` enviados pelo cliente (o cliente envia só o `placeId`; o backend re-resolve).

**O session token atravessa a fronteira HTTP.** Uma sessão do Places só entra no SKU de sessão se o `Place Details` que a encerra carregar o **mesmo** token dos requests de autocomplete. Como quem chama o `Place Details` é o backend, o cliente precisa enviar o `sessionToken` junto do `placeId`, e o backend precisa repassá-lo ao Google. Sem isso cada tecla digitada é cobrada como autocomplete avulso — custo maior desde o primeiro usuário, não só em escala.

**O cache é ciente da sessão.** Cache por `placeId` e fechamento de sessão são objetivos conflitantes: se um `placeId` em cache dispensa a chamada ao `Place Details`, a sessão do cliente nunca fecha e o autocomplete daquele usuário é cobrado avulso de qualquer forma — cachear pode sair mais caro que não cachear. A regra:

```
request traz sessionToken  →  SEMPRE chama Place Details (encerra a sessão)
                              e atualiza o cache com o resultado
request sem sessionToken   →  serve do cache; só chama o Google em miss
```

Quem envia token veio de uma caixa de autocomplete e precisa encerrar a sessão. Quem não envia veio de um `placeId` já persistido — o caso de maior repetição (o `placeId` da escola do dependente, reusado a cada busca), e onde o cache efetivamente paga. O cache é Caffeine em memória, então com mais de uma instância a taxa de acerto cai; ele é otimização de custo e latência, nunca requisito de correção.

Dois pontos desta decisão ficam **em aberto** e são resolvidos na fase 1 — ver § Open Questions (Q1 e Q2). Nenhum dos dois muda a forma do D5 no papel; ambos mudam a conta.

Um teste de contrato deve garantir que o `sessionToken` recebido é repassado ao Google. Note que ele prova apenas o repasse: se o Google **faturou** como sessão só se confirma no console de billing.

### D6 — Geocoding API adiada

O bridge entre a seleção do motorista e a busca do cliente é feito por `normalized_name` (unaccent + lowercase) sob o mesmo pai. Isso funciona porque os nomes vêm do texto canônico do próprio Google nos dois lados.

A Geocoding API existiria para resolver cada componente ancestral a um `place_id` canônico — `addressComponents` traz o nome de cada nível, mas **não traz `place_id` por componente`**. Fica registrada como saída caso o spike da fase 1 revele divergência real de grafia, mas **não entra na v1**.

### D7 — Profundidade variável via auto-FK

`district` tem `parent_id` nullable apontando para ela mesma. Taguatinga (`parent_id = null`, filho direto de `city`), QNL 5 (`parent_id = Taguatinga`), Conjunto J (`parent_id = QNL 5`).

Alternativa rejeitada: tabelas `district` e `sub_district` separadas — rígido, e um quinto nível exigiria tabela nova.

### D8 — Distrito obrigatório por política curada no estado

`driver_service_area.district_id` é nullable no schema, mas a validação exige distrito quando a cidade escolhida está sob uma política que o obriga.

O problema a resolver: no DF — a praça de lançamento — o nível cidade é inútil. O Distrito Federal tem um único município, então "Brasília" declara 5.800 km², do Gama a Sobradinho. O mesmo vale para a capital de São Paulo (1.521 km²). Cidades pequenas precisam continuar podendo declarar a cidade inteira.

**A regra não pode depender do estado da árvore.** A formulação anterior — "exigir distrito quando a cidade já possui distritos cadastrados" — é dependente de estado mutável: o mesmo input é válido ou inválido conforme o relógio.

```
t0   Brasília sem distritos na árvore
     motorista A cadastra "Brasília"          → ACEITO  (5.800 km², para sempre)
t1   motorista B cadastra "Taguatinga"        → cria o 1º distrito
t2   motorista C cadastra "Brasília"          → REJEITADO

A e C fizeram a mesma coisa e têm tratamento oposto.
A continua aparecendo em toda busca do DF, indefinidamente.
```

E a árvore nasce **vazia**: os primeiros motoristas da praça de lançamento caem 100% no caso furado, que é exatamente onde a regra precisava valer.

**A pergunta não é derivável do Google.** O motorista escolhe o que enviar:

```
digita "Taguatinga"  → components [BR, DF, Brasília, Taguatinga]  → tem distrito
digita "Brasília"    → components [BR, DF, Brasília]              → não tem distrito
```

No segundo caso o Google não está dizendo "Brasília não tem bairros" — está dizendo "você me pediu a cidade". Não existe chamada em Places que responda *"esta cidade tem distritos?"*. Logo "pode declarar esta cidade inteira?" é um fato **sobre a cidade**, curado, não uma resposta da API.

**Onde o fato é curado:** em `state`. A change já divide o mundo em `country`/`state` curados e `city`/`district` lazy (D3, e o requisito de país curado da spec `geography-tree`). Uma lista curada por `placeId` de cidade fura essa divisão — cria uma tabela de manutenção contínua num nível que era pra ser automático, e um lookup extra no momento da criação. Um flag em `state` mantém a curadoria exatamente onde ela já existe: 27 linhas, populadas por migration.

```sql
requires = COALESCE(city.requires_district, city.state.requires_district)
```

- `state.requires_district` — `NOT NULL DEFAULT false`, curado. Seed inicial: `DF = true`, `SP = true`, demais `false`.
- `city.requires_district` — **nullable**, `NULL` significa "herda do estado". Existe para o caso conhecido do interior de SP (Itapetininga não é a capital), sem exigir migration de schema quando ele chegar: vira um `UPDATE`.

O flag **não é copiado** para a linha de `city` no momento da criação — é lido através da cadeia na validação. Isso custa zero query extra (o resolver já atravessa `country → state → city` para montar a cadeia, então o estado está carregado) e evita reintroduzir o mesmo congelamento temporal que motivou esta decisão.

A validação recebe a **cadeia resolvida**, não uma contagem no banco: se a cadeia não tem componente de nível `district` e `requires` é verdadeiro, rejeita com mensagem via MessageSource. Determinístico, testável sem preparar estado.

Alternativas rejeitadas:

| Alternativa | Motivo |
|---|---|
| Contar distritos da cidade no banco | Dependente de estado mutável; furada justamente na praça de lançamento (acima) |
| Lista curada por `google_place_id` de cidade | Quebra a divisão curado/lazy; manutenção contínua; lookup extra na criação da cidade |
| `requires_district` default `true` em toda cidade | Trava o motorista de cidade pequena cujo place não devolve sublocality até intervenção humana |
| Seed manual dos 33 distritos do DF | Nome digitado à mão colide com o R2: se a grafia divergir da canônica do Google, nasce nó irmão duplicado. Se for feito, tem de passar pelo resolver, alimentado por `placeId`s |

> Nota terminológica: no recorte do IBGE, distrito é subdivisão de **município**, não de estado. `state.requires_district` não afirma o contrário — ele responde "as cidades deste estado exigem granularidade abaixo da cidade?", que é uma política de produto, e o override em `city` cobre a heterogeneidade dentro do estado.

### D9 — Superfície HTTP

| Método | Path | Efeito |
|--------|------|--------|
| `PUT` | `/api/user/me/address` | Cria/atualiza endereço residencial a partir de `placeId` + `sessionToken?` + `number?` + `complement?` |
| `GET` | `/api/user/me/address` | Lê o endereço residencial do chamador |
| `GET` | `/api/drivers/me/service-areas` | Lista as regiões do motorista autenticado |
| `PUT` | `/api/drivers/me/service-areas` | Substitui o conjunto de regiões (lista de `placeId` + `sessionToken?` por item) |
| `GET` | `/api/drivers/search` | `originPlaceId` + `destinationPlaceId` (+ `originSessionToken?` / `destinationSessionToken?`) → motoristas que cobrem ambos |
| `POST` | `/api/schools/resolve` | Resolve um `placeId` de escola em uma `school` persistida, criando na primeira vez |

`POST` — e não `GET` — em `/api/schools/resolve` porque a operação faz `findOrCreate`: é escrita. `GET` é definido como safe, e um `GET` que cria linha é pré-buscável e cacheável por intermediários — prefetch de browser criaria escola. A operação continua idempotente por `google_place_id` (unique), então repetir o `POST` devolve a mesma linha: `200 OK` quando já existia, `201 Created` quando criou.

`GET /api/drivers/search` permanece `GET` porque é genuinamente read-only (D3): ele resolve âncoras sem escrever na árvore.

O `sessionToken` é opcional em todos os pontos (ver D5): presente, força a chamada ao `Place Details` para encerrar a sessão; ausente, permite servir do cache. Cada caixa de autocomplete tem a sua sessão, por isso a busca aceita dois tokens distintos.

Identificadores públicos permanecem `token` opaco (regra 13). O `placeId` do Google é dado de entrada, não identificador de recurso da Vanep.

### D10 — Onboarding como enum, não boolean

`GET /api/user/me` ganha `onboarding.pendingSteps: ["PERSONAL_ADDRESS", "SERVICE_AREA"]`.

Enum backed (regra 14) em vez de flags booleanas: o mobile não precisa saber que `SERVICE_AREA` só se aplica a motorista, e passos futuros entram sem quebrar o app.

---

## Risks / Trade-offs

**R1 — Mapeamento `types` → nível é inconsistente no Google (ALTO).**
Em São Paulo `administrative_area_level_2` e `locality` são ambos "São Paulo"; no DF `locality` às vezes é "Brasília" e às vezes "Taguatinga". Se `locality` cair como `city` em SP e como `district` no DF, a árvore sai torta e o match falha **silenciosamente** — sem erro, apenas sem resultado.

O spike da fase 1 cobre a praça de lançamento (DF e capital de SP), mas o comportamento do Google varia entre capitais: bairro oficial nem sempre coincide com distrito administrativo, e zona rural frequentemente não traz `sublocality`. Ao abrir uma praça nova o risco **volta**, agora com dado real já na árvore.

*Mitigações:*
- Fase 1 coleta `addressComponents` crus de ~10 endereços reais e monta a tabela de mapeamento com evidência. Nenhuma migration antes disso.
- **Falhar alto em `type` não mapeado:** ao encontrar um componente cujo `type` não está na tabela decidida, o resolver lança erro de negócio em vez de ignorar o componente. É o que converte "busca sem resultado" em erro visível — a única mitigação que ataca o silêncio, que é o que torna o R1 alto.
- **Log de ambiguidade** quando `administrative_area_level_2` e `locality` trazem o mesmo nome (o caso SP), para detectar a divergência em produção antes de o usuário reclamar.
- **Escopo declarado:** a v1 vale para DF e capital de SP. Abrir praça nova exige adicionar fixtures reais daquela praça e reconferir a tabela de mapeamento — não é rollout de configuração.

**R2 — O primeiro cadastro define o nome canônico da região.**
Como a árvore é lazy, o primeiro motorista a cadastrar Taguatinga cria o nó. Se o Google devolver grafia diferente depois, surge nó duplicado irmão.
*Mitigação:* unique parcial em (`parent_id`, `city_id`, `normalized_name`) com `WHERE deleted_at IS NULL`, e `normalized_name` com unaccent + lowercase.

⚠️ **Esta mitigação não funciona ingenuamente.** Distrito de primeiro nível tem `parent_id = NULL`, e em PostgreSQL `NULL` não é igual a `NULL` num índice único — duas "Taguatinga" filhas diretas de Brasília passariam pelo índice sem conflito. É justamente o caso mais comum. O índice tem de usar uma das formas:

```sql
-- PostgreSQL 15+
CREATE UNIQUE INDEX ... ON district (parent_id, city_id, normalized_name)
  NULLS NOT DISTINCT WHERE deleted_at IS NULL;

-- ou, portátil: expressão que elimina o NULL
CREATE UNIQUE INDEX ... ON district (COALESCE(parent_id, 0), city_id, normalized_name)
  WHERE deleted_at IS NULL;
```

**R6 — Endpoints que gastam dinheiro por request não têm limite (MÉDIO).**
`POST /api/schools/resolve` e `GET /api/drivers/search` disparam `Place Details` pago a partir de `placeId` fornecido pelo cliente. Um usuário autenticado varrendo `placeId`s gera custo no Google e, no caso da escola, linha nova no banco a cada id distinto. O R3 trata custo como "monitorar quota", o que não impede abuso.
*Mitigação:* aplicar rate limit por usuário nesses dois endpoints (o projeto já tem infraestrutura de rate limit — `vanep.security.rate-limit.enabled`), além da quota diária por chave definida na fase 0. Dimensionar com o volume real; não é bloqueio para a v1, mas tem de estar decidido antes de abrir ao público.

**R7 — As migrations não são exercitadas por nenhum teste (MÉDIO).**
Os testes rodam com `spring.flyway.enabled=false` e `spring.jpa.hibernate.ddl-auto=create-drop` (`src/test/resources/application-test.properties`): o schema de teste vem do Hibernate, não das migrations. Índices parciais, FKs e constraints escritos em SQL **nunca são executados em CI** — o índice quebrado do R2 acima passaria verde em toda a suíte.
*Mitigação:* aceito para a v1 (mudar a estratégia de teste está fora do escopo desta change), mas as migrations desta change são validadas manualmente contra o PostgreSQL local antes do merge de cada fase, e o comportamento de unicidade que o índice garante ganha teste de repositório equivalente em H2 onde for possível. Registrado para tratamento próprio depois.

**R3 — Custo do Places cresce com a busca.**
Duas caixas de autocomplete por busca, mais `Place Details` no backend.
*Mitigação:* session token obrigatório no cliente; cache de `Place Details` por `placeId` no backend. Monitorar quota antes de abrir para o público.

**R4 — Dependência dura de um fornecedor externo.**
Sem Google, não há cadastro de região nem busca.
*Mitigação:* a árvore é persistida localmente, então o match de motoristas já cadastrados continua funcionando offline; apenas a resolução de novos places para. Aceito para a v1.

**R5 — Corte destrutivo de `driver.city` / `driver.service_areas`.**
Aceito explicitamente: não há dados reais em produção (confirmado com o time).

---

## Open Questions

**Toda questão em aberto se resolve na fase 1.** Ela existe para isso: é a fase de experimento com a API, com chamada real e chave real. Nada neste design que dependa de achismo, suposição ou "não sabemos" pode atravessar para uma fase de código — ou a fase 1 responde, ou registra a decisão explícita de conviver com a incerteza, com o motivo. Não se decide nenhuma dessas no papel.

| # | Questão | Depende de | Impacto se a resposta for a desfavorável |
|---|---|---|---|
| **Q1** | O faturamento por sessão sobrevive ao autocomplete usar a chave de referrer/bundle e o `Place Details` usar a chave de servidor, no mesmo projeto? | D5 | O autocomplete passa a ser cobrado avulso. Muda o custo, não a arquitetura — a reação correta é quantificar antes de mexer no D5 |
| **Q2** | Qual SKU o *field mask* do `Place Details` seleciona? Pedir só `id` + `addressComponents` cai na faixa mais barata? | D5 | Define se o cache por `placeId` se paga ou é complexidade sem retorno |
| **Q3** | Qual a ordem de grandeza do custo por busca, no volume projetado? | R3, R6 | É o que dimensiona o rate limit e a quota; sem isso ambos são chute |
| **Q4** | Qual o mapeamento `types` → nível que vale para DF e capital de SP? | R1, D11 | É o risco alto da change; sem evidência a árvore sai torta e o match falha em silêncio |
| **Q5** | O comportamento dos `addressComponents` se mantém fora de DF/SP? | R1 | Não bloqueia a v1 (o escopo declarado é DF + capital de SP), mas define o que abrir praça nova exige |

Q1, Q2 e Q3 se resolvem pelo meio mais barato que dê resposta confiável — doc oficial, suporte do Maps Platform ou experimento controlado, nessa ordem de preferência. A fase 1 é o momento certo para o experimento porque o projeto ainda tem tráfego zero de Places: qualquer linha de SKU no relatório é atribuível sem ambiguidade, o que deixa de ser verdade depois do lançamento.

## Migration Plan

Sem backfill. Migrations a partir de `V20` (última aplicada: `V19`), nenhuma migration existente editada (regra 2).

| Migration | Conteúdo |
|-----------|----------|
| `V20` | `district` (auto-FK, soft delete, unique parcial em `parent_id` + `city_id` + `normalized_name` com tratamento de `NULL` — ver R2); `google_place_id` + `normalized_name` em `state` e `city`; `state.requires_district` (`NOT NULL DEFAULT false`) e `city.requires_district` (nullable) com seed `DF = true`, `SP = true` (D8) |
| `V21` | `address`: `district_id` FK, `google_place_id`; remove `district varchar`; FKs de `school.address_id` e `dependent.address_id`; `assistant.address_id` |
| `V22` | `driver_service_area` |
| `V23` | `school`: `google_place_id` único, `city_id`, `district_id`; remove `cnpj`, `phone`, `email` |
| `V24` | remove `driver.city` e `driver.service_areas` |

---

## Rollout — grafo de dependência e plano de PRs

```
  Fase 0 (BLOQUEIO HUMANO — chaves do Google)
     │
     ▼
  Fase 1 (spike: mapeamento types → nível)
     │
     ├────────────────┐
     ▼                ▼
  Fase 2           Fase 3
  (árvore:         (client do
   district +       Google Places
   migrations)      + cache)
     │                │
     └────────┬───────┘
              ▼
           Fase 4
       (resolver da árvore)
              │
     ┌────────┼────────┐
     ▼        ▼        ▼
  Fase 5   Fase 6   Fase 7
 (endereço (service  (school
  pessoal)  areas)    magra)
     │        │        │
     └────────┼────────┘
              ▼
           Fase 8
        (busca)
              │
              ▼
           Fase 9
     (onboarding + limpeza)
```

| Phase | Contents | Depends on | Parallel with |
|-------|----------|------------|---------------|
| 0 | **Bloqueio humano.** Google Cloud Console: habilitar Places API (New) + Geocoding API, criar 3 chaves restritas (server/web/mobile), popular `.env` e `.env.example` | — | — |
| 1 | Spike: coletar `addressComponents` de ~10 endereços reais, definir tabela `types` → nível, registrar em `design.md` | 0 | — |
| 2 | Migrations `V20`; `district` (model, repo, feature package); `normalized_name` em state/city | 1 | 3 |
| 3 | `br.com.vanep.places`: `PlacesClient` (RestClient), config por env, cache Caffeine, DTOs de resposta | 1 | 2 |
| 4 | `LocationResolverService`: `addressComponents` → `findOrCreate` da cadeia; resolução de âncora read-only; ancestrais de distrito | 2, 3 | — |
| 5 | Migration `V21`; endereço pessoal: refactor de `address`, FKs faltantes, `assistant.address_id`, `PUT/GET /api/user/me/address` | 4 | 6, 7 |
| 6 | Migration `V22`; `driver_service_area`: tabela, model, repo, validação D8, `GET/PUT /api/drivers/me/service-areas` | 4 | 5, 7 |
| 7 | Migration `V23`; `school` magra: `google_place_id`, remoção de cnpj/phone/email, `GET /api/schools/resolve` | 4 | 5, 6 |
| 8 | `GET /api/drivers/search`: origem + destino, match por contenção, paginação | 5, 6, 7 | — |
| 9 | Migration `V24`; `onboarding.pendingSteps` em `/me`; remoção de seeders, do CRUD de escrita de city e de `driver.city`/`service_areas` | 8 | — |

Cada fase é uma branch e um PR (regra 35), respeita ~600 linhas produtivas e 10 arquivos novos (regra 40), e ship com seus próprios testes (regra 42). Fases 2 e 3 podem ser revisadas em paralelo; 5, 6 e 7 também.

**"Parallel with" é paralelismo de review, não de merge (regra 39).** A entrega usa PRs empilhados: a fase 8 mergeia na 7, a 7 na 6, e assim por diante até a 1 chegar na `main`. Como a ordem de merge é a ordem da stack, `V20 → V21 → V22 → V23 → V24` chegam ao banco em ordem crescente, e o Flyway (que roda com `out-of-order` desabilitado) não tem como falhar por versão fora de ordem.

A garantia depende da stack, não do plano: **se a stack for reordenada** — por exemplo aprovando a fase 6 primeiro e desempilhando-a antes da 5 — a migration da fase desempilhada precisa ser renumerada para o próximo `V` livre antes do merge. Caso contrário o `validate` do Flyway falha no deploy seguinte, ao encontrar uma versão resolvida menor que a última aplicada.

**Nenhuma fase de código pode começar antes da fase 0 estar concluída** — sem as chaves do Google não há como resolver place algum, e o spike da fase 1 depende de chamadas reais.
