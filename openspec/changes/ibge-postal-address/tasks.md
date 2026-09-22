## 1. Fase 1 — Schema (PR 1)

> Objetivo: `ibge_code` em `city`, `neighborhood` em `address`; dropar `google_place_id` não usado em `city` e `state`. Sem HTTP ainda.
> Branch: `feat/ibge-postal-address-schema` a partir de `main`
> Depende de: — | Paralelo com: —

- [ ] 1.1 Testes de repositório falhando: `ibge_code` único entre cidades ativas; `neighborhood` persiste em `address`; persistir/carregar `city` e `state` sem mapeamento de `googlePlaceId`
- [ ] 1.2 Migration `V36` (conferir a maior versão em `main` antes de criar — hoje `V35`, do auth N-177; se a branch já tem um `V34__...` escrito antes disso, **renomear para `V36`**, senão o Flyway recusa duas migrations na versão 34; recriar o banco local que aplicou a `V34` antiga) (não editar arquivos já aplicados em ambiente compartilhado, regra 2): `city.ibge_code varchar(7)` + índice unique parcial `WHERE deleted_at IS NULL`; `address.neighborhood varchar(128)` nullable; `DROP INDEX` `city_google_place_id_active_key` e `state_google_place_id_active_key`; `DROP COLUMN` `city.google_place_id` e `state.google_place_id`. Deixar `district.google_place_id`, `address.google_place_id` e `school.google_place_id`
- [ ] 1.3 Atualizar `CityModel` e `StateModel` (remover `googlePlaceId`); dropar `CityRepository.findByGooglePlaceId`; adicionar `ibgeCode` em `CityModel` e `neighborhood` em `AddressModel`; aplicar `V36` no Postgres local
- [ ] 1.4 `make lint` + testes desta fase; abrir PR

## 2. Fase 2 — Seeder do catálogo IBGE (PR 2)

> Objetivo: todo município brasileiro é uma linha de `city`. Testes nunca chamam IBGE.
> Branch: `feat/ibge-postal-address-seeder` a partir de `feat/ibge-postal-address-schema`
> Depende de: 1 | Paralelo com: —

- [ ] 2.1 **Uma vez, na mão:** baixar o JSON IBGE `localidades/municipios` e commitar em `src/main/resources/seed/ibge-municipalities.json` (regra 50 — não pela suíte de testes)
- [ ] 2.2 Testes unitários falhando do `CitySeeder` com fixture **pequena** commitada (Brasília + outro município): cria linhas com `ibge_code`; segunda execução é idempotente; mapeia UF para o `state` existente
- [ ] 2.3 Implementar `CitySeeder` depois do `StateSeeder` no `DataSeeder`; upsert por `ibge_code`; nunca HTTP para IBGE em runtime
- [ ] 2.4 Ligar `vanep.seed.enabled` para local/prod carregar o dump completo; testes seguem com fixture pequena / inserts explícitos
- [ ] 2.5 `make lint` + `./mvnw verify`; abrir PR

## 3. Fase 3 — Picker autenticado em `/api/states` e `/api/cities` (PR 3)

> Objetivo: lista de UFs + cidades por `uf` + `search` opcional. Sem `/api/geo`. Auth `isAuthenticated()`.
> Branch: `feat/ibge-postal-address-picker` a partir de `feat/ibge-postal-address-seeder`
> Depende de: 2 | Paralelo com: 4

- [ ] 3.1 Testes slice falhando: `401` sem token; `GET /api/states` devolve UFs para cliente autenticado (sem `list_states`); `GET /api/cities?uf=DF&search=brasilia` devolve token de Brasília; sem `uf` → `400`; UF desconhecida `404`; `search` omitido lista a UF paginada; `search` ignora acento e caixa; cidade inativa não aparece, com e sem `search`
- [ ] 3.2 Chaves MessageSource EN + `messages_pt_BR.properties` para UF desconhecida e `uf` ausente
- [ ] 3.3 `CityController` / `StateController`: `@PreAuthorize("isAuthenticated()")`; `GET /api/cities` exige `uf`; `search` opcional em `normalized_name`; só cidades ativas (`active = true`); DTOs com tokens opacos. Sem `CityCatalogController`
- [ ] 3.4 Autorização explícita (regras 20–21): `@PreAuthorize("isAuthenticated()")` nos controllers; a cadeia `/api/**` (`@Order(3)` do `SecurityConfig`, `anyRequest().authenticated()`) já exige JWT, então não há matcher novo — só conferir que nada de `/api/auth/**` (cadeia pública do N-177) alcança esses paths. `list_cities` / `list_states` não são mais exigidas nesses GETs
- [ ] 3.5 Atualizar testes admin existentes de city/state que esperavam `403` sem `list_cities` / `list_states`
- [ ] 3.6 `make lint` + testes desta fase; abrir PR

## 4. Fase 4 — Google casa cidade IBGE, nunca cria cidade (PR 4)

