## ADDED Requirements

### Requirement: One query returns the passengers of a trip from both sources

`RoutePassengerQueryService` MUST return, for a driver, a service date and an `OperationShift`, the list of passengers made of:

- the items of the driver's contracts with `status = ACTIVE` and `starts_on <= serviceDate <= ends_on`, whose schedule has a slot on the service date's weekday with that shift;
- the driver's non-deleted unlinked passengers whose schedule has a slot on the service date's weekday with that shift.

Each passenger MUST carry its `source` (`CONTRACT_ITEM` or `UNLINKED_PASSENGER`), the token of the item or of the unlinked passenger, the name, the school, the `leg`, the boarding window and the pickup address. The query MUST NOT issue one query per passenger (rule 17).

This change exposes the query as an internal service only; it MUST NOT add an HTTP endpoint.

#### Scenario: Contracted and unlinked students appear together

- **WHEN** a driver has an `ACTIVE` contract item and an unlinked passenger, both with an `OUTBOUND` `MORNING` slot on `MONDAY`
- **AND** the query runs for that driver, a Monday, and `MORNING`
- **THEN** it returns two passengers
- **AND** one has `source = CONTRACT_ITEM` and the other `source = UNLINKED_PASSENGER`

#### Scenario: A suspended contract puts no student on the route

- **WHEN** the driver's only contract is `SUSPENDED`
- **THEN** its items are not returned

#### Scenario: An expired active contract is ignored

- **WHEN** a contract is still `ACTIVE` but `ends_on` is before the service date
- **THEN** its items are not returned

#### Scenario: Another shift returns nobody

- **WHEN** every slot of the driver's passengers has shift `MORNING`
- **AND** the query runs with `AFTERNOON`
- **THEN** it returns an empty list

### Requirement: Operation records reference the passenger by source

Tables that record per-passenger operation facts (`checklist_entry` in #151, `absence` in #160) MUST reference the passenger through two nullable foreign keys, `contract_item_id` and `unlinked_passenger_id`, with a `CHECK` that exactly one is set, and MUST NOT store `contract_id` or `dependent_id`. Their per-trip uniqueness MUST be expressed as two partial unique indexes, one per column. `stop_change_request` (#152) MUST reference only `contract_item_id`.

This change MUST guarantee the convention by never physically deleting `contract_item` or `unlinked_passenger` rows.

#### Scenario: Deleting a contracted item keeps its history reachable

- **WHEN** an admin deletes a contract
- **THEN** its `contract_item` rows remain in the database with `deleted_at` set
- **AND** a row that references one of those items by `contract_item_id` keeps a valid foreign key
