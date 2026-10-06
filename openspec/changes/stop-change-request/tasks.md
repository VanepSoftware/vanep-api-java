> **Pilha de branches.** As fases são empilhadas: cada branch nasce de dentro da anterior e cada PR
> aponta a anterior como `--base`. A fase 1 é a única que sai de `main`. Depois de todas subirem, o
> GitHub mostra "preview stack" no topo de qualquer uma delas; criar a stack ali e mergear pela
> **última** PR (`merge stack`) integra tudo de uma vez.
>
> ```
> main ─ …-schema ─ …-policies ─ …-service ─ …-client-driver-http ─ …-admin-crud
>      PR 1 → main  PR 2 → PR 1  PR 3 → PR 2       PR 4 → PR 3         PR 5 → PR 4
> ```

## PR Plan Table

| Phase | Contents | Depends on | Parallel with |
|---|---|---|---|
| **Phase 1** | `StopChangeStatus`, Migration `V40`, `StopChangeRequestModel`, `StopChangeRequestRepository`, `clean.sql`, repository tests | — | — |
| **Phase 2** | `StopChangeTransitionPolicy` (pure domain class) + 100% matrix transition unit tests | Phase 1 | — |
| **Phase 3** | `StopChangeRequestService`, domain events, permissions no `PermissionEnum` e bundles do seeder, `SecurityEvaluator`, chaves de `MessageSource`, testes unitários do service (Mockito) | Phase 2 | — |
| **Phase 4** | DTOs de request e response, `StopChangeRequestMapper`, endpoints do cliente (`/api/clients/me/stop-change-requests`) e do motorista (`/api/drivers/me/stop-change-requests`), testes MockMvc com segurança | Phase 3 | — |
| **Phase 5** | Endpoints administrativos (`/api/stop-change-requests`), soft delete, restore, paginação, `StopChangeRequestSeeder`, testes MockMvc administrativos | Phase 4 | — |

## 0. Preparation

- [x] 0.1 Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1) — já lido e conferido.
- [x] 0.2 Confirmar o próximo número de Flyway: `V40` livre (V36 a V39 reservadas pelas branches em voo de `client-driver` e `rating-link`).
- [x] 0.3 Obter aprovação formal do plano antes de iniciar a codificação (regra 37) — aprovado pelo usuário.

## 1. Phase 1 — Schema, Enum, Model e Repository (PR 1)

> Goal: a tabela `stop_change_request`, o enum `StopChangeStatus`, a entidade e o repositório existem, com unicidade garantida no banco. Sem HTTP, sem regras de negócio, sem serviço.
> Depends on: — | Parallel with: —
> Order: test → migration → model → repository

- [x] 1.1 Criar branch `feat/stop-change-request-schema` a partir de `main`.
- [x] 1.2 Testes de repositório para `StopChangeRequestRepository`:
  - busca por token (ativo e soft-deletado).
  - busca por `(trip, dependent)`.
  - busca de solicitações pendentes por `trip`.
  - busca de solicitações por solicitante (`requestedBy`).
  - busca por data de serviço (`serviceDate`).
  - geração automática de token opaco no `@PrePersist`.
  - status default `PENDING`.
