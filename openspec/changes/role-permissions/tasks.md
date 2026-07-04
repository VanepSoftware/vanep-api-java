# Tasks — role-permissions

Entrega faseada (constituição, regras 33–41): PRs empilhados, uma camada de dependência por PR, ~600 linhas / 10 arquivos por PR, cada fase com testes + `make lint` (`spotless:check`) e `make test-coverage` (`verify`) verdes. Ordem dentro da fase segue a regra 39: teste → migração → entity → repository → security → request DTO → service → controller → response DTO.

## Dependency graph & layer assignment

```
Fase 1  PermissionEnum ─┐
        PermissionRegistry ─┤
        V8 migration ─┐     │  (+ roles.role_name)
                      ▼     ▼
        RolePermissionModel ── RoleModel.rolePermission + roleName (relação)
                      │
                      ▼
        RolePermissionRepository + PermissionListValidator + RoleRepository.findByRoleName
                      │
   ┌──────────────────┼──────────────────────┐
   ▼ Fase 2           ▼ Fase 3               │
JwtTokenCustomizer  RolePermission service/  │
+ SecurityConfig    controller/DTOs/mapper   │
+ DataSeeder(seed, roleName tags, seedDrivers) (usa hasAuthority)
+ RegistrationService (auto role_id)   │       │
   │                   │                       │
   └─────────┬─────────┘                       │
             ▼ Fase 4                           │
   RoleController + ClientController  ◀─────────┘
   hasRole('ADMIN') → hasAuthority(...)
```

## PR plan

| Phase | Contents | Depends on | Parallel with |
|---|---|---|---|
| 1 — Fundação | V8 migration (+ `role_name`), PermissionEnum, PermissionRegistry, RoleName, RolePermissionModel, RoleModel relação, repositories (`findByRoleName`), validador + testes | — | — |
| 2 — Token + seed + auto-atribuição | JwtTokenCustomizer (claim `permissions`), SecurityConfig converter, DataSeeder (bundle ADMIN + role + backfill + `role_name` tags + `seedDrivers`), RegistrationService (auto `role_id` em Client/Driver) + testes | 1 | 3 |
| 3 — CRUD role-permissions | DTOs, mapper, service, controller (`hasAuthority`) + testes | 1 | 2 |
| 4 — Migração de rotas | RoleController + ClientController `hasRole`→`hasAuthority` + testes | 2, 3 | — |

## 1. Fase 1 — Fundação (schema + catálogo de permissões)

- [x] 1.1 Teste unitário `PermissionRegistryTest`: `all()` retorna exatamente os valores do enum, sem duplicatas
- [x] 1.2 Teste unitário `PermissionEnumTest`: todos os valores são `verb_resource` minúsculo em inglês; `crudFor("roles")` retorna os 5 esperados
- [x] 1.3 Slice/persistência `RolePermissionRepositoryTest` (H2): round-trip de `permissions` `List<String>` ⇄ JSON; soft delete some da listagem padrão
- [x] 1.4 Teste `RoleRepositoryTest`: `findByRoleName` localiza a role independente do valor atual de `name` (renomear não quebra o lookup)
- [x] 1.5 Criar migração `V8__create_role_permissions_table.sql`: tabela `role_permissions` (id, token, name unique, permissions jsonb, timestamps, deleted_at) + `roles.role_permissions_id` (nullable, unique, FK) + `roles.role_name` (nullable, unique, varchar) — não editar V1–V7
- [x] 1.6 Criar `PermissionEnum` em `br.com.vanep.auth.security` (CRUD de roles, role_permissions, clients; helper `crudFor`)
- [x] 1.7 Criar `PermissionRegistry.all()` (fonte única de valores válidos)
- [x] 1.8 Criar `RoleName` enum (`ADMIN`, `CLIENT`, `DRIVER`) em `br.com.vanep.role`
- [x] 1.9 Criar `RolePermissionModel` em `br.com.vanep.rolepermission.model` (token via `@PrePersist`, `@SoftDelete`, converter/tipo JSON para `List<String>`)
- [x] 1.10 Adicionar em `RoleModel`: relação com o bundle (`role_permissions_id`) e `@Enumerated(EnumType.STRING) roleName` (nullable, unique) — `name` (string livre, CRUD existente) permanece intocado
- [x] 1.11 Criar `RolePermissionRepository`; estender `RoleRepository` com `findByRoleName(RoleName)`
- [x] 1.12 Criar validador Bean Validation `@PermissionsInRegistry` (ou validador de lista) checando cada string contra `PermissionRegistry`
- [x] 1.13 `make lint` + `make test-coverage` verdes; abrir PR da Fase 1

