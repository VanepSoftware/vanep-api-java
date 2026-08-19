## ADDED Requirements

### Requirement: Personal address created from a resolved place

The system SHALL expose `PUT /api/user/me/address` and `GET /api/user/me/address` for the authenticated caller. The request MUST accept a Google `placeId` plus optional `number` and `complement`. The system MUST resolve the place server-side via Place Details and MUST NOT trust address components supplied by the client.

The resulting `address` row MUST link to the geographic tree through `city_id` and a nullable `district_id`, and MUST store `google_place_id`. The free-text `district` column MUST be removed in favour of the FK.

Controllers MUST stay thin; resolution and persistence MUST live in a `@Service`.

#### Scenario: Address created from place id

- **WHEN** an authenticated user sends `PUT /api/user/me/address` with a valid `placeId`
- **THEN** the system resolves the place, creates or reuses the tree chain, persists the address
- **AND** returns `200 OK` with an explicit response DTO exposing opaque tokens

#### Scenario: Client supplied components ignored

- **WHEN** a request carries address component fields in addition to `placeId`
- **THEN** the system ignores them and uses only the server-side Place Details result

#### Scenario: Complement preserved

- **WHEN** the request includes a `complement` that Google does not provide
- **THEN** the system persists the user-supplied complement unchanged

#### Scenario: Unknown place id

- **WHEN** the request carries a `placeId` that Place Details cannot resolve
- **THEN** the system returns `400 Bad Request` with a pt-BR message resolved through MessageSource

#### Scenario: Unauthenticated access

- **WHEN** a request without a valid Bearer token calls `PUT /api/user/me/address` or `GET /api/user/me/address`
- **THEN** the system returns `401 Unauthorized`

### Requirement: Personal address available to every role

The system SHALL allow a personal address for client, driver, assistant, and dependent. The missing foreign keys on `school.address_id` and `dependent.address_id` MUST be created, and `assistant` MUST gain an `address_id` column.

#### Scenario: Assistant gains an address

- **WHEN** an authenticated assistant sets a personal address
- **THEN** the system persists it and links it through `assistant.address_id`

#### Scenario: Referential integrity enforced

- **WHEN** the migration adding the foreign keys runs
- **THEN** `school.address_id` and `dependent.address_id` reference `address(id)`

### Requirement: Personal address is private

The system MUST NOT expose street, number, complement, or zip code of a personal address in any driver-facing or search response. Personal address data MUST only be readable by its owner.

#### Scenario: Address absent from search results

- **WHEN** a client performs a driver search
- **THEN** no street, number, complement, or zip code of any driver appears in the response

#### Scenario: Owner reads own address

- **WHEN** an authenticated user reads `GET /api/user/me/address`
- **THEN** the system returns the full address for that caller only
