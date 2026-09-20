## Why

O `PUT /api/user/me/address` trata o Google Places como fonte da verdade postal: a pessoa só manda `placeId`, não edita rua/CEP, e o backend grava o que o Place Details devolver. No Brasil isso é inconsistente (quadra do DF sem `route`, CEP errado, grafia de Maps) e impede o fluxo comum (CEP → form). Recusar município fora da praça no form afasta quem ainda não tem oferta. Prioridade: **Alta** — o endereço pessoal entra no contrato cliente–motorista e precisa ser um documento postal editável.

## What Changes

- **Município IBGE como catálogo de `city`.** Seed dos ~5.570 municípios com `ibge_code` único. UF continua o `StateSeeder`. A pessoa de qualquer município brasileiro cadastra endereço; “não operamos aí” é busca vazia + copy, não HTTP 400 no form.
- **Picker nacional:** `GET /api/states` e `GET /api/cities?uf=&search=` autenticados (`isAuthenticated()`). `uf` obrigatório na lista de cidades; `search` opcional (nome normalizado). Lista fechada, nunca texto livre. Sem `/api/geo`. Permissões `list_cities` / `list_states` deixam de guardar esses GETs.
- **`GET /api/cep/{cep}`** no backend: ViaCEP → casa `ibge` com `city.ibge_code` → devolve `cityToken` + prefill. Lookup é atalho; **o PUT não chama ViaCEP**. Fora do ar / CEP desconhecido → o app cai no picker. Testes stubam o client (regra 50).
- **Endereço pessoal (C). BREAKING** em `PUT /api/user/me/address`: deixa de exigir `placeId`. Passa a exigir `cityToken` + `street` + `zipCode` (8 dígitos). `number`, `complement`, `neighborhood` opcionais. `district_id` e `google_place_id` ficam nulos neste fluxo. Coluna `address.zip_code` continua nullable (V25; tabela compartilhada). UF/cidade na tela são rótulo do catálogo; o save manda só o token.
- **Google no resto, sem criar cidade.** `LocationResolverService` casa `administrative_area_level_2` / `localidade` com `(UF, normalized_name)` na linha IBGE e **não** faz `findOrCreate` de `city`. Sem match → erro de negócio visível (MessageSource), em escrita **e** na busca — não página vazia silenciosa. Sem tabela de alias nesta change. `district` continua lazy do Places. Escola, área de atuação e busca de motorista continuam Places.
- **Drop de `google_place_id` morto em `city` e `state`.** Place Details só traz `place_id` do pin escolhido, não por componente; o resolver nunca gravou o campo (match é UF / `normalized_name`). Unique 1:1 também não é o gancho de um Geocoding futuro (alias é N→1). `district`, `address` e `school` **mantêm** a coluna.
- **Endereço de embarque do dependente (fase 7). BREAKING** em `POST/PATCH /api/dependent`: a PR #173 (`dependent-address-by-place`) tinha regredido `AddressRequestDTO` para `placeId`/Google porque o app não conseguia obter um `cityToken` — `GET /api/cities` era 403 pro papel CLIENT e não tinha busca por nome. As fases 2–3 desta change resolvem exatamente esse buraco (catálogo IBGE + picker `isAuthenticated()`). A fase 7 volta o endereço de embarque do dependente para o mesmo contrato do endereço pessoal (`cityToken` + `street` + `zipCode`), usando um DTO próprio (`DependentAddressRequestDTO`) para não reabrir o contrato compartilhado com escola.
- **Fora de escopo:** Geocoding / mini-mapa; alias Google↔IBGE; endereço de **escola** (continua `placeId`/Google via `AddressService` + `AddressPlaceResolverService`; escola não tem tela no app cliente hoje, sem pressão para migrar); importar distrito IBGE; frontend/mobile; `placeId` no endereço pessoal “para o futuro”.

## Capabilities

### New Capabilities

- `ibge-city-catalog`: municípios brasileiros curados (`ibge_code`), seeder a partir de dump no repo, `GET /api/states` e `GET /api/cities?uf=&search=` autenticados.
- `cep-lookup`: `GET /api/cep/{cep}` autenticado, ViaCEP stubável, match por código IBGE, sem gravar endereço.
- `dependent-owned-address`: endereço de embarque do dependente por `cityToken` (fase 7), substituindo a parte de dependente do `place-backed-owned-address` proposto em `dependent-address-by-place`.

### Modified Capabilities

- `personal-address`: contrato postal (`cityToken` + form), não mais Place Details. (Baseline: `openspec/changes/location-system/specs/personal-address/spec.md` — ainda não arquivada em `openspec/specs/`.)
- `geography-tree`: `city` deixa de ser lazy do Google; passa a ser catálogo IBGE. Distrito permanece lazy. Resolver recusa município Google sem linha IBGE. `city` e `state` perdem `google_place_id`. (Baseline: `openspec/changes/location-system/specs/geography-tree/spec.md`.)
- `driver-location-search`: município Google sem match IBGE deixa de virar página vazia e passa a HTTP 400. Cidade válida sem motorista continua lista vazia.
- `place-backed-owned-address` (de `dependent-address-by-place`): perde o dependente. Passa a cobrir só escola; os requirements e cenários de dependente daquele documento são substituídos pelos de `dependent-owned-address` nesta change (fase 7).

## Impact

- **Código:** `CitySeeder` + dump JSON; `CityModel.ibgeCode`; remover `googlePlaceId` de `CityModel` / `StateModel` e `CityRepository.findByGooglePlaceId`; busca de cidades; `ViaCepClient` (`RestClient`); `CepLookupController`; `PersonalAddressRequestDTO` / `PersonalAddressService`; `LocationResolverService.findOrCreateCity` vira match-only; MessageSource; `application-test.properties` com URL ViaCEP inroteável. Fase 7: `DependentAddressRequestDTO` (novo); `AddressCatalogResolverService` (novo colaborador, reaproveitado por `PersonalAddressService`); `AddressService.upsertForDependent` troca de `AddressRequestDTO` para `DependentAddressRequestDTO`; `AddressResponseDTO`/`AddressMapper` ganham `neighborhood`.
- **Schema:** próxima Flyway (`V34` se a `main` estiver em `V33`): `city.ibge_code`; `address.neighborhood`; drop `city.google_place_id` e `state.google_place_id` (e os unique parciais). Não editar migrations aplicadas (regra 2). Não é breaking de API — admin nunca expôs o campo.
- **API BREAKING:** `PUT /api/user/me/address` — `placeId` deixa de ser o contrato; clientes que só mandam place quebram até o app novo. `GET /api/cities` passa a exigir `uf` e deixa de exigir `list_cities`; `GET /api/states` deixa de exigir `list_states`.
- **Deps:** HTTP ViaCEP; URL e timeout via env (regras 1/3). Sem SDK.
- **Auth:** CEP lookup, lista de UF/cidades e endereço pessoal com `isAuthenticated()`. Rate limit no CEP (abuso do ViaCEP).
- **Testes:** unit + slice; nenhum teste chama IBGE, ViaCEP ou Google de verdade (regra 50). Dump completo só no seeder de app; testes usam fixture pequena.
- **Delivery:** fases = PRs (constituição 36–43).
