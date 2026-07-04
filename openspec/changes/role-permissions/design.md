## Context

O RBAC do Vanep está montado pela metade: a tabela `roles` existe (migração V7) e `users.role_id` está mapeado em `User`, mas nada popula ou lê esse vínculo. A autorização real acontece por `@PreAuthorize("hasRole('ADMIN')")`, que só reflete o `user_type` — o `JwtTokenCustomizer` emite `roles = ["ROLE_" + user_type]` e o `SecurityConfig` converte esse claim em authorities. Não há permissões finas.

O projeto `checklists` (Laravel) já validou o modelo-alvo: um `role` aponta para um bundle `role_permissions` que guarda uma lista JSON de strings de permissão (`verb_resource`), um `PermissionsEnum` + `PermissionsRegistry` como fonte de verdade, e checagem por `in_array(permission, bundle.permissions)`. O `vanep-dbdiagram` confirma a modelagem (`role.role_permissions_id` 1-1 com `role_permissions`).

Stack e regras vinculantes: Java 25 / Spring Boot 4 / Flyway / OAuth2 Authorization Server; código por feature com sufixos de papel (`*Model`, `*Repository`, ...); nunca editar migração aplicada; identificadores em inglês, mensagens de usuário em pt-BR; entrega faseada em PRs empilhados (constituição, regras 33–41).

## Goals / Non-Goals

**Goals:**
- Ativar RBAC com permissões finas replicando o padrão do `checklists`.
- Persistir `role_permissions` (bundle JSON) com relação 1-1 a `role`.
- Emitir as permissões do usuário no access token e convertê-las em authorities.
- Trocar `hasRole('ADMIN')` por `hasAuthority('<permission>')` nas rotas hoje gateadas por papel, sem regredir ADMIN (seed com todas as permissões).
- CRUD completo de `role_permissions` validado contra o registry.
- Identificar de forma estável as 3 roles de sistema (`RoleName`: ADMIN, CLIENT, DRIVER) e popular `user.role_id` automaticamente quando um `Client` ou `Driver` é criado.

**Non-Goals:**
- CRUD/UX de `role` (já existe) — só ganha a coluna/relação e migração das rotas.
- Permissões amarradas a objeto de domínio (ACL por linha) — descartado, ver Decisões.
- Migrar controllers de features que ainda não existem (vehicle, dependent estão em outras changes) ou rotas web/auth não gateadas por `hasRole`.
- UI de atribuição de papel a usuário; atribuição manual/avulsa de `role_id` (a automática ao criar Client/Driver está no escopo, ver D7/D8).
- Corrigir o fluxo OAuth (`OAuthAccountService.completeRegistration`): hoje cria apenas o `User` (sem `Client`/`Driver`, sem `role_id`) — gap pré-existente, fora de escopo desta change (ver Open Questions).
- CRUD/rota de aprovação de driver — o seed de driver (D8) já nasce `APPROVED` para contornar a ausência desse fluxo.

## Decisions

### D1 — Enforcement por `hasAuthority`, não `hasPermission`/PermissionEvaluator

Permissões são strings globais e planas (`list_roles`). `hasAuthority('list_roles')` é nativo do Spring e faz exatamente a comparação de string que o modelo do `checklists` (`in_array`) faz — zero beans extras.

**Alternativa considerada:** `hasPermission(target, permission)` + `AppPermissionEvaluator`. Rejeitada: a assinatura é ACL-shaped (dois argumentos, alvo + permissão), forçaria remontar `roles`+`list`→`list_roles`, exige registrar `MethodSecurityExpressionHandler`, e **já foi introduzida e revertida** (commits 13c4f67 → e5f4ae0) como stub `return isAdmin(auth)`. Sem caso de decisão por objeto, não se paga.

### D2 — Permissões viajam no token como claim `permissions`, convertidas em authorities

O `JwtTokenCustomizer` resolve `user.role_id → role → role_permissions.permissions` e adiciona o claim `permissions`. O converter em `SecurityConfig` mapeia cada string para `SimpleGrantedAuthority` **sem prefixo** (`list_roles`, não `ROLE_list_roles`) — é isso que `hasAuthority` compara. O claim `roles` (`ROLE_<user_type>`) é mantido, então `hasRole` continua funcional onde ainda existir e para o fallback ADMIN.

**Alternativa considerada:** resolver permissões por consulta ao banco a cada request (via `UserDetails`/filtro). Rejeitada: a API é stateless por JWT (regra do resource server); carregar no token evita I/O por request. Trade-off assumido em Riscos (staleness).

