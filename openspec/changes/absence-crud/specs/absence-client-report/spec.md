## ADDED Requirements

### Requirement: Guardian reports absence for today by route leg

The system SHALL let an authenticated `CLIENT` report that a dependent will not ride **today** on a specific van, by choosing a UI scope of `OUTBOUND` (ida), `RETURN` (volta), or `BOTH` (ida e volta). The report MUST be stored as one `absence` row per leg: `BOTH` becomes two rows (`OUTBOUND` and `RETURN`). The system MUST NOT persist `BOTH` as a column value.

The day MUST be `LocalDate.now(ZoneId.of("America/Sao_Paulo"))`. The request MUST NOT carry an absence date. Public identifiers MUST be opaque `token` strings. The system MUST NOT write to `dependent`, `client_driver`, or any future `contract` row (RN-19).

The HTTP surface MUST be:

- `POST /api/client-drivers/{linkToken}/absences` with body `{ dependentToken, scope }`
- `GET /api/client-drivers/{linkToken}/absences/today?dependentToken=`
- `DELETE /api/client-drivers/{linkToken}/absences` with the same body shape as create

`POST` MUST return `201` when at least one new leg row was inserted and `200` when every requested leg already existed. The response MUST be a list of absence response DTOs, never a JPA model graph.

#### Scenario: Report outbound only

- **WHEN** the guardian of a `MORNING` or `FULLTIME` dependent posts `scope = OUTBOUND` on an `ACTIVE` client-driver link they belong to
- **THEN** the system persists exactly one `absence` row with `leg = OUTBOUND`, `source = CLIENT`, and today's `absence_date`
- **AND** returns HTTP 201
- **AND** the response lists that one absence token

#### Scenario: Report both legs as two rows

- **WHEN** the guardian of a `FULLTIME` dependent posts `scope = BOTH`
- **THEN** the system persists two rows, one `OUTBOUND` and one `RETURN`, both with `source = CLIENT` and today's date
- **AND** returns HTTP 201
- **AND** the response lists two tokens

#### Scenario: Return-only leaves the outbound trip expected

- **WHEN** the guardian posts `scope = RETURN` and no `OUTBOUND` absence exists for that dependent today
- **THEN** the system persists only `RETURN`
- **AND** MUST NOT create an `OUTBOUND` row

#### Scenario: Client-supplied date is ignored because the field does not exist

- **WHEN** the guardian sends a body with an extra `absenceDate` property
- **THEN** the persisted `absence_date` is still the server date in `America/Sao_Paulo`
- **AND** either Jackson ignores the unknown property or Bean Validation never reads it — the stored day MUST NOT come from the client

#### Scenario: Scope not covered by the dependent shift is rejected

- **WHEN** the dependent's `shift` is `MORNING` and the guardian posts `scope = RETURN` or `scope = BOTH`
- **THEN** the system returns HTTP 400
- **AND** the message is resolved from MessageSource key `absence.scope.not_in_shift`
- **AND** no row is inserted

---

### Requirement: Client absence is owned and tied to an active van link

The caller MUST own the dependent (`dependent.client_id` is the caller's client). The `{linkToken}` MUST be a `client_driver` row with `status = ACTIVE` whose `client_id` is that same client. A client MUST NOT report absence for another family's dependent or on another family's van.

The permission `report_absence` MUST be required. Controllers MUST stay thin; ownership and link status MUST be enforced in the `@Service`.

#### Scenario: Another client's dependent is forbidden

- **WHEN** an authenticated client posts an absence for a dependent token that belongs to a different client
- **THEN** the system returns HTTP 403 or 404 consistent with other owned resources
- **AND** no `absence` row is inserted

#### Scenario: Inactive van link is refused

- **WHEN** the client-driver link is `PENDING`, `INACTIVE`, or `BLOCKED`
- **AND** the guardian posts an absence on that link
- **THEN** the system returns HTTP 409
- **AND** the message is resolved from `absence.link.not_active`

#### Scenario: Unauthenticated report

- **WHEN** a request without a valid Bearer token calls the client absence endpoint
- **THEN** the system returns HTTP 401

#### Scenario: Driver token cannot use the client endpoint

- **WHEN** an authenticated `DRIVER` calls `POST /api/client-drivers/{linkToken}/absences`
- **THEN** the system returns HTTP 403

---

### Requirement: Guardian may undo a client report until the leg is on the road

The system SHALL accept `DELETE /api/client-drivers/{linkToken}/absences` with `{ dependentToken, scope }` for rows the same caller created with `source = CLIENT` on today's date. Soft delete MUST be used (`deleted_at`). Undo MUST succeed only when every targeted leg either has no trip or the matching trip is `SCHEDULED`. If any targeted leg's trip is `IN_PROGRESS`, `COMPLETED`, or `CANCELLED`, the system MUST return HTTP 409 with key `absence.undo.trip_started` and MUST NOT delete any of the targeted rows.

Undo of `BOTH` when only one leg exists MUST delete that one leg and succeed. Driver/assistant no-show rows MUST NOT be removed by this endpoint.

#### Scenario: Undo before the trip starts

- **WHEN** a client absence exists for `OUTBOUND` today
- **AND** no trip exists for that van's morning shift, or it is `SCHEDULED`
- **AND** the guardian deletes `scope = OUTBOUND`
- **THEN** the system soft-deletes that row
- **AND** returns HTTP 204
- **AND** a later GET today does not include that leg

#### Scenario: Undo is refused after the trip is in progress

- **WHEN** a client absence exists for `OUTBOUND`
- **AND** the matching trip is `IN_PROGRESS`
- **AND** the guardian deletes that scope
- **THEN** the system returns HTTP 409
- **AND** the row remains active

#### Scenario: Undo of both legs is all-or-nothing

- **WHEN** `OUTBOUND` is still `SCHEDULED` and `RETURN` is `IN_PROGRESS`
- **AND** the guardian deletes `scope = BOTH`
- **THEN** the system returns HTTP 409
- **AND** both rows remain active
