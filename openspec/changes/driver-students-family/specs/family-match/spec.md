## ADDED Requirements

### Requirement: Accepting an invite suggests a match

A passenger invite MUST suggest that passenger. A group invite MUST suggest the driver's only not-merged unlinked passenger with the dependent's school and the same normalized first name (accent-insensitive, case-insensitive); with zero or several candidates it MUST NOT create a match. There MUST be at most one `PENDING` match per unlinked passenger. The unlinked passenger response MUST carry `familyMatch { token, dependentName, dependentAge, clientName }` while it is `PENDING`.

#### Scenario: Group link with one candidate

- **WHEN** a client accepts the group link for "Rafael Costa" at school X and the driver has one unlinked "Rafael" at school X
- **THEN** that passenger gets a `PENDING` match

#### Scenario: Group link with two candidates

- **WHEN** the driver has two unlinked "Rafael" at school X
- **THEN** no match is created

### Requirement: The driver merges a matched passenger

`POST /api/drivers/me/unlinked-passengers/{token}/merge` MUST require a `PENDING` match (otherwise 409 `family_match.not_pending`), set the passenger's `dependent_id` and the dependent's name, and mark the match `CONFIRMED`. Of concurrent merges, exactly one MUST succeed. The passenger MUST keep its token, schedule and operation records, and MUST count in `occupancy.studentsWithFamily`.

#### Scenario: Merge keeps the route position

- **WHEN** the driver merges a passenger that has checklist entries
- **THEN** the passenger keeps its token and its entries
- **AND** the route passenger query still returns it as `UNLINKED_PASSENGER`, now with the `dependentToken`

### Requirement: The driver dismisses a match

`POST /api/drivers/me/unlinked-passengers/{token}/family-match/dismiss` MUST mark the `PENDING` match `DISMISSED`. The passenger stays without family and its invite stays valid.

#### Scenario: Not the same student

- **WHEN** the driver dismisses the match
- **THEN** the passenger has `familyStatus = INVITED` and no `familyMatch`

### Requirement: A contract for the same dependent replaces the merged passenger

When a contract of the driver becomes `ACTIVE` with an item for a dependent that a live unlinked passenger is merged with, the system MUST soft delete that passenger in the same transaction.

#### Scenario: No duplicate after the contract

- **WHEN** the contract with an item for the merged dependent becomes `ACTIVE`
- **THEN** the students list shows that student once, as `CONTRACT_ITEM`
