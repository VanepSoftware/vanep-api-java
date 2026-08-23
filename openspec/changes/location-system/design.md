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

Efeito colateral previsto: quem escolhe "Taguatinga Norte" acabaria cobrindo Taguatinga inteira.

> **Atualização da fase 4 — esse efeito colateral provavelmente não acontece.** A fixture
> `df-escola-objetivo` mostra que o Google devolve "Taguatinga Norte" como
> `sublocality_level_2` **junto** com "Taguatinga" em `administrative_area_level_4`. Ou seja,
> a cadeia resolvida é `Taguatinga → Taguatinga Norte → QI 21`, e quem escolhe Taguatinga Norte
> cadastra o nó mais fundo, não a RA inteira.
>
> Duas ressalvas: não coletamos fixture do *place da região* "Taguatinga Norte" em si, só de um
> endereço dentro dela — então o comportamento para aquele place específico segue não verificado.
> E a diferença não afeta a correção da busca: a contenção do D4 compara contra os **ancestrais**
> do ponto, então um motorista cadastrado em Taguatinga continua casando com um endereço em
> Taguatinga Norte. A regra do D2 (persistir o derivado, nunca o escolhido) permanece intacta —
> o que muda é só a granularidade que ela produz na prática.

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

A fase 1 fechou a **Q2**: o mask mínimo (`id` + `addressComponents`) cai em *Place Details Essentials* — 10.000 eventos grátis por mês, US$ 5,00/1.000 depois. Com esse teto, o cache continua sendo otimização e não requisito, como já estava escrito. A **Q1** (a sessão sobrevive a chaves distintas?) segue aberta e depende de uma leitura do console de billing. Nenhuma das duas muda a forma do D5; ambas mudam a conta.

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

### D11 — Mapeamento `types` → nível (decidido na fase 1, com evidência)

Fixtures em `src/test/resources/fixtures/places/` (10 lugares reais, coletados uma vez — regra 50).

| Nível | `types` aceitos | Observação |
|---|---|---|
| `country` | `country` | casar por `shortText` (ISO `BR`), nunca por `longText` — ele vem em inglês ("Brazil") |
| `state` | `administrative_area_level_1` | `shortText` é a UF (`DF`, `SP`); `longText` pode vir em inglês ("State of Goiás") |
| `city` | `administrative_area_level_2`, com fallback para `locality` | ver abaixo |
| `district` profundidade 1 | `administrative_area_level_4` ou `sublocality_level_1` | |
| `district` profundidade 2 | `sublocality_level_2` | |
| `district` profundidade 3 | `sublocality_level_3` | |
| ignorados | `route`, `street_number`, `postal_code`, `premise`, componente **sem `types`** | logradouro vai para `address`, não para a árvore |

**`locality` não é o sinal de cidade — `administrative_area_level_2` é.** É o oposto do que o R1 supunha. Nas 10 fixtures, `administrative_area_level_2` trouxe a cidade correta em 10/10 (Brasília, São Paulo, Formosa, Itapetininga). Já `locality` apareceu em **2/10** — só nas cidades do interior, e ali apenas duplicando o `administrative_area_level_2`. Em nenhum endereço do DF e em nenhum endereço da capital de SP existe `locality`. O fallback existe só para o caso não observado de um place sem `administrative_area_level_2`.

**`administrative_area_level_4` é ambíguo e exige desempate por nome.** No DF ele é a Região Administrativa (Taguatinga, Águas Claras, Ceilândia, Lago Norte) — exatamente o distrito que queremos. Mas em Formosa e Itapetininga ele **repete o nome da cidade**:

```
Formosa  → locality: Formosa | adm_4: Formosa | adm_2: Formosa
```

Sem tratamento, isso cria um distrito "Formosa" dentro da cidade "Formosa" em toda cidade do interior — poluição da árvore e o R2 em cheio. **Regra: descartar o componente de distrito cujo `normalized_name` seja igual ao da cidade resolvida.**

**A ordem do array `addressComponents` não é confiável.** Duas fixtures do DF trazem os mesmos níveis em ordens opostas:

```
df-taguatinga-qnl5   → [route, QNL 5 (sl_3), Setor L Norte (sl_2), Taguatinga (adm_4), ...]
df-escola-objetivo   → [<sem types>, Taguatinga Norte (sl_2), QI 21 (sl_3), Taguatinga (adm_4), ...]
```

