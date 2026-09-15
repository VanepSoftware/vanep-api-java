## 1. Fase 1 — Schema (PR 1)

> Objetivo: `ibge_code` em `city`, `neighborhood` em `address`; dropar `google_place_id` não usado em `city` e `state`. Sem HTTP ainda.
> Depende de: — | Paralelo com: —

- [ ] 1.1 Testes de repositório falhando: `ibge_code` único entre cidades ativas; `neighborhood` persiste em `address`; persistir/carregar `city` e `state` sem mapeamento de `googlePlaceId`
- [ ] 1.2 Migration `V34` (não editar arquivos já aplicados): `city.ibge_code varchar(7)` + índice unique parcial `WHERE deleted_at IS NULL`; `address.neighborhood varchar(128)` nullable; `DROP INDEX` `city_google_place_id_active_key` e `state_google_place_id_active_key`; `DROP COLUMN` `city.google_place_id` e `state.google_place_id`. Deixar `district.google_place_id`, `address.google_place_id` e `school.google_place_id`
- [ ] 1.3 Atualizar `CityModel` e `StateModel` (remover `googlePlaceId`); dropar `CityRepository.findByGooglePlaceId`; adicionar `ibgeCode` em `CityModel` e `neighborhood` em `AddressModel`; aplicar `V34` no Postgres local
- [ ] 1.4 `make lint` + testes desta fase; abrir PR

## 2. Fase 2 — Seeder do catálogo IBGE (PR 2)

> Objetivo: todo município brasileiro é uma linha de `city`. Testes nunca chamam IBGE.
> Depende de: 1 | Paralelo com: —

- [ ] 2.1 **Uma vez, na mão:** baixar o JSON IBGE `localidades/municipios` e commitar em `src/main/resources/seed/ibge-municipios.json` (regra 50 — não pela suíte de testes)
- [ ] 2.2 Testes unitários falhando do `CitySeeder` com fixture **pequena** commitada (Brasília + outro município): cria linhas com `ibge_code`; segunda execução é idempotente; mapeia UF para o `state` existente
- [ ] 2.3 Implementar `CitySeeder` depois do `StateSeeder` no `DataSeeder`; upsert por `ibge_code`; nunca HTTP para IBGE em runtime
- [ ] 2.4 Ligar `vanep.seed.enabled` para local/prod carregar o dump completo; testes seguem com fixture pequena / inserts explícitos
- [ ] 2.5 `make lint` + `./mvnw verify`; abrir PR

## 3. Fase 3 — Picker autenticado em `/api/states` e `/api/cities` (PR 3)

> Objetivo: lista de UFs + cidades por `uf` + `search` opcional. Sem `/api/geo`. Auth `isAuthenticated()`.
> Depende de: 2 | Paralelo com: 4

- [ ] 3.1 Testes slice falhando: `401` sem token; `GET /api/states` devolve UFs para cliente autenticado (sem `list_states`); `GET /api/cities?uf=DF&search=brasilia` devolve token de Brasília; sem `uf` → `400`; UF desconhecida `404`; `search` omitido lista a UF paginada
- [ ] 3.2 Chaves MessageSource EN + `messages_pt_BR.properties` para UF desconhecida e `uf` ausente
- [ ] 3.3 `CityController` / `StateController`: `@PreAuthorize("isAuthenticated()")`; `GET /api/cities` exige `uf`; `search` opcional em `normalized_name`; DTOs com tokens opacos. Sem `CityCatalogController`
- [ ] 3.4 Declarar autorização no `SecurityConfig` se preciso (regras 20–21). `list_cities` / `list_states` não são mais exigidas nesses GETs
- [ ] 3.5 Atualizar testes admin existentes de city/state que esperavam `403` sem `list_cities` / `list_states`
- [ ] 3.6 `make lint` + testes desta fase; abrir PR

## 4. Fase 4 — Google casa cidade IBGE, nunca cria cidade (PR 4)

