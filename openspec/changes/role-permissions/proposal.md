## Why

Hoje a autorização das rotas usa `@PreAuthorize("hasRole('ADMIN')")`, que na prática só verifica o `user_type` do token — a tabela `roles` (RBAC) existe mas está dormente: `users.role_id` nunca é populado nem lido, e não há granularidade de permissão. Isso impede papéis intermediários (ex.: um operador que só lista clientes mas não deleta). Precisamos ativar o RBAC com permissões finas, seguindo a modelagem do `vanep-dbdiagram`.

## What Changes

- Nova tabela/entidade **`role_permissions`**: bundle nomeado com uma lista (JSON) de strings de permissão; `roles` passa a referenciar um bundle via `role_permissions_id` (1-para-1).
- Novo **`PermissionEnum`** (convenção `verb_resource`, ex.: `list_roles`, `delete_client`) + **`PermissionRegistry`** como fonte única de valores válidos para validação.
- **CRUD completo de `role_permissions`** (controller/service/DTOs/mapper/repository), com validação de que cada permissão enviada pertence ao registry.
- **Fiação no token OAuth2**: `JwtTokenCustomizer` passa a carregar `role_id → role → role_permissions.permissions` e emitir o claim `permissions`; `SecurityConfig` mapeia esse claim para `GrantedAuthority`.
- **Migração das rotas** de `@PreAuthorize("hasRole('ADMIN')")` para `@PreAuthorize("hasAuthority('<permission>')")` nos controllers que hoje gateiam por papel (`RoleController`, `ClientController`) e no novo `RolePermissionController`. Verificações de posse (`@clientSecurity.isOwner`) permanecem inalteradas.
- **Seed**: um bundle `role_permissions` "ADMIN" contendo **todas** as permissões, ligado a um `role` ADMIN, garantindo que usuários ADMIN mantêm acesso total após a troca. **BREAKING** (operacional): tokens emitidos antes da migração não têm o claim `permissions`; exigem novo login para acessar rotas migradas.
- **Novo `RoleName`** (enum `ADMIN`, `CLIENT`, `DRIVER`) + coluna `roles.role_name` (nullable, unique): identifica de forma estável as 3 roles de sistema, sem travar o CRUD livre de `role` (nome exibido continua editável via `RoleController`; só as 3 linhas de sistema carregam a tag).
- **Atribuição automática de role**: ao criar um `Client` (`RegistrationService.registerClient`, seeds) ou `Driver` (`RegistrationService.registerDriver`, seeds), `user.role_id` passa a ser fixado para a role com `role_name` correspondente (`CLIENT`/`DRIVER`), populando o vínculo hoje dormente.
- **Seed de driver**: `DataSeeder` ganha `seedDrivers()` (paralelo ao `seedClients()` já existente), criando driver(s) de teste já com `approval_status = APPROVED` — usuário "especial" pronto para uso sem passar pelo fluxo de aprovação, já que ainda não há CRUD/admin flow de aprovação de driver.

## Capabilities

### New Capabilities
- `role-permissions`: gerenciamento de bundles de permissão (`role_permissions`) — modelo, CRUD, validação contra o registry de permissões, e a relação 1-1 com `role`.
- `permission-authorization`: como o token carrega permissões e como as rotas são protegidas por `hasAuthority('<permission>')` (claim `permissions` → authorities), incluindo o seed do bundle ADMIN e a migração das rotas existentes.
- `role-permissions` também passa a cobrir: `RoleName` (enum de identidade das 3 roles de sistema) e a atribuição automática de `role_id` na criação de `Client`/`Driver`.

### Modified Capabilities
<!-- Sem specs de capacidade pré-existentes em openspec/specs/. As mudanças de autorização de role/client entram como requisitos da nova capability permission-authorization. -->

## Impact

- **Schema**: nova migração Flyway `V8__create_role_permissions_table.sql` (tabela + colunas/FK `roles.role_permissions_id` e `roles.role_name`). Migrações V1–V7 permanecem intocadas (constituição, regra 2).
- **Código novo**: pacote `br.com.vanep.rolepermission` (controller/dto/model/mapper/repository/service), `br.com.vanep.auth.security.PermissionEnum` + `PermissionRegistry`, `br.com.vanep.role.RoleName`.
- **Código alterado**: `JwtTokenCustomizer`, `SecurityConfig` (converter), `RoleController`, `ClientController`, `RoleModel`, `RoleRepository`, `DataSeeder`, `RegistrationService`.
- **Auth**: comportamento do token muda (novo claim `permissions`); requer novo login para rotas migradas.
- **Testes**: unit (service/validação/registry) + slice (`MockMvc` + security) para o CRUD novo e para as rotas migradas.
