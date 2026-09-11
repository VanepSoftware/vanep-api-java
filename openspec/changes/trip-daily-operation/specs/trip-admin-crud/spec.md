## ADDED Requirements

### Requirement: Administrative CRUD over trips addressed by token

The system MUST expose, under `/api/trips`, a full CRUD addressed by opaque `token`, alongside the driver's `/me` flow:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/trips` | create a trip row directly (administrative reconstruction) |
| `GET` | `/api/trips` | paginated listing |
| `GET` | `/api/trips/{token}` | detail |
| `PATCH` | `/api/trips/{token}` | partial correction |
| `DELETE` | `/api/trips/{token}` | soft delete |
| `POST` | `/api/trips/{token}/restore` | undo the soft delete |

These endpoints MUST NOT be the driver's operating surface. Starting and finishing a route MUST stay on `/api/drivers/me/trips/**`, where the state machine, idempotency and work-window evaluation apply.

Requests MUST bind to dedicated request DTOs with Bean Validation, never to `TripModel`. Responses MUST be shaped by `TripResponseDTO` through `TripMapper`, and MUST expose `token`, never a numeric `id`.

#### Scenario: Admin creates a trip for a driver

- **WHEN** a caller holding `create_trip` posts a trip with a driver token, service date and shift
- **THEN** the system returns HTTP 201
- **AND** the trip is persisted with `status = SCHEDULED` and null `started_at`

#### Scenario: Creating a duplicate active slot is rejected

- **WHEN** an active trip already exists for a driver, service date and shift
- **AND** a caller holding `create_trip` posts the same combination
- **THEN** the system returns HTTP 409
- **AND** the message is resolved from MessageSource key `trip.duplicate_slot`

#### Scenario: Listing is paginated and excludes removed trips

- **WHEN** a caller holding `list_trips` lists trips
- **AND** one trip has been soft-deleted
- **THEN** the response is a page
- **AND** the removed trip is absent from it

### Requirement: Trip authorization separates administrator from owning driver

`PermissionEnum` MUST gain `list_trips`, `show_trip`, `create_trip`, `update_trip`, `delete_trip` and `restore_trip`, granted to the `ADMIN` bundle.

`SecurityEvaluator` MUST gain `isTripOwner(String token, Authentication authentication)`, resolving the caller with `SecurityHelper.getCallerUid` and comparing it to the trip's driver's user token. Per-feature security services MUST NOT be created.

Read and update MUST allow either the permission holder or the owning driver. Delete and restore MUST require the permission alone — a driver MUST NOT remove their own day of operation.

#### Scenario: Owning driver reads their own trip by token

- **WHEN** a driver without `show_trip` reads a trip whose driver is that same user
- **THEN** the system returns HTTP 200

#### Scenario: A driver cannot read another driver's trip

- **WHEN** a driver without `show_trip` reads a trip belonging to a different driver
- **THEN** the system returns HTTP 403

#### Scenario: A driver cannot delete their own trip

- **WHEN** the owning driver, holding no `delete_trip`, deletes their own trip
- **THEN** the system returns HTTP 403
- **AND** the trip's `deleted_at` remains null

#### Scenario: Admin deletes and restores a trip

- **WHEN** a caller holding `delete_trip` deletes a trip
- **THEN** the trip is absent from listing and detail
- **AND** a subsequent restore by a caller holding `restore_trip` returns HTTP 200
- **AND** the trip is present again

### Requirement: Administrative correction may set state the driver flow cannot reach

`PATCH /api/trips/{token}` MUST follow the partial-update rule: `JsonNullable` on every mutable field — `shift`, `status`, `startedAt`, `finishedAt`. Omitted fields MUST leave the stored value unchanged; an explicit JSON `null` MUST clear a nullable column and MUST return HTTP 400 for a `NOT NULL` one.

The transition policy MUST NOT constrain this endpoint. It governs the driver's `start` and `finish`; administrative correction exists precisely to reach a state the policy would refuse, such as reopening a route closed by mistake.

`driver` and `serviceDate` MUST NOT be mutable: moving a trip between drivers or days would silently break the uniqueness the slot represents.

#### Scenario: Admin reopens a completed route

- **WHEN** a caller holding `update_trip` patches a `COMPLETED` trip with `status = IN_PROGRESS` and `finishedAt = null`
- **THEN** the system returns HTTP 200
- **AND** the stored status is `IN_PROGRESS` and `finished_at` is null

#### Scenario: A single-field patch leaves every other field unchanged

- **WHEN** a trip has a shift, a status, a `startedAt` and a `finishedAt`
- **AND** a caller holding `update_trip` patches only `status`
- **THEN** the stored `shift`, `startedAt` and `finishedAt` are unchanged

#### Scenario: Changing the driver is refused

- **WHEN** a caller holding `update_trip` patches a trip with a different driver token
- **THEN** the system returns HTTP 400

#### Scenario: Clearing a non-nullable field is refused

- **WHEN** a caller holding `update_trip` patches a trip with `"shift": null`
- **THEN** the system returns HTTP 400

### Requirement: Administrative writes may reach an illegal path but never an impossible state

`POST /api/trips` and `PATCH /api/trips/{token}` MUST validate that the resulting combination of `status`, `startedAt` and `finishedAt` can exist, and MUST return HTTP 400 when it cannot. The decision MUST live in a policy class depending on no Spring context, no JPA model and no servlet type.

| `status` | `startedAt` | `finishedAt` |
| --- | --- | --- |
| `SCHEDULED` | forbidden | forbidden |
| `IN_PROGRESS` | required | forbidden |
| `COMPLETED` | required | required |
| `CANCELLED` | optional | optional |

For every status, `finishedAt` MUST NOT be earlier than `startedAt`, and a present `finishedAt` MUST have a present `startedAt`.

The transition policy governs the path taken; this one governs the state reached. The driver's `start` and `finish` endpoints MUST NOT be validated by it — they are coherent by construction.

#### Scenario: Reopening a completed route without clearing the finish is refused

- **WHEN** a `COMPLETED` trip carries both timestamps
- **AND** a caller holding `update_trip` patches only `status = IN_PROGRESS`
- **THEN** the system returns HTTP 400
- **AND** the stored status remains `COMPLETED`

#### Scenario: Reopening a completed route while clearing the finish is allowed

- **WHEN** a `COMPLETED` trip carries both timestamps
- **AND** a caller holding `update_trip` patches `status = IN_PROGRESS` and `finishedAt = null`
- **THEN** the system returns HTTP 200
- **AND** the stored status is `IN_PROGRESS` with a null `finished_at`

#### Scenario: Creating a completed trip without timestamps is refused

- **WHEN** a caller holding `create_trip` posts a trip with `status = COMPLETED` and no timestamps
- **THEN** the system returns HTTP 400
- **AND** no `trip` row is persisted

#### Scenario: Creating a trip that finished before it started is refused

- **WHEN** a caller holding `create_trip` posts a trip whose `finishedAt` precedes its `startedAt`
- **THEN** the system returns HTTP 400
- **AND** no `trip` row is persisted
