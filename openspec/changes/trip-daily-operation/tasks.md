> **Pilha de branches.** As fases são empilhadas: cada branch nasce de dentro da anterior e cada PR
> aponta a anterior como `--base`. A fase 1 é a única que sai de `main`. Depois de todas subirem, o
> GitHub mostra "preview stack" no topo de qualquer uma delas; criar a stack ali e mergear pela
> **última** PR (`merge stack`) integra tudo de uma vez.
>
> ```
> main ─ …-schema ─ …-policies ─ …-service ─ …-driver-http ─ …-admin-crud
>      PR 1 → main  PR 2 → PR 1  PR 3 → PR 2   PR 4 → PR 3    PR 5 → PR 4
> ```

## 0. Preparation

- [x] 0.1 Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] 0.2 Revisar `proposal.md`, `design.md` e os specs (`trip-lifecycle`, `trip-daily-query`, `trip-admin-crud`)
- [x] 0.3 **Confirmar o próximo número de Flyway.** ✅ **`V31` livre.** Verificado não só na `main` (`V30__client_can_read_drivers.sql`) mas em **todas as branches remotas** com `git ls-tree` — nenhuma carrega `V31`. Reconfirmar se a pilha ficar aberta por dias
- [ ] 0.4 Confirmar com o time a decisão **D1** (`COMPLETED`, não `FINISHED` como está escrito na issue #150) e corrigir o texto da issue, para que o critério de aceite não contradiga o schema entregue

## 1. Phase 1 — `Shift` partilhado, `TripStatus`, V31, model e repository (PR 1)

> Goal: a tabela e o tipo existem, com a unicidade garantida pelo banco. Sem HTTP, sem regra de transição, sem serviço.
> Depends on: — | Parallel with: —
> Order: test → migration → model → repository

- [x] 1.1 Criar branch `feat/trip-daily-operation-schema` a partir de `main`
- [x] 1.2 Testes de repositório para `TripRepository`: busca por motorista + data + turno; ausência devolve vazio; trip de ontem não é devolvida como a de hoje; duas trips do mesmo dia em turnos diferentes coexistem; dois motoristas no mesmo turno coexistem; token opaco gerado no persist; status default `SCHEDULED`. **7 testes passando.** Dois achados:
    - ~~teste de que a segunda trip igual é rejeitada pelo índice único~~ — **removido**: passou sem lançar nada. H2 não roda Flyway, e declarar `uniqueConstraints` no `@Table` só para o teste está descartado pela mesma razão que o `owned-address-model` registrou na tarefa 1.1 ("do not add a test-only unique index"). A garantia fica na tarefa 1.6 (R4 atualizado)
    - ordenar `findByDriverAndServiceDate` por `shift` devolveu `AFTERNOON` antes de `MORNING` — com `@Enumerated(STRING)` o banco ordena por nome. Trocado para `order by trip.id`, que é a ordem em que o motorista iniciou os turnos (D10)
- [x] 1.3 **Promover o enum `Shift`** de `br.com.vanep.dependent.enums` para área partilhada — passa a ser usado por `dependent` e `trip`, e a regra 5 manda promover em vez de duplicar. Ajustar imports em `DependentModel`, `DependentMapper` e DTOs de dependent; é refactor mecânico, sem mudança de comportamento (R5)
- [x] 1.4 Criar `TripStatus` com `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` (regra 14). `CANCELLED` entra agora mesmo sem endpoint que o produza: acrescentar valor ao enum Java é trivial, mas o `check` da coluna exige migration nova (D1)
- [x] 1.5 Migration `V31__create_trip_table.sql`: `id`, `token`, `driver_id` (FK → `driver(id)`), `service_date`, `shift`, `status` default `SCHEDULED`, `started_at`, `finished_at`, `created_at`, `updated_at`, **`deleted_at`** (D4 revisto). Índices únicos **parciais** (`where deleted_at is null`) em `token` e em `(driver_id, service_date, shift)` — sem o parcial, uma trip removida pelo admin travaria aquele slot para sempre
- [x] 1.6 **Aplicar a `V31` manualmente contra o PostgreSQL local e verificar o índice** ✅ **Feito contra PostgreSQL 17.** Flyway aplicou as 31 migrations do zero. Três verificações: (a) segunda trip no mesmo `(driver, service_date, shift)` ativo → `duplicate key value violates unique constraint "trip_driver_service_date_shift_active_key"`; (b) mesmo dia em turno diferente → aceito; (c) slot soft-deletado e recriado → aceito, provando que o `WHERE deleted_at IS NULL` evita a trava permanente do D4. Original: inserindo duas trips com o mesmo `(driver_id, service_date, shift)` — a suíte roda com `flyway.enabled=false` e `ddl-auto=create-drop`, então nenhum teste executa esta migration (R4, mesmo procedimento da tarefa 2.6 do `location-system`)
- [x] 1.7 Criar pacote `br.com.vanep.trip` com `model/TripModel` anotado com `@SoftDelete(columnName = "deleted_at", strategy = TIMESTAMP)` como todo model removível (regra 19). **`CANCELLED` e `deleted_at` são coisas diferentes** e coexistem: cancelada é rota que não vai acontecer e continua visível; removida é linha que não deveria existir e some (D4)
- [x] 1.8 Criar `repository/TripRepository` com busca por `(driver, serviceDate, shift)`, por `(driver, serviceDate)` e por `token`; usar fetch join para o motorista e o usuário dele (regra 17 — `DriverModel.user` é `EAGER`, então sem o fetch join uma lista de N trips dispara N queries extras)
- [x] 1.9 Acrescentar `trip` ao `src/test/resources/db/clean.sql` na ordem correta de FK — delete físico é permitido em limpeza de teste (regra 19)
- [x] 1.10 `make lint` + `./mvnw verify -B` (comando exato do CI): **772 testes, 0 falhas, JaCoCo dentro do mínimo, Spotless OK, BUILD SUCCESS**. PR ainda não aberta (aguardando commit)
    - ~~Retirado `TripRepository.findByToken` por ser código morto (regra 34)~~ — **devolvido**: o CRUD administrativo da fase 5 endereça trips por token, então o método passou a ter consumidor
    - ⚠️ **Correção fora do escopo desta change, candidata a PR separada na base da pilha:** `RateLimiterTest.expiredWindowResets` e `LoginAttemptServiceTest.expiredWindowUnblocks` construíam a janela com `0`. A expiração é `now.isAfter(start.plus(window))`, então zero só expira se o relógio andar entre as duas linhas — anda no Linux do CI, não anda no Windows, onde dois `Instant.now()` consecutivos são idênticos. Trocado para janela negativa, que é determinística em qualquer relógio. **O CI da `main` estava verde**; a falha era só local

## 2. Phase 2 — Policies puras: transição e janela de expediente (PR 2)

> Goal: as duas regras de negócio decidem sozinhas, sem Spring, JPA ou servlet. Nada é fiado ainda.
> Depends on: Phase 1 | Parallel with: —
> Order: test → policy

- [ ] 2.1 Criar branch `feat/trip-daily-operation-policies` **de dentro de** `feat/trip-daily-operation-schema`
- [ ] 2.2 Teste de `TripTransitionPolicy` cobrindo **a matriz inteira**: os 16 pares ordenados de `TripStatus`, aceitando só `SCHEDULED → IN_PROGRESS` e `IN_PROGRESS → COMPLETED`. Rodar sem contexto Spring e sem persistência — se este teste precisar de banco, a policy não é pura e o D-da-transição não está provado
- [ ] 2.3 Implementar `service/TripTransitionPolicy` recebendo estado atual e alvo, devolvendo a legalidade da transição. Sem `@Service` com dependência de repositório; sem tipo de framework na assinatura (regra 9)
- [ ] 2.4 Teste de `WorkWindowPolicy`: antes do `workStartTime` → fora; depois do `workEndTime` → fora; dia não incluído em `workDays` → fora; dentro → dentro. **E os casos de configuração ausente** — `workDays` nulo, `workDays` vazio, `workDays` malformado, `workStartTime` nulo — todos devolvem "não está fora", porque ausência de configuração não é evidência de estar fora da janela (R3)
- [ ] 2.5 Implementar `service/WorkWindowPolicy` recebendo `workDays`, `workStartTime`, `workEndTime` e o instante do início. Ela **classifica, não bloqueia** (D2): nenhum caminho dela lança exceção de negócio
- [ ] 2.6 `make lint` + `./mvnw verify`; abrir PR fase 2 apontando `--base feat/trip-daily-operation-schema`

## 3. Phase 3 — Serviço, permissões e gate RN-02 (PR 3)

> Goal: iniciar, finalizar e consultar funcionam pela camada de serviço, com autorização. Sem controller ainda.
> Depends on: Phase 2 | Parallel with: —
> Order: test → security/authorization → messages → service

- [ ] 3.1 Criar branch `feat/trip-daily-operation-service` **de dentro de** `feat/trip-daily-operation-policies`
- [ ] 3.2 Testes unitários de `TripService` (Mockito): início cria com `IN_PROGRESS` e `started_at`; **segundo início devolve a mesma trip sem reescrever `started_at`**; finalizar grava `finished_at` e `COMPLETED`; segundo finish é idempotente; finalizar sem trip → 409 `trip.not_started`; iniciar sobre `COMPLETED` → 409 `trip.already_completed`; motorista `PENDING` → 403 `driver.not_approved`; usuário `CLIENT` → 403
- [ ] 3.3 **Teste da corrida do início** (R1) — ⚠️ **reformulado pela fase 1.** A versão original ("dois inícios concorrentes produzem uma linha só") é impossível na suíte: H2 não tem o índice único, então nada é violado e nada é lançado. O teste possível é unitário com o repositório mockado lançando `DataIntegrityViolationException` no `save`, assertando que o serviço **relê e devolve a trip vencedora** em vez de propagar `500`. A garantia do banco em si é a tarefa 1.6, manual
- [ ] 3.4 Acrescentar `START_TRIP("start_trip")` e `FINISH_TRIP("finish_trip")` ao `PermissionEnum` e ao bundle `DRIVER` do seeder. As permissões de CRUD (`list/show/create/update/delete/restore_trip`) entram na fase 5, junto das rotas que as usam — permissão sem rota é string morta (regra 34)
- [ ] 3.5 **Não acrescentar método a `SecurityEvaluator` nesta fase.** Os três endpoints do motorista são `/me`: ele sai do `Authentication`, não de um token na URL, então não há dono a comparar — mesma conclusão do PR #135 para `driver_service_area`. O `isTripOwner` só nasce na fase 5, onde o CRUD endereça por token (D9 revisto pelo D11)
- [ ] 3.6 Chaves de MessageSource em `messages.properties` (EN) e `messages_pt_BR.properties` (pt-BR servido): `trip.not_started`, `trip.already_completed`, `driver.not_approved`. Nunca hardcodar a string pt-BR no ponto do `throw` (regra 46)
- [ ] 3.7 Implementar `TripService.startToday`: resolver o motorista com `requireByTokenAndType(uid, UserType.DRIVER)`, aplicar o gate RN-02 (`approvalStatus == APPROVED`), derivar `service_date` do relógio do servidor em `America/Sao_Paulo` — **nunca do cliente** (D7), e fixar o zone id explicitamente porque o container roda UTC e `LocalDate.now()` viraria o dia às 21h local, no meio do turno vespertino
- [ ] 3.8 No `startToday`, implementar a idempotência como **insert-then-catch**: tentar inserir, capturar `DataIntegrityViolationException` e reler a linha vencedora. Não usar "consultar, se não achar inserir" — entre a consulta e o insert cabe outra requisição, e o duplo toque produziria duas trips ou um `500` (D5)
- [ ] 3.9 Implementar `TripService.finishToday` (consulta `TripTransitionPolicy` antes de gravar; **nenhuma escrita contra checklist**, D3) e `TripService.findToday`
- [ ] 3.10 `make lint` + `./mvnw verify`; abrir PR fase 3 apontando `--base feat/trip-daily-operation-policies`

## 4. Phase 4 — Superfície HTTP do motorista (PR 4)

> Goal: os três endpoints `/me` do D9 respondem. Sem CRUD administrativo ainda.
> Depends on: Phase 3 | Parallel with: —
> Order: test → request DTO → controller → response DTO → mapper

- [ ] 4.1 Criar branch `feat/trip-daily-operation-driver-http` **de dentro de** `feat/trip-daily-operation-service`
- [ ] 4.2 Testes MockMvc de `TripController` com segurança: sem JWT → 401; JWT de `CLIENT` → 403; motorista aprovado inicia → **201**; inicia de novo → **200** com o mesmo token; finaliza → 200; consulta com uma trip → 200 com 1 elemento; **consulta com dois turnos no dia → 200 com 2 elementos, matutino primeiro**; consulta sem trip → **200 com lista vazia** (D10, não 204); finalizar sem iniciar → 409; turno inválido no corpo → 400
- [ ] 4.3 Teste nomeado de não vazamento: a resposta de `GET /today` **não contém `id`** da trip nem do motorista, só `token` opaco (regra 13). Mesmo padrão do teste de privacidade do PR #135, que confere que `street`/`zipCode`/`number` não aparecem
- [ ] 4.4 Criar `dto/TripStartRequestDTO` com `shift` obrigatório e validado por Bean Validation (`@NotNull`), aplicado com `@Valid` no controller (regras 10 e 11). O turno vem no corpo, não é derivado da hora nem dos contratos (D6)
- [ ] 4.5 Criar `controller/TripController` em `/api/drivers/me/trips` com `POST /start`, `POST /finish` e `GET /today`, resolvendo o motorista via `SecurityHelper.requireCallerUid(authentication)`. Controller fino: só delega (regra 7). Espelhar `DriverServiceAreaController`, que é o padrão `/me` já estabelecido
- [ ] 4.6 Criar `dto/TripResponseDTO` com `token`, `serviceDate`, `shift`, `status`, `startedAt`, `finishedAt` e `outsideWorkWindow` — nunca devolver `TripModel` (regra 12)
- [ ] 4.7 Criar `mapper/TripMapper` (`TripModel` → `TripResponseDTO`), mantendo o `TripService` livre de montagem de resposta. **15 das 17 features com controller têm mapper**; as duas exceções (`driverservicearea`, `permission`) são o desvio, não o padrão, e a regra 5 lista `mapper` como papel próprio
- [ ] 4.8 Confirmar que `SecurityConfig` não deixa a rota nova pública por omissão (regras 19 e 20); as três exigem autenticação
- [ ] 4.9 ~~Criar `seed/TripSeeder`~~ — **movido para a fase 5** (5.6), junto do CRUD que o admin usa para inspecionar o dado semeado. Original: criar `seed/TripSeeder` — seeder por feature, como `DependentSeeder`, `DriverCnhSeeder`, `DriverDocumentSeeder` e `DriverRatingSeeder`; **não** empilhar no `DataSeeder`. Semear, para o motorista APROVADO já existente, uma trip `COMPLETED` de ontem e uma `IN_PROGRESS` de hoje, para que o app tenha os dois estados sem precisar operar
- [ ] 4.10 `make lint` + `./mvnw verify`; abrir PR fase 4 apontando `--base feat/trip-daily-operation-service`

## 5. Phase 5 — CRUD administrativo por token (PR 5)

> Goal: create, list, show, patch, delete e restore sob `/api/trips`, com posse e permissão separadas. Fecha a change.
> Depends on: Phase 4 | Parallel with: —
> Order: test → security/authorization → request DTO → service → controller

- [ ] 5.1 Criar branch `feat/trip-daily-operation-admin-crud` **de dentro de** `feat/trip-daily-operation-driver-http`
- [ ] 5.2 Testes MockMvc do CRUD: `POST` cria com `SCHEDULED` e `started_at` nulo → 201; slot ativo duplicado → 409 `trip.duplicate_slot`; `GET` paginado exclui removidas; `GET /{token}` do dono → 200; `GET /{token}` de outro motorista → **403**; `DELETE` pelo dono sem `delete_trip` → **403**; `DELETE` por admin → some da listagem; `restore` → volta
- [ ] 5.3 **Teste nomeado do PATCH parcial** (regra 16): corpo só com `status` num trip que tem `shift`, `startedAt` e `finishedAt` — os três permanecem inalterados. Omitir ≠ `null`
- [ ] 5.4 Acrescentar `isTripOwner(String token, Authentication authentication)` ao `SecurityEvaluator` (regra 22) — resolve o chamador com `SecurityHelper.getCallerUid` e compara com o token de usuário do motorista da trip. **Isto reverte a parte do D9 que dizia "não acrescentar método"**, que valia enquanto tudo era `/me`; o CRUD endereça por token e ali existe dono. Não criar `TripSecurityService` (regra 22)
- [ ] 5.5 Acrescentar `LIST_TRIPS`, `SHOW_TRIP`, `CREATE_TRIP`, `UPDATE_TRIP`, `DELETE_TRIP`, `RESTORE_TRIP` ao `PermissionEnum` e ao bundle `ADMIN` do seeder
- [ ] 5.6 Criar `seed/TripSeeder` — seeder por feature, como `DependentSeeder`, `DriverCnhSeeder`, `DriverDocumentSeeder` e `DriverRatingSeeder`; **não** empilhar no `DataSeeder`. Semear, para o motorista APROVADO já existente, uma trip `COMPLETED` de ontem e uma `IN_PROGRESS` de hoje
- [ ] 5.7 Criar `dto/TripCreateRequestDTO` (corpo completo, sem `JsonNullable` — regra 16) e `dto/TripUpdateRequestDTO` com `JsonNullable` em **shift, status, startedAt, finishedAt**, canônico compacto `undefined()` no construtor como em `UserProfileUpdateRequestDTO`. `driver` e `serviceDate` **não** são mutáveis: mover a trip de motorista ou de dia quebraria em silêncio a unicidade que o slot representa
- [ ] 5.8a Teste de `TripCoherencePolicy` (pura, sem contexto Spring): matriz status × timestamps — `SCHEDULED` proíbe ambos; `IN_PROGRESS` exige início e proíbe término; `COMPLETED` exige os dois; `CANCELLED` aceita qualquer combinação coerente; término anterior ao início recusado em todos; término sem início recusado em todos (D12)
- [ ] 5.8b Implementar `service/TripCoherencePolicy` devolvendo `Optional<TripCoherenceViolation>`; o enum carrega a chave de MessageSource, para que a policy não conheça `MessageSource` nem HTTP (regra 9)
- [ ] 5.8c Aplicar a coerência **só** em `create` e `update` — `start`/`finish` são coerentes por construção e validar ali seria checar o que a máquina de estados já garante (D12)
- [ ] 5.8 Implementar os métodos de CRUD no `TripService`. O `PATCH` **não** consulta a `TripTransitionPolicy`: ela governa o fluxo do motorista, e a correção administrativa existe justamente para alcançar estado que a policy recusaria (reabrir rota fechada por engano). Merge com `isPresent()`, nunca reusando o `applyRequest` do create (regra 16)
- [ ] 5.9 Implementar `controller/TripController` para `/api/trips` com `@PreAuthorize("hasAuthority('<perm>') or @sec.isTripOwner(#token, authentication)")` em read/update, e **só** `hasAuthority` em delete/restore — motorista não remove o próprio dia de operação
- [ ] 5.10 Chaves de MessageSource (EN + pt-BR): `trip.duplicate_slot`, `trip.not_found`, `trip.field.null` e as cinco de `trip.coherence.*`
- [ ] 5.11 `make lint` + `./mvnw verify`; abrir PR fase 5 apontando `--base feat/trip-daily-operation-driver-http`, com `Closes #150`

## 6. Encerramento

- [ ] 6.1 Abrir a "preview stack" no GitHub a partir de qualquer PR da pilha, criar a stack e mergear pela **última** PR (`merge stack`)
- [ ] 6.2 Confirmar que os 3 specs da change (`trip-lifecycle`, `trip-daily-query`, `trip-admin-crud`) foram cobertos pelas fases entregues
- [ ] 6.3 Rodar `./mvnw verify` completo na `main` já integrada e confirmar a cobertura mínima do JaCoCo (regra 24)
- [ ] 6.4 Registrar na issue #150 a resposta das duas decisões que ela deixou em aberto: início fora do expediente (**D2 — registra, não bloqueia**) e checklist pendente ao finalizar (**D3 — permanece pendente**)
- [ ] 6.5 Levar a **Q1** (turno integral é uma trip ou duas?) para a issue #151, que é onde `checklist_entry` dá forma às fases e a pergunta pode ser respondida com evidência
- [ ] 6.6 **Atualizar o `vanep-diagram.dbml`** acrescentando `deleted_at` a `trip` (R7). Enquanto não for feito, diagrama e schema divergem — a dívida exata que o D1 existe para evitar
- [ ] 6.7 Sincronizar as specs para `openspec/specs/` (`/opsx:sync`) e arquivar a change (`/opsx:archive`)
