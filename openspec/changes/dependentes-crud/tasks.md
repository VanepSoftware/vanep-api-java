## 0. Preparação

- [x] 0.1 Criar branch `feat/dependentes-crud` a partir de `main`
- [ ] 0.2 Fazer `git merge origin/main` na branch (incorporar PR #54 `@SoftDelete` e PR #56 convenções)
- [ ] 0.3 Revisar artifacts da change (`proposal.md`, `design.md`, `specs/dependentes/spec.md`)

## 1. PR1 — Fundação (schema + persistência)

> Objetivo: migration, entity, repository. Abrir PR1 → `main` ao concluir.
> Commits: 1 arquivo por commit.

- [ ] 1.1 Criar `V7__create_dependent_table.sql` (tabela `dependent`, FK `client_id`, default `shift = MORNING`, índices únicos parciais em `token` e `document`)
- [ ] 1.2 Criar enum `br.com.vanep.dependente.enums.Shift` (MORNING, AFTERNOON, NIGHT, FULLTIME)
- [ ] 1.3 Criar `br.com.vanep.dependente.entity.DependenteEntity` (`@SoftDelete`, `@PrePersist` para token, sem campo `deletedAt`)
- [ ] 1.4 Criar `DependenteRepository` (`findByToken`, `findByClientId`, `countByClientId`, `existsByDocument`; native `restoreByToken`, `existsDeletedByToken`)
- [ ] 1.5 Adicionar `findByUserId(Long userId)` em `ClientRepository`
- [ ] 1.6 Validar: `./mvnw verify` passa após PR1
- [ ] 1.7 Abrir PR1 `feat(dependentes): fase 1 — fundação` → `main`

## 2. PR2 — API REST (DTOs + service + controller)

> Objetivo: endpoints CRUD + restore + RN12. Merge `main` na branch antes de iniciar.
> Commits: 1 arquivo por commit.

- [ ] 2.1 Criar `DependenteCreateDTO` com Bean Validation (`name` obrigatório, demais opcionais)
- [ ] 2.2 Criar `DependenteUpdateDTO` (campos opcionais para PATCH)
- [ ] 2.3 Criar `DependenteResponseDTO` (record, sem `id` interno)
- [ ] 2.4 Criar `DependenteMapper` (DTO → entity, entity → response)
- [ ] 2.5 Criar `DependenteService` — create (ownership, RN12 primeiro dependente)
- [ ] 2.6 Implementar em `DependenteService` — list, getByToken, update (inclui troca de `is_default`)
- [ ] 2.7 Implementar em `DependenteService` — delete via `repository.delete()` + promoção de padrão
- [ ] 2.8 Implementar em `DependenteService` — restore via `restoreByToken` (native query)
- [ ] 2.9 Criar `DependenteController` com rotas `/api/dependentes` e `@PreAuthorize`
- [ ] 2.10 Validar: `./mvnw verify` passa após PR2
- [ ] 2.11 Abrir PR2 `feat(dependentes): fase 2 — API REST` → `main`

## 3. PR3 — Seed + testes

> Objetivo: dados de teste e cobertura completa. Merge `main` na branch antes de iniciar.
> Commits: 1 arquivo por commit.

- [ ] 3.1 Estender seed com user CLIENT + perfil `client` de teste (se ainda não existir)
- [ ] 3.2 Criar `DependenteSeeder` (2 dependentes: um padrão, um não-padrão; idempotente)
- [ ] 3.3 Integrar `DependenteSeeder` no `DataSeeder`
- [ ] 3.4 Estender `src/test/resources/db/clean.sql` com `delete from dependent;` (antes de `client`)
- [ ] 3.5 Criar `DependenteServiceTest` — RN12 (primeiro padrão, troca de padrão, promoção ao delete)
- [ ] 3.6 Criar `DependenteControllerTest` — POST create (201, 401, 400)
- [ ] 3.7 Adicionar testes — GET list/detail (200, 403, 404)
- [ ] 3.8 Adicionar testes — PATCH update (200, 403)
- [ ] 3.9 Adicionar testes — DELETE (204) + GET após delete (404)
- [ ] 3.10 Adicionar testes — POST restore (200, 409) + GET após restore (200)
- [ ] 3.11 Adicionar testes — ROLE_ADMIN acessa dependentes de qualquer client
- [ ] 3.12 Validar: `./mvnw spotless:check` e `./mvnw verify` (JaCoCo) passam
- [ ] 3.13 Abrir PR3 `feat(dependentes): fase 3 — seed e testes` → `main`

## 4. Encerramento

- [ ] 4.1 Revisão com o time antes do merge final
- [ ] 4.2 Arquivar change OpenSpec após merge de PR3 (`opsx-archive`)
