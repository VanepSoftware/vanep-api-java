## ADDED Requirements

### Requirement: At most one pending stop change request per dependent and trip

The system MUST enforce that a dependent has at most one `PENDING` stop change request active on the same `trip`. A Flyway migration MUST enforce this via a partial unique index on `(dependent_id, trip_id)` restricted to `WHERE status = 'PENDING' AND deleted_at IS NULL`.

#### Scenario: Creating a second pending request for the same dependent and trip is rejected
- **WHEN** a client creates a stop change request for a dependent on a given trip
- **AND** another request is submitted for the same dependent and trip while the first is still `PENDING`
- **THEN** the system returns HTTP 409 Conflict
- **AND** the message indicates that a request is already pending for that dependent

#### Scenario: Creating a request after previous one was rejected or approved is allowed
- **WHEN** a client's stop change request was previously `REJECTED` or `CANCELLED`
- **AND** a new request is submitted for the same dependent and trip
- **THEN** the new request is accepted and persisted with status `PENDING`

### Requirement: Stop change status transitions and policy

`StopChangeStatus` MUST be a backed Java enum with values `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`, `CANCELLED`.
The legal state transitions MUST be:
- `PENDING → APPROVED`
- `PENDING → REJECTED`
- `PENDING → CANCELLED`
- `PENDING → EXPIRED`

Any transition from a terminal state (`APPROVED`, `REJECTED`, `CANCELLED`, `EXPIRED`) to another state MUST be rejected with HTTP 409 Conflict.

#### Scenario: Transition policy is decided without database
- **WHEN** the transition policy is exercised for every pair of `StopChangeStatus` values
- **THEN** it accepts only the valid transitions from `PENDING`
- **AND** rejects transitions attempting to overwrite terminal states
- **AND** the test runs with no Spring context and no database

### Requirement: Client requests stop change for their own dependent

The client endpoint `POST /api/clients/me/stop-change-requests` MUST resolve the calling client from the `Authentication`. The client MUST only request stop changes for dependents they own (`dependent.client_id == client.id`). Requests for dependents belonging to other clients MUST be rejected with HTTP 403 Forbidden.

The request MUST specify:
- `dependentToken`: token of the dependent
- `tripToken`: token of the trip for today
- `reason`: reason for the stop change
- `dropoffAddress`: new address details (either existing `addressToken` or `placeId` + number/complement)

The trip MUST be in `SCHEDULED` or `IN_PROGRESS` state. If the trip is `COMPLETED` or `CANCELLED`, the system MUST return HTTP 409 Conflict.

#### Scenario: Client requests stop change successfully
- **WHEN** an authenticated client submits a valid request for their dependent on an active trip
- **THEN** the system returns HTTP 201 Created
- **AND** a new `stop_change_request` is persisted with status `PENDING`
- **AND** `requested_by_user_id` records the client user's ID
- **AND** the dependent's permanent registered address remains unchanged

#### Scenario: Client cannot request for another client's dependent
- **WHEN** an authenticated client attempts to request a stop change for a dependent owned by another client
- **THEN** the system returns HTTP 403 Forbidden
- **AND** no `stop_change_request` is created

### Requirement: Driver approves or rejects stop change for their trip

The driver endpoints `POST /api/drivers/me/stop-change-requests/{token}/approve` and `POST /api/drivers/me/stop-change-requests/{token}/reject` MUST resolve the calling driver from the `Authentication`.
The driver MUST only respond to requests associated with trips that they drive (`trip.driver_id == driver.id`). Responses by other drivers MUST be rejected with HTTP 403 Forbidden.
Drivers with `approval_status != APPROVED` MUST be rejected with HTTP 403 Forbidden (RN-02).

When approved:
- `status` MUST be set to `APPROVED`
- `responded_by_user_id` MUST be set to the driver's user ID
- `responded_at` MUST record the current timestamp
- A domain event `StopChangeApprovedEvent` MUST be published

When rejected:
- `status` MUST be set to `REJECTED`
- `responded_by_user_id` MUST be set to the driver's user ID
- `responded_at` MUST record the current timestamp
- A domain event `StopChangeRejectedEvent` MUST be published

#### Scenario: Driver approves pending request
- **WHEN** an approved driver approves a pending request for their trip
- **THEN** the system returns HTTP 200 OK
- **AND** `status` is updated to `APPROVED`
- **AND** `responded_by_user_id` and `responded_at` are persisted

#### Scenario: Driver cannot respond to another driver's trip request
- **WHEN** a driver attempts to approve or reject a request on a trip belonging to another driver
- **THEN** the system returns HTTP 403 Forbidden

### Requirement: Client cancels pending stop change

The client endpoint `POST /api/clients/me/stop-change-requests/{token}/cancel` allows the requesting client to cancel a request before the driver responds.

#### Scenario: Client cancels their own pending request
- **WHEN** the requesting client cancels a request with status `PENDING`
- **THEN** the system returns HTTP 200 OK
- **AND** `status` is updated to `CANCELLED`

#### Scenario: Client cannot cancel a request that is already approved
- **WHEN** a client attempts to cancel a request with status `APPROVED`
- **THEN** the system returns HTTP 409 Conflict

### Requirement: Soft delete and restore

The entity and table MUST support soft delete using `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)`.
Admins can delete via `DELETE /api/stop-change-requests/{token}` and restore via `POST /api/stop-change-requests/{token}/restore`.
Deleted requests MUST NOT appear in default queries or active unique constraints.
