## 0. Phase 0 — Google Cloud / Firebase console setup (BLOQUEIO HUMANO)

> **⛔ NENHUMA FASE DE CÓDIGO PODE COMEÇAR ANTES DESTA ESTAR CONCLUÍDA.**
>
> Esta fase **não pode ser executada por IA nem automatizada**: exige acesso ao console do Google Cloud
> (o mesmo projeto do Firebase), aceite de termos, vínculo de billing e criação de credenciais.
> Executor: **Joao**, manualmente.
>
> Sem as chaves não há como resolver nenhum `placeId`, e o spike da fase 1 depende de chamadas reais
> à API. Toda a change está bloqueada por aqui.
>
> Depends on: — | Parallel with: —

- [ ] 0.1 Abrir o projeto no Google Cloud Console (mesmo projeto do Firebase) e confirmar que há **billing account** vinculada — as APIs do Maps Platform não respondem sem billing, mesmo dentro da cota gratuita
- [ ] 0.2 Habilitar a **Places API (New)** — atenção: é a "(New)", não a legada; os campos `addressComponents` e o contrato de `Place Details` diferem entre as duas
- [ ] 0.3 Habilitar a **Geocoding API** — não é usada na v1 (ver `design.md` D6), habilitar apenas para o spike da fase 1 poder comparar componentes se necessário
- [ ] 0.4 Criar a **chave de servidor** (backend Java), restrita por **endereço IP** e limitada às APIs acima
- [ ] 0.5 Criar a **chave web** (vanep-frontend), restrita por **HTTP referrer**, limitada a Places API (New)
- [ ] 0.6 Criar a **chave mobile** (vanep-mobile), restrita por **package name + SHA-1 / bundle id**, limitada a Places API (New)
- [ ] 0.7 Definir **quota diária** por chave no console, para evitar surpresa de fatura enquanto o volume é desconhecido
- [ ] 0.8 Adicionar ao `.env` local (nunca commitado — constituição regra 4): `GOOGLE_PLACES_API_KEY`, `GOOGLE_PLACES_BASE_URL`
- [ ] 0.9 Documentar os **placeholders** em `.env.example` (sem valores reais) e registrar as propriedades correspondentes em `application.properties` como `${VAR}` — nunca hardcoded (regras 1 e 3)
- [ ] 0.10 Validar manualmente com um `curl` de `Place Details` para um endereço conhecido do DF, confirmando que a chave de servidor responde `200` e traz `addressComponents`
- [ ] 0.11 Comunicar ao time que a fase 0 está concluída e as fases seguintes estão desbloqueadas

## 1. Phase 1 — Spike: mapeamento `types` → nível (PR 1)

> Goal: eliminar o risco R1 do `design.md` com evidência real antes de qualquer migration.
> O Google é inconsistente: em SP `administrative_area_level_2` e `locality` são ambos "São Paulo";
> no DF `locality` ora é "Brasília", ora "Taguatinga". Errar isso faz o match falhar **silenciosamente**.
> Depends on: Phase 0 | Parallel with: —
> Order: coleta → análise → decisão registrada

- [ ] 1.1 Criar branch `feat/location-system-spike` a partir de `main`
- [ ] 1.2 Coletar `addressComponents` crus de ~10 lugares reais: 3 quadras do DF em RAs diferentes (ex.: QNL Taguatinga, Águas Claras, Ceilândia), 2 bairros de São Paulo, 1 escola do DF, 1 escola de SP, 2 cidades do interior, 1 destino não-escola
- [ ] 1.3 Salvar os JSONs crus como fixtures de teste em `src/test/resources/` — servirão para mockar o Places nas fases seguintes sem chamar a API real
- [ ] 1.4 Montar a tabela de mapeamento `types` → nível (`country` / `state` / `city` / `district`), decidindo explicitamente o conflito `administrative_area_level_2` vs `locality`
- [ ] 1.5 Registrar a tabela decidida em `design.md` como decisão **D11**, com as evidências que a sustentam
- [ ] 1.6 Confirmar o próximo número de migration Flyway (última aplicada: `V19`)
- [ ] 1.7 Abrir PR fase 1 (pt-BR, sem código de produção — fixtures + documento de decisão)

