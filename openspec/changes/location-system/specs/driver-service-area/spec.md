## ADDED Requirements

### Requirement: Driver declares public service areas

The system SHALL expose `GET /api/drivers/me/service-areas` and `PUT /api/drivers/me/service-areas` for the authenticated driver. The `PUT` MUST accept a list of Google `placeId` values and MUST replace the driver's whole set of areas.

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

### Requirement: District required when the city has districts

The system MUST reject a service area that names only a city when that city already has at least one registered district. Cities with no registered districts MUST accept a city-level area.

This prevents a driver in the Federal District — which has a single municipality — from implicitly claiming the entire region.

#### Scenario: City level rejected where districts exist

- **WHEN** a driver submits a `placeId` resolving to "Brasília" and districts already exist under it
- **THEN** the system returns `400 Bad Request` with a pt-BR message resolved through MessageSource

#### Scenario: City level accepted in a small city

- **WHEN** a driver submits a `placeId` resolving to a city with no registered districts
- **THEN** the system accepts the city-level area

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
