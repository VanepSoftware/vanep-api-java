## ADDED Requirements

### Requirement: An owned address is submitted as a Google place

The system SHALL accept an owned address (dependent or school) as `AddressRequestDTO` carrying `placeId`, an optional `sessionToken`, and the caller-supplied `number` and `complement`. The system MUST NOT accept `cityToken`, `zipCode` or `street` from the caller, because it resolves all three from `Place Details` and the client cannot be trusted with components the system already owns.

The system MUST resolve the place through `PlacesClient.findPlaceDetails(placeId, sessionToken)` and persist the geography through `LocationResolverService.resolveAndPersist`, filling city, deepest district, street, zip code and `google_place_id` on the `address` row.

#### Scenario: Dependent created with an address

- **WHEN** a client creates a dependent with `address` carrying a valid `placeId`
- **THEN** the system resolves the place and persists one `address` row
- **AND** that row carries the city, district, street and zip code returned by `Place Details`
- **AND** the response `address` is the resolved `AddressResponseDTO`

#### Scenario: Caller-supplied components are ignored

- **WHEN** a request body carries `cityToken`, `zipCode` or `street` alongside `placeId`
- **THEN** the system persists only what `Place Details` resolved
- **AND** no caller-supplied component reaches the `address` row

#### Scenario: The session token closes the autocomplete session

- **WHEN** a request carries both `placeId` and `sessionToken`
- **THEN** the system forwards the `sessionToken` to `Place Details`

#### Scenario: The number is taken from the caller before the place

- **WHEN** the caller sends a `number` and `Place Details` also returns a street number
- **THEN** the `address` row stores the caller's `number`

#### Scenario: The number falls back to the place

- **WHEN** the caller sends no `number` and `Place Details` returns a street number
- **THEN** the `address` row stores the number from the place

### Requirement: Creating an address requires a place

The system MUST return HTTP 400 when an `address` object is present, the owner has no address yet, and `placeId` is missing or blank. The message MUST be resolved from MessageSource key `address.place_required`.

The system MUST NOT create an `address` row without a resolved city, because `address.city_id` is NOT NULL and a row without geography cannot be searched.

#### Scenario: Address object without a place on a new owner

- **WHEN** a dependent with no address is created or updated with `address` carrying only `number`
- **THEN** the system returns HTTP 400
- **AND** the message is resolved from `address.place_required`
- **AND** no `address` row is created

### Requirement: An existing address is amended without reselecting the place

The system SHALL accept an `address` object carrying only `number` and `complement` when the owner already has an address, and MUST update those two columns while keeping the resolved city, district, street, zip code and `google_place_id` unchanged.

Sending a `placeId` for an owner that already has an address MUST re-resolve and replace the whole row in place, keeping the same `address.id` so the owner pointer and its unique index are untouched.

#### Scenario: Only the number changes

- **WHEN** a dependent with an address is updated with `address` carrying `number` and no `placeId`
- **THEN** the stored street, city, district, zip code and `google_place_id` are unchanged
- **AND** the stored number is the new one
- **AND** `Place Details` is not called

#### Scenario: The place is replaced

- **WHEN** a dependent with an address is updated with `address` carrying a different `placeId`
- **THEN** the row is re-resolved from the new place
- **AND** the `address.id` is the same row as before

#### Scenario: Clearing the address is unchanged

- **WHEN** a dependent is updated with `address` present and JSON null
- **THEN** the system soft-deletes the row and nulls `dependent.address_id`, as it already does

### Requirement: Place failures are reported, not swallowed

The system MUST return HTTP 400 when `Place Details` cannot resolve the `placeId`, and MUST return HTTP 400 with key `location.address.street_required` when the resolved place carries no street, because an owned address without a street is not an address.

The system MUST NOT persist a partially resolved address.

#### Scenario: Unknown place

- **WHEN** a request carries a `placeId` that `Place Details` does not resolve
- **THEN** the system returns HTTP 400
- **AND** no `address` row is created or modified

#### Scenario: Place without a street

- **WHEN** the resolved place carries no street component
- **THEN** the system returns HTTP 400 with the message from `location.address.street_required`
- **AND** no `address` row is created or modified

### Requirement: One place-resolution path for every address

The system MUST resolve a place into an `address` row in exactly one place in the codebase. `PersonalAddressService` and `AddressService` MUST both call that collaborator rather than each holding a copy of the `Place Details` → street → chain → columns sequence.

#### Scenario: The personal address keeps its behaviour

- **WHEN** `PUT /api/user/me/address` is called after the extraction
- **THEN** the stored row carries the same city, district, street, zip code, number, complement and `google_place_id` it carried before
- **AND** its existing tests pass unchanged
