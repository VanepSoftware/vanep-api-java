## ADDED Requirements

### Requirement: Cities provide a stable nearby-search point

Every city in the API's IBGE catalog MUST store the latitude and longitude of its municipal seat, or its official locality point for the Fernando de Noronha state district, from the versioned IBGE 2022 locality catalog. The database MUST reject a coordinate without its pair and MUST reject values outside valid geographic ranges.

#### Scenario: Existing catalog cities receive coordinates

- **WHEN** the coordinate data migration runs on an existing city with IBGE code `5300108`
- **THEN** both latitude and longitude are populated

#### Scenario: A partial coordinate is rejected

- **WHEN** a write provides latitude without longitude
- **THEN** the database rejects the write

### Requirement: City coordinates are exposed through addresses

City responses and address responses MUST expose nullable `cityLatitude` and `cityLongitude` fields. The values MUST come from the referenced city and MUST NOT expose an internal database identifier.

#### Scenario: A personal address supplies the search origin

- **WHEN** an authenticated user reads an address whose city has coordinates
- **THEN** the response contains that city's `cityLatitude` and `cityLongitude`

#### Scenario: A custom city has no known point

- **WHEN** a city has no coordinates
- **THEN** both response fields are null

### Requirement: Coordinate loading is offline and deterministic

Migration, seed, and automated tests MUST NOT call Google or another external geocoding service. A fresh seeded database and an upgraded database MUST use the same committed IBGE coordinate catalog.

#### Scenario: A fresh database is seeded

- **WHEN** the application seeds municipalities after migrations
- **THEN** each created IBGE city receives the coordinate from the committed catalog
