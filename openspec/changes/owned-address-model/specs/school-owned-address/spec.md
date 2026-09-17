## ADDED Requirements

### Requirement: School address is an owned nested object

The system MUST persist each school's address as that school's exclusive `address` row. School HTTP APIs MUST NOT accept or return numeric `addressId`. Create (`POST /api/schools`) MUST accept an optional nested `address` (`AddressRequestDTO`, city via `cityToken`). Responses MUST include nested `address` (`AddressResponseDTO` or null). Public school `token` remains the school identifier.

When `POST /api/schools` includes `address`, the system MUST upsert after the school row exists.

Authorization remains existing school permissions (`create_school`, `update_school`, `show_school`, `list_schools`).

#### Scenario: Create school with address

- **WHEN** a caller with `create_school` posts a school with a valid nested `address`
- **THEN** the system returns HTTP 201
- **AND** the response `address` contains street and `cityToken`
- **AND** `addressId` is absent from the JSON

#### Scenario: Numeric addressId rejected

- **WHEN** a create or update body includes `addressId` as the way to set address (no nested object)
- **THEN** that field is not part of the request DTO
- **AND** the system does not set `school.address_id` from a client-supplied Long

### Requirement: School update is PATCH not PUT

The system MUST expose `PATCH /api/schools/{token}` for partial update, authorized with `update_school`. The system MUST NOT map `PUT /api/schools/{token}` (no alias). The PATCH body MUST be `SchoolUpdateRequestDTO` (or equivalent) where `name`, `cnpj`, `phone`, `email`, and `address` are each `JsonNullable` (or equivalent) so omitted JSON fields are distinct from explicit null. Controllers MUST stay thin. Address upsert/clear MUST use `AddressService`. The service MUST NOT copy the create `applyRequest` onto PATCH.

Field rules:

- Omitted → stored value unchanged.
- `name` present and non-blank → persist. `name` present and null or blank → HTTP 400. `name` MUST NOT be cleared.
- `cnpj`, `phone`, `email` present and non-null → persist (CNPJ 14 digits / e-mail format when a value is supplied). Present and JSON null → clear the column (nullable).
- `address` present as object → upsert that school's exclusive row. `"address": null` → clear (soft-delete the address row, null `address_id`).

CNPJ uniqueness on PATCH: when `cnpj` is present, non-null, and different from the stored value, the system MUST reject HTTP 409 if another **active** school already has that CNPJ (lookup MUST exclude this school's id). Re-sending the same CNPJ MUST be a no-op without 409. Clearing CNPJ MUST NOT run the duplicate check.

#### Scenario: Patch name only does not clear other scalars or address

- **WHEN** a school has name A, cnpj C, phone P, email E, and an address
- **AND** a caller with `update_school` PATCHes only `{ "name": "B" }`
- **THEN** the system returns HTTP 200
- **AND** cnpj remains C, phone P, email E, and the address row is unchanged

#### Scenario: Patch school without address keeps previous

- **WHEN** a school already has an address
- **AND** a caller with `update_school` PATCHes a new `name` without an `address` field
- **THEN** the system returns HTTP 200
- **AND** the school address row is unchanged

#### Scenario: Patch school with address updates owned row

- **WHEN** a caller with `update_school` PATCHes a nested `address` with a new street
- **THEN** the school's existing `address.id` is updated in place when one exists
- **OR** a row is created and linked when `address_id` was null

#### Scenario: Patch null address clears

- **WHEN** a caller with `update_school` PATCHes `"address": null`
- **THEN** the school has no address
- **AND** the previous address row is soft-deleted

#### Scenario: Patch null cnpj clears cnpj

- **WHEN** a school has a non-null cnpj
- **AND** a caller with `update_school` PATCHes `"cnpj": null`
- **THEN** the system returns HTTP 200
- **AND** stored cnpj is null

#### Scenario: Present blank name rejected

- **WHEN** a caller PATCHes `"name": ""` or `"name": null`
- **THEN** the system returns HTTP 400
- **AND** the stored name is unchanged

#### Scenario: Duplicate cnpj on another school

- **WHEN** school A has cnpj `11111111111111`
- **AND** school B PATCHes cnpj to `11111111111111`
- **THEN** the system returns HTTP 409
- **AND** B's cnpj is unchanged

#### Scenario: Same cnpj resent is no-op

- **WHEN** school A has cnpj `11111111111111`
- **AND** school A PATCHes cnpj to `11111111111111`
- **THEN** the system returns HTTP 200 without a duplicate conflict

#### Scenario: Put school is gone

- **WHEN** any caller sends `PUT /api/schools/{token}`
- **THEN** the system does not serve school update on that method
- **AND** the response is HTTP 404 (no mapping) or HTTP 405 Method Not Allowed — not a successful replace