> Objetivo: `findOrCreateCity` some. Miss → 400 + log. Distrito continua lazy. Miss na busca ≠ página vazia.
> Depende de: 2 | Paralelo com: 3

- [ ] 4.1 Testes unitários falhando: persistir sob Brasília existente cria só Taguatinga; persistir com nome de cidade `Embu` sob SP (sem esse `normalized_name`) lança e não insere nada; `resolveAnchor` lança em cidade sem match em vez de optional vazio
- [ ] 4.2 Slice falhando: `PUT` área de atuação / `POST` school resolve / `GET` busca de motorista com componente de cidade sem match → `400` MessageSource; busca com cidade casada e sem motoristas continua `200` página vazia
- [ ] 4.3 Chave MessageSource para cidade Google sem correspondência no IBGE (EN + pt-BR); logar UF, nome Google da cidade, place id
- [ ] 4.4 Mudar o caminho de cidade do `LocationResolverService` para só find; manter `findOrCreateDistrict`; mapear a exceção nos services de busca/escola/área de atuação
- [ ] 4.5 Atualizar testes existentes de resolver/busca/escola/área de atuação que assumiam que o Google criava `city` — MUST inserir a cidade IBGE antes (Brasília / São Paulo)
- [ ] 4.6 `make lint` + `./mvnw verify`; abrir PR

## 5. Fase 5 — Lookup ViaCEP (PR 5)

> Objetivo: `GET /api/cep/{cep}`. PUT ainda não chama ViaCEP.
> Depende de: 2 | Paralelo com: 6

- [ ] 5.1 Commitar fixtures JSON gravadas do ViaCEP em `src/test/resources`; cravar `vanep.viacep.base-url` em `http://localhost:1/...` no `application-test.properties` (regra 50)
- [ ] 5.2 Testes unitários falhando do `ViaCepClient`: mapeia `ibge` `5300108`; `{ "erro": true }` → não encontrado; falha de conexão → exceção de lookup
- [ ] 5.3 Slice falhando: `401`; CEP de oito dígitos de Brasília `200` + `cityToken`; formato inválido `400`; ViaCEP desconhecido `404`; transporte `503`; `ibge_code` ausente no catálogo `404`; rate limit `429` sem chamar ViaCEP
- [ ] 5.4 Config env + `.env.example`: `vanep.viacep.base-url`, timeout, knobs de rate-limit (regras 1/3)
- [ ] 5.5 Implementar client (`RestClient`), service, `CepLookupController`, bean de rate limiter, chaves MessageSource
- [ ] 5.6 `make lint` + testes desta fase; abrir PR

## 6. Fase 6 — Endereço pessoal (PR 6)

> Objetivo: **BREAKING** `PUT /api/user/me/address` = `cityToken` + `street` + `zipCode` (8 dígitos) + campos postais opcionais. Sem Places neste caminho.
> Depende de: 1 | Paralelo com: 5

- [ ] 6.1 Slice falhando: `200` com `cityToken` + street + CEP de 8 dígitos; CEP omitido `400`; CEP inválido `400`; street em branco `400`; token desconhecido `404` `city.not_found`; `cityName` extra ignorado; `placeId` não é aceito como contrato; `neighborhood` persiste e volta; `district_id` e `google_place_id` null; `401`; DELETE + PUT ainda cria linha nova; resposta da busca continua sem neighborhood/street de motoristas
- [ ] 6.2 Trocar `PersonalAddressRequestDTO`; parar de chamar `PlacesClient` / `LocationResolverService` no `PersonalAddressService`
- [ ] 6.3 DTO de resposta: `neighborhood`; não exigir `googlePlaceId`
- [ ] 6.4 MessageSource conforme precisar; manter onboarding `PERSONAL_ADDRESS` atrelado a `users.address_id`
- [ ] 6.5 `make lint` + `./mvnw verify`; abrir PR
