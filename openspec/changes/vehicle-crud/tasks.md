## 0. Preparation

- [x] 0.1 Confirm active branch `feat-(N-33)/CRUD-of-vehicles` from `main`
- [x] 0.2 Review change artifacts (`proposal.md`, `design.md`, `tasks.md`)

## 1. PR1 — Foundation (schema + persistence)

> Goal: migration, entity, repository, and driver repository extension. Open PR1 → `main` when done.
> Commits: 1 file per commit.

- [x] 1.1 Create `V7__create_vehicle_table.sql` (`vehicle` table, FK `driver_id`, photo URLs, partial unique indexes on `token` and `plate`)
- [x] 1.2 Add `findByToken(String token)` to `DriverRepository`
- [x] 1.3 Create `br.com.vanep.vehicle.Vehicle` (`@SoftDelete`, `@PrePersist` for token, no `deletedAt` field)
- [x] 1.4 Create `VehicleRepository` (`findByToken`, `findByDriverId`, `existsByPlate`, native `restoreByToken`, `existsDeletedByToken`)
- [x] 1.5 Validate: `./mvnw verify` (clean compile, no errors)
- [x] 1.6 Open PR1 `feat(vehicle): phase 1 — foundation` → `main`

## 2. PR2 — REST API (DTOs + service + controller)

> Goal: CRUD endpoints + restore + security. Merge `main` into the branch before starting.
> Commits: 1 file per commit.

- [x] 2.1 Create `VehicleRequestDTO` with Bean Validation (`plate`, `brand`, `model`, `manufactureYear`, `color`, `capacity` required, photo URLs optional; `driverToken` optional for admins)
- [x] 2.2 Create `VehicleResponseDTO` (record, no internal `id`, contains `driverToken` and vehicle info)
- [x] 2.3 Create `VehicleMapper` (DTO → entity, entity → response)
- [x] 2.4 Create `VehicleSecurityService` (check if the authenticated caller owns the vehicle by checking driver token vs caller uid)
- [x] 2.5 Create `VehicleService` — create (ownership lookup, plate unique validation, capacity > 0 validation)
- [x] 2.6 Implement in `VehicleService` — list, findByToken, update, delete (via `repository.delete()`), and restore (via native query)
- [x] 2.7 Create `VehicleController` with `/api/vehicles` routes and `@PreAuthorize`
- [x] 2.8 Validate: `./mvnw verify` passes after PR2
- [x] 2.9 Open PR2 `feat(vehicle): phase 2 — REST API` → `main`

## 3. PR3 — Seed + tests

> Goal: test data and full coverage. Merge `main` into the branch before starting.
> Commits: 1 file per commit.

- [x] 3.1 Extend `src/test/resources/db/clean.sql` with `delete from vehicle;` (before `driver`)
- [x] 3.2 Create a vehicle seeder or extend `DataSeeder` (add a test vehicle linked to a driver)
- [x] 3.3 Create `VehicleServiceTest` — unit tests for ownership, plate validation, capacity constraints
- [x] 3.4 Create `VehicleControllerTest` — integration tests for all endpoints (`POST`, `GET`, `PUT`, `DELETE`, `POST restore`) covering 200, 201, 204, 401, 403, 404, 409
- [x] 3.5 Validate: `./mvnw spotless:check` and `./mvnw verify` (JaCoCo) pass locally
- [x] 3.6 Open PR3 `feat(vehicle): phase 3 — seed and tests` → `main`

## 4. Wrap-up

- [x] 4.1 Team review before final merge
