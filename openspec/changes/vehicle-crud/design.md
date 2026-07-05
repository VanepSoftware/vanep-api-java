## Context

The Vanep backend (Spring Boot 4, Java 25, JPA/Flyway/PostgreSQL) organizes code by feature (`br.com.vanep.<feature>`) with subpackages by architectural role (CONSTITUTION rule 5).
Currently, the system has no table or logic for the `Vehicle` entity.
A vehicle belongs to a `Driver`. Drivers (`ROLE_DRIVER`) must be able to create, read, update, delete (soft delete), and restore their own vehicles. Admins (`ROLE_ADMIN`) must be able to manage all vehicles.

**Delivery branch:** `feat-(N-33)/CRUD-of-vehicles` (single branch), with 3 sequential PRs to `main`, atomic commits (1 file per commit), merge commit on GitHub.

## Goals / Non-Goals

**Goals:**

- Implement full vehicle CRUD with `@SoftDelete` and restore via native query.
- Align schema with a vehicle table design linked to `driver`.
- Authorization: `ROLE_DRIVER` (ownership by `driver_id` derived from JWT user token) and `ROLE_ADMIN` (global access).
- Plate uniqueness validation among active vehicles.
- Seeder + MockMvc tests covering all endpoints.
- Deliver in 3 small, reviewable PRs.

**Non-Goals:**

- Associate multiple drivers to one vehicle.
- Vehicle history or maintenance log tracking.
- Driver dashboard frontend integration.

## Decisions

### 1. English naming throughout

- **Decision:** table `vehicle`, REST routes under `/api/vehicles`, feature package `br.com.vanep.vehicle`, classes prefixed with `Vehicle`.
- **Rationale:** Aligned with project guidelines (CONSTITUTION rule 45).

### 2. Public identifier: `token`, never `id`

- **Decision:** HTTP request paths use `{token}`; responses expose `token` via DTOs.
- **Rationale:** CONSTITUTION rule 13; pattern already used in `User`, `Client`, `Driver`.

### 3. Update via PUT

- **Decision:** `PUT /api/vehicles/{token}` for updates.
- **Rationale:** Standard API convention for updating the resource.

### 4. Migration V7 schema

> The `vehicle` table lands in **V7**.

```sql
-- V7__create_vehicle_table.sql
create table vehicle (
  id                  bigint generated always as identity primary key,
  token               varchar(32)  not null,
  driver_id           bigint       not null references driver (id),
  plate               varchar(10)  not null,
  brand               varchar(100) not null,
  model               varchar(100) not null,
  year                integer      not null,
  color               varchar(50)  not null,
  capacity            integer      not null,
  photo_front_url     varchar(255),
  photo_side_url      varchar(255),
  photo_document_url  varchar(255),
  is_active           boolean      not null default true,
  created_at          timestamptz  not null default now(),
  updated_at          timestamptz  not null default now(),
  deleted_at          timestamptz
);

comment on table vehicle is 'Veículos cadastrados por motoristas.';

-- Partial unique indexes (active records only)
create unique index vehicle_token_active_key on vehicle (token) where deleted_at is null;
create unique index vehicle_plate_active_key on vehicle (plate) where deleted_at is null;
```

### 5. Soft delete via `@SoftDelete`

- **Decision:** `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)` on the entity; **no** mapped `deletedAt` field.
- **Delete:** `repository.delete(entity)` — Hibernate sets `deleted_at` automatically.
- **Restore:** Restore MUST use a **native query** in the repository:

```java
@Modifying
@Query(value = "UPDATE vehicle SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
int restoreByToken(@Param("token") String token);

@Query(value = "SELECT count(*) > 0 FROM vehicle WHERE token = :token AND deleted_at IS NOT NULL", nativeQuery = true)
boolean existsDeletedByToken(@Param("token") String token);
```

### 6. Two-layer authorization

- **Controller:** `@PreAuthorize("hasAnyRole('DRIVER','ADMIN')")` on all endpoints.
- **Service:** resolve `User` from JWT subject (email) → `Driver` via `DriverRepository.findByUserId` → validate ownership.
- **Admin:** bypass ownership; may supply `driverToken` on create.

### 7. Package structure

```
br.com.vanep.vehicle/
├── Vehicle.java                 Entity class mapping the table "vehicle"
├── controller/
│   └── VehicleController.java
├── dto/
│   ├── VehicleRequestDTO.java
│   └── VehicleResponseDTO.java
├── mapper/
│   └── VehicleMapper.java
├── repository/
│   └── VehicleRepository.java   Repository including restore queries
├── security/
│   └── VehicleSecurityService.java
└── service/
    └── VehicleService.java
```

### 8. HTTP status codes

| Operation | Status |
|-----------|--------|
| POST create | 201 + body |
| GET list/detail | 200 |
| PUT update | 200 + body |
| DELETE | 204 |
| POST restore | 200 + body |
| Unauthenticated | 401 |
| No permission (ownership) | 403 |
| Not found / soft-deleted | 404 |
| Restore of active record | 409 |
| Duplicate plate (active) | 409 |

## Tests

- `VehicleControllerTest`: `@SpringBootTest` + `MockMvc` + `jwt()`.
- **Cleanup:** extend `src/test/resources/db/clean.sql` with `delete from vehicle;` **before** `driver`.
- `VehicleServiceTest`: Unit tests for vehicle business logic, ownership checks, plate validation.
