> **Pilha de branches.** Cada branch nasce de dentro da anterior e cada PR aponta a anterior
> como `--base`. A fase 1 é a única que sai de `main`.
>
> ```
> main ─ feat/30-client-driver-schema ─ …-service ─ …-http
>          PR 1 → main        PR 2 → PR 1   PR 3 → PR 2
> ```
>
> **Mergear pelo `merge stack` na última PR.** O repo tem `delete_branch_on_merge: false`,
> e sem apagar a branch o GitHub não reaponta a base das filhas — foi assim que a pilha
> do #150 colapsou e precisou de uma PR de recuperação.

## 0. Preparation

- [x] 0.1 Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] 0.2 Revisar `proposal.md`, `design.md` e o spec `client-driver-link`
- [x] 0.3 **Confirmar o próximo número de Flyway.** `V34` e `V35` estão tomadas pela pilha do N-177 (auth nativa), então o plano assume **`V36`**. Reconfirmar com `git ls-tree` em todas as branches remotas antes de escrever o arquivo — foi o que salvou a `trip`
- [ ] 0.4 Corrigir o texto da issue #30: ela diz **"dependente"** em cinco lugares (copy-paste do template do `dependent`). O critério de aceite não pode descrever outra entidade

## 1. Phase 1 — schema, model e repository (PR 1)

> Goal: a tabela e o tipo existem, com a unicidade do par garantida pelo banco. Sem HTTP, sem serviço.
> Depends on: — | Parallel with: —
> Order: test → migration → model → repository

- [x] 1.1 Criar branch `feat/30-client-driver-schema` a partir de `main`
- [x] 1.2 Testes de repositório: busca por par; busca por token; listar por cliente; listar por motorista; um cliente com dois motoristas; soft delete some das queries; **par soft-deletado pode ser recriado**
- [x] 1.3 Criar `RelationshipStatus` com `PENDING`, `ACTIVE`, `INACTIVE` (regra 14), conforme o `Enum relationship_status` do DBML (D1)
- [x] 1.4 Migration `V36__create_client_driver_table.sql`: `id`, `token`, `client_id` (FK → `client(id)`), `driver_id` (FK → `driver(id)`), `status` default `PENDING`, `created_at`, `updated_at`, `deleted_at`
- [x] 1.5 Índices únicos **parciais** (`where deleted_at is null`) em `token` e em `(client_id, driver_id)`. **Sem o parcial, um vínculo removido travaria aquele par para sempre** e o `restore` viraria a única saída de um bloqueio que ele deveria evitar (D2)
- [x] 1.6 **Aplicar a `V36` manualmente contra o PostgreSQL local e verificar o índice** — inserir dois vínculos do mesmo par, soft-deletar um e recriar. A suíte roda `flyway.enabled=false` com `ddl-auto=create-drop`, então nenhum teste executa esta migration (R3)
- [x] 1.7 Criar `br.com.vanep.clientdriver` com `model/ClientDriverModel`, anotado com `@SoftDelete(columnName = "deleted_at", strategy = TIMESTAMP)` (regra 19)
- [x] 1.8 Criar `repository/ClientDriverRepository` com busca por par, por token, por cliente e por motorista; fetch join em `client.user` e `driver.user` (regra 17 — ambos são `EAGER`, sem o fetch join uma listagem de N vínculos dispara 2N queries)
- [x] 1.9 Acrescentar `client_driver` ao `src/test/resources/db/clean.sql` na ordem correta de FK
- [ ] 1.10 `make lint` + `./mvnw verify`; abrir PR fase 1 em pt-BR com status de lint/teste, apontando `--base main` (regras 44 e 47)

## 2. Phase 2 — serviço, permissões e posse de duas partes (PR 2)

> Goal: criar, ler, atualizar, remover e restaurar pela camada de serviço, com autorização. Sem controller.
> Depends on: Phase 1 | Parallel with: —
> Order: test → security/authorization → messages → requestDTO → service → responseDTO → mapper

