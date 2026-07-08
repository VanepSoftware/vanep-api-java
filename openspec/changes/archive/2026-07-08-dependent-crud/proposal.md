## Why

The Vanep product requires clients (guardians) to register and manage dependents — transported students — for reuse in proposals and contracts (UC13, RF-119 to RF-123, RN12). The backend has no table, endpoints, or tests for this entity yet; without them the frontend (S14) and proposal flow (S10) are blocked.

## What Changes

- New Flyway migration `V7__create_dependent_table.sql` with `deleted_at` column and partial unique indexes (`WHERE deleted_at IS NULL`), aligned with the `V6__soft_delete_partial_unique_indexes.sql` pattern.
- New feature package `br.com.vanep.dependent` with subpackages by role (`controller`, `dto`, `entity`, `enums`, `mapper`, `repository`, `service`, `seed`) and explicit suffixes (CONSTITUTION rule 5).
- `DependentModel` with `@SoftDelete(columnName = "deleted_at", strategy = TIMESTAMP)` — same pattern as `User`, `Client`, and `Driver` (PR #54).
- New `Shift` enum (`MORNING`, `AFTERNOON`, `NIGHT`, `FULLTIME`).
- Authenticated REST endpoints under `/api/dependent`:
  - `POST /api/dependent` — create
  - `GET /api/dependent` — list (filtered by ownership for `ROLE_CLIENT`)
  - `GET /api/dependent/{token}` — detail
  - `PATCH /api/dependent/{token}` — update (includes `is_default`)
  - `DELETE /api/dependent/{token}` — soft delete via `repository.delete()` (`@SoftDelete`)
  - `POST /api/dependent/{token}/restore` — restore via native query (`deleted_at = NULL`)
- Authorization: `ROLE_CLIENT` accesses only dependents of their `client_id`; `ROLE_ADMIN` accesses all.
- RN12 rule: the sole dependent becomes default automatically; with two or more, the client sets it manually via `is_default`.
- Default delete rule: if exactly one active dependent remains, promote it to default; if zero or two or more remain, no active default until the client sets one again.
- Test dependent seeder (requires client in seed).
- Automated tests covering all endpoints (MockMvc + JWT); cleanup with `db/clean.sql` (native DELETE).
- `ClientRepository` extension with `findByUserId` to resolve ownership from the JWT.

## Capabilities

### New Capabilities

- `dependent`: REST CRUD for dependents with `@SoftDelete`, restore via native query, default dependent rules (RN12), role-based authorization, and test data seeder.

### Modified Capabilities

- _(none — no existing specs in `openspec/specs/`)_

## Impact

- **Database**: new `dependent` table (FK `client_id → client.id`; `school_id` and `address_id` nullable without FK until `school` and `address` tables exist); partial unique indexes on `token` and `document`.
- **API**: new endpoints under `/api/dependent/**`; public identifiers via `token` (never numeric `id`).
- **Security**: `@PreAuthorize` and ownership validation in the service; routes under `/api/**` already require JWT.
- **Seed**: extension of `DataSeeder` or dedicated feature seeder.
- **Tests**: new slice tests; `clean.sql` extended with `dependent`; JaCoCo on `verify`.
- **Delivery**: single branch `feat/dependent-crud` with 3 sequential PRs to `main` (foundation → API → seed/tests), atomic commits (1 file per commit).
