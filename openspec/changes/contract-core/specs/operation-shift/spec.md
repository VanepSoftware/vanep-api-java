## ADDED Requirements

### Requirement: School shift and operation shift are distinct types

The system MUST represent the shift a student attends school and the shift a route runs as two backed Java enums in `br.com.vanep.shared.enums`:

- `SchoolShift` with `MORNING`, `AFTERNOON`, `NIGHT` and `FULLTIME`, used by `dependent.shift` and `unlinked_passenger.school_shift`;
- `OperationShift` with `MORNING`, `AFTERNOON` and `NIGHT`, used by `trip.shift` and `schedule_slot.shift`.

`SchoolShift` MUST replace the current `Shift` enum without changing the JSON values accepted or returned by the dependent endpoints.

#### Scenario: A dependent keeps accepting a full-time school shift

- **WHEN** a client creates a dependent with `shift = FULLTIME`
- **THEN** the system persists the dependent with `shift = FULLTIME`
- **AND** the response returns `shift = FULLTIME`

#### Scenario: A trip rejects a full-time shift

- **WHEN** an approved driver starts a route with `shift = FULLTIME`
- **THEN** the system returns HTTP 400
- **AND** no `trip` row is created

#### Scenario: A full-time student generates two trips

- **WHEN** a student's schedule has an `OUTBOUND` slot with shift `MORNING` and a `RETURN` slot with shift `AFTERNOON` on the same weekday
- **THEN** the student is a passenger of the driver's `MORNING` trip for the outbound leg
- **AND** a passenger of the driver's `AFTERNOON` trip for the return leg

### Requirement: Existing full-time trips are converted

A Flyway migration MUST rewrite every existing `trip` row with `shift = 'FULLTIME'` to `shift = 'MORNING'`, so that every stored row can be read as an `OperationShift`. The migration MUST NOT edit any previously applied migration.

#### Scenario: A stored full-time trip is readable after the migration

- **WHEN** the database holds a `trip` row with `shift = 'FULLTIME'` before the migration runs
- **THEN** after the migration the row has `shift = 'MORNING'`
- **AND** reading it through the trip endpoints returns HTTP 200
