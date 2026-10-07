## ADDED Requirements

### Requirement: Driver or assistant records a no-show on the current trip

The system SHALL expose `POST /api/trips/{tripToken}/no-shows` with body `{ dependentToken, reason }`. `reason` MUST be `@NotBlank`. The call creates at most one `absence` row for the trip's mapped leg (`OUTBOUND` for `MORNING` and `NIGHT`, `RETURN` for `AFTERNOON`). `source` MUST be `DRIVER` when the caller is the trip's driver and `ASSISTANT` when the caller is an `ACTIVE` assistant of that driver.

`trip_id` MUST be set from the URL. `absence_date` MUST equal the trip's `service_date`, which MUST be today in `America/Sao_Paulo`. The permission `report_no_show` MUST be required. Authorization MUST also use `@sec.isTripOperator` so only the van's driver or that driver's active assistant succeed.

#### Scenario: Driver records no-show on a morning trip

- **WHEN** an approved driver with `report_no_show` posts a non-blank `reason` for a dependent on their own `IN_PROGRESS` `MORNING` trip for today
- **THEN** the system persists one row with `leg = OUTBOUND`, `source = DRIVER`, that `trip_id`, and the given reason
- **AND** returns HTTP 201
- **AND** the response does not include numeric `id` fields

#### Scenario: Assistant records no-show as ASSISTANT

- **WHEN** an `ACTIVE` assistant of the trip's driver posts the same endpoint
- **THEN** the row's `source` is `ASSISTANT`
- **AND** returns HTTP 201

#### Scenario: Blank reason is rejected

- **WHEN** the body has a blank or missing `reason`
- **THEN** the system returns HTTP 400 from Bean Validation
- **AND** no row is inserted

#### Scenario: Trip from another van is forbidden

- **WHEN** a driver posts a no-show on a trip token that belongs to a different driver
- **THEN** the system returns HTTP 403
- **AND** no row is inserted

#### Scenario: Unlinked assistant is forbidden

- **WHEN** an assistant with status other than `ACTIVE`, or linked to another driver, posts a no-show
- **THEN** the system returns HTTP 403

---

### Requirement: No-show is only valid on today's live trip

A no-show MUST be rejected when the trip's `service_date` is not today, when status is `COMPLETED` or `CANCELLED`, or when the dependent's shift does not include the trip's mapped leg. HTTP 409 MUST use MessageSource keys (`absence.trip.not_today`, `absence.trip.not_open`, `absence.scope.not_in_shift`).

#### Scenario: Finished trip refuses no-show

- **WHEN** the trip is `COMPLETED` or `CANCELLED`
- **AND** the operator posts a no-show
- **THEN** the system returns HTTP 409 with key `absence.trip.not_open`
- **AND** no row is inserted

#### Scenario: Yesterday's trip refuses no-show

- **WHEN** the trip `service_date` is not today in `America/Sao_Paulo`
- **THEN** the system returns HTTP 409 with key `absence.trip.not_today`

#### Scenario: Afternoon-only student on a morning trip

- **WHEN** the dependent `shift` is `AFTERNOON`
- **AND** the operator posts a no-show on a `MORNING` trip
- **THEN** the system returns HTTP 400 or 409 with key `absence.scope.not_in_shift`
- **AND** no row is inserted
