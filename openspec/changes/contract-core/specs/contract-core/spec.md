## ADDED Requirements

### Requirement: A contract belongs to a client-driver link and has one item per dependent

A `contract` MUST reference one `client_driver` row. A `contract_item` MUST reference one `contract` and one `dependent`, and the dependent MUST belong to the client of that link. A contract MUST have at least one item and MUST NOT have two items for the same dependent.

At most one contract per link MAY have `status = ACTIVE`. A Flyway migration MUST enforce this with a partial unique index on `client_driver_id` `WHERE status = 'ACTIVE' AND deleted_at IS NULL`.

The contract MUST NOT carry a reference to a proposal, a document or a source type: later changes link to the contract from their own tables.

#### Scenario: Siblings share one contract

- **WHEN** an admin creates a contract for a link whose client has two dependents, with one item each
- **THEN** the system persists one `contract` and two `contract_item` rows
- **AND** returns HTTP 201 with both items

#### Scenario: A dependent of another client is rejected

- **WHEN** an admin creates a contract with an item whose dependent belongs to a different client than the link's
- **THEN** the system returns HTTP 422 with the message resolved from `contract.item.dependent_not_in_link`
- **AND** nothing is persisted

#### Scenario: A second active contract for the same link is rejected

- **WHEN** a link already has an `ACTIVE` contract
- **AND** an admin creates another contract for the same link
- **THEN** the system returns HTTP 409 with the message resolved from `contract.active_conflict`

### Requirement: Contract terms are explicit and the period is capped at twelve months

Every contract MUST have `starts_on` and `ends_on`, with `ends_on` after `starts_on` and no later than `starts_on` plus twelve months. `total_amount` MUST be greater than zero, `installments` MUST be between 1 and 12, and `due_day` MUST be between 1 and 28. The required dates and the numeric ranges MUST be validated with Bean Validation on the request DTO (HTTP 400). The date order and the twelve-month cap MUST be decided by a policy class with no Spring context, JPA model or servlet type (HTTP 422), and the policy MUST NOT repeat the single-field ranges. The database MUST also enforce the date order and the numeric ranges with `CHECK` constraints, as the last barrier for writes that bypass the DTO.

#### Scenario: A contract without an end date is rejected

- **WHEN** an admin creates a contract without `endsOn`
- **THEN** the system returns HTTP 400
- **AND** nothing is persisted

#### Scenario: A period longer than twelve months is rejected

- **WHEN** an admin creates a contract with `startsOn = 2027-02-01` and `endsOn = 2028-02-02`
- **THEN** the system returns HTTP 422 with the message resolved from `contract.period.too_long`

#### Scenario: A school year that crosses the calendar year is accepted

- **WHEN** an admin creates a contract with `startsOn = 2027-07-01` and `endsOn = 2028-06-30`
- **THEN** the system returns HTTP 201

#### Scenario: An end date on the start date is rejected

- **WHEN** an admin creates a contract with `startsOn = 2027-02-01` and `endsOn = 2027-02-01`
- **THEN** the system returns HTTP 422 with the message resolved from `contract.period.invalid`

#### Scenario: A due day of 31 is rejected

- **WHEN** an admin creates a contract with `dueDay = 31`
- **THEN** the system returns HTTP 400 with the message resolved from `contract.due_day.invalid`

#### Scenario: Thirteen installments are rejected

- **WHEN** an admin creates a contract with `installments = 13`
- **THEN** the system returns HTTP 400 with the message resolved from `contract.installments.invalid`

### Requirement: Items copy the school and the pickup address

When an item is created, the system MUST copy the dependent's `school_id` and the fields of the dependent's address (`city_id`, `zip_code`, `street`, `number`, `complement`, `neighborhood`, `district_id`, `google_place_id`) into the item. The item MUST NOT reference the `address` table. Later edits to the dependent or its address MUST NOT change existing items.

A dependent without a school or without an address MUST NOT be added to a contract.

#### Scenario: Editing the dependent's address does not change the contract

- **WHEN** a contract item was created while the dependent lived at "Rua A, 10"
- **AND** the client later changes the dependent's address to "Rua B, 20"
- **THEN** reading the contract still returns "Rua A, 10" as the pickup address

#### Scenario: A dependent without an address is rejected

- **WHEN** an admin creates a contract with an item for a dependent that has no address
- **THEN** the system returns HTTP 422 with the message resolved from `contract.item.address_required`

### Requirement: A dependent is not scheduled twice for the same weekday and leg

The system MUST reject a contract item whose schedule has a `(weekday, leg)` that the same dependent already has in another contract that is signed and not ended, that is, with status `SIGNED`, `ACTIVE` or `SUSPENDED`. A suspended contract keeps its slots reserved, so reactivating it never collides. The check MUST run when a contract is created and when a signed, not ended contract is restored. A dependent MAY be in two signed, not ended contracts with different drivers when their slots do not collide.

#### Scenario: Outbound with one driver and return with another

- **WHEN** a dependent has an `ACTIVE` contract with driver A covering only `OUTBOUND` Monday to Friday
- **AND** an admin creates a contract with driver B covering only `RETURN` Monday to Friday for the same dependent
- **THEN** the system returns HTTP 201

#### Scenario: Colliding slots are rejected

- **WHEN** a dependent has an `ACTIVE` contract covering `OUTBOUND` on `MONDAY`
- **AND** an admin creates another contract covering `OUTBOUND` on `MONDAY` for the same dependent
- **THEN** the system returns HTTP 409 with the message resolved from `contract.item.slot_conflict`

#### Scenario: A suspended contract keeps its slots reserved

