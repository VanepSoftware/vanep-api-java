## ADDED Requirements

### Requirement: School resolved from a place

The system SHALL expose `POST /api/schools/resolve` accepting a Google `placeId` with an optional `sessionToken`, and returning a persisted `school`, creating it on first use. The `school` record MUST carry `google_place_id` (unique), `name`, `city_id`, and a nullable `district_id`. The columns `cnpj`, `phone`, and `email` MUST be removed, since Google does not provide them.

The verb MUST be `POST`, not `GET`. The operation performs `findOrCreate`, which is a write: a `GET` is defined as safe and is prefetchable and cacheable by intermediaries, so a browser prefetch would create a school row.

The operation MUST remain idempotent by `google_place_id`, returning the same row on repeated calls: `201 Created` when the row is created, `200 OK` when it already existed.

#### Scenario: First resolution creates the school

- **WHEN** a `placeId` for a school is resolved for the first time
- **THEN** the system creates the school row with its `google_place_id`
- **AND** returns `201 Created` with an opaque token

#### Scenario: Second resolution reuses the school

- **WHEN** the same school `placeId` is resolved again
- **THEN** the system returns the existing row without creating a duplicate
- **AND** returns `200 OK`

#### Scenario: Unauthenticated access

- **WHEN** a request without a valid Bearer token calls `POST /api/schools/resolve`
- **THEN** the system returns `401 Unauthorized`

### Requirement: School resolution is rate limited

The system MUST rate limit `POST /api/schools/resolve` per user. Each distinct `placeId` triggers a paid `Place Details` call and creates a row, so an authenticated caller sweeping place ids would generate both external cost and unbounded rows.

#### Scenario: Rate limit applied

- **WHEN** a user exceeds the configured resolution rate limit
- **THEN** the system rejects the request without calling Google
- **AND** creates no school row
