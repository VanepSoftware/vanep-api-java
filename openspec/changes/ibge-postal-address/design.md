## Contexto

O `PUT /api/user/me/address` hoje aceita só `placeId` (+ `sessionToken`, `number`, `complement` opcionais). O `PersonalAddressService` delega a `AddressPlaceResolverService.applyPlace` (extraído pela PR #173), que chama Place Details, exige um componente de rua e faz `findOrCreate` na árvore. `city` nasce lazy do Google; o `StateSeeder` já cura as UFs. ViaCEP é não-objetivo documentado de `owned-address-model`. Testes cravam URLs de saída em `http://localhost:1` (regra 50). A `main` já está em `V45` (auth nativo N-177 usou `V34`/`V35`; o refactor de rating por `client_driver` tomou `V36`–`V39`; mídia tomou `V41`–`V45`); a próxima Flyway desta change é `V46`. Motivação em `proposal.md`.

`GET /api/cities` e `GET /api/states` hoje são admin (`list_cities` / `list_states`). Esta change **reusa esses paths** como picker autenticado: município IBGE é dado de referência, não catálogo operacional. Motivação em `proposal.md`.

## Objetivos e itens fora do escopo

**Objetivos:**

- Uma tabela `city` = municípios IBGE; o Google nunca insere `city`.
- Escrita do endereço pessoal com `cityToken` do catálogo.
- ViaCEP só como prefill opcional num GET, nunca no PUT.
- Falha visível quando o texto de cidade do Google não casa com o IBGE (escrita e busca).
- Dropar `google_place_id` não usado em `city` e `state` (nunca foi gravado; não é o gancho de um Geocoding futuro).
- Endereço de embarque do dependente com o mesmo contrato `cityToken` do endereço pessoal (fase 7).

**Fora do escopo:**

- Tabela de alias nome Google → `ibge_code`.
- Geocoding, lat/lng, mini-mapa.
- `AddressRequestDTO` de **escola** — continua `placeId`/Google via `AddressService` + `AddressPlaceResolverService` (`dependent-address-by-place`). Sem tela no app cliente hoje; sem pressão para migrar.
- Distrito/subdistrito IBGE.
- Mudar as regras de match da busca de motorista além de cidade sem match → 400.
- Frontend.

## Decisões

### D1 — `city` é IBGE; `district` continua lazy do Google

Desfaz o D5 de `location-system`: aquela decisão recusou IBGE porque a **busca** precisa de Places ao vivo (`qnl 5`, nomes de escola). Município já tem código oficial. O campo `ibge` do ViaCEP mapeia 1:1.

O `LocationResolverService` mantém `findOrCreateDistrict`. Lookup de cidade vira só `findByStateIdAndNormalizedName`. Miss → exceção de negócio (mesma chave MessageSource na persistência e na busca). Sem tabela `city_alias`.

**Alternativas:** manter `findOrCreateCity` depois do seed IBGE (linhas gêmeas na deriva de grafia); alias nesta change (tabela vazia até vai; gerar as linhas não — adiado).

### D2 — Dump no repo, seeder no boot, não 5570 inserts na Flyway

Capturar IBGE `localidades/municipios` **uma vez** em `src/main/resources/seed/ibge-municipalities.json`. `CitySeeder` (depois do `StateSeeder`) faz upsert por `ibge_code`. Testes nunca baixam IBGE: unitários usam fixture de duas cidades; slice insere as cidades que precisa.

`CountrySeeder` → `StateSeeder` → `CitySeeder` rodam num `ApplicationRunner` próprio (`GeographicDataSeeder`), ligado por padrão (`vanep.geographic-data.seed-enabled`) e separado do `DataSeeder` de demonstração (`vanep.seed.enabled`, default `false`). O catálogo geográfico é dado de referência que a busca de motorista, o picker e o CEP exigem em qualquer ambiente; gate-lo atrás do mesmo flag do admin/dados fictícios obrigaria produção a escolher entre ficar sem cidades ou subir um admin de senha padrão.

Cada item do JSON é **um** município. `microrregiao` e `regiao-imediata` são recortes estatísticos do mesmo município, não duas cidades. O seeder lê só:

| JSON | `city` |
|------|--------|
| `id` | `ibge_code` (varchar 7, ex. Cristalina `5206206`) |
| `nome` | `name` (`normalized_name` no `@PrePersist`) |
| `microrregiao.mesorregiao.UF.sigla` | FK para `state` já curado (`GO`) |
| `regiao-imediata.regiao-intermediaria.UF.sigla` | fallback da UF se `microrregiao` for `null` (ex. Boa Esperança do Norte / MT) |

Se `id`, `nome` ou UF ainda faltarem depois do fallback, o seeder pula o município e loga; não aborta o seed. O resto do objeto (meso, micro, região, imediata como cidade, `UF.id`/`nome`) é descartado. ViaCEP `ibge` casa com esse `id`.

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

Não criar `/api/geo` nem `CityCatalogController`. Reusar `CityController` / `StateController` e métodos em `CityService` / `StateService`. DTOs de lista: token opaco, nome, UF; sem exigir `list_cities` / `list_states`. `CityService` não tem create/update/delete — CRUD de escrita de `city` já não existe desde antes desta change (saiu no `location-system`) e o catálogo IBGE agora é a fonte de verdade da tabela, então admin não deve controlar essas linhas. `LIST_CITIES`, `SHOW_CITY`, `CREATE_CITY`, `UPDATE_CITY` e `DELETE_CITY` foram removidas do `PermissionEnum` (nada mais as referenciava); `list_states`/`show_state` seguem no enum (não fazem parte deste comentário), só as authorities mortas nos testes admin de state foram limpas.

`/api/countries` **permanece** admin: país ainda é “onde a Vanep opera”.

**Alternativas:** namespace `/api/geo` (path inventado, fora do estilo da API); só trocar `@PreAuthorize` sem exigir `uf` (dump de 5.570 cidades).

### D4 — ViaCEP só no GET; PUT nunca chama Correios

`ViaCepClient` + bean `RestClient`, base URL `vanep.viacep.base-url` (env). Perfil de teste: `http://localhost:1/viacep`. CEP desconhecido: ViaCEP `{ "erro": true }` → 404. Falha de transporte → 503. `ibge` sem linha de cidade → 404 (buraco no catálogo). Rate limit: bean `RateLimiter` dedicado (mesmo padrão do `placesRateLimiter`), chave `cep-lookup:` + token do usuário do JWT — como o `school-resolve:` do `SchoolResolveService`. Nunca chavear por IP nem ler `X-Forwarded-For`: o refactor de auth da `main` (N-177) fechou esse furo no `RateLimitingFilter`, que agora usa só `getRemoteAddr()` com o proxy confiável configurado em `server.tomcat.remoteip`. O `RateLimitingFilter` só cobre `POST /api/auth/**`; o CEP é um `GET` sob `/api/**` e tem o limiter próprio.

Esse rate limit é por usuário e não soma o total: toda saída da VPS é o mesmo IP, e é esse IP que o ViaCEP pode bloquear por uso excessivo, não o uid individual. `ViaCepClient` tem um cache Caffeine CEP → `ViaCepResponseDTO`, igual ao `PlacesClient` (`vanep.viacep.cache-ttl-minutes`/`cache-max-size`), com TTL bem mais longo (7 dias, contra 24h do Places) porque CEP é designação postal oficial e praticamente não muda. O cache corta a maioria das chamadas repetidas na fonte, em vez de só reagir ao 503 quando o ViaCEP já bloqueou o IP.

`CityRepository.findByIbgeCode` (usado em `requireCatalogCity`) não filtra `active`, então o lookup por CEP pode resolver uma cidade que o picker da fase 3 esconderia. Isso é intencional, não uma lacuna: `active` é filtro de descoberta do picker (`findByStateIdAndActiveTrue*`), não de validade de um identificador já conhecido — a mesma regra que a fase 6 formaliza pro PUT de endereço pessoal (`cityToken` de cidade inativa segue aceito). Hoje isso nem é alcançável na prática: nenhum código chama `CityModel.setActive(false)` em lugar nenhum — a coluna sobrevive de uma convenção mais antiga da árvore de localização (o mesmo vale pra `state`), sem gatilho de escrita desde que o CRUD de admin saiu.

O PUT `/api/user/me/address` resolve `cityToken` só no banco.

`CepLookupService` **não** é `@Transactional`: a chamada HTTP ao ViaCEP (timeouts de conexão e leitura) segurava uma conexão do pool durante toda a espera. `city.state` é `EAGER` e o repositório já abre a própria transação de leitura. `ibge` nulo ou vazio na resposta é tratado como cidade fora do catálogo (`404`), nunca consultado por `findByIbgeCode(null)`, que casaria uma cidade qualquer sem `ibge_code`.

**Alternativa:** o app chama viacep.com.br — funciona, mas a API não consegue devolver `cityToken` sem um segundo round-trip e perdemos o match por código no servidor.

**`code` estável em `cep.ibge.not_found` (pós-review, mesma lacuna de D9).** `cep.not_found` (CEP inexistente no ViaCEP) já tinha `code` por passar pelo `CepLookupErrorAdvice`; `cep.ibge.not_found` (cidade do CEP fora do catálogo) nascia como `ResponseStatusException` avulso em `catalogMiss`, sem `code`, e o mobile diferenciava os dois procurando `"catálogo"`/`"catalog"` no `detail` (`isCityNotInCatalogDetail`) — achado ao revisar o app para o fix análogo da fase 7. Mesmo tratamento: `catalogMiss` seta `code=cep.ibge.not_found` no próprio `ProblemDetail`. `cep.invalid`/`cep.rate_limited` (mencionados em 5.5) continuam sem `code` — nenhuma ambiguidade de texto foi reportada para eles.

### D5 — Contrato postal do PUT

`PersonalAddressRequestDTO`: `cityToken` `@NotBlank`, `street` `@NotBlank @Size(max=255)`, `zipCode` `@NotBlank` + `@Pattern` (8 dígitos). `number` / `complement` / `neighborhood` opcionais com os size caps atuais. Jackson ignora `placeId` desconhecido. PUT não chama ViaCEP: obrigatório é só validação do body.

> Nota (fase 7): quando este design foi escrito, a suposição era que `AddressRequestDTO` de dependente/escola já usava esse mesmo contrato `cityToken`. Não é mais verdade — a PR #173 (`dependent-address-by-place`, hoje na `main`) regrediu os dois para `placeId`/Google porque o app não tinha como obter um `cityToken` (`GET /api/cities` era 403 pro papel CLIENT). A fase 7 (D9) desfaz isso só para dependente; escola permanece em `placeId`. Ao fim das fases 6 e 7, `AddressPlaceResolverService` só é chamado pelo caminho de escola.

`PersonalAddressService.replaceMyAddress`: carrega a cidade pelo token → 404 `city.not_found`; grava rua e campos postais; `district_id = null`; `google_place_id = null`; não chama `PlacesClient`.

Resposta GET: adicionar `neighborhood`; `googlePlaceId` pode ser nulo (manter por compatibilidade ou dropar — **dropar do DTO pessoal** se nada lê ainda; o Flutter deste PUT não está shipped em `placeId` se vamos quebrar de qualquer jeito). Preferir omitir `googlePlaceId` da resposta pessoal nesta change para o app não tratar como obrigatório.

Coluna `address.neighborhood varchar(128)` nullable. `address.zip_code` **permanece nullable** (V25 tirou o NOT NULL porque Place Details vinha sem CEP; não restaurar NOT NULL nesta change — a tabela é compartilhada e linhas antigas do path Places podem ser nulas). A exigência vive só no DTO do PUT. A coluna `google_place_id` de `address` fica; este caminho grava null.

### D6 — Busca 400 vs página vazia

`resolveAnchor` hoje devolve `Optional.empty()` quando a linha de cidade falta (busca → página vazia). Depois do seed IBGE, cidade ausente significa **nome que não casa**, não “não há motoristas”. Lançar o mesmo erro de município sem match; `DriverSearchService` mapeia para 400. Empty 200 permanece só quando a cidade IBGE casou e o ranking está vazio.

### D7 — Dropar `google_place_id` morto em `city` e `state`

A V23 adicionou `google_place_id` em `state` e `city` “para rastrear origem”. Place Details só devolve `place_id` do pin escolhido, não por `addressComponent`. O resolver casa estado por UF e cidade por `(state, normalized_name)` e nunca chama `setGooglePlaceId`. `CityRepository.findByGooglePlaceId` não é usado. DTOs admin de city/state nunca expõem o campo. Gravar o id do pin clicado na linha de cidade violaria “persistir o nó derivado, não o place escolhido” e brigaria com o unique (dois pins em Brasília são dois ids).

Uma change futura de Geocoding/alias é **N nomes ou place ids Google → um `ibge_code`**. Coluna unique 1:1 em `city` é o schema errado para isso. Estado já tem UF. `district.google_place_id` fica: esse nível não tem código oficial e é o único nó da árvore que o Geocoding pode canonicalizar depois. `address` e `school` mantêm a coluna (identidade do place escolhido).

Dropar na `V46` junto com `ibge_code` (momento mais barato; não editar V23). Não é breaking HTTP.

**Alternativas:** deixar as colunas null “pro Geocoding” (convite a preencher o 1:1 errado depois); dropar numa migration seguinte (mesmo trabalho, mais tarde).

### D8 — PRs faseados

```
V46 + models (drop city/state google_place_id)
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
                    │
                    ▼
              Dependente por cityToken (fase 7)
```

Fases 3 e 4 podem seguir em paralelo depois do seeder. Fase 5 depende da 2 (`ibge_code` para casar). Fase 6 depende da 1 (`neighborhood`) e pode ir sem ViaCEP. Fase 7 depende da 6 — reaproveita o colaborador de resolução por cidade que a fase 6 introduz em `PersonalAddressService` (ver D9); precisa também da 3 (picker) já estar no ar, mas essa dependência é de produto/mobile, não de merge order no backend.

**Ordem de merge recomendada: 1, 2, 3, 5, 6, 7 e a 4 por último.** A fase 4 (resolver só casa cidade) quebra todo teste que ainda depende de o Google criar `city`. Dos testes existentes, dependiam disso `PersonalAddressControllerTest` e `OnboardingStepsTest`, que a fase 6 **reescreve por completo** para `cityToken`, além dos de resolver, busca de motorista, área de atuação e `SchoolResolve`. `DependentControllerTest`, `AddressServiceTest` e `SchoolControllerTest` já inserem a cidade antes de resolver o place e não quebram. Se a 4 entrasse antes da 6, teria de consertar os dois primeiros só para a fase 6 jogar o conserto fora (e conflitarem). Com a 4 por último, ela só toca o que sobrevive: busca, área de atuação, escola e o próprio resolver. As fases 6 e 7 não dependem da 4, então a reordem é só de merge, não de branch.

| Fase | Conteúdo | Depende de | Paralelo com |
|------|----------|------------|--------------|
| 1 | `V46` + `CityModel.ibgeCode` + `AddressModel.neighborhood`; drop `google_place_id` em `city` e `state` | — | — |
| 2 | Dump IBGE + `CitySeeder` + testes do seeder (fixture pequena) | 1 | — |
| 3 | Abrir `GET /api/states` e `GET /api/cities?uf=&search=` (`isAuthenticated()`) | 2 | 4 |
| 4 | Resolver só match de cidade; 400 no miss (persistência + busca) | 2 | 3 |
| 5 | `ViaCepClient` + `GET /api/cep/{cep}` | 2 | 6 |
| 6 | **BREAKING** PUT postal `/api/user/me/address` | 1 | 5 |
| 7 | **BREAKING** endereço de embarque do dependente por `cityToken` (D9) | 6 | — |

### D9 — Endereço de embarque do dependente volta a `cityToken`, só para dependente

A PR #173 (`dependent-address-by-place`) trocou `AddressRequestDTO` (compartilhado por dependente e escola) de `cityToken` para `placeId`, porque na época o app cliente não tinha como produzir um `cityToken` válido — `GET /api/cities` exigia `list_cities` (403 pro papel CLIENT) e não tinha busca por nome. As fases 2 e 3 desta própria change resolvem exatamente isso: catálogo semeado e `GET /api/cities?uf=&search=` só com `isAuthenticated()`. Mantida a regressão, o dependente ficaria como o único fluxo de endereço do produto ainda preso ao Google, sem motivo — o bloqueio original não existe mais.

**Escopo — só dependente, não escola.** Em `main`, `AddressRequestDTO` / `AddressService.upsertForDependent` / `upsertForSchool` são um serviço só, e o esqueleto privado `upsertOwnedAddress(currentAddressId, AddressRequestDTO, rejectIfOwnedByAnother, linkToOwner)` guarda a checagem cruzada de posse (`rejectIfOwnedByAnotherActiveOwner` conta dependentes **e** escolas). Migrar os dois de uma vez amplia o escopo além do pedido; escola não tem tela no app cliente hoje, então fica no `placeId` existente. Isso bifurca o ponto de entrada:

- **Novo** `DependentAddressRequestDTO` (`address.dto`), mesmo shape do `PersonalAddressRequestDTO` e com o mesmo `@JsonIgnoreProperties(ignoreUnknown = true)`: `cityToken` `@NotBlank`, `street` `@NotBlank @Size(max=255)`, `zipCode` `@NotBlank @Pattern` (8 dígitos), `number`/`complement`/`neighborhood` opcionais com os caps atuais. `placeId` e `sessionToken` enviados são ignorados.
- `AddressService.upsertForDependent(Long, DependentAddressRequestDTO)` **troca a assinatura** do método existente (não é um overload; o de `AddressRequestDTO` deixa de existir para dependente). Dependente para de passar por `AddressPlaceResolverService`. `upsertForSchool(Long, AddressRequestDTO)` fica como está.
- `upsertOwnedAddress` **deixa de receber `AddressRequestDTO`** e passa a receber a escrita como parâmetro (um `Consumer<AddressModel>` para linha nova e outro para linha existente). Assim o esqueleto de posse/link continua único e não conhece nenhum dos dois DTOs. Dependente passa o mesmo consumer para os dois casos (`applyCity` sobrescreve tudo); escola passa `applyToNewAddress` / `applyToExistingAddress` como hoje (place, com amend de número/complemento).
- A checagem de posse cruzada (`rejectIfOwnedByAnotherActiveOwner`) não muda — continua contando dependentes e escolas ativos sobre o mesmo `address.id`, independente de qual contrato criou a linha.

**`address` do dependente é substituição completa, sem amend parcial.** A PR #173 deu ao dependente um amend "só número/complemento sem reenviar o place", por um motivo válido naquele contrato: o app não tinha o `placeId` de um endereço já gravado (a resposta não o expõe). Aqui o motivo não existe: `AddressResponseDTO` já devolve `cityToken`, rua, CEP, número, complemento e (com este D9) bairro, então o app monta o formulário a partir da leitura e reenvia tudo. Manter também um amend parcial exigiria `cityToken`/`street`/`zipCode` opcionais no DTO (contradizendo o `@NotBlank` acima) e validação de obrigatoriedade no `@Service`, além de deixar campos aninhados `String` onde omitido e `"x": null` colapsam no mesmo `null` — exatamente o que a regra 16 proíbe. Quando `address` está presente no PATCH ele é um objeto completo, e a regra 10 manda o `@Valid` aninhado valer — o mesmo formato do `PUT /api/user/me/address` (regra 16: sub-recurso com corpo completo `@Valid`). Consequências:

- Linha existente: sobrescrita inteira, mesmo `address.id`; `number`/`complement`/`neighborhood` omitidos ficam nulos.
- `address` incompleto → 400 (Bean Validation), com ou sem endereço prévio.
- PATCH sem `address` deixa o endereço intacto (teste nomeado, regra 16); `address: null` continua limpando, como já faz `applyAddressMerge`.
- `address.place_required` deixa de ser usada pelo dependente (fica só para escola).

**Colaborador reaproveitado.** `PersonalAddressService.replaceMyAddress` (fase 6) já tem o bloco "carrega `CityModel` pelo token → seta city/district=null/googlePlaceId=null/street/zipCode/number/complement/neighborhood (com `blankToNull`)". Esse bloco migra para um novo `AddressCatalogResolverService.applyCity(AddressModel, cityToken, street, zipCode, number, complement, neighborhood)`, chamado pelos dois: `PersonalAddressService` (sem mudança de comportamento) e o novo caminho de `AddressService.upsertForDependent`. É o espelho de `AddressPlaceResolverService.applyPlace(address, placeId, sessionToken, number, complement)`, com parâmetros soltos em vez de DTO, o mesmo padrão que a PR #173 usou (regra 6, regra 32). O `city.not_found` 404 nasce no colaborador.

**Resposta ganha `neighborhood`.** `AddressResponseDTO`/`AddressMapper` (usado por dependente e escola) não expõe `neighborhood` hoje, embora a coluna exista desde a fase 1. Sem isso, o app conseguiria gravar o bairro do dependente mas nunca leria de volta (e não montaria o formulário de edição). Adicionar o campo é aditivo e não quebra escola (fica `null` lá, como hoje).

**Alternativas:** migrar dependente e escola juntos (mais DRY na checagem de posse, mas amplia o escopo pedido sem necessidade); manter `AddressRequestDTO` único e sobrecarregar `cityToken` *e* `placeId` nele (contrato ambíguo — reabre exatamente o problema que a fase 6 do endereço pessoal evitou ao não aceitar os dois); manter o amend parcial da PR #173 com `cityToken`/`street`/`zipCode` opcionais (contradiz a regra 16 e o `@NotBlank`; sem motivo, já que a leitura devolve o formulário todo).

**`code` estável nos dois 404 do PATCH.** No `PATCH /api/dependent`, `city.not_found` (token de cidade desconhecido, lançado em `AddressCatalogResolverService.requireCityByToken`) e `dependent.not_found` (token de dependente desconhecido, lançado em `DependentService.notFound` e no `requireDependent` de `AddressService`) chegavam como o mesmo `404` sem nenhum campo além do `detail` textual — o mobile (vanep-mobile#67) diferenciava os dois procurando `"cidade"`/`"city"` na mensagem, um acoplamento frágil a texto de erro (que ainda por cima é i18n). Cada um passa a setar `problem.getBody().setProperty("code", <chave>)` no próprio `ResponseStatusException`, mesmo padrão de `code` estável já usado em `LocationErrorAdvice`/`CepLookupErrorAdvice`, sem introduzir uma exceção customizada nem um `@RestControllerAdvice` novo — os dois já eram `ResponseStatusException` avulsos, então bastou mutar o `ProblemDetail` da própria exceção antes de lançá-la. Escopo é só os dois 404 do fluxo de dependente citados no comentário; `school.not_found` e `address.not_found` (mesmo arquivo) ficam de fora por não fazerem parte da ambiguidade relatada.

## Riscos / trade-offs

- **[Risco] Dump IBGE desatualizado** (município novo) → ViaCEP 404 de código; recapturar dump. Sem IBGE em runtime.
- **[Risco] Grafia Google ≠ IBGE no interior** → 400 alto até a change de alias. Lançamento DF/SP capital: fixtures atuais casam. O `log.warn` do miss em `LocationResolverService` era o único sinal disso; agora incrementa também `location.city.unmatched` (contador Micrometer, tag `uf`) via `spring-boot-starter-actuator`, consultável em `GET /actuator/metrics/location.city.unmatched` — restrito a `ROLE_ADMIN` no `defaultSecurityFilterChain` (`@Order(4)`), diferente de `/actuator/health`/`/info`/`/mappings` que continuam públicos. Isso não é um alerta — ninguém é avisado proativamente, e o contador zera a cada restart. Ligar isso a um alerta de verdade (Prometheus/Datadog/Grafana com scrape + regra) é decisão de infra do time, fora do escopo desta change; o `MeterRegistry` já publica em qualquer backend que for adicionado depois, sem mudar este código.
- **[Risco] BREAKING no PUT de endereço** → app ainda em `placeId` quebra; coordenar release. Sem contrato duplo (place **ou** form) nesta change — dois caminhos reabrem duas verdades.
- **[Risco] Seeder 5570 linhas no boot** → uma vez, idempotente, em todo boot de qualquer ambiente (`GeographicDataSeeder` ligado por padrão); aceitável. Testes não carregam o dump completo e desligam `vanep.geographic-data.seed-enabled`.
- **[Risco] ViaCEP fora do ar** → 503 no GET; PUT e picker seguem. Não acoplar save ao Correios.
- **[Risco] `GET /api/cities` sem `uf`** → breaking para quem listava o catálogo admin inteiro; nenhum cliente de produção assumido. Sem `uf` é `400` de propósito (5.570 municípios).
- **[Risco] Reintroduzir `google_place_id` em `city` no Geocoding** → unique 1:1 é o modelo errado; alias N→1. D7 dropa de propósito.
- **[Risco] BREAKING no `POST/PATCH /api/dependent`** (fase 7) → o branch `feat/8-dependent-address` do `vanep-mobile`, escrito contra o contrato `placeId` da PR #173, precisa migrar para `cityToken` antes do release; coordenar como no D9/personal-address (sem contrato duplo). Além da troca de campos, o app perde o amend "só número": passa a reenviar o formulário completo montado a partir da leitura.
- **[Risco] Colisão de versão Flyway** → branches de fase escritas antes de um trecho da `main` entrar duplicam a versão de um arquivo já ocupado lá (primeiro `V34` do auth N-177, depois `V36` do refactor de rating por `client_driver`). O `git merge` não acusa (nomes de arquivo diferentes); o Flyway recusa subir com duas migrations na mesma versão. Renomear o arquivo da fase 1 para a próxima versão livre em `main` antes de mergear cada vez que isso acontecer (`V34__...` → `V36__...` → `V46__...`); a versão local antiga nunca foi aplicada fora do banco do dev (que precisa ser recriado ou reparado). Regra 2 não impede: o arquivo não foi aplicado em ambiente compartilhado.
- **[Risco] Fase 4 antes das 6/7** → reescreve testes que as 6/7 apagam (ver D8); mitigado pela ordem de merge com a 4 por último.

## Plano de migração

1. `V46`: `city.ibge_code varchar(7)` unique where `deleted_at is null`; `address.neighborhood varchar(128)`; drop `city.google_place_id` e `state.google_place_id` mais `city_google_place_id_active_key` / `state_google_place_id_active_key`. Não editar V23 (regra 2).
2. Deploy do seeder (dev/prod). Cidades lazy Google existentes sem `ibge_code`: **sem dado de produção** assumido; se um banco local tiver resto Google, recriar ou casar por nome e setar `ibge_code` na mão. Sem backfill heróico nesta change.
3. Deploy do resolver (Google para de criar cidade) **depois** do seeder ter rodado, senão o primeiro Place Details dá 400 em Brasília.
4. Deploy CEP + PUT postal; o app troca no mesmo trem de release.

Rollback: nova migration para reverter a V46 se já aplicada (dropar `ibge_code` / `neighborhood`, repor `google_place_id` só se um rollback do D7 for mesmo necessário — sempre foi null); não editar V46 (regra 2). Rollback do resolver restauraria `findOrCreateCity` — não enviar o resolver antes do catálogo estar populado.

## Questões em aberto

Nenhuma que mude as specs. A data do snapshot do dump IBGE é escolhida na implementação da fase 2.
