## ADDED Requirements

### Requirement: Driver reads the state of today's operation

The system MUST expose `GET /api/drivers/me/trips/today` for the authenticated driver. It MUST return **all** trips the driver has for the current service date, because the unique key is `(driver_id, service_date, shift)` and a driver who runs two shifts has two trips on the same day.

Each element MUST carry the opaque `token`, the `shift`, the `status`, `serviceDate`, `startedAt`, `finishedAt` and `outsideWorkWindow`. It MUST NOT expose the numeric `id` of the trip or of the driver.

The list MUST be ordered by creation, which is the order the driver started the shifts. It MUST NOT be ordered by `shift`: with `@Enumerated(STRING)` the database sorts by enum name, which puts `AFTERNOON` before `MORNING`.

When the driver has no trip for the current service date, the system MUST return HTTP 200 with an empty list, not HTTP 204.

Responses MUST be shaped by a dedicated response DTO, never by returning the JPA model.

#### Scenario: Driver with a route in progress reads its state

- **WHEN** an approved driver started a route today
- **AND** that driver reads today's trips
- **THEN** the system returns HTTP 200
- **AND** the list holds one element with `status = IN_PROGRESS` and a non-null `startedAt`
- **AND** that element's `finishedAt` is null

#### Scenario: Driver running two shifts sees both

- **WHEN** an approved driver has a `COMPLETED` `MORNING` trip and an `IN_PROGRESS` `AFTERNOON` trip today
- **AND** that driver reads today's trips
- **THEN** the system returns HTTP 200
- **AND** the list holds two elements
- **AND** the `MORNING` element comes first, because it was created first

#### Scenario: Driver with no route today

- **WHEN** an approved driver has no trip for the current service date
- **AND** that driver reads today's trips
- **THEN** the system returns HTTP 200
- **AND** the body is an empty list

#### Scenario: Yesterday's trip is not returned as today's

- **WHEN** a driver has a `COMPLETED` trip whose `service_date` is the previous day
- **AND** no trip exists for the current service date
- **AND** that driver reads today's trips
- **THEN** the body is an empty list

#### Scenario: The response exposes no numeric identifier

- **WHEN** today's trips are returned
- **THEN** the JSON body contains no `id` field for any trip or for the driver
- **AND** each trip is identified by an opaque `token`

### Requirement: Starting outside the configured work window is recorded, not blocked

The system MUST accept a route start whose instant falls outside the driver's configured work window, and MUST record that fact as `outsideWorkWindow = true` on the trip response. The system MUST NOT return an error because of the work window alone.

The evaluation MUST live in a policy class that takes the driver's `workDays`, `workStartTime`, `workEndTime` and the start instant, and depends on no Spring context, no JPA model and no servlet type.

When the driver's work window is not configured — `workDays` absent, empty or unparseable, or either time null — the policy MUST report `false`, because absence of configuration is not evidence of being outside the window.

#### Scenario: Driver starts before the configured start time

- **WHEN** a driver has `work_start_time = 07:00`
- **AND** that driver starts a route at 06:41 on a configured work day
- **THEN** the system returns HTTP 201
- **AND** the trip is persisted with `status = IN_PROGRESS`
- **AND** the response carries `outsideWorkWindow = true`

#### Scenario: Driver starts on a day not in the work days

- **WHEN** a driver has work days that exclude Saturday
- **AND** that driver starts a route on a Saturday
- **THEN** the system returns HTTP 201
- **AND** the response carries `outsideWorkWindow = true`

#### Scenario: Driver starts inside the window

- **WHEN** a driver starts a route on a configured work day between `work_start_time` and `work_end_time`
- **THEN** the response carries `outsideWorkWindow = false`

#### Scenario: Unconfigured work window does not mark the trip

- **WHEN** a driver has `work_days` null or empty, or `work_start_time` null
- **AND** that driver starts a route
- **THEN** the system returns HTTP 201
- **AND** the response carries `outsideWorkWindow = false`

### Requirement: The trip is the single source of the operation state

The system MUST NOT introduce a redundant flag on `driver` to mean "on route". The driver's operational state MUST be derived from `trip.status`. `driver.is_available` keeps its existing meaning of commercial availability and MUST NOT be written by starting or finishing a route.

#### Scenario: Starting a route does not write driver availability

- **WHEN** a driver starts a route
- **THEN** the stored `driver.is_available` keeps the value it had before the call