O aninhamento tem de ser montado pela **profundidade declarada na tabela acima**, nunca pela posição no array. Um resolver que confie na ordem monta "Setor L Norte" como filho de "QNL 5" em um caso e o inverso no outro.

**DF e capital de SP usam ramos disjuntos.** DF: `adm_4` → `sublocality_level_2` → `sublocality_level_3`, e nunca `sublocality_level_1`. Capital de SP: só `sublocality_level_1` (Pinheiros, Vila Mariana, Vila Gomes Cardim), e nunca `adm_4`. Por isso profundidade 1 aceita os dois `types`.

---

## Risks / Trade-offs

**R1 — Mapeamento `types` → nível é inconsistente no Google (ALTO → MÉDIO após a fase 1).**

> ⚠️ **A formulação original deste risco estava errada, e a fase 1 provou isso.** Ela dizia: "em São Paulo `administrative_area_level_2` e `locality` são ambos 'São Paulo'; no DF `locality` às vezes é 'Brasília' e às vezes 'Taguatinga'". Nas 10 fixtures coletadas, `locality` **não aparece nem uma vez** no DF ou na capital de SP. O conflito temido não existe; o mapeamento real está na D11. Mantido aqui o que sobrou de risco de verdade.

O que continua verdadeiro: os `types` variam por praça, e um mapeamento errado faz o match falhar **silenciosamente** — sem erro, apenas sem resultado. Os três pontos concretos que a fase 1 encontrou, todos capazes de produzir esse silêncio:

1. **`administrative_area_level_4` duplica o nome da cidade no interior** (Formosa, Itapetininga) — sem desempate por nome, nasce um distrito espúrio em toda cidade pequena.
2. **A ordem do array não é estável** — aninhar por posição inverte a hierarquia entre duas chamadas ao mesmo endpoint.
3. **Existe componente sem o campo `types`** (fixture `df-escola-objetivo`, componente `"Q1 21 LOTE 18 A 26"`).

O spike da fase 1 cobre a praça de lançamento (DF e capital de SP), mas o comportamento do Google varia entre capitais: bairro oficial nem sempre coincide com distrito administrativo, e zona rural frequentemente não traz `sublocality`. Ao abrir uma praça nova o risco **volta**, agora com dado real já na árvore.

*Mitigações:*
- ~~Fase 1 coleta `addressComponents` crus~~ — **feito.** 10 fixtures em `src/test/resources/fixtures/places/`, tabela D11 decidida com evidência. Nenhuma migration foi escrita antes disso.
- **Falhar alto em `type` não mapeado, com uma exceção explícita.** Ao encontrar um componente cujo `type` não está na D11, o resolver lança erro de negócio em vez de ignorar. É o que converte "busca sem resultado" em erro visível.

  ⚠️ **Esta mitigação, como estava escrita, rejeitaria endereços válidos.** A fixture `df-escola-objetivo` traz um componente **sem o campo `types`** (`"Q1 21 LOTE 18 A 26"`). "Falhar em tudo que não está na tabela" derruba a resolução do Colégio Objetivo inteiro — um place real, de uma escola real, na praça de lançamento. A regra correta separa dois casos:

  | Caso | Ação |
  |---|---|
  | Componente **sem `types`** (campo ausente ou lista vazia) | ignorar, com log em `WARN` |
  | Componente com `types` presentes, **todos** fora da D11 | erro de negócio |
  | Componente com `types` presentes, **algum** na lista de ignorados (`route`, `postal_code`, …) | ignorar em silêncio |

- **Log de ambiguidade** quando um componente de distrito tem o mesmo `normalized_name` da cidade (o caso Formosa/Itapetininga), para medir em produção com que frequência o desempate da D11 dispara.
- **Escopo declarado:** a v1 vale para DF e capital de SP. Abrir praça nova exige adicionar fixtures reais daquela praça e reconferir a D11 — não é rollout de configuração. Reforçado pela evidência: DF e SP já usam ramos de `types` **disjuntos** entre si, então não há motivo para supor que uma terceira praça reuse qualquer um dos dois.