- **WHEN** a dependent has a `SUSPENDED` contract with driver A covering `OUTBOUND` on `MONDAY`
- **AND** an admin creates a contract with driver B covering `OUTBOUND` on `MONDAY` for the same dependent
- **THEN** the system returns HTTP 409 with the message resolved from `contract.item.slot_conflict`

#### Scenario: Restoring a contract whose slots were taken meanwhile is rejected

- **WHEN** an `ACTIVE` contract covering `OUTBOUND` on `MONDAY` was deleted
- **AND** the same dependent got another signed, not ended contract covering `OUTBOUND` on `MONDAY`
- **AND** an admin restores the deleted contract
- **THEN** the system returns HTTP 409 with the message resolved from `contract.item.slot_conflict`
- **AND** the contract stays deleted

### Requirement: Contract status and transitions

`ContractStatus` MUST be a backed Java enum with `AWAITING_SIGNATURES`, `SIGNED`, `ACTIVE`, `SUSPENDED`, `ENDED`, `TERMINATED`, `CANCELLED` and `SUPERSEDED`. The legal transitions MUST be:

- `AWAITING_SIGNATURES → SIGNED | CANCELLED`
- `SIGNED → ACTIVE | CANCELLED`
- `ACTIVE → SUSPENDED | ENDED | TERMINATED | SUPERSEDED`
- `SUSPENDED → ACTIVE | ENDED | TERMINATED`

`ENDED`, `TERMINATED`, `CANCELLED` and `SUPERSEDED` MUST be terminal. The decision MUST live in a policy class with no Spring context, JPA model or servlet type.

#### Scenario: The transition policy is decided without a database

- **WHEN** the policy is exercised for all 64 ordered pairs of `ContractStatus`
- **THEN** it accepts exactly the transitions listed above
- **AND** the test runs with no Spring context and no persistence

### Requirement: The admin creates active contracts and corrects them through the state machine

In this change the admin MUST be the only writer of contracts. `POST /api/contracts` MUST create the contract with `status = ACTIVE`, MUST require the link's driver to have `approval_status = APPROVED`, and MUST reject a link with `status = BLOCKED`.

`PATCH /api/contracts/{token}` MUST accept only `status` (rule 16, `JsonNullable`) and MUST apply the transition policy: the admin correction MUST NOT bypass it. `SUPERSEDED` MUST be rejected on `PATCH`, because it only exists with a successor contract. Dates, amounts, items and schedules MUST NOT be mutable.

#### Scenario: Ending an active contract

- **WHEN** an admin patches an `ACTIVE` contract with `status = ENDED`
- **THEN** the system returns HTTP 200 with `status = ENDED`

#### Scenario: Reopening an ended contract is rejected

- **WHEN** an admin patches an `ENDED` contract with `status = ACTIVE`
- **THEN** the system returns HTTP 409 with the message resolved from `contract.status.invalid_transition`
- **AND** the stored status remains `ENDED`

#### Scenario: Superseding by PATCH is rejected

- **WHEN** an admin patches an `ACTIVE` contract with `status = SUPERSEDED`
- **THEN** the system returns HTTP 422 with the message resolved from `contract.status.requires_successor`

#### Scenario: An empty PATCH changes nothing

- **WHEN** an admin patches a contract with an empty JSON body
- **THEN** the system returns HTTP 200
- **AND** every stored field of the contract is unchanged

#### Scenario: A driver pending approval cannot be contracted

- **WHEN** an admin creates a contract for a link whose driver has `approval_status = PENDING`
- **THEN** the system returns HTTP 422 with the message resolved from `contract.driver.not_approved`

### Requirement: Contracts are soft-deleted as an administrative correction

`contract`, `contract_item`, `schedule`, `schedule_slot` and `unlinked_passenger` MUST use soft delete (rule 19). Deleting a contract MUST soft-delete its items and their schedules in the same transaction. Restoring MUST bring them back, and MUST be rejected when the link already has another `ACTIVE` contract. Ending a contract is a status change, not a deletion.

#### Scenario: A deleted contract disappears from listings

- **WHEN** an admin deletes a contract
- **THEN** the system returns HTTP 204
- **AND** the contract no longer appears in `GET /api/contracts`
- **AND** the row and its items remain in the database with `deleted_at` set

#### Scenario: Restoring over a newer active contract is rejected

- **WHEN** a link has an `ACTIVE` contract created after another one was deleted
- **AND** an admin restores the deleted contract, which was `ACTIVE`
- **THEN** the system returns HTTP 409 with the message resolved from `contract.active_conflict`

### Requirement: The parties of the link read their contracts

The client and the driver of a link MUST be able to read a contract of that link through `GET /api/contracts/{token}`, authorized by `hasAuthority('show_contract') or @sec.isContractParty(#token, authentication)`, and list the contracts of the link through `GET /api/client-drivers/{token}/contracts`, authorized by `hasAuthority('list_contracts') or @sec.isClientDriverLinkParty(#token, authentication)`. Creating, patching, deleting and restoring MUST require the matching admin permission only.

Responses MUST expose only opaque tokens, never numeric ids.

#### Scenario: The client reads its own contract

- **WHEN** the client of the link requests `GET /api/contracts/{token}`
- **THEN** the system returns HTTP 200

#### Scenario: Another client cannot read the contract

- **WHEN** a client who is not part of the link requests `GET /api/contracts/{token}`
- **THEN** the system returns HTTP 403

#### Scenario: A party cannot end the contract

- **WHEN** the driver of the link patches the contract with `status = ENDED`
- **THEN** the system returns HTTP 403

#### Scenario: The response carries no internal id

- **WHEN** a party reads a contract
- **THEN** the response contains `token` fields for the contract, the link, the items and the dependents
- **AND** contains no `id` field