> Objetivo: `findOrCreateCity` some. Miss → 400 + log. Distrito continua lazy. Miss na busca ≠ página vazia.
> Branch: `feat/ibge-postal-address-resolver` a partir de `feat/ibge-postal-address-seeder`
> Depende de: 2 | Paralelo com: 3
> **Mergear por último** (depois de 6 e 7, ver D8): esta fase quebra todo teste que ainda espera `city` criada pelo Google. Se a branch ficar pronta antes, o conserto dos testes de endereço pessoal e dependente é descartado no rebase sobre a 6/7.

- [ ] 4.1 Testes unitários falhando: persistir sob Brasília existente cria só Taguatinga; persistir com nome de cidade `Embu` sob SP (sem esse `normalized_name`) lança e não insere nada; `resolveAnchor` lança em cidade sem match em vez de optional vazio
- [ ] 4.2 Slice falhando: `PUT` área de atuação / `POST` school resolve / `GET` busca de motorista com componente de cidade sem match → `400` MessageSource; busca com cidade casada e sem motoristas continua `200` página vazia; `POST` de escola com place em cidade sem match → `400` e nenhuma linha de `city`, `district`, `address` nem `school` (rollback)
- [ ] 4.3 Chave MessageSource para cidade Google sem correspondência no IBGE (EN + pt-BR); logar UF, nome Google da cidade, place id
- [ ] 4.4 Mudar o caminho de cidade do `LocationResolverService` para só find; manter `findOrCreateDistrict`; a exceção vira `400` no `LocationErrorAdvice` global (mesma chave `location.city.unmatched` na persistência e na busca), sem `try/catch` nos services
- [ ] 4.5 Atualizar testes existentes que assumiam que o Google criava `city` — MUST inserir a cidade IBGE antes (Brasília / São Paulo): `LocationResolverServiceTest`, `DriverSearchControllerTest`, `DriverServiceAreaControllerTest`, `SchoolResolveControllerTest`, `PersonalAddressControllerTest` e `OnboardingStepsTest` (estes dois a fase 6 reescreve por completo). `AddressServiceTest`, `DependentControllerTest` e `SchoolControllerTest` já inserem a cidade antes e não mudam
- [ ] 4.6 `make lint` + `./mvnw verify`; abrir PR

## 5. Fase 5 — Lookup ViaCEP (PR 5)

> Objetivo: `GET /api/cep/{cep}`. PUT ainda não chama ViaCEP.
> Branch: `feat/ibge-postal-address-cep` a partir de `feat/ibge-postal-address-seeder`
> Depende de: 2 | Paralelo com: 6

- [ ] 5.1 Commitar fixtures JSON gravadas do ViaCEP em `src/test/resources`; cravar `vanep.viacep.base-url` em `http://localhost:1/...` no `application-test.properties` (regra 50)
- [ ] 5.2 Testes unitários falhando do `ViaCepClient`: mapeia `ibge` `5300108`; `{ "erro": true }` → não encontrado; falha de conexão → exceção de lookup
- [ ] 5.3 Slice falhando: `401`; CEP de oito dígitos de Brasília `200` + `cityToken`; formato inválido `400`; ViaCEP desconhecido `404`; transporte `503`; `ibge_code` ausente no catálogo `404`; `ibge` nulo ou vazio na resposta do ViaCEP também `404` `cep.ibge.not_found`; rate limit `429` sem chamar ViaCEP
- [ ] 5.4 Config env + `.env.example`: `vanep.viacep.base-url`, timeout, knobs de rate-limit (regras 1/3)
- [ ] 5.5 Implementar client (`RestClient`), service, `CepLookupController`, bean de rate limiter (chave `cep-lookup:` + uid do JWT, nunca IP nem `X-Forwarded-For`), chaves MessageSource; `CepLookupService` sem `@Transactional` (a chamada HTTP não segura conexão do pool)
- [ ] 5.6 `make lint` + testes desta fase; abrir PR

## 6. Fase 6 — Endereço pessoal (PR 6)

> Objetivo: **BREAKING** `PUT /api/user/me/address` = `cityToken` + `street` + `zipCode` (8 dígitos) + campos postais opcionais. Sem Places neste caminho.
> Branch: `feat/ibge-postal-address-personal-address` a partir de `feat/ibge-postal-address-schema`
> Depende de: 1 | Paralelo com: 5

