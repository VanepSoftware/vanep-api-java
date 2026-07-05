## Why

The Vanep product requires drivers to register and manage their vehicles (vans, school buses, etc.) for use in school transportation routes, contracts, and proposals. Currently, the backend has no table, endpoints, or tests for the `Vehicle` entity. Implementing a full CRUD for vehicles will enable drivers to manage their vehicles and allow clients/admins to see which vehicles are registered.

## What Changes

- New Flyway migration `V7__create_vehicle_table.sql` with a `deleted_at` column and partial unique indexes (`WHERE deleted_at IS NULL`), aligned with the soft delete pattern.
- New feature package `br.com.vanep.vehicle` with subpackages by role (`controller`, `dto`, `entity` if needed or root of feature package, `mapper`, `repository`, `service`, `security`) and explicit suffixes.
- `Vehicle` entity class with `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)`.
- Authenticated REST endpoints under `/api/vehicles`:
  - `POST /api/vehicles` — create (linked to the driver)
  - `GET /api/vehicles` — list (filtered by ownership for `ROLE_DRIVER`, showing all for `ROLE_ADMIN`)
  - `GET /api/vehicles/{token}` — detail
  - `PUT /api/vehicles/{token}` — update
  - `DELETE /api/vehicles/{token}` — soft delete
  - `POST /api/vehicles/{token}/restore` — restore (removes soft delete timestamp)
- Authorization: `ROLE_DRIVER` accesses and manages only their own vehicles; `ROLE_ADMIN` accesses all; `ROLE_CLIENT` has read-only access (or no access, depending on design; let's allow `ROLE_ADMIN` and `ROLE_DRIVER`).
- Automated tests covering all endpoints (MockMvc + JWT) and service rules; cleanup with `db/clean.sql`.

## Capabilities

### New Capabilities

- `vehicle`: REST CRUD for vehicles with `@SoftDelete`, restore via native query, ownership validation, role-based authorization, and test data seeder.

### Modified Capabilities

- _(none)_

## Impact

- **Database**: new `vehicle` table (FK `driver_id` pointing to `driver.id`, plate, brand, model, year, color, capacity, photo_front_url, photo_side_url, photo_document_url).
- **API**: new endpoints under `/api/vehicles/**`; public identifiers via `token`.
- **Security**: `@PreAuthorize` and ownership validation in the controller/service.
- **Seed**: extension of seed with vehicles for test drivers.
- **Tests**: new slice tests and unit tests; `clean.sql` extended with `vehicle`; JaCoCo on `verify`.
- **Delivery**: single branch `feat-(N-33)/CRUD-of-vehicles` with 3 sequential PRs to `main` (foundation → API → seed/tests), atomic commits.