- [ ] 2.1 Criar branch `feat/30-client-driver-service` **de dentro de** `feat/30-client-driver-schema`
- [x] 2.2 Testes unitários do `ClientDriverService` (Mockito): cria com `PENDING`; par ativo duplicado → 409 `client_driver.duplicate_pair`; cliente ou motorista inexistente → 404; patch de `status` persiste; patch vazio não muda nada; `status` explicitamente nulo → 400; restore de vínculo não removido → 404
- [x] 2.3 **Acrescentar `isClientDriverLinkParty(String token, Authentication authentication)` ao `SecurityEvaluator`** (regra 22). É o **primeiro** método de posse com **duas** partes: devolve verdadeiro se o chamador for o usuário do cliente **ou** o do motorista. Não chamar de `...Owner` — não há dono, há partes (D3)
- [x] 2.4 **Teste nomeado para cada lado** (R4): o cliente enxerga, o motorista enxerga, um terceiro toma 403. Quem copiar o padrão dos outros seis `is<Entity>Owner` vai comparar só um lado, e o bug seria silencioso — motorista tomando 403 no próprio vínculo
- [x] 2.5 Acrescentar `LIST_CLIENT_DRIVERS`, `SHOW_CLIENT_DRIVER`, `CREATE_CLIENT_DRIVER`, `UPDATE_CLIENT_DRIVER`, `DELETE_CLIENT_DRIVER`, `RESTORE_CLIENT_DRIVER` ao `PermissionEnum` e ao bundle `ADMIN` do seeder
- [x] 2.6 Chaves de MessageSource em `messages.properties` (EN) e `messages_pt_BR.properties`: `client_driver.duplicate_pair`, `client_driver.not_found`, `client_driver.client.not_found`, `client_driver.driver.not_found`, `client_driver.status.required`. Nunca hardcodar pt-BR no ponto do `throw` (regra 46)
- [x] 2.6b Criar `dto/ClientDriverCreateRequestDTO` (corpo completo, `clientToken` e `driverToken` obrigatórios, `status` opcional), `dto/ClientDriverUpdateRequestDTO` com `JsonNullable` só em `status` (D5), `dto/ClientDriverResponseDTO` e `mapper/ClientDriverMapper`. **Vêm aqui, não na fase 3**: o serviço usa os quatro, e a regra 42 manda `requestDTO` antes de `service`
- [x] 2.7 Implementar `service/ClientDriverService` com create, findAll paginado, findByToken, update, delete, restore e `findMine`
- [x] 2.8 No `findMine`, derivar o lado da tabela a partir do `UserType` do chamador (D7). Devolver lista — um cliente tem vários motoristas e um motorista tem muitos clientes; chamador sem vínculo recebe lista vazia, não 204
- [ ] 2.9 `make lint` + `./mvnw verify`; abrir PR fase 2 apontando `--base feat/30-client-driver-schema`

## 3. Phase 3 — superfície HTTP, mapper e seeder (PR 3)

> Goal: o CRUD responde. Fecha a change.
> Depends on: Phase 2 | Parallel with: —
> Order: test → controller → seeder

- [ ] 3.1 Criar branch `feat/30-client-driver-http` **de dentro de** `feat/30-client-driver-service`
- [x] 3.2 Testes MockMvc com segurança: sem JWT → 401; `POST` por admin → 201 com `PENDING`; par duplicado → 409; token de cliente inexistente → 404; `GET` paginado exclui removidos; `GET /{token}` pelo **cliente** → 200; pelo **motorista** → 200; por terceiro → **403**; `DELETE` por qualquer das partes → **403**; `DELETE` por admin → some; `restore` → volta
- [x] 3.3 **Teste nomeado do PATCH parcial** (regra 16): corpo vazio num vínculo com `status`, cliente e motorista → os três permanecem inalterados. Omitir ≠ `null`
- [x] 3.4 Teste nomeado de não vazamento: a resposta **não contém `id`** do vínculo, do cliente nem do motorista — só tokens opacos (regra 13)
- [x] 3.7 Criar `controller/ClientDriverController` em `/api/client-drivers`. Read e update: `hasAuthority('<perm>') or @sec.isClientDriverLinkParty(#token, authentication)`. Delete e restore: **só** `hasAuthority` — nenhuma das partes apaga um dado que a outra também usa (D4). Controller fino, só delega (regra 7)
- [x] 3.8 Criar `GET /api/client-drivers/me` com `isAuthenticated()`, resolvendo o chamador por `SecurityHelper.requireCallerUid`
- [x] 3.9 Confirmar que `SecurityConfig` não deixa nenhuma rota nova pública por omissão (regras 20 e 21)
- [x] 3.10 Criar `seed/ClientDriverSeeder` — seeder por feature, como `DependentSeeder` e `TripSeeder`; **não** empilhar no `DataSeeder`. Vincular o primeiro cliente ao primeiro motorista APROVADO, para que o app e o Postman tenham dado (R5)
- [ ] 3.11 `make lint` + `./mvnw verify`; abrir PR fase 3 apontando `--base feat/30-client-driver-service`, com `Closes #30`

## 4. Encerramento

- [ ] 4.1 Mergear pelo **`merge stack`** na última PR, ou apagando a branch a cada merge
- [ ] 4.2 Confirmar que o spec `client-driver-link` foi coberto pelas fases entregues
- [ ] 4.3 Rodar `./mvnw verify` na `main` integrada e confirmar a cobertura mínima do JaCoCo (regra 24)
- [ ] 4.4 **Abrir issue para o R1**: migrar `driver_rating` (V16) e `client_rating` (V33) para o hub. Hoje são 2 tabelas furando o canônico; com `proposal`, `contract` e `conversation` viram 5, e a migração fica bem mais cara. Os TODOs já plantados nos dois services são o gancho — e o R2 (V16 tem `deleted_at`, V33 não) some junto
- [ ] 4.5 Registrar na issue #42 a **Q1** — se o vínculo volta a `PENDING` ou nasce outro quando o contrato encerra; é a #42 que move o status
- [ ] 4.6 Sincronizar o spec para `openspec/specs/` (`/opsx:sync`) e arquivar a change (`/opsx:archive`)