### D3 — `permissions` como coluna JSON, mapeada a `List<String>`

Espelha `role_permissions.permissions json` do dbdiagram/checklists. Em Postgres usamos `jsonb`; no JPA, um converter `List<String>` ⇄ JSON (ou tipo JSON do Hibernate 6). H2 nos testes aceita o mesmo mapeamento via fallback de coluna `text`/`json` — validar no slice de persistência.

**Alternativa considerada:** tabela de junção `role_permission_items` (linha por permissão). Rejeitada: diverge do `checklists` que fomos mandados copiar e adiciona joins sem ganho — o conjunto é lido inteiro sempre.

### D4 — `PermissionEnum` + `PermissionRegistry` em `br.com.vanep.auth.security` (compartilhado)

Permissões cruzam features (roles, clients, ...), então o catálogo é código compartilhado, não de uma feature (constituição, regra 5). O enum agrupa por recurso e oferece helper `crudFor(resource)` como no `checklists`. `PermissionRegistry.all()` é a fonte única para o `Rule::in` equivalente (validação Bean Validation custom).

### D5 — Recursos cobertos agora

Controllers que hoje usam `@PreAuthorize`: `RoleController` (só `hasRole('ADMIN')`) e `ClientController` (`hasRole('ADMIN')`, e um `hasRole('ADMIN') or @clientSecurity.isOwner`). Mais o novo `RolePermissionController`. Enum cobre CRUD de `roles`, `role_permissions`, `clients`. Mapeamento das rotas:

| Rota | Antes | Depois |
|---|---|---|
| `GET /api/roles` (e /{token}, POST, PUT, DELETE, restore) | `hasRole('ADMIN')` | `hasAuthority('list_roles' | show_role | create_role | update_role | delete_role)` |
| `GET /api/clients` | `hasRole('ADMIN')` | `hasAuthority('list_clients')` |
| `GET /api/clients/{token}` | `hasRole('ADMIN') or @clientSecurity.isOwner(...)` | `hasAuthority('show_client') or @clientSecurity.isOwner(...)` |
| `PUT /api/clients/{token}` | `@clientSecurity.isOwner(...)` | inalterado (posse) |
| `DELETE /api/clients/{token}` | `hasRole('ADMIN')` | `hasAuthority('delete_client')` |
| `POST/GET/PUT/DELETE /api/role-permissions` | (novo) | `hasAuthority('create_/list_/show_/update_/delete_role_permission')` |

### D6 — Seed do ADMIN com todas as permissões

`DataSeeder` cria (idempotente) um bundle `role_permissions` com `PermissionRegistry.all()`, um `role` ADMIN ligado a ele (localizado por `RoleName.ADMIN`, ver D7), e garante que usuários ADMIN existentes recebam `role_id`. Sem isso, trocar `hasRole('ADMIN')` por `hasAuthority` trancaria os admins para fora.

### D7 — `RoleName` como tag de identidade, sem travar o CRUD livre de `role`