## 2. Phase 2 — Árvore geográfica: `district` + migrations (PR 2)

> Goal: schema e modelo da árvore. Sem HTTP, sem Google ainda.
> Depends on: Phase 1 | Parallel with: Phase 3
> Order: test → migration → model → repository

- [ ] 2.1 Testes de repositório para `DistrictRepository` (filho direto de city, filho aninhado, unique parcial, soft delete oculta)
- [ ] 2.2 Migration `V20`: tabela `district` (auto-FK `parent_id`, `city_id`, `name`, `normalized_name`, `google_place_id`, soft delete, unique parcial em `parent_id` + `city_id` + `normalized_name` `WHERE deleted_at IS NULL`)
- [ ] 2.3 Migration `V20` (cont.): adicionar `normalized_name` e `google_place_id` em `state` e `city`
- [ ] 2.4 Criar feature package `br.com.vanep.district` com `model/DistrictModel`, `repository/DistrictRepository` (constituição regra 5)
- [ ] 2.5 Adicionar `findByStateId` em `CityRepository` e `normalizedName` nos models de `state`/`city`
- [ ] 2.6 Implementar utilitário de normalização (unaccent + lowercase) com testes unitários
- [ ] 2.7 `make lint` + `./mvnw verify`; abrir PR fase 2

## 3. Phase 3 — Client do Google Places (PR 3)

> Goal: acesso HTTP ao Places com config por env e cache. Nenhum teste chama a API real.
> Depends on: Phase 1 | Parallel with: Phase 2
> Order: test → config → DTOs → client

- [ ] 3.1 Testes unitários do `PlacesClient` usando as fixtures da tarefa 1.3 (resposta OK, place inexistente, erro 4xx/5xx, timeout)
- [ ] 3.2 Criar feature package `br.com.vanep.places`
- [ ] 3.3 Adicionar propriedades `vanep.google.places.api-key` e `vanep.google.places.base-url` em `application.properties` como `${VAR}`; documentar em `.env.example` (regras 1 e 3)
- [ ] 3.4 DTOs de resposta do Places (`PlaceDetailsResponseDTO`, `AddressComponentDTO`) — apenas os campos usados
- [ ] 3.5 Implementar `PlacesClient` com `RestClient`, timeout explícito e tratamento de erro que lança exceção significativa (regra 30) — nunca `catch` vazio
- [ ] 3.6 Adicionar cache em memória (Caffeine) de `Place Details` por `placeId`, com TTL configurável por env
- [ ] 3.7 `make lint` + `./mvnw verify`; abrir PR fase 3

## 4. Phase 4 — Resolver da árvore (PR 4)

> Goal: o coração da change — `addressComponents` → nó da árvore, nos dois modos (escrita e leitura).
> Depends on: Phase 2, Phase 3 | Parallel with: —
> Order: test → resolver → âncora → ancestrais

- [ ] 4.1 Testes unitários de `LocationResolverService.resolveAndPersist` (cria cadeia nova; reusa cadeia existente; idempotência; país casado por ISO `BR`; país não suportado → erro de negócio)
- [ ] 4.2 Testes unitários da regra D2: place "Taguatinga Norte" cujos componentes trazem "Taguatinga" ancora em "Taguatinga" e **não** cria nó "Taguatinga Norte"
- [ ] 4.3 Testes unitários de `resolveAnchor` (read-only): componentes mais profundos que a árvore param no nó mais profundo existente e **nenhum insert** ocorre
- [ ] 4.4 Testes unitários de `findAncestors(districtId)` subindo por `parent_id`
- [ ] 4.5 Implementar mapeamento `types` → nível conforme a decisão D11 registrada na fase 1
- [ ] 4.6 Implementar `LocationResolverService` (`resolveAndPersist` e `resolveAnchor`), sem detalhes de web ou JPA vazando para a regra (regra 9)
- [ ] 4.7 Adicionar MessageSource keys (EN + `messages_pt_BR.properties`) para place não resolvido e país não suportado (regra 45)
- [ ] 4.8 `make lint` + `./mvnw verify`; abrir PR fase 4

## 5. Phase 5 — Endereço pessoal (PR 5)

