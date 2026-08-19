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

Consequência: o autocomplete é responsabilidade do cliente (web/mobile), com chave restrita por referrer/bundle-id e **session token por caixa de busca**, encerrado na seleção. Duas caixas por busca (origem + destino) = duas sessions. O backend não faz proxy de autocomplete — proxiar adicionaria latência a cada tecla sem ganho.

O backend chama apenas `Place Details`, com cache em memória por `placeId`, e nunca confia em `addressComponents` enviados pelo cliente (o cliente envia só o `placeId`; o backend re-resolve).

### D6 — Geocoding API adiada

O bridge entre a seleção do motorista e a busca do cliente é feito por `normalized_name` (unaccent + lowercase) sob o mesmo pai. Isso funciona porque os nomes vêm do texto canônico do próprio Google nos dois lados.

A Geocoding API existiria para resolver cada componente ancestral a um `place_id` canônico — `addressComponents` traz o nome de cada nível, mas **não traz `place_id` por componente`**. Fica registrada como saída caso o spike da fase 1 revele divergência real de grafia, mas **não entra na v1**.

### D7 — Profundidade variável via auto-FK

`district` tem `parent_id` nullable apontando para ela mesma. Taguatinga (`parent_id = null`, filho direto de `city`), QNL 5 (`parent_id = Taguatinga`), Conjunto J (`parent_id = QNL 5`).

Alternativa rejeitada: tabelas `district` e `sub_district` separadas — rígido, e um quinto nível exigiria tabela nova.

### D8 — Distrito obrigatório quando a cidade tem distritos

`driver_service_area.district_id` é nullable no schema, mas a validação exige distrito quando a cidade escolhida já possui distritos cadastrados.

Sem isso, no DF — a praça de lançamento — o nível é inútil: o Distrito Federal tem um único município, então "Brasília" declara 5.800 km², do Gama a Sobradinho. O mesmo vale para São Paulo (1.521 km²). Cidades pequenas sem distrito cadastrado continuam podendo selecionar só a cidade.

### D9 — Superfície HTTP

| Método | Path | Efeito |
|--------|------|--------|
| `PUT` | `/api/user/me/address` | Cria/atualiza endereço residencial a partir de `placeId` + `number?` + `complement?` |
| `GET` | `/api/user/me/address` | Lê o endereço residencial do chamador |
| `GET` | `/api/drivers/me/service-areas` | Lista as regiões do motorista autenticado |
| `PUT` | `/api/drivers/me/service-areas` | Substitui o conjunto de regiões (lista de `placeId`) |
| `GET` | `/api/drivers/search` | `originPlaceId` + `destinationPlaceId` → motoristas que cobrem ambos |
| `GET` | `/api/schools/resolve` | Resolve um `placeId` de escola em uma `school` persistida |

Identificadores públicos permanecem `token` opaco (regra 13). O `placeId` do Google é dado de entrada, não identificador de recurso da Vanep.

### D10 — Onboarding como enum, não boolean

`GET /api/user/me` ganha `onboarding.pendingSteps: ["PERSONAL_ADDRESS", "SERVICE_AREA"]`.

Enum backed (regra 14) em vez de flags booleanas: o mobile não precisa saber que `SERVICE_AREA` só se aplica a motorista, e passos futuros entram sem quebrar o app.

---

## Risks / Trade-offs

**R1 — Mapeamento `types` → nível é inconsistente no Google (ALTO).**
Em São Paulo `administrative_area_level_2` e `locality` são ambos "São Paulo"; no DF `locality` às vezes é "Brasília" e às vezes "Taguatinga". Se `locality` cair como `city` em SP e como `district` no DF, a árvore sai torta e o match falha **silenciosamente** — sem erro, apenas sem resultado.
*Mitigação:* fase 1 é um spike que coleta `addressComponents` crus de ~10 endereços reais e monta a tabela de mapeamento com evidência. Nenhuma migration antes disso.

**R2 — O primeiro cadastro define o nome canônico da região.**
Como a árvore é lazy, o primeiro motorista a cadastrar Taguatinga cria o nó. Se o Google devolver grafia diferente depois, surge nó duplicado irmão.
*Mitigação:* unique parcial em (`parent_id`, `normalized_name`) com `WHERE deleted_at IS NULL`, e `normalized_name` com unaccent + lowercase.

**R3 — Custo do Places cresce com a busca.**
Duas caixas de autocomplete por busca, mais `Place Details` no backend.
*Mitigação:* session token obrigatório no cliente; cache de `Place Details` por `placeId` no backend. Monitorar quota antes de abrir para o público.

**R4 — Dependência dura de um fornecedor externo.**
Sem Google, não há cadastro de região nem busca.
*Mitigação:* a árvore é persistida localmente, então o match de motoristas já cadastrados continua funcionando offline; apenas a resolução de novos places para. Aceito para a v1.

**R5 — Corte destrutivo de `driver.city` / `driver.service_areas`.**
Aceito explicitamente: não há dados reais em produção (confirmado com o time).

---

## Migration Plan

Sem backfill. Migrations a partir de `V20` (última aplicada: `V19`), nenhuma migration existente editada (regra 2).

| Migration | Conteúdo |
|-----------|----------|
| `V20` | `district` (auto-FK, soft delete, unique parcial em `parent_id` + `normalized_name`); `google_place_id` + `normalized_name` em `state` e `city` |
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

**Nenhuma fase de código pode começar antes da fase 0 estar concluída** — sem as chaves do Google não há como resolver place algum, e o spike da fase 1 depende de chamadas reais.