`role.name` continua string livre, editável via `RoleController` (PR #63) — não vamos travar em 3 valores fixos, pois o CRUD genérico já existe e não há pedido para removê-lo. Em vez disso, `RoleModel` ganha `role_name` (coluna nova, `@Enumerated(EnumType.STRING)`, nullable, unique): só as 3 linhas semeadas pelo sistema (`ADMIN`, `CLIENT`, `DRIVER`) carregam essa tag. Código que precisa localizar "a role ADMIN" ou "a role CLIENT" de forma confiável (seed, auto-atribuição, D6) consulta por `role_name`, não por `name` — a string de exibição pode ser renomeada pelo admin sem quebrar nada. Roles adicionais que um admin crie no futuro simplesmente têm `role_name = null`.

**Alternativa considerada:** substituir `name` pelo enum (travando em 3 valores). Rejeitada nesta rodada: o CRUD de `role` já foi entregue com nome livre (PR #63) e não há pedido para revogar essa capacidade; travar agora seria uma mudança de escopo não solicitada.

### D8 — Atribuição automática de `role_id` na criação de Client/Driver

`RegistrationService.registerClient` e `registerDriver` passam a resolver `roleRepository.findByRoleName(RoleName.CLIENT|DRIVER)` e setar `user.setRoleId(...)` antes de salvar. `DataSeeder.seedClients()` e o novo `seedDrivers()` fazem o mesmo, para que usuários de seed não fiquem sem permissões. Isso é o que faz o restante do RBAC (D1–D6) ter efeito prático: sem `role_id` populado, o claim `permissions` do token sempre seria vazio para clients/drivers.

**Nota:** o fluxo OAuth (`OAuthAccountService.completeRegistration`) não cria `Client`/`Driver` hoje (gap pré-existente) — logo não ganha auto-atribuição nesta change; fica registrado como Open Question.

### D9 — Seed de driver "especial" (`seedDrivers`)

Não existe ainda CRUD/fluxo de aprovação de driver pelo admin (só `RegistrationService.registerDriver`, que nasce `PENDING`). Para ter um driver de teste utilizável imediatamente, `seedDrivers()` (espelhando `seedClients()`) cria driver(s) com `approval_status = APPROVED` e `is_active = true` diretamente, sem passar pelo fluxo de aprovação — mesmo padrão de "usuário especial" já usado pelo seed do ADMIN.

## Risks / Trade-offs

- **Staleness do token** → permissões mudadas num bundle só valem após novo token. Mitigação: TTL curto do access token (já configurado) + documentar; re-login resolve. Revogação imediata fica fora de escopo.
- **Admins sem `role_id` após deploy** → seed idempotente que faz backfill do ADMIN + o claim `roles=ROLE_ADMIN` continua existindo como rede de segurança durante a transição.
- **Tokens antigos sem `permissions`** (BREAKING operacional) → usuários logados batem 403 em rotas migradas até relogar. Mitigação: comunicar; TTL curto encurta a janela.
- **JSON em H2 vs Postgres** → mapeamento pode divergir entre `jsonb` e o fallback de teste. Mitigação: slice test de persistência round-trip cobrindo `List<String>` já na Fase 1.
- **Validação de permissão desconhecida** → sem checagem, um bundle poderia guardar strings inúteis. Mitigação: validador Bean Validation contra `PermissionRegistry` em create/update (regra 10).
- **OAuth signup não popula `role_id`** (nem cria `Client`/`Driver`) → usuários vindos do fluxo Google continuam sem permissões após esta change, mesmo com D8. Fora de escopo (ver Non-Goals); registrado como Open Question para decidir se vira change futura.

## Migration Plan

Entrega faseada (constituição 33–41), PRs empilhados, uma camada de dependência por PR, ~600 linhas / 10 arquivos por PR, cada fase com testes e `spotless:check`/`verify` verdes:

1. **Fase 1 — Fundação (sem tocar rota):** migração `V8__create_role_permissions_table.sql` (tabela + colunas/FK `roles.role_permissions_id` e `roles.role_name`), `RolePermissionModel`, relação em `RoleModel`, `RoleName` enum, `RoleRepository.findByRoleName`, `PermissionEnum` + `PermissionRegistry`, `RolePermissionRepository`, validador. Testes: registry, validador, persistência JSON round-trip, `findByRoleName`.
2. **Fase 2 — Token + seed + auto-atribuição (ativa a cadeia):** `JwtTokenCustomizer` resolve e emite `permissions`; converter no `SecurityConfig`; `DataSeeder` tageia as 3 roles com `role_name`, cria bundle ADMIN + backfill, e ganha `seedDrivers()`; `RegistrationService.registerClient/registerDriver` passam a setar `user.role_id` via `RoleName`. Testes: customizer, converter, seed idempotente (roles + driver), auto-atribuição em `RegistrationServiceTest`.
3. **Fase 3 — CRUD `role-permissions`:** DTOs (request/response), mapper, service, controller já anotado com `hasAuthority`. Testes: service (unit) + slice `MockMvc`+security.
4. **Fase 4 — Migração das rotas existentes:** trocar `hasRole('ADMIN')`→`hasAuthority(...)` em `RoleController` e `ClientController`. Testes de slice atualizados (permitido com permissão, 403 sem, posse preservada).

**Rollback:** cada fase é um PR isolado; reverter a Fase 4 restaura `hasRole('ADMIN')` sem tocar no schema. O schema novo (Fase 1) é aditivo — nenhuma migração aplicada é editada.

## Open Questions

- Nome canônico do papel/bundle ADMIN no seed (`ADMIN` vs `Administrador`)? `name` é inglês (identificador) — provável `ADMIN`; `description` em pt-BR.
- `create_client` existe no enum por completude do CRUD, mas não há rota de criação de client hoje (clientes nascem no signup). Manter no enum como reserva ou omitir até existir a rota?
- Corrigir `OAuthAccountService.completeRegistration` (não cria `Client`/`Driver`, não popula `role_id`) vira change própria depois, ou fica como débito conhecido?
- Quantos drivers de seed (`seedDrivers`) e com quais dados — 1 fixo (como o admin) ou uma lista pequena (como os 5 clients)?