**R2 — O primeiro cadastro define o nome canônico da região.**
Como a árvore é lazy, o primeiro motorista a cadastrar Taguatinga cria o nó. Se o Google devolver grafia diferente depois, surge nó duplicado irmão.
*Mitigação:* unique parcial em (`parent_id`, `city_id`, `normalized_name`) com `WHERE deleted_at IS NULL`, e `normalized_name` com unaccent + lowercase.

⚠️ **Esta mitigação não funciona ingenuamente.** Distrito de primeiro nível tem `parent_id = NULL`, e em PostgreSQL `NULL` não é igual a `NULL` num índice único — duas "Taguatinga" filhas diretas de Brasília passariam pelo índice sem conflito. É justamente o caso mais comum. O índice tem de usar uma das formas:

```sql
CREATE UNIQUE INDEX ... ON district (parent_id, city_id, normalized_name)
  NULLS NOT DISTINCT WHERE deleted_at IS NULL;
```

`NULLS NOT DISTINCT` exige PostgreSQL 15+, e o `docker-compose.yml` roda `postgres:17-alpine` — está disponível. O fallback portátil (`COALESCE(parent_id, 0)`) fica descartado por ser menos legível sem ganho.

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

| # | Questão | Status | Resposta / evidência |
|---|---|---|---|
| **Q1** | O faturamento por sessão sobrevive a chaves distintas (autocomplete com referrer, `Place Details` com servidor)? | 🟡 **parcial** | A doc confirma que o SKU **Autocomplete Session Usage custa US$ 0, ilimitado**, e que a sessão só entra nele se o `Place Details` que a encerra carregar o mesmo `sessionToken`. Isso *eleva* a importância do D5: sem o repasse, cada tecla vira `Autocomplete Requests` (10k grátis, depois US$ 2,83/1.000). **O que falta:** confirmar que a fronteira entre chaves não quebra a sessão. Só o relatório de billing responde — pendente de verificação humana |
| **Q2** | Qual SKU o *field mask* seleciona? | ✅ **resolvida** | Documentado, sem gastar chamada. `id` → *Place Details Essentials IDs Only* (grátis ilimitado); `addressComponents` e `formattedAddress` → *Place Details Essentials* (**10.000 grátis/mês**, US$ 5,00/1.000 depois). O mask mínimo da change cai na faixa paga mais barata que ainda devolve componentes. **Consequência para o D5:** o cache se paga, mas o teto gratuito é generoso o bastante para não ser requisito de correção — segue como otimização, exatamente como o D5 já dizia |
| **Q3** | Qual a ordem de grandeza do custo por busca? | 🟡 **parcial** | Tabela conhecida: 1 busca = 2 `Place Details` = 2 eventos do balde de 10.000/mês → **~5.000 buscas/mês grátis**; acima disso, **US$ 0,01 por busca**. 1 endereço salvo = 1 evento; 1 escola resolvida = 1 evento (mais o SKU Pro, ver Q6). **O que falta:** confirmar no relatório de billing que os eventos caem no SKU previsto. Pendente de verificação humana |
| **Q4** | Qual o mapeamento `types` → nível para DF e capital de SP? | ✅ **resolvida** | Ver **D11**. 10 fixtures reais. A premissa do R1 estava errada: `locality` não é o sinal de cidade — `administrative_area_level_2` é (10/10) |
| **Q5** | O comportamento se mantém fora de DF/SP? | ❌ **não, e está aceito** | As fixtures de Formosa (GO) e Itapetininga (SP) já divergem: `administrative_area_level_4` repete o nome da cidade, o que no DF nunca acontece. DF e capital de SP usam ramos de `types` disjuntos entre si. Não bloqueia a v1 (escopo declarado), mas confirma que **abrir praça nova exige fixtures novas e reconferência da D11** — não é configuração |
| **Q6** | Resolver uma escola exige `displayName`, que é SKU **Pro**. De onde vem o nome? | 🔴 **aberta, decidir na fase 7** | `SchoolModel.name` é obrigatório e a fase 7 o mantém. `displayName` está no *Place Details Pro*, SKU distinto do Essentials — um mask que peça os dois é cobrado nos dois. Alternativa: o cliente já recebe o nome do autocomplete de graça e o envia junto do `placeId`; o backend continua re-resolvendo a **geografia** pelos `addressComponents` (fronteira de confiança intacta) e usa o texto do cliente apenas como rótulo. Risco reduzido a "nome errado na listagem", não a árvore torta |

