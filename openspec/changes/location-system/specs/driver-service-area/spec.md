## ADDED Requirements

### Requirement: Driver declares public service areas

The system SHALL expose `GET /api/drivers/me/service-areas` and `PUT /api/drivers/me/service-areas` for the authenticated driver. The `PUT` MUST accept a list of Google `placeId` values, each with an optional `sessionToken`, and MUST replace the driver's whole set of areas.

Each area MUST persist as a row in `driver_service_area` with `driver_id`, a required `city_id`, and a nullable `district_id`, where `district_id = NULL` means the whole city. The table MUST NOT contain street, number, complement, or zip code columns.

#### Scenario: Driver registers a district

- **WHEN** an authenticated driver sends a `placeId` that resolves to the district "Taguatinga"
- **THEN** the system persists one area row with the Brasília city and the Taguatinga district

#### Scenario: Driver registers a whole city

- **WHEN** an authenticated driver sends a `placeId` that resolves only to a city with no districts registered
- **THEN** the system persists one area row with `district_id` null

#### Scenario: Replacing the set

- **WHEN** a driver who has areas `[Taguatinga, Águas Claras]` sends a `PUT` with only `[Taguatinga]`
- **THEN** the system removes the Águas Claras area
- **AND** keeps only Taguatinga

#### Scenario: Non-driver rejected

- **WHEN** an authenticated user without a driver profile calls `PUT /api/drivers/me/service-areas`
- **THEN** the system returns `403 Forbidden`

#### Scenario: Unauthenticated access

- **WHEN** a request without a valid Bearer token calls either endpoint
- **THEN** the system returns `401 Unauthorized`

### Requirement: District required by curated policy

The system MUST reject a service area whose resolved chain carries no district-level component when the city is under a policy that requires one. The policy is `COALESCE(city.requires_district, city.state.requires_district)`.

`state.requires_district` MUST be `NOT NULL DEFAULT false` and curated by migration, seeded `true` for `DF` and `SP`. `city.requires_district` MUST be nullable, where `NULL` means "inherit from the state"; it exists as a per-city override for states whose cities are heterogeneous, and MUST NOT be populated by the lazy resolver.

The decision MUST be derived from the resolved chain and the curated flags only. The system MUST NOT decide it by counting districts already registered under the city: that makes an identical request valid or invalid depending on when it is sent, and lets the earliest drivers in a launch market permanently claim a whole city.

The effective flag MUST be read through the loaded `city → state` chain at validation time, and MUST NOT be copied onto the city row when the city is created.

This prevents a driver in the Federal District — which has a single municipality — from implicitly claiming the entire 5,800 km² region.

#### Scenario: City level rejected under a requiring state

- **WHEN** a driver submits a `placeId` resolving to `[BR, DF, Brasília]` with no district component
- **AND** `DF.requires_district` is true and `Brasília.requires_district` is null
- **THEN** the system returns `400 Bad Request` with a pt-BR message resolved through MessageSource

#### Scenario: City level rejected on the very first registration

- **WHEN** the geographic tree contains no district at all under "Brasília"
- **AND** a driver submits a `placeId` resolving to `[BR, DF, Brasília]`
- **THEN** the system still returns `400 Bad Request`

#### Scenario: City level accepted in a small city

- **WHEN** a driver submits a `placeId` resolving to a city whose effective policy is false
- **THEN** the system accepts the city-level area

#### Scenario: City override wins over the state

- **WHEN** a city has `requires_district` set to false and its state has `requires_district` true
- **THEN** the system accepts a city-level area for that city

#### Scenario: District component satisfies the policy

- **WHEN** a driver submits a `placeId` resolving to `[BR, DF, Brasília, Taguatinga]`
- **THEN** the system accepts it regardless of the policy flags

### Requirement: Service areas are public

The system SHALL treat service areas as public data, exposing them in driver-facing responses as region names and tokens.

#### Scenario: Areas visible in driver detail

- **WHEN** any authenticated user reads a driver's public profile
- **THEN** the system returns the driver's service areas as region names with opaque tokens

### Requirement: Legacy free-text location removed

The system MUST remove `driver.city` and `driver.service_areas`. No backfill is performed, since no production data exists.

#### Scenario: Columns dropped

- **WHEN** the removal migration runs
- **THEN** `driver.city` and `driver.service_areas` no longer exist
- **AND** the driver DTOs no longer expose them
