## ADDED Requirements

### Requirement: The driver lists all of their students from both sources

`GET /api/drivers/me/students` MUST return, for the authenticated driver, the items of their `ACTIVE` contracts with `starts_on <= today <= ends_on` and their non-deleted unlinked passengers, in one list ordered by name. A caller whose `UserType` is not `DRIVER` MUST get HTTP 403.

Each student MUST carry `token`, `source` (`CONTRACT_ITEM` or `UNLINKED_PASSENGER`), `name`, `schoolToken`, `schoolName`, `schoolShift` (null for a contract item), `slots`, `vehicleToken`, `dependentToken`, `familyStatus`, `invitedAt` and `familyMatch`. The query count MUST NOT grow with the number of students (rule 17).

#### Scenario: Contracted and unlinked students together

- **WHEN** a driver has one `ACTIVE` contract item and one unlinked passenger
- **THEN** the list has two students, one per source

#### Scenario: A suspended contract is not listed

- **WHEN** the driver's only contract is `SUSPENDED`
- **THEN** its items are not in the list

#### Scenario: A constant number of queries

- **WHEN** the list runs for a driver with 3 students and then with 30
- **THEN** both runs issue the same number of SQL statements

### Requirement: The family status is derived

`familyStatus` MUST be:
- `WITH_FAMILY` for a contract item and for an unlinked passenger with `dependent_id`;
- `FAMILY_JOINED` for an unlinked passenger with a `PENDING` family match;
- `INVITED` for an unlinked passenger with a live invite and no pending match;
- `NOT_INVITED` otherwise.

#### Scenario: Invited student

- **WHEN** the driver invited the family of an unlinked passenger and nobody accepted
- **THEN** the student has `familyStatus = INVITED` and `invitedAt` set

#### Scenario: Merged student

- **WHEN** an unlinked passenger was merged with a dependent
- **THEN** the student has `familyStatus = WITH_FAMILY`, the `dependentToken`, and the dependent's name
