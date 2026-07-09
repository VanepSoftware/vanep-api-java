# dependent Specification

## Purpose
TBD - created by archiving change dependent-crud. Update Purpose after archive.
## Requirements
### Requirement: Dependent persistence with soft delete

The system MUST persist dependents in the `dependent` table according to the DBML schema (`vanep-dbdiagram`), including a `deleted_at` column for soft delete. The entity MUST use `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)` — same pattern as `User`, `Client`, and `Driver` (PR #54). Records with `deleted_at` set MUST be filtered automatically by Hibernate in standard JPA queries.

The migration MUST create partial unique indexes (`WHERE deleted_at IS NULL`) on `token` and `document`, following the `V6__soft_delete_partial_unique_indexes.sql` pattern.

#### Scenario: Active record visible

- **WHEN** a dependent exists with null `deleted_at`
- **THEN** the system includes it in listings and allows read by `token`

#### Scenario: Soft-deleted record hidden

- **WHEN** a dependent has `deleted_at` set
- **THEN** the system returns HTTP 404 on read by `token` on standard routes
- **AND** the record remains in the database for eventual restore

### Requirement: Public identifiers via token

The system MUST expose and accept dependent identifiers as opaque `token` strings (25-character string). The system MUST NOT expose the internal numeric `id` in API URLs or response bodies.

#### Scenario: Response without numeric id

- **WHEN** a client queries a dependent
- **THEN** the response contains `token` and does not contain the internal `id` field

### Requirement: Feature-based naming with explicit suffixes

Feature files MUST follow CONSTITUTION rule 5: subpackages by role (`controller`, `dto`, `model`, `enums`, `mapper`, `repository`, `service`, `seed`) and suffixes matching the subpackage (`DependentController`, `DependentModel`, `DependentCreateDTO`, etc.).

#### Scenario: Model in the correct subpackage

- **WHEN** the feature is implemented
- **THEN** the JPA model lives in `model/DependentModel.java` with `@SoftDelete`

### Requirement: Client reference via token in responses

The system MUST NOT expose numeric `client_id` in API responses. When a dependent's client is returned, the system MUST nest a `client` object containing only the client's `token`.

#### Scenario: Response nests client token

- **WHEN** a client queries a dependent
- **THEN** the response contains `client.token` and does not contain `clientId` or internal client `id`

### Requirement: School and address references via token

The system MUST NOT expose numeric `school_id` or `address_id` in API requests or responses. School and address MUST be referenced by `schoolToken` / `addressToken` on input and nested `school.token` / `address.token` on output. Until `school` and `address` tables exist, supplying `schoolToken` or `addressToken` on create/update MUST return HTTP 400.

#### Scenario: School or address token rejected until tables exist

- **WHEN** an authenticated user sends a create or update request with `schoolToken` or `addressToken`
- **THEN** the system returns HTTP 400 with a message in English

### Requirement: Dependent creation

The system MUST allow an authenticated user with `ROLE_CLIENT` to create a dependent linked to their `client_id`. The system MUST allow a user with `ROLE_ADMIN` to create a dependent for any `client_id` supplied in the request.

Accepted creation fields: `name` (required), `birth_date`, `gender`, `document`, `phone`, `email`, `is_self`, `shift`, `schoolToken`, `addressToken`, and `clientToken` (ADMIN only).

#### Scenario: Client creates dependent successfully

- **WHEN** an authenticated `ROLE_CLIENT` user sends `POST /api/dependent` with a valid `name`
- **THEN** the system returns HTTP 201
- **AND** the dependent is persisted with the authenticated client's `client_id`
- **AND** a unique `token` is generated automatically

#### Scenario: Creation without authentication

- **WHEN** a `POST /api/dependent` request is sent without a valid JWT
- **THEN** the system returns HTTP 401

#### Scenario: Name required

- **WHEN** an authenticated user sends a create request without `name`
- **THEN** the system returns HTTP 400 with a validation message in English

### Requirement: Dependent listing

The system MUST allow `GET /api/dependent` for users with `ROLE_CLIENT` or `ROLE_ADMIN`. `ROLE_CLIENT` users MUST receive only active dependents (`deleted_at` null) for their `client_id`. `ROLE_ADMIN` users MUST receive all active dependents.

#### Scenario: Client lists only their own

- **WHEN** an authenticated `ROLE_CLIENT` calls `GET /api/dependent`
- **THEN** the system returns HTTP 200 with a list containing only dependents of their `client_id`
- **AND** no soft-deleted dependents are included

#### Scenario: Admin lists all

- **WHEN** an authenticated `ROLE_ADMIN` calls `GET /api/dependent`
- **THEN** the system returns HTTP 200 with all active dependents in the system

### Requirement: Dependent read by token

The system MUST allow `GET /api/dependent/{token}` for `ROLE_CLIENT` (only if owner) and `ROLE_ADMIN` (any active record).

#### Scenario: Client accesses own dependent

- **WHEN** a `ROLE_CLIENT` calls `GET /api/dependent/{token}` for a dependent of their `client_id`
- **THEN** the system returns HTTP 200 with the `DependentResponse`

#### Scenario: Client accesses another client's dependent

- **WHEN** a `ROLE_CLIENT` calls `GET /api/dependent/{token}` for another client's dependent
- **THEN** the system returns HTTP 403

#### Scenario: Dependent not found or deleted

- **WHEN** the `token` does not exist or the record is soft-deleted
- **THEN** the system returns HTTP 404 with a message in English

### Requirement: Dependent update

The system MUST allow `PATCH /api/dependent/{token}` for partial update of editable fields. The same ownership rules as read MUST apply (`ROLE_CLIENT` edits only their own; `ROLE_ADMIN` edits any).

#### Scenario: Successful partial update

- **WHEN** the owner sends `PATCH /api/dependent/{token}` with valid fields
- **THEN** the system returns HTTP 200 with the updated dependent
- **AND** `updated_at` is updated

#### Scenario: Client attempts to edit another client's dependent

- **WHEN** a `ROLE_CLIENT` sends `PATCH` for a dependent of another `client_id`
- **THEN** the system returns HTTP 403

### Requirement: Dependent soft delete

The system MUST allow `DELETE /api/dependent/{token}` via `repository.delete(entity)`, which under `@SoftDelete` sets `deleted_at` to the current timestamp without removing the record from the database.

#### Scenario: Successful delete

- **WHEN** the owner or admin sends `DELETE /api/dependent/{token}` for an active dependent
- **THEN** the system returns HTTP 204
- **AND** `deleted_at` is set by Hibernate

#### Scenario: Delete of already deleted record

- **WHEN** attempting to delete an already soft-deleted dependent
- **THEN** the system returns HTTP 404

### Requirement: Soft-deleted dependent restore

The system MUST allow `POST /api/dependent/{token}/restore` to clear `deleted_at` and reactivate the record. Because `@SoftDelete` prevents reading deleted records via JPA, restore MUST use a native query in the repository (`UPDATE dependent SET deleted_at = NULL WHERE token = :token`).

#### Scenario: Successful restore

- **WHEN** the owner or admin sends `POST /api/dependent/{token}/restore` for a soft-deleted dependent
- **THEN** the system returns HTTP 200 with the restored dependent
- **AND** `deleted_at` is cleared via native query

#### Scenario: Restore of active record

- **WHEN** attempting to restore a dependent that is not soft-deleted
- **THEN** the system returns HTTP 409 with a message in English

### Requirement: Default dependent (RN12)

The system MUST implement RN12 via the `is_default` field:

- When creating the **first** active dependent for a `client_id`, the system MUST set `is_default = true` automatically.
- When creating additional dependents, the system MUST set `is_default = false` by default, unless the request explicitly sets `is_default = true`.
- When setting `is_default = true` on a dependent, the system MUST set `is_default = false` on all other active dependents of the same `client_id`.
- Only one active dependent per `client_id` MUST have `is_default = true` at any time.

#### Scenario: First dependent becomes default

- **WHEN** a client with no active dependents creates the first one
- **THEN** the created dependent has `is_default = true`

#### Scenario: Setting new default unsets others

- **WHEN** a client with two active dependents updates one to `is_default = true`
- **THEN** only that dependent remains with `is_default = true`
- **AND** the previous one is updated to `is_default = false`

### Requirement: Default promotion when deleting the default dependent

When soft-deleting the dependent with `is_default = true`, the system MUST apply:

- If **exactly one** active dependent remains for the `client_id`, MUST promote it to `is_default = true`.
- If **zero** or **two or more** active dependents remain, MUST leave none with `is_default = true` until the client sets one manually.

#### Scenario: Delete default with one remaining

- **WHEN** a client with two active dependents (one default) deletes the default
- **THEN** the remaining dependent has `is_default = true`

#### Scenario: Delete default with multiple remaining

- **WHEN** a client with three active dependents deletes the default
- **THEN** the two remaining dependents have `is_default = false`

### Requirement: Input validation via DTOs

The system MUST validate requests with Bean Validation on dedicated DTOs (`@Valid` on the controller). The system MUST NOT accept direct request-body binding to a JPA entity.

#### Scenario: Duplicate document

- **WHEN** a create or update sets `document` already used by another active dependent
- **THEN** the system returns HTTP 409 with a message in English

### Requirement: Test dependent seeder

With `vanep.seed.enabled=true`, the system MUST populate valid test dependents linked to a seed client, including a default dependent scenario.

#### Scenario: Idempotent seed

- **WHEN** the seeder runs and test dependents already exist
- **THEN** the seeder does not duplicate records

### Requirement: Automated test coverage

The system MUST include automated tests covering all endpoints (`create`, `read`, `update`, `delete`, `restore`), including authentication (401), authorization (403), not found (404), and RN12 scenarios. Cleanup between tests MUST use `src/test/resources/db/clean.sql` with native DELETE (including `dependent`), because `repository.deleteAll()` is soft delete under `@SoftDelete`.

#### Scenario: Green CI

- **WHEN** `./mvnw verify` is run after implementation
- **THEN** all tests pass and the minimum JaCoCo coverage is met

#### Scenario: Cleanup between tests

- **WHEN** an integration test needs to clear dependents
- **THEN** it uses the native SQL script in `clean.sql`, not `repository.deleteAll()`

