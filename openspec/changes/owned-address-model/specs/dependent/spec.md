## MODIFIED Requirements

### Requirement: School and address references via token

The system MUST NOT expose numeric `school_id` or `address_id` in API requests or responses. School MUST be referenced by `schoolToken` on input and nested `school.token` on output. Until school **linking** is implemented in a later change, supplying `schoolToken` on create or PATCH (any value, including JSON null) MUST return HTTP 400. Omitting `schoolToken` on PATCH MUST leave `school_id` unchanged.

Address MUST NOT be referenced by `addressToken`. Address MUST be sent as a nested `address` object (`AddressRequestDTO`: `cityToken`, `zipCode`, `street`, `number`, `complement`, `district`) and returned as nested `AddressResponseDTO` (or null). Each dependent MUST have their own `address` row (1:1); it MUST NOT share `address_id` with the parent client or another dependent.

#### Scenario: School token rejected until linking exists

- **WHEN** an authenticated user sends a create or PATCH request with `schoolToken` present (non-null or JSON null)
- **THEN** the system returns HTTP 400

#### Scenario: Nested address is persisted

- **WHEN** an authenticated client sends a create or PATCH with a valid nested `address` and no `schoolToken`
- **THEN** the system does not return 400 for the address payload
- **AND** the dependent is stored with its own `address_id`

#### Scenario: addressToken is not accepted

- **WHEN** a create or PATCH body includes `addressToken` as the address field
- **THEN** that field is not on the request DTO
- **AND** the system does not resolve a catalog token into `dependent.address_id`

### Requirement: Dependent creation

The system MUST allow an authenticated user with `ROLE_CLIENT` to create a dependent linked to their `client_id`. The system MUST allow a user with `ROLE_ADMIN` to create a dependent for any `client_id` supplied in the request.

Accepted creation fields: `name` (required), `birth_date`, `gender`, `document`, `phone`, `email`, `is_self`, `shift`, nested `address` (optional), `schoolToken` (still rejected with HTTP 400 in this change), and `clientToken` (ADMIN only). Create remains a full POST body (not JsonNullable).

#### Scenario: Client creates dependent successfully

- **WHEN** an authenticated `ROLE_CLIENT` user sends `POST /api/dependent` with a valid `name`
- **THEN** the system returns HTTP 201
- **AND** the dependent is persisted with the authenticated client's `client_id`
- **AND** a unique `token` is generated automatically

#### Scenario: Client creates dependent with pickup address

- **WHEN** an authenticated `ROLE_CLIENT` user sends `POST /api/dependent` with a valid `name` and nested `address`
- **THEN** the system returns HTTP 201
- **AND** the response includes `address` with street and city fields
- **AND** the new address row is not the client's home `address_id`

#### Scenario: Creation without authentication

- **WHEN** a `POST /api/dependent` request is sent without a valid JWT
- **THEN** the system returns HTTP 401

#### Scenario: Name required

- **WHEN** an authenticated user sends a create request without `name`
- **THEN** the system returns HTTP 400 with a validation message in English

## ADDED Requirements

### Requirement: Dependent PATCH is JsonNullable merge

The system MUST expose `PATCH /api/dependent/{token}` with a body where every mutable field is `JsonNullable` (or equivalent) so omitted JSON is distinct from explicit null: `name`, `birthDate`, `gender`, `document`, `phone`, `email`, `isSelf`, `isDefault`, `shift`, `address`. Compact `undefined()` MUST match `UserProfileUpdateRequestDTO`. Controllers MUST stay thin. The mapper MUST NOT use `if (getX() != null)` as omit. Address upsert/clear MUST use `AddressService`. Ownership MUST remain: CLIENT only their dependents; ADMIN any.

`schoolToken` MUST NOT be a mergeable field: omitted → `school_id` unchanged; present → HTTP 400.

Field rules:

- Omitted → stored value unchanged.
- `name` present and non-blank → persist. `name` present and null or blank → HTTP 400. `name` MUST NOT be cleared.
- `isSelf`, `isDefault`, `shift` present and non-null → persist (`isDefault` still follows existing RN12). Present and JSON null → HTTP 400 (columns NOT NULL).
- `birthDate`, `gender`, `document`, `phone`, `email` present and non-null → persist. Present and JSON null → clear the column (nullable).
- `address` present as object → upsert that dependent's exclusive row. `"address": null` → clear (soft-delete the address row, null `address_id`).

Document uniqueness on PATCH: when `document` is present, non-null, and different from the stored value, the system MUST reject HTTP 409 if another **active** dependent already has that document (lookup MUST exclude this dependent's token). Re-sending the same document MUST be a no-op without 409. Clearing document MUST NOT run the duplicate check.

When `isDefault` is present, the existing default-dependent rule (RN12) MUST run via `isPresent()` (the service already applies RN12 on update today; the JsonNullable merge MUST keep that behavior).

#### Scenario: Patch name only does not clear other scalars or address

- **WHEN** a dependent has name A, phone P, email E, birthDate D, and an address
- **AND** the owner PATCHes only `{ "name": "B" }`
- **THEN** the system returns HTTP 200
- **AND** phone remains P, email E, birthDate D, and the address row is unchanged

#### Scenario: Patch omit address

- **WHEN** the owner PATCHes only `name`
- **THEN** the dependent `address_id` is unchanged

#### Scenario: Patch updates pickup address

- **WHEN** the owner PATCHes `/api/dependent/{token}` with a nested `address` and a new number
- **THEN** the system returns HTTP 200
- **AND** that dependent's address row is updated in place when one existed

#### Scenario: Patch null address clears

- **WHEN** the owner PATCHes `"address": null`
- **THEN** the dependent has no address
- **AND** the previous address row is soft-deleted

#### Scenario: Patch null phone clears phone

- **WHEN** a dependent has a non-null phone
- **AND** the owner PATCHes `"phone": null`
- **THEN** the system returns HTTP 200
- **AND** stored phone is null

#### Scenario: Present blank name rejected

- **WHEN** the owner PATCHes `"name": ""` or `"name": null`
- **THEN** the system returns HTTP 400
- **AND** the stored name is unchanged

#### Scenario: Present null isDefault rejected

- **WHEN** the owner PATCHes `"isDefault": null`
- **THEN** the system returns HTTP 400
- **AND** stored `is_default` is unchanged

#### Scenario: Duplicate document on another dependent

- **WHEN** dependent A has document `11111111111`
- **AND** dependent B PATCHes document to `11111111111`
- **THEN** the system returns HTTP 409
- **AND** B's document is unchanged

#### Scenario: Same document resent is no-op

- **WHEN** dependent A has document `11111111111`
- **AND** dependent A PATCHes document to `11111111111`
- **THEN** the system returns HTTP 200 without a duplicate conflict