- [x] 1.3 Criar enum `StopChangeStatus` em `br.com.vanep.stopchange.enums` com `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`, `CANCELLED` (regra 14).
- [x] 1.4 Migration `V40__create_stop_change_request_table.sql`:
  - `id`, `token`, `contract_id` (nullable bigint, sem FK física até #41), `dependent_id` (FK → `dependent`), `trip_id` (FK → `trip`), `service_date`, `requested_by_user_id` (FK → `users`), `new_dropoff_address_id` (FK → `address`), `reason`, `status`, `responded_by_user_id` (FK → `users`), `responded_at`, `created_at`, `updated_at`, `deleted_at`.
  - Índice único parcial em `token` (`WHERE deleted_at IS NULL`).
  - Índice único parcial para garantir no máximo uma solicitação pendente por aluno e viagem:
    `create unique index stop_change_request_pending_dependent_trip_active_key on stop_change_request (dependent_id, trip_id) where status = 'PENDING' and deleted_at is null;`
  - Índices parciais de FK: `trip_id`, `dependent_id`, `requested_by_user_id`, `service_date`.
- [x] 1.5 Criar pacote `br.com.vanep.stopchange` com `model/StopChangeRequestModel`:
  - Anotado com `@Entity`, `@Table(name = "stop_change_request")`.
  - Anotado com `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)` (regra 19).
  - Mapeamento das associações: `DependentModel`, `TripModel`, `UserModel requestedBy`, `UserModel respondedBy`, `AddressModel newDropoffAddress`.
- [x] 1.6 Criar `repository/StopChangeRequestRepository`:
  - Consultas com fetch joins necessários para evitar N+1 (regra 17).
  - `findByToken`, `findByTripIdAndStatus`, `findByDependentIdAndTripIdAndStatus`, `findByRequestedByUserId`.
- [x] 1.7 Atualizar `src/test/resources/db/clean.sql` incluindo `stop_change_request` na ordem correta de FKs (regra 19).
- [x] 1.8 `make lint` + `./mvnw test -Dtest=StopChangeRequestRepositoryTest`. Phase 1 concluída.

## 2. Phase 2 — Policy pura de transição de status (PR 2)

> Goal: a máquina de estados decide sozinha, sem Spring, JPA ou servlet. Regra 100% provada por testes unitários.
> Depends on: Phase 1 | Parallel with: —
> Order: test → policy

- [ ] 2.1 Criar branch `feat/stop-change-request-policies` a partir de `feat/stop-change-request-schema`.
- [ ] 2.2 Teste de `StopChangeTransitionPolicy` cobrindo a matriz completa de transições entre estados de `StopChangeStatus`:
  - `PENDING -> APPROVED` permitido.
  - `PENDING -> REJECTED` permitido.
  - `PENDING -> CANCELLED` permitido.
  - `PENDING -> EXPIRED` permitido.
  - Qualquer transição a partir de `APPROVED`, `REJECTED`, `CANCELLED` ou `EXPIRED` rejeitada.
  - Rodar sem contexto Spring e sem banco de dados (regra 8 e 26).
- [ ] 2.3 Implementar `service/StopChangeTransitionPolicy` recebendo status atual e status pretendido, devolvendo validade ou lançando violação coerente (regra 9).
- [ ] 2.4 `make lint` + `./mvnw verify`. Abrir PR da fase 2 apontando para `feat/stop-change-request-schema`.

## 3. Phase 3 — Serviço, eventos, permissões e autorização (PR 3)

> Goal: solicitar, aprovar, recusar, cancelar e consultar funcionam na camada de serviço com validações e autorização completa. Sem controllers ainda.
> Depends on: Phase 2 | Parallel with: —
> Order: test → security/authorization → messages → events → service

- [ ] 3.1 Criar branch `feat/stop-change-request-service` a partir de `feat/stop-change-request-policies`.
- [ ] 3.2 Testes unitários de `StopChangeRequestService` com Mockito:
  - Solicitação por cliente com dependente próprio cria com status `PENDING` (201).
  - Solicitação por cliente de dependente de outro cliente lança 403 Forbidden.
  - Solicitação para trip `COMPLETED` ou `CANCELLED` lança 409 Conflict.
  - Segunda solicitação simultânea para mesmo dependente/trip captura violação de integridade e lança 409 `stop_change.already_pending`.
  - Aprovação por motorista dono da trip atualiza status para `APPROVED`, grava `respondedBy` e `respondedAt` e dispara evento de domínio.
  - Aprovação por motorista de outra trip lança 403 Forbidden.
  - Motorista não aprovado (`approvalStatus != APPROVED`) lança 403 Forbidden (RN-02).
  - Recusa por motorista dono da trip atualiza para `REJECTED`, grava auditoria e dispara evento.
  - Resposta a solicitação já finalizada lança 409 Conflict.
  - Resposta idêntica a solicitação já aprovada/recusada pelo mesmo motorista é idempotente (200).
  - Cancelamento pelo cliente solicitante atualiza para `CANCELLED`.
  - Cancelamento de solicitação já aprovada lança 409 Conflict.
- [ ] 3.3 Adicionar novas permissões ao `PermissionEnum`:
  - `REQUEST_STOP_CHANGE("request_stop_change")` no bundle `CLIENT`.
  - `APPROVE_STOP_CHANGE("approve_stop_change")` e `REJECT_STOP_CHANGE("reject_stop_change")` no bundle `DRIVER`.
  - `LIST_STOP_CHANGE_REQUESTS`, `SHOW_STOP_CHANGE_REQUEST`, `DELETE_STOP_CHANGE_REQUEST`, `RESTORE_STOP_CHANGE_REQUEST` no bundle `ADMIN`.
  - Atualizar seeder de permissões (`RolePermissionSeeder` / `PermissionSeeder`).
- [ ] 3.4 Adicionar métodos de checagem de posse no `SecurityEvaluator` (`@sec`):
  - `isStopChangeRequestClient(String token, Authentication authentication)`
  - `isStopChangeRequestDriver(String token, Authentication authentication)`
- [ ] 3.5 Adicionar chaves de mensagens i18n em `messages.properties` e `messages_pt_BR.properties` (regras 46 e 49):
  - `stop_change.already_pending`, `stop_change.already_resolved`, `stop_change.trip_not_active`, `stop_change.not_found`, `stop_change.not_owner`, `stop_change.cannot_cancel`.
- [ ] 3.6 Criar eventos de domínio desacoplados:
  - `StopChangeRequestedEvent`
  - `StopChangeApprovedEvent`
  - `StopChangeRejectedEvent`
- [ ] 3.7 Implementar `service/StopChangeRequestService`:
  - Métodos: `requestChange`, `approveChange`, `rejectChange`, `cancelChange`, `findByToken`, `listTodayForClient`, `listTodayForDriver`.
  - Integração com `AddressPlaceResolverService` / `AddressModel` para resolução segura do endereço de desembarque sem alterar o cadastro do dependente.
- [ ] 3.8 `make lint` + `./mvnw verify`. Abrir PR da fase 3 apontando para `feat/stop-change-request-policies`.

## 4. Phase 4 — Superfície HTTP do Cliente e do Motorista (PR 4)

> Goal: os endpoints `/me` do cliente e do motorista respondem via REST com validação de entrada Bean Validation e mapeamento DTO explícito.
> Depends on: Phase 3 | Parallel with: —
> Order: test → request DTO → controller → response DTO → mapper

- [ ] 4.1 Criar branch `feat/stop-change-request-client-driver-http` a partir de `feat/stop-change-request-service`.
- [ ] 4.2 Testes MockMvc com segurança:
  - `POST /api/clients/me/stop-change-requests` com cliente autenticado cria com 201.
  - `GET /api/clients/me/stop-change-requests/today` lista solicitações de hoje do cliente.
  - `POST /api/clients/me/stop-change-requests/{token}/cancel` cancela solicitação com 200.
  - `GET /api/drivers/me/stop-change-requests/today` lista solicitações de hoje do motorista.
  - `POST /api/drivers/me/stop-change-requests/{token}/approve` aprova solicitação com 200.
  - `POST /api/drivers/me/stop-change-requests/{token}/reject` recusa solicitação com 200.
  - Sem autenticação -> 401; tipos de usuário trocados -> 403.
  - Respostas não vazam IDs numéricos internos (regra 13).
- [ ] 4.3 Criar DTOs de request:
  - `StopChangeCreateRequestDTO` (validação com `@NotNull`, `@NotBlank`, etc. via Bean Validation — regra 10).
  - DTO de cancelamento / recusa se aplicável.
- [ ] 4.4 Criar DTOs de response:
  - `StopChangeResponseDTO` com tokens opacos, endereço de desembarque formatado, timestamps e status (regra 12).
- [ ] 4.5 Criar `mapper/StopChangeRequestMapper`.
- [ ] 4.6 Criar controllers:
  - `controller/ClientStopChangeRequestController` em `/api/clients/me/stop-change-requests`.
  - `controller/DriverStopChangeRequestController` em `/api/drivers/me/stop-change-requests`.
- [ ] 4.7 Verificar segurança em `SecurityConfig` (regras 20 e 21).
- [ ] 4.8 `make lint` + `./mvnw verify`. Abrir PR da fase 4 apontando para `feat/stop-change-request-service`.

## 5. Phase 5 — CRUD Administrativo por Token e Seeder (PR 5)

> Goal: CRUD administrativo por token sob `/api/stop-change-requests` com soft delete, restore, paginação e seeder.
> Depends on: Phase 4 | Parallel with: —
> Order: test → security/authorization → request DTO → service → controller → seeder

- [ ] 5.1 Criar branch `feat/stop-change-request-admin-crud` a partir de `feat/stop-change-request-client-driver-http`.
- [ ] 5.2 Testes MockMvc do CRUD administrativo:
  - `GET /api/stop-change-requests` paginado com permissão `list_stop_change_requests`.
  - `GET /api/stop-change-requests/{token}` com permissão de admin ou posse do recurso.
  - `DELETE /api/stop-change-requests/{token}` com soft delete.
  - `POST /api/stop-change-requests/{token}/restore` restaura solicitação soft-deletada.
- [ ] 5.3 Criar `controller/StopChangeRequestController` administrativo.
- [ ] 5.4 Criar `seed/StopChangeRequestSeeder` com dados de teste representativos (seeder por feature, regra 5).
- [ ] 5.5 `make lint` + `./mvnw verify`. Abrir PR da fase 5 apontando para `feat/stop-change-request-client-driver-http`.
