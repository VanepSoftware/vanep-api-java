## ADDED Requirements

### Requirement: Client me includes home address

The system MUST include the authenticated client's home address on `GET /api/clients/me` as a nested `AddressResponseDTO` named `address`, or `null` when `address_id` is null. The system MUST NOT expose numeric `address_id`. Authorization MUST remain `isAuthenticated()` with the existing CLIENT profile lookup (`403`/`404` as today when the caller is not a client with a profile).

#### Scenario: Me with address

- **WHEN** an authenticated CLIENT who has a linked address calls `GET /api/clients/me`
- **THEN** the system returns HTTP 200
- **AND** `address` contains token, zipCode, street, number, complement, district, cityToken, cityName, and stateUf

#### Scenario: Me without address

- **WHEN** an authenticated CLIENT with null `address_id` calls `GET /api/clients/me`
- **THEN** the system returns HTTP 200
- **AND** `address` is null

#### Scenario: Unauthenticated me

- **WHEN** `GET /api/clients/me` is called without a valid Bearer token
- **THEN** the system returns HTTP 401

### Requirement: Client upserts home address on me

The system SHALL expose `PUT /api/clients/me/address` for the authenticated CLIENT identified by JWT `uid`. The body MUST be `@Valid AddressRequestDTO`. On success the system MUST upsert that client's exclusive address row and return HTTP 200 with `AddressResponseDTO`. A non-CLIENT authenticated user MUST receive 403 or 404 consistent with `getMyProfile`. Invalid CEP or missing cityToken MUST return 400 from Bean Validation or 404 for unknown city.

#### Scenario: Create home address

- **WHEN** an authenticated CLIENT with no address PUTs a valid payload
- **THEN** the system returns HTTP 200 with the new address token
- **AND** `GET /api/clients/me` then includes that address

#### Scenario: Update home address in place

- **WHEN** an authenticated CLIENT who already has an address PUTs a different complement
- **THEN** the system returns HTTP 200
- **AND** the address token is unchanged
- **AND** the persisted complement is the new value

#### Scenario: Unauthenticated put

- **WHEN** `PUT /api/clients/me/address` is called without a valid Bearer token
- **THEN** the system returns HTTP 401

### Requirement: Client clears home address on me

The system SHALL expose `DELETE /api/clients/me/address` for the authenticated CLIENT. On success the system MUST soft-delete the client's address row, null `client.address_id`, and return HTTP 204. When there is no address, the system MUST return HTTP 204 (idempotent) or HTTP 404; implementation MUST pick **204 idempotent** so the app can always DELETE.

#### Scenario: Delete existing address

- **WHEN** an authenticated CLIENT with an address calls `DELETE /api/clients/me/address`
- **THEN** the system returns HTTP 204
- **AND** `GET /api/clients/me` has `address` null

#### Scenario: Delete when none

- **WHEN** an authenticated CLIENT with null `address_id` calls `DELETE /api/clients/me/address`
- **THEN** the system returns HTTP 204

### Requirement: Client list and get expose nested address not catalog token

`GET /api/clients` and `GET /api/clients/{token}` MUST return nested `address` (`AddressResponseDTO` or null) and MUST NOT return `addressToken`. `PUT /api/clients/{token}` MUST accept `photo` only for role fields (no `addressToken`). Home address writes go through `/api/clients/me/address`. `PUT /api/clients/{token}` authorization MUST remain `@sec.isClientOwner`.

#### Scenario: Get by token includes address object

- **WHEN** the owner or a caller with `show_client` GETs `/api/clients/{token}` for a client who has an address
- **THEN** the response contains `address.token` and street fields
- **AND** the response does not contain `addressToken`

#### Scenario: Put with addressToken is ignored or rejected

- **WHEN** the owner PUTs `/api/clients/{token}` with body containing `addressToken`
- **THEN** the system does not link a catalog row via that field
- **AND** either the extra property is ignored (Jackson default) or the request is validated without an `addressToken` field on the DTO
