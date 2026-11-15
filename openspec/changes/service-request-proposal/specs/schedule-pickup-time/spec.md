## Purpose

A schedule slot has one pickup time, the time the driver and the family agree on. It replaces the boarding window of the `contract-schedule` (`window_start` and the optional `window_end`).

## ADDED Requirements

### Requirement: A slot has one pickup time

Each `schedule_slot` MUST carry `weekday`, `leg`, `shift` and a required `pickup_time`: when the driver picks the student up, at home on the outbound leg and at school on the return. A slot MUST NOT have an end time. Every request and response that carries a slot (the contract, the unlinked passenger, the proposal and the route passengers) MUST name the field `pickupTime`, and none MUST carry `windowStart` or `windowEnd`. A `windowEnd` sent in a request body MUST be ignored.

#### Scenario: A contract slot carries only its pickup time

- **WHEN** an admin creates a contract with an `OUTBOUND` `MORNING` slot on `MONDAY` with `pickupTime = 06:40` and `windowEnd = 06:41` in the body
- **THEN** the contract's slot has `pickupTime = 06:40`
- **AND** it has neither `windowStart` nor `windowEnd`

#### Scenario: A slot without its pickup time is rejected

- **WHEN** a slot is submitted without `pickupTime`
- **THEN** the system returns HTTP 400
- **AND** nothing is persisted

### Requirement: Slots are unique per weekday and leg

The system MUST reject a schedule where the same `(weekday, leg)` appears twice or that has no slot at all, decided by the `ScheduleSlotPolicy` and backed by the partial unique index on `(schedule_id, weekday, leg)`. There MUST be no rule on the order of two times in a slot, and the `schedule.slot.window_invalid` message MUST NOT exist.

#### Scenario: Duplicate weekday and leg is rejected

- **WHEN** a schedule has two `OUTBOUND` slots on `MONDAY`
- **THEN** the system returns HTTP 400 with the message resolved from `schedule.slot.duplicate`

#### Scenario: Empty schedule is rejected

- **WHEN** an owner is submitted with an empty slot list
- **THEN** the system returns HTTP 400 with the message resolved from `schedule.slots.required`

### Requirement: Route passengers come in pickup order

`RoutePassengerQueryService` MUST return each passenger with the `pickupTime` of its slot, ordered by `pickupTime` and then by name.

#### Scenario: The earlier pickup comes first

- **WHEN** a contracted student has a `MONDAY` `OUTBOUND` `MORNING` slot at 06:50 and an unlinked passenger has the same slot at 06:40
- **AND** the query runs for that driver, a Monday, and `MORNING`
- **THEN** the unlinked passenger comes first, with `pickupTime = 06:40`
