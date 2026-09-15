## Contexto

O `PUT /api/user/me/address` hoje aceita só `placeId` (+ `sessionToken`, `number`, `complement` opcionais). O `PersonalAddressService` chama Place Details, exige um componente de rua e faz `findOrCreate` na árvore. `city` nasce lazy do Google; o `StateSeeder` já cura as UFs. ViaCEP é não-objetivo documentado de `owned-address-model`. Testes cravam URLs de saída em `http://localhost:1` (regra 50). Próxima Flyway depois da `V33` é `V34`. Motivação em `proposal.md`.

`GET /api/cities` e `GET /api/states` hoje são admin (`list_cities` / `list_states`). Esta change **reusa esses paths** como picker autenticado: município IBGE é dado de referência, não catálogo operacional. Motivação em `proposal.md`.

## Objetivos e itens fora do escopo

**Objetivos:**

- Uma tabela `city` = municípios IBGE; o Google nunca insere `city`.
- Escrita do endereço pessoal postal com `cityToken` do catálogo.
- ViaCEP só como prefill opcional num GET, nunca no PUT.
- Falha visível quando o texto de cidade do Google não casa com o IBGE (escrita e busca).
- Dropar `google_place_id` não usado em `city` e `state` (nunca foi gravado; não é o gancho de um Geocoding futuro).

**Fora do escopo:**

- Tabela de alias nome Google → `ibge_code`.
- Geocoding, lat/lng, mini-mapa.
- `AddressRequestDTO` aninhado de dependente / escola (follow-up).
- Distrito/subdistrito IBGE.
- Mudar as regras de match da busca de motorista além de cidade sem match → 400.
- Frontend.

## Decisões

### D1 — `city` é IBGE; `district` continua lazy do Google

Desfaz o D5 de `location-system`: aquela decisão recusou IBGE porque a **busca** precisa de Places ao vivo (`qnl 5`, nomes de escola). Município já tem código oficial. O campo `ibge` do ViaCEP mapeia 1:1.

O `LocationResolverService` mantém `findOrCreateDistrict`. Lookup de cidade vira só `findByStateIdAndNormalizedName`. Miss → exceção de negócio (mesma chave MessageSource na persistência e na busca). Sem tabela `city_alias`.

**Alternativas:** manter `findOrCreateCity` depois do seed IBGE (linhas gêmeas na deriva de grafia); alias nesta change (tabela vazia até vai; gerar as linhas não — adiado).

### D2 — Dump no repo, seeder no boot, não 5570 inserts na Flyway

Capturar IBGE `localidades/municipios` **uma vez** em `src/main/resources/seed/ibge-municipios.json`. `CitySeeder` (depois do `StateSeeder`) faz upsert por `ibge_code`. Testes nunca baixam IBGE: unitários usam fixture de duas cidades; slice insere as cidades que precisa.

Cada item do JSON é **um** município. `microrregiao` e `regiao-imediata` são recortes estatísticos do mesmo município, não duas cidades. O seeder lê só:

| JSON | `city` |
|------|--------|
| `id` | `ibge_code` (varchar 7, ex. Cristalina `5206206`) |
| `nome` | `name` (`normalized_name` no `@PrePersist`) |
| `microrregiao.mesorregiao.UF.sigla` | FK para `state` já curado (`GO`) |

O resto do objeto (meso, micro, região, imediata, `UF.id`/`nome`) é descartado. ViaCEP `ibge` casa com esse `id`.

**Alternativas:** CSV Flyway de 5570 (checksum doloroso, migration enorme); HTTP IBGE em runtime (viola regra 50 se o teste bater; mais um modo de queda).

### D3 — Picker nos `/api/states` e `/api/cities` existentes, sem `/api/geo`

Município IBGE não é dado de painel. Os GETs atuais são CRUD operacional antigo (“cidades atendidas”); as escritas já saíram no `location-system`. Esta change abre a leitura para qualquer autenticado e ajusta o contrato da lista de cidades.

| Método | Path | Auth |
|--------|------|------|
| GET | `/api/states` | `isAuthenticated()` |
| GET | `/api/states/{token}` | `isAuthenticated()` |
| GET | `/api/cities?uf=DF&search=` | `isAuthenticated()` |
| GET | `/api/cities/{token}` | `isAuthenticated()` |

