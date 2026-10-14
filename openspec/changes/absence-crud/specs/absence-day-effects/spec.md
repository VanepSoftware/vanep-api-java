## ADDED Requirements

### Requirement: One active absence per dependent, day and leg

A Flyway migration MUST create `absence` with `deleted_at` and a **partial** unique index on `(dependent_id, absence_date, leg) WHERE deleted_at IS NULL`. `trip_id` MUST be nullable. `client_driver_id` MUST be NOT NULL and reference `client_driver(id)`. The system MUST NOT rely on read-then-insert to enforce uniqueness: it MUST insert, catch `DataIntegrityViolationException`, and re-read the winning row.

Soft-deleting a row MUST free that slot so the guardian can report the same leg again the same day.

#### Scenario: Duplicate client report returns the existing row

- **WHEN** a guardian reports `OUTBOUND` for a dependent today
- **AND** reports `OUTBOUND` again for the same dependent today
- **THEN** exactly one active row exists
- **AND** the second call returns HTTP 200 with the same absence token
- **AND** neither call returns HTTP 500

#### Scenario: Both after outbound inserts only the missing leg

- **WHEN** an `OUTBOUND` row already exists
- **AND** the guardian posts `scope = BOTH`
- **THEN** the system inserts `RETURN` and keeps the existing `OUTBOUND`
- **AND** returns HTTP 201
- **AND** two active rows exist

#### Scenario: Concurrent duplicate does not 500

- **WHEN** two concurrent requests insert the same `(dependent, date, OUTBOUND)`
- **THEN** exactly one active row exists afterwards
- **AND** neither request returns HTTP 500

#### Scenario: Soft-deleted leg can be reported again

- **WHEN** today's `OUTBOUND` row was soft-deleted
- **AND** the guardian reports `OUTBOUND` again
- **THEN** the system inserts a new active row
- **AND** returns HTTP 201

---

### Requirement: Absence before and during a trip changes the day route contract

When an absence exists for a leg **before** checklist generation, that dependent MUST NOT receive checklist entries for that leg. When an absence is recorded while the trip is `IN_PROGRESS`, the system MUST ask `AbsenceDayEffectPort` to drop that dependent's `PENDING` checklist entries on that trip and to mark home and school stops for exclusion from route recalculation (RN-15). Entries already completed MUST stay completed.

Until `checklist_entry` and `route` exist, the production port MAY be a named no-op; unit tests MUST still assert that the service calls the port after a successful persist. Undo MUST NOT attempt to reconstruct checklist entries.

#### Scenario: Absence before start is visible to generation

- **WHEN** a client `OUTBOUND` absence exists and no morning trip has started
- **THEN** a checklist generator that queries absences for that dependent, date and `OUTBOUND` finds the row
- **AND** MUST NOT create boarding entries for that dependent on that leg

#### Scenario: Absence during an in-progress trip notifies the effect port

- **WHEN** the morning trip is `IN_PROGRESS`
- **AND** a client or operator records `OUTBOUND` for a dependent on that van
- **THEN** the service calls the day-effect port to drop `PENDING` entries and mark stops for exclusion
- **AND** the absence row is persisted even if the port is a no-op

#### Scenario: Completed checklist entries are not rewritten by this change's contract

- **WHEN** the dependent already has a non-`PENDING` checklist entry on the trip
- **THEN** the port contract MUST NOT require those entries to be deleted or flipped to boarded

---

### Requirement: Counterpart is notified and `notified_at` is set only after send

After a successful client report the system MUST email the van's driver using MessageSource key `absence.notify.client_report` (scope in the arguments). After a successful no-show it MUST email the dependent's client using `absence.notify.no_show` with the stop time in `America/Sao_Paulo`. `notified_at` MUST be set only when the mail send succeeds. A mail failure MUST NOT roll back the absence row; `notified_at` stays null.

Tests MUST stub `MailService` and MUST NOT reach a real SMTP host.

#### Scenario: Client report notifies the driver

- **WHEN** a guardian successfully reports absence
- **AND** the mail send succeeds
- **THEN** `notified_at` is non-null on each newly inserted or already-existing row that was part of the request
- **AND** the driver account email is the recipient

#### Scenario: No-show notifies the client with the time

- **WHEN** an operator successfully records a no-show
- **AND** the mail send succeeds
- **THEN** the client email is sent with the localized “aluno não encontrado na parada às {time}” message
- **AND** `notified_at` is set

#### Scenario: Mail failure keeps the absence

- **WHEN** persist succeeds and `MailService` throws
- **THEN** the absence row remains active
- **AND** `notified_at` is null
