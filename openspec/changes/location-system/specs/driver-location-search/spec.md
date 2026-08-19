## ADDED Requirements

### Requirement: Search drivers by origin and destination

The system SHALL expose `GET /api/drivers/search` accepting `originPlaceId` and `destinationPlaceId`. The destination MUST accept any place, not only schools. The system MUST return a paginated list of drivers whose service areas cover **both** points.

Both place ids MUST be resolved server-side to anchors in the geographic tree. Resolution MUST be read-only.

#### Scenario: Driver covering both points

- **WHEN** a client searches with an origin in Taguatinga and a destination in Águas Claras
- **AND** a driver has service areas for both Taguatinga and Águas Claras
- **THEN** the system includes that driver in the results

#### Scenario: Driver covering only one point excluded

- **WHEN** a client searches with an origin in Taguatinga and a destination in Águas Claras
- **AND** a driver has a service area only for Taguatinga
- **THEN** the system excludes that driver

#### Scenario: Non-school destination accepted

- **WHEN** a client searches with a destination `placeId` that is a residential address
- **THEN** the system performs the same matching as for a school destination

### Requirement: Ancestor containment matching

The system SHALL match a service area against a point when the area's city equals the point's city **and** either the area has no district, or the area's district is the point's anchor district or one of its ancestors.

The system MUST NOT require PostGIS, geometry types, or materialized paths, so that the query runs on both PostgreSQL and the H2 test database.

#### Scenario: Broad area covers a specific point

- **WHEN** a driver registered the whole city "Brasília" with no district
- **AND** a client searches for a point resolving to "QNL 5 Conjunto J"
- **THEN** the system matches that driver

#### Scenario: Ancestor district covers a deeper point

- **WHEN** a driver registered the district "Taguatinga"
- **AND** a client searches for a point whose anchor is "QNL 5" under Taguatinga
- **THEN** the system matches that driver

#### Scenario: Sibling district does not match

- **WHEN** a driver registered only "Águas Claras"
- **AND** a client searches for a point anchored in "Taguatinga"
- **THEN** the system does not match that driver

#### Scenario: Different city does not match

- **WHEN** a driver registered "Campinas"
- **AND** a client searches for a point in "Brasília"
- **THEN** the system does not match that driver

#### Scenario: Query runs on H2

- **WHEN** the search slice and repository tests run against the in-memory H2 database
- **THEN** the matching query executes without spatial extensions

### Requirement: Broad city search lists all drivers in the city

The system SHALL support a search whose anchor is a city, returning every driver with a service area in that city regardless of district.

#### Scenario: City search returns district level drivers

- **WHEN** a client searches for "Brasília"
- **AND** drivers exist with areas in Taguatinga, in Águas Claras, and city-wide
- **THEN** the system returns all three drivers

### Requirement: Search does not mutate the tree

The system MUST NOT create geographic nodes while serving a search.

#### Scenario: Deep unknown place creates nothing

- **WHEN** a client searches with a `placeId` resolving to components deeper than any existing node
- **THEN** the system anchors at the deepest existing node
- **AND** performs no insert on `state`, `city`, or `district`

### Requirement: School resolved from a place

The system SHALL expose `GET /api/schools/resolve` accepting a Google `placeId` and returning a persisted `school`, creating it on first use. The `school` record MUST carry `google_place_id` (unique), `name`, `city_id`, and a nullable `district_id`. The columns `cnpj`, `phone`, and `email` MUST be removed, since Google does not provide them.

#### Scenario: First resolution creates the school

- **WHEN** a `placeId` for a school is resolved for the first time
- **THEN** the system creates the school row with its `google_place_id`
- **AND** returns it with an opaque token

#### Scenario: Second resolution reuses the school

- **WHEN** the same school `placeId` is resolved again
- **THEN** the system returns the existing row without creating a duplicate