`uf` é o código de duas letras e **obrigatório** em `GET /api/cities`. Sem `uf` → `400` (MessageSource). UF desconhecida → `404`. `search` opcional, casado em `normalized_name` (contains ou prefix — escolher na implementação e testar). Sem `search`, lista paginada daquela UF. Paginação como o `Pageable` existente.

Não criar `/api/geo` nem `CityCatalogController`. Reusar `CityController` / `StateController` e métodos em `CityService` / `StateService`. DTOs de lista: token opaco, nome, UF; sem exigir `list_cities` / `list_states`. Essas authorities deixam de guardar os GETs (podem permanecer no enum/seed nesta change).

`/api/countries` **permanece** admin: país ainda é “onde a Vanep opera”.

**Alternativas:** namespace `/api/geo` (path inventado, fora do estilo da API); só trocar `@PreAuthorize` sem exigir `uf` (dump de 5.570 cidades).

### D4 — ViaCEP só no GET; PUT nunca chama Correios

`ViaCepClient` + bean `RestClient`, base URL `vanep.viacep.base-url` (env). Perfil de teste: `http://localhost:1/viacep`. CEP desconhecido: ViaCEP `{ "erro": true }` → 404. Falha de transporte → 503. `ibge` sem linha de cidade → 404 (buraco no catálogo). Rate limit: bean `RateLimiter` dedicado (mesmo padrão do `placesRateLimiter`).

O PUT `/api/user/me/address` resolve `cityToken` só no banco.

**Alternativa:** o app chama viacep.com.br — funciona, mas a API não consegue devolver `cityToken` sem um segundo round-trip e perdemos o match por código no servidor.

### D5 — Contrato postal do PUT

`PersonalAddressRequestDTO`: `cityToken` `@NotBlank`, `street` `@NotBlank @Size(max=255)`, `zipCode` `@NotBlank` + `@Pattern` (8 dígitos) — mesmo contrato do `AddressRequestDTO` de dependente/escola. `number` / `complement` / `neighborhood` opcionais com os size caps atuais. Jackson ignora `placeId` desconhecido. PUT não chama ViaCEP: obrigatório é só validação do body.

`PersonalAddressService.replaceMyAddress`: carrega a cidade pelo token → 404 `city.not_found`; grava rua e campos postais; `district_id = null`; `google_place_id = null`; não chama `PlacesClient`.

Resposta GET: adicionar `neighborhood`; `googlePlaceId` pode ser nulo (manter por compatibilidade ou dropar — **dropar do DTO pessoal** se nada lê ainda; o Flutter deste PUT não está shipped em `placeId` se vamos quebrar de qualquer jeito). Preferir omitir `googlePlaceId` da resposta pessoal nesta change para o app não tratar como obrigatório.

Coluna `address.neighborhood varchar(128)` nullable. `address.zip_code` **permanece nullable** (V25 tirou o NOT NULL porque Place Details vinha sem CEP; não restaurar NOT NULL nesta change — a tabela é compartilhada e linhas antigas do path Places podem ser nulas). A exigência vive só no DTO do PUT. A coluna `google_place_id` de `address` fica; este caminho grava null.

### D6 — Busca 400 vs página vazia

`resolveAnchor` hoje devolve `Optional.empty()` quando a linha de cidade falta (busca → página vazia). Depois do seed IBGE, cidade ausente significa **nome que não casa**, não “não há motoristas”. Lançar o mesmo erro de município sem match; `DriverSearchService` mapeia para 400. Empty 200 permanece só quando a cidade IBGE casou e o ranking está vazio.

### D7 — Dropar `google_place_id` morto em `city` e `state`

A V23 adicionou `google_place_id` em `state` e `city` “para rastrear origem”. Place Details só devolve `place_id` do pin escolhido, não por `addressComponent`. O resolver casa estado por UF e cidade por `(state, normalized_name)` e nunca chama `setGooglePlaceId`. `CityRepository.findByGooglePlaceId` não é usado. DTOs admin de city/state nunca expõem o campo. Gravar o id do pin clicado na linha de cidade violaria “persistir o nó derivado, não o place escolhido” e brigaria com o unique (dois pins em Brasília são dois ids).