**Q2, Q4 e Q5 estão fechadas.** Q1 e Q3 têm a parte documental resolvida e dependem de uma leitura do console de billing — a única evidência que fecha as duas. A leitura deve ser feita **antes do lançamento**, enquanto o projeto ainda tem tráfego baixo de Places e cada linha de SKU no relatório é atribuível sem ambiguidade. Q6 nasceu na fase 1 e é decidida na fase 7, que é quando ela morde.

## Migration Plan

Sem backfill. Migrations a partir de `V21` (última aplicada: `V20__owned_address_foreign_keys.sql`, mergeada depois deste documento ser escrito), nenhuma migration existente editada (regra 2).

| Migration | Conteúdo |
|-----------|----------|
| `V21` | `district` (auto-FK, soft delete, unique parcial em `parent_id` + `city_id` + `normalized_name` com tratamento de `NULL` — ver R2); `google_place_id` + `normalized_name` em `state` e `city`; `state.requires_district` (`NOT NULL DEFAULT false`) e `city.requires_district` (nullable) com seed `DF = true`, `SP = true` (D8) |
| `V22` | `address`: `district_id` FK, `google_place_id`; remove `district varchar`; FKs de `school.address_id` e `dependent.address_id`; `assistant.address_id` |
| `V23` | `driver_service_area` |
| `V24` | `school`: `google_place_id` único, `city_id`, `district_id`; remove `cnpj`, `phone`, `email` |
| `V25` | remove `driver.city` e `driver.service_areas` |

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
| 2 | Migration `V21`; `district` (model, repo, feature package); `normalized_name` em state/city | 1 | 3 |
| 3 | `br.com.vanep.places`: `PlacesClient` (RestClient), config por env, cache Caffeine, DTOs de resposta | 1 | 2 |
| 4 | `LocationResolverService`: `addressComponents` → `findOrCreate` da cadeia; resolução de âncora read-only; ancestrais de distrito | 2, 3 | — |
| 5 | Migration `V22`; endereço pessoal: refactor de `address`, FKs faltantes, `assistant.address_id`, `PUT/GET /api/user/me/address` | 4 | 6, 7 |
| 6 | Migration `V23`; `driver_service_area`: tabela, model, repo, validação D8, `GET/PUT /api/drivers/me/service-areas` | 4 | 5, 7 |
| 7 | Migration `V24`; `school` magra: `google_place_id`, remoção de cnpj/phone/email, `GET /api/schools/resolve` | 4 | 5, 6 |
| 8 | `GET /api/drivers/search`: origem + destino, match por contenção, paginação | 5, 6, 7 | — |
| 9 | Migration `V25`; `onboarding.pendingSteps` em `/me`; remoção de seeders, do CRUD de escrita de city e de `driver.city`/`service_areas` | 8 | — |

Cada fase é uma branch e um PR (regra 35), respeita ~600 linhas produtivas e 10 arquivos novos (regra 40), e ship com seus próprios testes (regra 42). Fases 2 e 3 podem ser revisadas em paralelo; 5, 6 e 7 também.

**"Parallel with" é paralelismo de review, não de merge (regra 39).** A entrega usa PRs empilhados: a fase 8 mergeia na 7, a 7 na 6, e assim por diante até a 1 chegar na `main`. Como a ordem de merge é a ordem da stack, `V21 → V22 → V23 → V24 → V25` chegam ao banco em ordem crescente, e o Flyway (que roda com `out-of-order` desabilitado) não tem como falhar por versão fora de ordem.

A garantia depende da stack, não do plano: **se a stack for reordenada** — por exemplo aprovando a fase 6 primeiro e desempilhando-a antes da 5 — a migration da fase desempilhada precisa ser renumerada para o próximo `V` livre antes do merge. Caso contrário o `validate` do Flyway falha no deploy seguinte, ao encontrar uma versão resolvida menor que a última aplicada.

**Nenhuma fase de código pode começar antes da fase 0 estar concluída** — sem as chaves do Google não há como resolver place algum, e o spike da fase 1 depende de chamadas reais.
