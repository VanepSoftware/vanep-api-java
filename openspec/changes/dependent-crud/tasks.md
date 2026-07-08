## 0. Preparation

- [x] 0.1 Create branch `feat/dependent-crud` from `main`
- [x] 0.2 Run `git merge origin/main` on the branch (incorporate PR #54 `@SoftDelete` and PR #56 conventions)
- [x] 0.3 Review change artifacts (`proposal.md`, `design.md`, `specs/dependent/spec.md`)

## 1. PR1 — Foundation (schema + persistence)

> Goal: migration, entity, repository. Open PR1 → `main` when done.
> Commits: 1 file per commit.

- [x] 1.1 Create `V7__create_dependent_table.sql` (`dependent` table, FK `client_id`, default `shift = MORNING`, partial unique indexes on `token` and `document`)
- [x] 1.2 Create enum `br.com.vanep.dependent.enums.Shift` (MORNING, AFTERNOON, NIGHT, FULLTIME)
- [x] 1.3 Create `br.com.vanep.dependent.model.DependentModel` (`@SoftDelete`, `@PrePersist` for token, no `deletedAt` field)
- [x] 1.4 Create `DependentRepository` (`findByToken`, `findByClientId`, `countByClientId`, `existsByDocument`; native `restoreByToken`, `existsDeletedByToken`)
- [x] 1.5 Add `findByUserId(Long userId)` to `ClientRepository`
- [x] 1.6 Validate: `./mvnw verify` passes after PR1
- [ ] 1.7 Open PR1 `feat(dependent): phase 1 — foundation` → `main`

## 2. PR2 — REST API (DTOs + service + controller)

> Goal: CRUD endpoints + restore + RN12. Merge `main` into the branch before starting.
> Commits: 1 file per commit.

- [x] 2.1 Create `DependentCreateDTO` with Bean Validation (`name` required, others optional)
- [x] 2.2 Create `DependentUpdateDTO` (optional fields for PATCH)
- [x] 2.3 Create `DependentResponseDTO` (record, no internal `id`)
- [x] 2.4 Create `DependentMapper` (DTO → entity, entity → response)
- [x] 2.5 Create `DependentService` — create (ownership, RN12 first dependent)
- [x] 2.6 Implement in `DependentService` — list, getByToken, update (includes `is_default` change)
- [x] 2.7 Implement in `DependentService` — delete via `repository.delete()` + default promotion
- [x] 2.8 Implement in `DependentService` — restore via `restoreByToken` (native query)
- [x] 2.9 Create `DependentController` with `/api/dependent` routes and `@PreAuthorize`
- [x] 2.10 Validate: `./mvnw verify` passes after PR2
- [ ] 2.11 Open PR2 `feat(dependent): phase 2 — REST API` → `main`

## 3. PR3 — Seed + tests

> Goal: test data and full coverage. Merge `main` into the branch before starting.
> Commits: 1 file per commit.

- [x] 3.1 Extend seed with CLIENT user + test `client` profile (if not already present)
- [x] 3.2 Create `DependentSeeder` (2 dependents: one default, one non-default; idempotent)
- [x] 3.3 Integrate `DependentSeeder` into `DataSeeder`
- [x] 3.4 Extend `src/test/resources/db/clean.sql` with `delete from dependent;` (before `client`)
- [x] 3.5 Create `DependentServiceTest` — RN12 (first default, default switch, promotion on delete)
- [x] 3.6 Create `DependentControllerTest` — POST create (201, 401, 400)
- [x] 3.7 Add tests — GET list/detail (200, 403, 404)
- [x] 3.8 Add tests — PATCH update (200, 403)
- [x] 3.9 Add tests — DELETE (204) + GET after delete (404)
- [x] 3.10 Add tests — POST restore (200, 409) + GET after restore (200)
- [x] 3.11 Add tests — ROLE_ADMIN accesses dependents of any client
- [x] 3.12 Validate: `./mvnw spotless:check` and `./mvnw verify` (JaCoCo) pass
- [ ] 3.13 Open PR3 `feat(dependent): phase 3 — seed and tests` → `main`

## 4. Wrap-up

- [ ] 4.1 Team review before final merge
- [ ] 4.2 Archive OpenSpec change after PR3 merge (`opsx-archive`)
