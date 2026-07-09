## Context

Vanep API (Spring Boot 4, Java 25, JPA/Flyway/PostgreSQL) organizes code by feature (`br.com.vanep.<feature>`) with subpackages by architectural role (CONSTITUTION rule 5, PR #56). `User`, `Client`, and `Driver` entities use `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)` — Hibernate automatically filters deleted records and `repository.delete()` performs soft delete (PR #54). Migration `V6__soft_delete_partial_unique_indexes.sql` converted simple UNIQUE constraints into partial indexes (`WHERE deleted_at IS NULL`) to allow re-registration after soft delete.

**There is no REST CRUD** implemented for business entities beyond `/api/user/profile`.

The `dependent` schema is defined in `vanep-dbdiagram/vanep-diagram.dbml` (domain 3 — students). The product (`vanep-project-overview`) requires dependent management by the client (UC13, RF-119–123, RN12). The frontend does not consume these endpoints yet.

**Delivery branch:** `feat/dependent-crud` (single branch), with 3 sequential PRs to `main`, atomic commits (1 file per commit), merge commit (no squash).

## Goals / Non-Goals

**Goals:**

- Implement full dependent CRUD with `@SoftDelete` and restore via native query.
- Align schema with the `dependent` table in DBML (fields and FKs).
- Authorization: `ROLE_CLIENT` (ownership by `client_id`) + `ROLE_ADMIN` (global access).
- Implement RN12 (`is_default`) and default promotion when deleting the default dependent.
- Seeder + MockMvc tests covering all endpoints.
- Deliver in 3 small, reviewable PRs.

**Non-Goals:**

- CRUD for `school` or `address` (nullable FKs, no DB constraint for now).
- Proposal/contract endpoints that reference `dependent_id`.
- Pagination on `GET /api/dependent` (can be added later if needed).
- Dependent photo/avatar upload.
- Frontend integration (not implemented yet).

## Decisions

### 1. English naming throughout

- **Decision:** table `dependent`, REST routes under `/api/dependent` (singular, like `/api/user/profile`), feature package `br.com.vanep.dependent`, classes prefixed with `Dependent` — consistent with `client`, `driver`, and `user`. JPA class lives in `model/DependentModel` (not `entity/`).
- **Rationale:** single English vocabulary in singular form across DB, API, code, and product specs; `Model` suffix for persistence classes in new features.

### 2. Public identifier: `token`, never `id`

- **Decision:** paths use `{token}`; responses expose `token` via `DependentResponseDTO`.
- **Rationale:** CONSTITUTION rule 13; pattern already used in `User`, `Client`, `Driver`.

### 3. Update via PATCH (partial)

- **Decision:** `PATCH /api/dependent/{token}` only.
- **Alternative:** PUT — rejected; partial update is the common case (e.g. setting `is_default`).

### 4. Migration V7 schema

> `V6` is already `soft_delete_partial_unique_indexes.sql` (PR #54). The `dependent` table lands in **V7**.

```sql
-- V7__create_dependent_table.sql
create table dependent (
  id          bigint generated always as identity primary key,
  token       varchar(32)  not null,
  client_id   bigint       not null references client (id),
  school_id   bigint,
  address_id  bigint,
  name        varchar(255) not null,
  birth_date  date,
  gender      varchar(16),
  document    varchar(64),
  phone       varchar(32),
  email       varchar(255),
  is_self     boolean      not null default false,
  is_default  boolean      not null default false,
  shift       varchar(16)  not null default 'MORNING',
  created_at  timestamptz  not null default now(),
  updated_at  timestamptz  not null default now(),
  deleted_at  timestamptz
);

-- Partial unique indexes (V6 pattern — active records only)
create unique index dependent_token_active_key
  on dependent (token) where deleted_at is null;
create unique index dependent_document_active_key
  on dependent (document) where deleted_at is null;
```

- **`shift`:** NOT NULL with default `MORNING` (team decision; aligned with DBML).
- **`gender`:** reuse `br.com.vanep.user.Gender` (MALE, FEMALE, OTHER).
- **`shift`:** new enum `br.com.vanep.dependent.enums.Shift` (MORNING, AFTERNOON, NIGHT, FULLTIME).
- **No simple `UNIQUE`** on `token`/`document` — use partial indexes from the start.

### 5. Soft delete via `@SoftDelete` (PR #54 pattern)

- **Decision:** `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)` on the entity; **no** mapped `deletedAt` field.
- **Delete:** `repository.delete(entity)` — Hibernate sets `deleted_at` automatically.
- **Queries:** Hibernate filters `deleted_at IS NULL` in all JPQL/Criteria; use `findByToken`, `findByClientId` (no `AndDeletedAtIsNull` suffix).
- **Restore:** `@SoftDelete` prevents reading deleted records via JPA — restore MUST use a **native query** in the repository:

```java
@Modifying
@Query(value = "UPDATE dependent SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
int restoreByToken(@Param("token") String token);

@Query(value = "SELECT count(*) > 0 FROM dependent WHERE token = :token AND deleted_at IS NOT NULL", nativeQuery = true)
boolean existsDeletedByToken(@Param("token") String token);
```

- **Rationale:** aligned with `User`, `Client`, `Driver`; avoids leaking deleted records by forgetting a manual filter.

### 6. Two-layer authorization

- **Controller:** `@PreAuthorize("hasAnyRole('CLIENT','ADMIN')")` on all endpoints.
- **Service:** resolve `User` from JWT subject (email) → `Client` via `ClientRepository.findByUserId` → validate ownership.
- **Admin:** bypass ownership; may supply `client_id` on create.
- **Rationale:** CONSTITUTION rules 18–19; defense in depth.

### 7. Ownership resolution

```
JWT subject (email)
  → UserRepository.findByEmail(email)        -- @SoftDelete filters automatically
  → ClientRepository.findByUserId(user.getId())
  → client.getId() used as the dependent's client_id
```

- **New method:** `ClientRepository.findByUserId(Long userId)`.

### 8. RN12 — `is_default` logic in the service

| Event | Behavior |
|-------|----------|
| Create (0 active) | `is_default = true` forced |
| Create (1+ active) | `is_default = false` unless explicitly requested |
| Update `is_default=true` | unsets other active records for the same `client_id` |
| Delete default, 1 remains | promote the remaining one |
| Delete default, 0 or 2+ remain | no default |

### 9. HTTP status codes

| Operation | Status |
|-----------|--------|
| POST create | 201 + body |
| GET list/detail | 200 |
| PATCH update | 200 + body |
| DELETE | 204 |
| POST restore | 200 + body |
| Unauthenticated | 401 |
| No permission (ownership) | 403 |
| Not found / soft-deleted (read) | 404 |
| Restore of active record | 409 |
| Duplicate document (active) | 409 |

### 10. Package structure (CONSTITUTION rule 5)

```
br.com.vanep.dependent/
├── controller/
│   └── DependentController.java
├── dto/
│   ├── DependentCreateDTO.java
│   ├── DependentUpdateDTO.java
│   └── DependentResponseDTO.java
├── model/
│   └── DependentModel.java         @SoftDelete, table "dependent"
├── enums/
│   └── Shift.java
├── mapper/
│   └── DependentMapper.java
├── repository/
│   └── DependentRepository.java    + restoreByToken (native)
├── service/
│   └── DependentService.java
└── seed/
    └── DependentSeeder.java
```

### 11. Seeder

- `DependentSeeder` in the feature package, orchestrated by `DataSeeder` when `vanep.seed.enabled=true`.
- Prerequisite: test client in seed (CLIENT user + client profile).
- Creates 2 dependents: one default (`is_default=true`) and one non-default.

### 12. Tests

- `DependentControllerTest`: `@SpringBootTest` + `MockMvc` + `jwt()` (`AuthEndpointsTest` pattern).
- **Cleanup:** extend `src/test/resources/db/clean.sql` with `delete from dependent;` **before** `client` — `repository.deleteAll()` is soft delete under `@SoftDelete` and does not clear data between tests.
- Setup: CLIENT user + client + ADMIN user; ownership, CRUD, restore, and RN12 scenarios.
- `DependentServiceTest`: unit tests for RN12 rules and promotion on delete.

### 13. PR plan (same branch)

| PR | Base | Contents |
|----|------|----------|
| PR1 — Foundation | `main` | V7 migration, `Shift`, `DependentModel`, `DependentRepository`, `ClientRepository.findByUserId` |
| PR2 — API | `main` (after PR1 merge) | DTOs, mapper, service, controller |
| PR3 — Seed + tests | `main` (after PR2 merge) | seeder, `clean.sql`, full test suite |

Flow: `git merge main` on the branch after each merge; merge commit on GitHub.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| `school_id`/`address_id` without FK — orphan data | Nullable columns; future validation when tables exist |
| `shift` NOT NULL on dependent vs shift on proposal | Default `MORNING`; proposal can override later |
| Restore requires native query (`@SoftDelete` limitation) | Dedicated repository methods; tests covering restore |
| PR2 depends on PR1 merged | Explicit sequence; single branch with `main` merge between phases |
| First project CRUD — becomes template | Follow CONSTITUTION and `@SoftDelete` pattern from PR #54 |
| `document` unique among active records — soft-deleted frees the value | Partial index `WHERE deleted_at IS NULL` |

## Migration Plan

1. Deploy migration V7 via Flyway (automatic on startup).
2. No existing data in `dependent` — rollback = new drop migration if needed (do not edit V7).
3. Optional seed via `vanep.seed.enabled=true` in dev.

## Open Questions

- _(none open — decisions closed in the explore session and aligned with PR #54/#56)_