Uma change futura de Geocoding/alias é **N nomes ou place ids Google → um `ibge_code`**. Coluna unique 1:1 em `city` é o schema errado para isso. Estado já tem UF. `district.google_place_id` fica: esse nível não tem código oficial e é o único nó da árvore que o Geocoding pode canonicalizar depois. `address` e `school` mantêm a coluna (identidade do place escolhido).

Dropar na `V34` junto com `ibge_code` (momento mais barato; não editar V23). Não é breaking HTTP.

**Alternativas:** deixar as colunas null “pro Geocoding” (convite a preencher o 1:1 errado depois); dropar numa migration seguinte (mesmo trabalho, mais tarde).

### D8 — PRs faseados

```
V34 + models (drop city/state google_place_id)
    │
    ▼
CitySeeder + dump
    │
    ├──────────────┐
    ▼              ▼
GET /api/states|/api/cities    Resolver só match
                    │
    ┌───────────────┤
    ▼               ▼
ViaCEP GET     PUT endereço pessoal
```

Fases 3 e 4 podem seguir em paralelo depois do seeder. Fase 5 depende da 2 (`ibge_code` para casar). Fase 6 depende da 1 (`neighborhood`) e pode ir sem ViaCEP.

| Fase | Conteúdo | Depende de | Paralelo com |
|------|----------|------------|--------------|
| 1 | `V34` + `CityModel.ibgeCode` + `AddressModel.neighborhood`; drop `google_place_id` em `city` e `state` | — | — |
| 2 | Dump IBGE + `CitySeeder` + testes do seeder (fixture pequena) | 1 | — |
| 3 | Abrir `GET /api/states` e `GET /api/cities?uf=&search=` (`isAuthenticated()`) | 2 | 4 |
| 4 | Resolver só match de cidade; 400 no miss (persistência + busca) | 2 | 3 |
| 5 | `ViaCepClient` + `GET /api/cep/{cep}` | 2 | 6 |
| 6 | **BREAKING** PUT postal `/api/user/me/address` | 1 | 5 |

## Riscos / trade-offs

- **[Risco] Dump IBGE desatualizado** (município novo) → ViaCEP 404 de código; recapturar dump. Sem IBGE em runtime.
- **[Risco] Grafia Google ≠ IBGE no interior** → 400 alto até a change de alias. Lançamento DF/SP capital: fixtures atuais casam.
- **[Risco] BREAKING no PUT de endereço** → app ainda em `placeId` quebra; coordenar release. Sem contrato duplo (place **ou** form) nesta change — dois caminhos reabrem duas verdades.
- **[Risco] Seeder 5570 linhas no boot** → uma vez, idempotente; aceitável. Testes não carregam o dump completo.
- **[Risco] ViaCEP fora do ar** → 503 no GET; PUT e picker seguem. Não acoplar save ao Correios.
- **[Risco] `GET /api/cities` sem `uf`** → breaking para quem listava o catálogo admin inteiro; nenhum cliente de produção assumido. Sem `uf` é `400` de propósito (5.570 municípios).
- **[Risco] Reintroduzir `google_place_id` em `city` no Geocoding** → unique 1:1 é o modelo errado; alias N→1. D7 dropa de propósito.

## Plano de migração

1. `V34`: `city.ibge_code varchar(7)` unique where `deleted_at is null`; `address.neighborhood varchar(128)`; drop `city.google_place_id` e `state.google_place_id` mais `city_google_place_id_active_key` / `state_google_place_id_active_key`. Não editar V23 (regra 2).
2. Deploy do seeder (dev/prod). Cidades lazy Google existentes sem `ibge_code`: **sem dado de produção** assumido; se um banco local tiver resto Google, recriar ou casar por nome e setar `ibge_code` na mão. Sem backfill heróico nesta change.
3. Deploy do resolver (Google para de criar cidade) **depois** do seeder ter rodado, senão o primeiro Place Details dá 400 em Brasília.
4. Deploy CEP + PUT postal; o app troca no mesmo trem de release.

Rollback: nova migration para reverter a V34 se já aplicada (dropar `ibge_code` / `neighborhood`, repor `google_place_id` só se um rollback do D7 for mesmo necessário — sempre foi null); não editar V34 (regra 2). Rollback do resolver restauraria `findOrCreateCity` — não enviar o resolver antes do catálogo estar populado.

## Questões em aberto

Nenhuma que mude as specs. A data do snapshot do dump IBGE é escolhida na implementação da fase 2.