- [ ] 6.1 Slice falhando: `200` com `cityToken` + street + CEP de 8 dígitos; CEP omitido `400`; CEP inválido `400`; street em branco `400`; token desconhecido `404` `city.not_found`; `cityName` extra ignorado; `placeId` não é aceito como contrato; `neighborhood` persiste e volta; `district_id` e `google_place_id` null; `401`; DELETE + PUT ainda cria linha nova; resposta da busca continua sem neighborhood/street de motoristas
- [ ] 6.2 Trocar `PersonalAddressRequestDTO`; `PersonalAddressService` deixa de depender de `AddressPlaceResolverService` (extraído pela PR #173; segue existindo, agora só para escola) e nunca mais chama `PlacesClient` / `LocationResolverService`
- [ ] 6.3 DTO de resposta: `neighborhood`; não exigir `googlePlaceId`
- [ ] 6.4 MessageSource conforme precisar; manter onboarding `PERSONAL_ADDRESS` atrelado a `users.address_id`; reescrever para `cityToken` os testes que montam endereço pessoal por `placeId` (`PersonalAddressControllerTest`, `OnboardingStepsTest`), sem stub de `PlacesClient`; manter `location.address.street_required` nos bundles (a escola ainda a usa via `AddressPlaceResolverService`); o `PUT` com `cityToken` de cidade inativa segue aceito (só o picker filtra por ativas)
- [ ] 6.5 `make lint` + `./mvnw verify`; abrir PR

## 7. Fase 7 — Endereço de embarque do dependente por `cityToken` (PR 7)

> Objetivo: **BREAKING** `POST/PATCH /api/dependent` = mesmo contrato do endereço pessoal (`cityToken` + `street` + `zipCode` + campos postais opcionais); `address` no PATCH é substituição completa, sem amend parcial. Desfaz a regressão a `placeId` da PR #173 (`dependent-address-by-place`, já na `main`) só para dependente — escola continua `placeId`.
> Branch: `feat/ibge-postal-address-dependent` a partir de `feat/ibge-postal-address-personal-address`
> Depende de: 6 | Paralelo com: —

- [ ] 7.1 Testes falhando: unit de `AddressCatalogResolverService.applyCity` (bloco extraído de `PersonalAddressService`: cidade → colunas com `blankToNull`; `district` e `googlePlaceId` nulos; `cityToken` desconhecido → `404` `city.not_found` sem mutar a linha); unit de `AddressService.upsertForDependent(Long, DependentAddressRequestDTO)` (cria linha nova ligada ao dependente; com endereço existente sobrescreve a **mesma** linha, mesmo `address.id`, e `number`/`complement`/`neighborhood` omitidos ficam nulos; nunca chama `PlacesClient`; conflito de posse cruzada com escola continua `409` `address.already_owned`); os casos de escola em `AddressServiceTest` seguem por place, inalterados; slice de `DependentControllerTest`: `201`/`200` com `cityToken`+`street`+CEP de 8 dígitos; CEP omitido/inválido `400`; `street` em branco `400`; `cityToken` desconhecido `404`; `placeId`/`sessionToken` ignorados e sem chamada a Places; `neighborhood` persiste e volta na resposta; `address` parcial (só `number`) em dependente com endereço → `400` e endereço inalterado; `address: null` limpa; DELETE do dependente limpa o endereço; **PATCH só com `name` não altera o endereço existente** (teste nomeado, regra 16 da constituição)
- [ ] 7.2 `DependentAddressRequestDTO` em `address.dto` com `@JsonIgnoreProperties(ignoreUnknown = true)` (`cityToken`/`street`/`zipCode` `@NotBlank`, `street` `@Size(max=255)`, `zipCode` `@Pattern` 8 dígitos, `number`/`complement`/`neighborhood` opcionais com os caps do pessoal)
- [ ] 7.3 Extrair `AddressCatalogResolverService.applyCity(address, cityToken, street, zipCode, number, complement, neighborhood)` de `PersonalAddressService.replaceMyAddress`; `PersonalAddressService` passa a delegar (sem mudança de comportamento — testes da fase 6 continuam verdes sem alteração)
- [ ] 7.4 `AddressService`: `upsertForDependent(Long, DependentAddressRequestDTO)` troca a assinatura do método atual (não é overload novo) e usa o colaborador acima; `upsertOwnedAddress` (privado, compartilhado) deixa de receber `AddressRequestDTO` e passa a receber a escrita por parâmetro (`Consumer<AddressModel>` para linha nova e para linha existente); `upsertForSchool` mantém `applyToNewAddress`/`applyToExistingAddress` e `AddressPlaceResolverService`, sem mudança de comportamento; injetar `AddressCatalogResolverService`
- [ ] 7.5 `AddressResponseDTO` + `AddressMapper` ganham `neighborhood`
- [ ] 7.6 `DependentCreateDTO.address` e `DependentUpdateDTO.address` trocam de `AddressRequestDTO` para `DependentAddressRequestDTO`; `DependentService.applyAddressMerge` e a chamada em `create` ajustam a assinatura; `SchoolService`/`SchoolRequestDTO`/`SchoolUpdateRequestDTO` não mudam
- [ ] 7.7 MessageSource EN + pt-BR: `dependent_address.city_token.required`, `.street.required`, `.street.too_long`, `.zip_code.required`, `.zip_code.invalid`, `.number.too_long`, `.complement.too_long`, `.neighborhood.too_long`
- [ ] 7.8 Migrar `DependentServiceTest` e `DependentControllerTest` para o contrato novo, sem stub de `PlacesClient`
- [ ] 7.9 `make lint` + `./mvnw verify`; abrir PR