## 2. Fase 2 — Token + seed + auto-atribuição (ativa a cadeia)

- [x] 2.1 Teste `JwtTokenCustomizerTest`: usuário com bundle → claim `permissions` = lista do bundle + `roles` preservado; usuário sem role → `permissions` vazio
- [x] 2.2 Teste do converter em `SecurityConfig`: claim `permissions` vira `SimpleGrantedAuthority` sem prefixo
- [x] 2.3 Teste `DataSeederTest`: cria bundle ADMIN com `PermissionRegistry.all()`, role ADMIN ligado (via `RoleName.ADMIN`), e é idempotente; `seedRoles()` tageia as 3 roles com `role_name`; `seedDrivers()` cria driver(s) `APPROVED` e idempotente
- [x] 2.4 Teste `RegistrationServiceTest`: `registerClient`/`registerDriver` setam `user.role_id` para a role tagueada `CLIENT`/`DRIVER` respectivamente
- [x] 2.5 Alterar `JwtTokenCustomizer` para resolver `role_id → role → role_permissions.permissions` e emitir o claim `permissions`
- [x] 2.6 Estender o converter do `SecurityConfig` para mapear `permissions` → authorities (sem `ROLE_`)
- [x] 2.7 Alterar `DataSeeder`: `seedRoles()` passa a tagear `role_name`; bundle ADMIN + role ADMIN (por `RoleName.ADMIN`) + backfill de `role_id` em usuários ADMIN (idempotente); novo `seedDrivers()` (paralelo a `seedClients()`, driver(s) `approval_status = APPROVED`)
- [x] 2.8 Alterar `RegistrationService.registerClient`/`registerDriver` para resolver a role via `RoleRepository.findByRoleName` e setar `user.role_id` antes de salvar; aplicar o mesmo em `DataSeeder.seedClients()`
- [x] 2.9 `make lint` + `make test-coverage` verdes; abrir PR da Fase 2

## 3. Fase 3 — CRUD de role-permissions

- [ ] 3.1 Teste `RolePermissionServiceTest` (Mockito): create/update validam nome único e permissões no registry; find/delete por token
- [ ] 3.2 Slice `RolePermissionControllerTest` (`MockMvc`+security): 201/200/404/400 e 403 sem a permissão exigida
- [ ] 3.3 Criar request DTOs (`RolePermissionCreateRequestDTO`, `RolePermissionUpdateRequestDTO`) com `@Valid`/validador do 1.10
- [ ] 3.4 Criar `RolePermissionService` (create, findAll paginado, findByToken, update, delete soft)
- [ ] 3.5 Criar `RolePermissionMapper`
- [ ] 3.6 Criar `RolePermissionResponseDTO` (expõe `token`, nunca `id`)
- [ ] 3.7 Criar `RolePermissionController` (`/api/role-permissions`) com `@PreAuthorize("hasAuthority('...')")` por método
- [ ] 3.8 `make lint` + `make test-coverage` verdes; abrir PR da Fase 3

## 4. Fase 4 — Migração das rotas existentes

- [ ] 4.1 Atualizar slice de `RoleControllerTest` e `ClientControllerTest`: permitido com a permissão, 403 sem ela, branch de posse do client preservado
- [ ] 4.2 `RoleController`: trocar `hasRole('ADMIN')` por `hasAuthority('list_roles'|show_role|create_role|update_role|delete_role)` por método (restore → `update_role`)
- [ ] 4.3 `ClientController`: `list`→`hasAuthority('list_clients')`, `get`→`hasAuthority('show_client') or @clientSecurity.isOwner(...)`, `delete`→`hasAuthority('delete_client')`; `update` (posse) inalterado
- [ ] 4.4 `make lint` + `make test-coverage` verdes; abrir PR da Fase 4
