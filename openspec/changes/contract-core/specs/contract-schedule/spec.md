## ADDED Requirements

### Requirement: Each owner has its own schedule of weekday and leg slots

A schedule MUST be stored as one `schedule` row and its `schedule_slot` rows. Each slot MUST carry `weekday` (`java.time.DayOfWeek` stored as text), `leg` (`RouteLeg` in `br.com.vanep.shared.enums`: `OUTBOUND` or `RETURN`, the same enum used by `absence.leg`), `shift` (`OperationShift`), a required `window_start` and an optional `window_end`.

Every owner (`contract_item`, `unlinked_passenger`, and in later changes `proposal_item` and `service_request_item`) MUST reference its own schedule through a `schedule_id` column. The system MUST NOT use a polymorphic owner reference in `schedule_slot`. A partial unique index on each owner's `schedule_id` (`WHERE deleted_at IS NULL`) MUST prevent two owners from sharing a schedule.

#### Scenario: A schedule with outbound and return on weekdays

- **WHEN** an owner is saved with `OUTBOUND` slots Monday to Friday at 06:40–06:50 with shift `MORNING` and `RETURN` slots Monday to Friday at 12:30 with shift `AFTERNOON`
- **THEN** the system persists one `schedule` and ten `schedule_slot` rows
- **AND** each `RETURN` slot has `window_end` null

#### Scenario: The shift is never derived from the time

- **WHEN** a slot is submitted without `shift`
- **THEN** the system returns HTTP 400
- **AND** nothing is persisted

### Requirement: Slots are valid and unique per weekday and leg

The system MUST reject a schedule where the same `(weekday, leg)` appears twice, where `window_end` is not after `window_start`, or that has no slot at all. The rule MUST be decided by a policy class with no Spring context, JPA model or servlet type, and the database MUST also enforce uniqueness with a partial unique index on `(schedule_id, weekday, leg)` `WHERE deleted_at IS NULL`.

#### Scenario: Duplicate weekday and leg is rejected

- **WHEN** a schedule has two `OUTBOUND` slots on `MONDAY`
- **THEN** the system returns HTTP 400 with the message resolved from `schedule.slot.duplicate`

#### Scenario: Window end before start is rejected

- **WHEN** a slot has `window_start = 07:00` and `window_end = 06:50`
- **THEN** the system returns HTTP 400 with the message resolved from `schedule.slot.window_invalid`

#### Scenario: Empty schedule is rejected

- **WHEN** an owner is submitted with an empty slot list
- **THEN** the system returns HTTP 400 with the message resolved from `schedule.slots.required`

### Requirement: A schedule is replaced as a whole

Replacing a schedule MUST soft-delete every current slot and insert the new ones in the same transaction. Slots MUST NOT have a token or an endpoint of their own.

#### Scenario: Replacing a schedule keeps no stale slot

- **WHEN** an owner with five `OUTBOUND` slots has its schedule replaced by three `OUTBOUND` slots
- **THEN** reading the owner returns exactly the three new slots
- **AND** the five previous slots remain in the table with `deleted_at` set
