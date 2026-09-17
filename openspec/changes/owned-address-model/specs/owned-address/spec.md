## ADDED Requirements

### Requirement: Exclusive owned address rows

The system MUST treat each `address` row as owned by at most one of: one client, one dependent, or one school. The same `address.id` MUST NOT be stored on two active (`deleted_at IS NULL`) rows of the same owner type. The system MUST NOT reuse a row for a second person or school because CEP and street number match. Complement, street, and number on that row belong only to that owner.

#### Scenario: Two clients at the same building get two rows

- **WHEN** client A upserts an address with a given CEP, street, and number
- **AND** client B upserts an address with the same CEP, street, and number
- **THEN** the system persists two `address` rows
- **AND** each client's `address_id` points at a different `id`

#### Scenario: Linking an already owned address is rejected

- **WHEN** an `address.id` is already referenced by an active client, dependent, or school
- **AND** a different owner would be assigned that same `id`
- **THEN** the system MUST NOT update the second owner's `address_id` to that `id`
- **AND** the system returns HTTP 409
- **AND** the message is resolved from MessageSource key `address.already_owned`

### Requirement: Foreign keys and unique pointers

A new Flyway migration (not a rewrite of V14) MUST add foreign keys from `client.address_id`, `dependent.address_id`, and `school.address_id` to `address.id`. The migration MUST add partial unique indexes on each pointer so two active rows of that table cannot share `address_id`. The migration MUST NOT clone or rewrite existing address rows (no data backfill). Local development MUST recreate the database so Flyway and the seeder start from empty schema; H2 tests already start empty.

#### Scenario: Unique pointer rejects a second client on the same id

- **WHEN** an active client already stores a given `address_id`
- **AND** another active client would persist the same `address_id`
- **THEN** the database unique index rejects the second pointer

#### Scenario: V14 is unchanged

- **WHEN** the change is applied
- **THEN** `V14__create_address_table.sql` checksum remains as already applied

### Requirement: Upsert and clear by owner

The system MUST create an `address` row and set the owner's `address_id` when the owner has none and a valid address payload is submitted. The system MUST update that owner's existing row in place when the owner already has `address_id`. The system MUST soft-delete that row via `repository.delete` and null the pointer when the owner clears the address. Controllers MUST stay thin; this logic MUST live in `AddressService`. Request bodies MUST use `AddressRequestDTO` with Bean Validation (`@Valid`), never a JPA model. Responses MUST use `AddressResponseDTO` (opaque `token`, never numeric `id`). City MUST be resolved by `cityToken`; missing city MUST return 404 with key `city.not_found`.

#### Scenario: First save creates and links

- **WHEN** a client with null `address_id` submits a valid address payload
- **THEN** a new `address` row is persisted
- **AND** `client.address_id` equals that row's `id`

#### Scenario: Second save updates the same row

- **WHEN** that client submits a new street on a later upsert
- **THEN** the same `address.id` is updated
- **AND** no second row is created for that client

#### Scenario: Clear soft-deletes the owner's row

- **WHEN** the owner clears their address
- **THEN** Hibernate soft-deletes that `address` row
- **AND** the owner's `address_id` is null
- **AND** standard queries no longer return that address

### Requirement: Owner soft delete also soft-deletes the address

When an active client, dependent, or school is soft-deleted, the system MUST in the same transaction soft-delete that owner's `address` row (via `AddressService` clear) and set the owner's `address_id` to null **before** soft-deleting the owner. The system MUST NOT leave an active `address` row whose only pointer is a soft-deleted owner. Restore of a dependent or school MUST succeed without resurrecting the previous address; the restored owner has `address` null. Client has no restore endpoint; the same clear-on-delete rule still applies. Exclusivity checks (`address.already_owned`) MUST count only active owners.

#### Scenario: Deleting a client hides the home address

- **WHEN** an admin soft-deletes a client who has an address
- **THEN** that `address` row is soft-deleted
- **AND** default JPA queries do not return it
- **AND** the deleted client's `address_id` is null

#### Scenario: Restore dependent does not restore pickup address

- **WHEN** a dependent with an address is soft-deleted and later restored
- **THEN** the system returns HTTP 200
- **AND** the restored dependent has `address` null

#### Scenario: Deleted owner does not block a new owner's exclusivity slot

- **WHEN** client A is soft-deleted (address cleared)
- **AND** client B upserts a new address
- **THEN** client B succeeds with a new `address` row
- **AND** no `address.already_owned` conflict is raised because of A


### Requirement: No standalone address HTTP catalog

The system MUST NOT expose `/api/addresses` (list, show, create, update, delete, or restore). Address rows MUST be created, updated, read, and cleared only through the owning resource (client, dependent, or school) via `AddressService`. The system MUST NOT provide an HTTP path that persists an `address` row with no owner pointer. Permission names `list_addresses`, `show_address`, `create_address`, `update_address`, and `delete_address` MUST be removed from `PermissionEnum` and from role permission bundles. `AddressRequestDTO` / `AddressResponseDTO` remain for nested payloads.

#### Scenario: Catalog routes are gone

- **WHEN** any caller requests `GET /api/addresses` or `POST /api/addresses`
- **THEN** the system does not serve `AddressController`
- **AND** the response is HTTP 404 (no mapping) or the equivalent of an unknown route — not a permission-gated catalog

#### Scenario: Write always has an owner

- **WHEN** a valid address payload is persisted
- **THEN** that row's `id` is stored on exactly one owner (`client`, `dependent`, or `school`) in the same use case
- **AND** no API creates an unlinked `address`