> Goal: endereço residencial real, privado, ligado à árvore, disponível a todos os papéis.
> Depends on: Phase 4 | Parallel with: Phase 6, Phase 7
> Order: test → migration → model → security → request DTO → service → controller → response DTO

- [ ] 5.1 Testes slice MockMvc: `401` sem token; `200` criando endereço a partir de `placeId`; `400` para `placeId` inválido; `complement` preservado; componentes enviados pelo cliente ignorados
- [ ] 5.2 Migration `V21`: `address` ganha `district_id` FK e `google_place_id`; **remove** `district varchar(128)`; cria as FKs faltantes de `school.address_id` e `dependent.address_id`; adiciona `assistant.address_id`
- [ ] 5.3 Atualizar `AddressModel` (FK de district, `google_place_id`) e o model de `assistant`
- [ ] 5.4 Declarar as regras de autorização em `SecurityConfig` para os endpoints novos (regras 19 e 20)
- [ ] 5.5 Criar `PersonalAddressRequestDTO` (`placeId` `@NotBlank`, `number?`, `complement?`) com Bean Validation (regra 10)
- [ ] 5.6 Implementar o service que resolve o place, persiste a cadeia e grava o endereço do chamador
- [ ] 5.7 Adicionar `PUT /api/user/me/address` e `GET /api/user/me/address` com `@Valid` + `isAuthenticated()`
- [ ] 5.8 Criar `PersonalAddressResponseDTO` expondo apenas tokens opacos (regras 12 e 13)
- [ ] 5.9 Remover `AddressSeeder`
- [ ] 5.10 `make lint` + `./mvnw verify`; abrir PR fase 5

## 6. Phase 6 — Áreas de atuação do motorista (PR 6)

> Goal: a região pública onde o motorista trabalha, com a validação D8.
> Depends on: Phase 4 | Parallel with: Phase 5, Phase 7
> Order: test → migration → model → repository → security → request DTO → service → controller → response DTO

- [ ] 6.1 Testes unitários da validação D8 (cidade com distritos → `400`; cidade sem distritos → aceita)
- [ ] 6.2 Testes slice MockMvc: `401` sem token; `403` para usuário sem perfil de motorista; `200` cadastrando distrito; substituição completa do conjunto no `PUT`
- [ ] 6.3 Migration `V22`: tabela `driver_service_area` (`driver_id`, `city_id` NOT NULL, `district_id` nullable, soft delete) — **sem** colunas de logradouro
- [ ] 6.4 Criar feature package `br.com.vanep.driverservicearea` com model e repository
- [ ] 6.5 Declarar as regras de autorização em `SecurityConfig`; se precisar de checagem de dono, adicionar ao `SecurityEvaluator` (`@sec`) — nunca criar `*SecurityService` por feature (regra 21)
- [ ] 6.6 Criar `DriverServiceAreaRequestDTO` (lista de `placeId`) com Bean Validation
- [ ] 6.7 Implementar o service (resolve cada place, aplica D8, substitui o conjunto do motorista)
- [ ] 6.8 Adicionar `GET` e `PUT /api/drivers/me/service-areas`
- [ ] 6.9 Criar `DriverServiceAreaResponseDTO` com nome da região + token opaco
- [ ] 6.10 Adicionar MessageSource keys (EN + pt-BR) para "distrito obrigatório nesta cidade"
- [ ] 6.11 `make lint` + `./mvnw verify`; abrir PR fase 6

## 7. Phase 7 — Escola magra (PR 7)

> Goal: `school` deixa de ser seed fictício e nasce de um place do Google.
> Depends on: Phase 4 | Parallel with: Phase 5, Phase 6
> Order: test → migration → model → service → controller → response DTO

- [ ] 7.1 Testes slice: primeira resolução cria a escola; segunda reusa sem duplicar; `401` sem token
- [ ] 7.2 Migration `V23`: `school` ganha `google_place_id` (unique parcial `WHERE deleted_at IS NULL`), `city_id`, `district_id`; **remove** `cnpj`, `phone`, `email`
- [ ] 7.3 Atualizar `SchoolModel`, `SchoolRepository` (busca por `google_place_id`), mapper e DTOs
- [ ] 7.4 Implementar `GET /api/schools/resolve` (resolve `placeId` → `school` persistida)
- [ ] 7.5 Remover `SchoolSeeder`
- [ ] 7.6 Ajustar os testes existentes de `school` e `dependent` que dependiam de `cnpj`/`phone`/`email`
- [ ] 7.7 `make lint` + `./mvnw verify`; abrir PR fase 7

