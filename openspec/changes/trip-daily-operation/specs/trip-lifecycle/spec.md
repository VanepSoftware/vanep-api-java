## ADDED Requirements

### Requirement: One trip per driver, service date and shift

The system MUST persist at most one **active** `trip` row per `(driver_id, service_date, shift)`. A Flyway migration MUST enforce this with a partial unique index on those three columns, `WHERE deleted_at IS NULL`. The system MUST NOT rely on a read-then-insert check to guarantee uniqueness; it MUST let the database reject the duplicate and re-read the winning row.

`service_date` MUST be derived from the server clock in the `America/Sao_Paulo` zone. The system MUST NOT accept a date from the request body or from a client-supplied header.

#### Scenario: Starting a route twice returns the same trip

- **WHEN** an approved driver starts a route for a given shift
- **AND** the same driver starts a route again for the same shift on the same day
- **THEN** the system persists exactly one `trip` row
- **AND** the first call returns HTTP 201 and the second returns HTTP 200
- **AND** both responses carry the same trip `token`
- **AND** `started_at` keeps the value written by the first call

#### Scenario: Two simultaneous starts do not create a duplicate

- **WHEN** two concurrent requests start a route for the same driver, date and shift
- **THEN** exactly one `trip` row exists afterwards
- **AND** neither request returns HTTP 500
- **AND** both responses carry the same trip `token`

#### Scenario: Same driver runs two shifts on the same day

- **WHEN** an approved driver starts a route with shift `MORNING`
- **AND** the same driver starts a route with shift `AFTERNOON` on the same day
- **THEN** the system persists two `trip` rows
- **AND** each row carries its own `shift`

### Requirement: Trip status transitions

`TripStatus` MUST be a backed Java enum with the values `SCHEDULED`, `IN_PROGRESS`, `COMPLETED` and `CANCELLED`, matching `vanep-diagram.dbml`. The legal transitions MUST be `SCHEDULED → IN_PROGRESS` and `IN_PROGRESS → COMPLETED`. Every other transition MUST be rejected.

The decision MUST live in a policy class that depends on no Spring context, no JPA model and no servlet type, so it is testable without a database.

Starting a route MUST set `status = IN_PROGRESS` and write `started_at`. Finishing MUST set `status = COMPLETED` and write `finished_at`. The system MUST NOT overwrite `started_at` on a repeated start, nor `finished_at` on a repeated finish.

#### Scenario: Finishing a trip that was never started is rejected

- **WHEN** a driver finishes a route and no trip exists for today and that shift
- **THEN** the system returns HTTP 409
- **AND** the message is resolved from MessageSource key `trip.not_started`
- **AND** no `trip` row is created

#### Scenario: Starting a trip that is already completed is rejected

- **WHEN** a trip for today and that shift has `status = COMPLETED`
- **AND** the driver starts a route for the same shift
- **THEN** the system returns HTTP 409
- **AND** the message is resolved from MessageSource key `trip.already_completed`
- **AND** `finished_at` keeps its stored value

#### Scenario: Finishing twice is idempotent

- **WHEN** a trip is `COMPLETED`
- **AND** the driver finishes the same route again
- **THEN** the system returns HTTP 200
- **AND** `finished_at` keeps the value written by the first finish

#### Scenario: Transition policy is decided without a database

- **WHEN** the transition policy is exercised for every ordered pair of `TripStatus` values
- **THEN** it accepts only `SCHEDULED → IN_PROGRESS` and `IN_PROGRESS → COMPLETED`
- **AND** the test runs with no Spring context and no persistence

### Requirement: Only the approved owning driver operates a trip

The `start`, `finish` and `today` endpoints MUST resolve the driver from the `Authentication`, never from an identifier in the URL, so no ownership comparison applies to them.

A caller whose `UserType` is not `DRIVER` MUST be rejected. A driver whose `approval_status` is not `APPROVED` MUST NOT start or finish a route (RN-02).

#### Scenario: A client cannot start a route

- **WHEN** an authenticated user of type `CLIENT` starts a route
- **THEN** the system returns HTTP 403
- **AND** no `trip` row is created

#### Scenario: A pending driver cannot start a route

- **WHEN** an authenticated driver has `approval_status = PENDING`
- **AND** that driver starts a route
- **THEN** the system returns HTTP 403
- **AND** the message is resolved from MessageSource key `driver.not_approved`
- **AND** no `trip` row is created

#### Scenario: An unauthenticated call is rejected

- **WHEN** a request reaches any trip endpoint without a bearer token
- **THEN** the system returns HTTP 401

### Requirement: Finishing a route preserves pending checklist work

Finishing a route MUST NOT change the status of any related checklist record. Records left `PENDING` MUST stay `PENDING`. The system MUST NOT infer that a boarding or drop-off happened because the route ended.

`finished_at` MUST be persisted so that later work can reject a confirmation recorded after the route ended.

#### Scenario: Finishing does not fabricate checklist outcomes

- **WHEN** a route is finished
- **THEN** the system persists `finished_at` and `status = COMPLETED`
- **AND** it performs no write against checklist records

> Note: `checklist_entry` is delivered by issue #151. This requirement fixes the contract that issue must honour; the rejection of a late confirmation is specified and tested there, against the `finished_at` written here.

### Requirement: Trip rows are soft-deletable, and removal is not cancellation

`trip` MUST carry a nullable `deleted_at` column and its model MUST be annotated with `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)`, like every other removable domain model. Removal MUST go through `repository.delete`, never a native `DELETE`.

`status = CANCELLED` and `deleted_at` MUST remain distinct: `CANCELLED` states that the route will not happen and stays visible in listings and history; `deleted_at` states that the row should not exist and hides it from every default query.

Because the table is soft-deletable, its unique indexes MUST be partial (`WHERE deleted_at IS NULL`). A removed trip MUST NOT prevent a new trip for the same `(driver, service_date, shift)`.

#### Scenario: A removed trip disappears from default queries

- **WHEN** a trip is deleted through the repository
- **THEN** looking it up by token returns empty
- **AND** it is absent from the driver's trips for that service date

#### Scenario: A removed slot can be used again

- **WHEN** a trip for a given driver, service date and shift is deleted
- **AND** a new trip is created for the same driver, service date and shift
- **THEN** the new trip is persisted as a distinct row
- **AND** looking up that driver, service date and shift returns the new trip

#### Scenario: Cancelling keeps the trip visible

- **WHEN** a trip's status is set to `CANCELLED`
- **THEN** the trip is still returned by lookups and listings
- **AND** its `deleted_at` remains null