## 8. Phase 8 — Busca de motoristas por origem + destino (PR 8)

> Goal: o requisito central do produto. Query de contenção que roda em H2.
> Depends on: Phase 5, Phase 6, Phase 7 | Parallel with: —
> Order: test → repository query → service → controller → response DTO

- [ ] 8.1 Testes de repositório em H2 cobrindo os cenários da spec: cobre ambos; cobre só um (exclui); área ampla cobre ponto profundo; distrito irmão não casa; cidade diferente não casa
- [ ] 8.2 Teste explícito de que a query **executa em H2** sem extensão espacial
- [ ] 8.3 Testes slice MockMvc: `200` com resultados paginados; destino não-escola aceito; `400` para `placeId` inválido; `401` sem token
- [ ] 8.4 Teste garantindo que a busca **não cria** nós na árvore (assert de contagem antes/depois)
- [ ] 8.5 Implementar a query de contenção em `DriverServiceAreaRepository` (`city_id` + `district_id IS NULL OR district_id IN (:ancestors)`), com fetch join para evitar N+1 (regra 16)
- [ ] 8.6 Implementar o service de busca (resolve as duas âncoras, intersecta os conjuntos de motoristas)
- [ ] 8.7 Adicionar `GET /api/drivers/search` com paginação e regra de autorização declarada
- [ ] 8.8 Criar `DriverSearchResponseDTO` — sem nenhum campo de endereço residencial (regra de privacidade da spec)
- [ ] 8.9 `make lint` + `./mvnw verify`; abrir PR fase 8

## 9. Phase 9 — Onboarding + limpeza (PR 9)

> Goal: expor o que falta no cadastro e remover o catálogo antigo.
> Depends on: Phase 8 | Parallel with: —
> Order: test → migration → enum → service → response DTO → remoções

- [ ] 9.1 Testes slice de `GET /api/user/me`: motorista sem nada → `[PERSONAL_ADDRESS, SERVICE_AREA]`; motorista só sem áreas → `[SERVICE_AREA]`; cliente sem endereço → `[PERSONAL_ADDRESS]` e nunca `SERVICE_AREA`; totalmente cadastrado → lista vazia
- [ ] 9.2 Criar enum backed `OnboardingStep` (regra 14)
- [ ] 9.3 Estender `UserMeResponseDTO` com o objeto `onboarding.pendingSteps`
- [ ] 9.4 Implementar o cálculo dos passos pendentes no service, sem N+1 (regra 16)
- [ ] 9.5 Migration `V24`: remover `driver.city` e `driver.service_areas`
- [ ] 9.6 Remover os campos correspondentes de `DriverModel`, `DriverUpdateRequestDTO`, `DriverResponseDTO`, `DriverMeSummaryResponseDTO` e `DriverService`
- [ ] 9.7 Remover `CitySeeder` e `StateSeeder`; ajustar `SeedRunner` (ou equivalente) para não referenciá-los
- [ ] 9.8 Remover `POST`/`PUT`/`DELETE`/`restore` de `CityController` e os métodos de escrita de `CityService` (a fonte passa a ser o Google); manter as leituras
- [ ] 9.9 Deletar código morto resultante (regra 33) e ajustar os testes existentes que dependiam dos seeders
- [ ] 9.10 Atualizar a spec main `openspec/specs/country-crud/spec.md` para refletir que `country` é o único nível curado
- [ ] 9.11 `make lint` + `./mvnw verify`; abrir PR fase 9

## 10. Encerramento

- [ ] 10.1 Confirmar que os 5 specs da change foram cobertos pelas fases entregues
- [ ] 10.2 Rodar `./mvnw verify` completo e confirmar a cobertura mínima do JaCoCo (regra 23)
- [ ] 10.3 Sincronizar as specs para `openspec/specs/` (`/opsx:sync`) e arquivar a change (`/opsx:archive`)
