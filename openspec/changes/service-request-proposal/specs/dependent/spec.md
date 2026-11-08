## ADDED Requirements

### Requirement: Weekly school days of a dependent

A dependent MUST be able to have a weekly school grid: for each weekday the dependent attends school, one entry with `weekday` (`DayOfWeek`) and `schoolShift` (`SchoolShift`, the period at school that day). A weekday with no school MUST have no entry. Each weekday MUST appear at most once per dependent, guaranteed by the database.

The grid is a sub-resource of the dependent, read and replaced as a whole:

| Method | Path | Permission | Effect |
|---|---|---|---|
| `GET` | `/api/dependent/{token}/school-days` | `show_dependent` | the grid, Monday first |
| `PUT` | `/api/dependent/{token}/school-days` | `update_dependent` | replaces the whole grid |

The same ownership rules as `GET /api/dependent/{token}` and `PATCH /api/dependent/{token}` MUST apply: a `ROLE_CLIENT` reads and replaces only their own dependents' grid, and gets HTTP 403 for another client's; `ROLE_ADMIN` reaches any active dependent; an unknown or soft-deleted dependent is HTTP 404.

The `PUT` body is `{"days": [...]}`. An empty list MUST clear the grid. A missing `weekday` or `schoolShift`, an unknown value, or the same weekday twice MUST return HTTP 400 and leave the stored grid unchanged. Replacing the grid MUST NOT change any other dependent field, nor any service request already sent.

While the dependent is in a contract that is `AWAITING_SIGNATURES`, `SIGNED`, `ACTIVE` or `SUSPENDED`, the `PUT` MUST return HTTP 422 with the message of `dependent.open_contract` and leave the stored grid unchanged: the grid is part of what the contract covers, and changing it is an amendment.

The grid is the only source of the school period: the dependent no longer has a single `shift` field.

#### Scenario: Full-time on two days

- **WHEN** a client replaces the grid of their dependent with `MORNING` on Monday, Wednesday and Friday and `FULLTIME` on Tuesday and Thursday
- **THEN** the system returns HTTP 200 with the five days, Monday first
- **AND** `GET /api/dependent/{token}/school-days` returns the same five days

#### Scenario: The same weekday twice

- **WHEN** a client sends a grid with two entries for `MONDAY`
- **THEN** the system returns HTTP 400
- **AND** the stored grid is unchanged

#### Scenario: Another client's dependent

- **WHEN** a `ROLE_CLIENT` replaces the grid of another client's dependent
- **THEN** the system returns HTTP 403
- **AND** that dependent's grid is unchanged

#### Scenario: Clearing the grid

- **WHEN** a client replaces the grid with an empty list
- **THEN** the system returns HTTP 200 with no days
- **AND** the dependent is no longer ready for a service request

#### Scenario: Replacing the grid keeps the other fields

- **WHEN** a client replaces the grid of a dependent
- **THEN** the dependent's name, birth date, school and address remain unchanged

#### Scenario: A deleted and restored dependent keeps its grid

- **WHEN** a dependent with a grid is soft-deleted and then restored
- **THEN** `GET /api/dependent/{token}/school-days` returns the grid it had before

#### Scenario: Replacing the grid under a contract

- **WHEN** a client replaces the grid of a dependent that is in an `ACTIVE` contract
- **THEN** the system returns HTTP 422 with the message of `dependent.open_contract`
- **AND** the stored grid is unchanged

### Requirement: The dependent's school comes with its address

The `school` object of every dependent response MUST include the school's `address` (`school-address`), or `null` when the school has none. Listing dependents MUST read the dependents' and the schools' addresses in one query, whatever the number of dependents.

#### Scenario: A dependent whose school has an address

- **WHEN** a client reads a dependent whose school was resolved from a place with a street
- **THEN** the response has `school.address` with the street, zip code, city and neighborhood of the school

#### Scenario: A dependent whose school has no address

- **WHEN** a client reads a dependent whose school has no address
- **THEN** the response has `school.address = null`

## MODIFIED Requirements

### Requirement: Dependent creation

The system MUST allow an authenticated user with `ROLE_CLIENT` to create a dependent linked to their `client_id`. The system MUST allow a user with `ROLE_ADMIN` to create a dependent for any `client_id` supplied in the request.

Accepted creation fields: `name` (required), `birth_date`, `gender`, `document`, `phone`, `email`, `is_self`, `schoolToken`, `addressToken`, and `clientToken` (ADMIN only). Neither the school nor the weekly school grid is required to create a dependent; both can be set later.

#### Scenario: Client creates dependent successfully

- **WHEN** an authenticated `ROLE_CLIENT` user sends `POST /api/dependent` with a valid `name`
- **THEN** the system returns HTTP 201
- **AND** the dependent is persisted with the authenticated client's `client_id`
- **AND** a unique `token` is generated automatically

#### Scenario: Creation without authentication

- **WHEN** a `POST /api/dependent` request is sent without a valid JWT
- **THEN** the system returns HTTP 401

#### Scenario: Name required

- **WHEN** an authenticated user sends a create request without `name`
- **THEN** the system returns HTTP 400 with a validation message in English

#### Scenario: A shift sent on creation is ignored

- **WHEN** a client sends `POST /api/dependent` with a `shift`
- **THEN** the system returns HTTP 201
- **AND** the response has no `shift` field

### Requirement: Dependent update

The system MUST allow `PATCH /api/dependent/{token}` for partial update of editable fields. The same ownership rules as read MUST apply (`ROLE_CLIENT` edits only their own; `ROLE_ADMIN` edits any).

While the dependent is in a contract that is `AWAITING_SIGNATURES`, `SIGNED`, `ACTIVE` or `SUSPENDED`, a `PATCH` that sends `schoolToken` or `address` MUST return HTTP 422 with the message of `dependent.open_contract` and change nothing, for any caller and even when the value is the current one. The other fields remain editable.

#### Scenario: Successful partial update

- **WHEN** the owner sends `PATCH /api/dependent/{token}` with valid fields
- **THEN** the system returns HTTP 200 with the updated dependent
- **AND** `updated_at` is updated

#### Scenario: Client attempts to edit another client's dependent

- **WHEN** a `ROLE_CLIENT` sends `PATCH` for a dependent of another `client_id`
- **THEN** the system returns HTTP 403

#### Scenario: Changing the school or the address under a contract

- **WHEN** a client sends `PATCH /api/dependent/{token}` with `schoolToken` or with `address` for a dependent that is in an `ACTIVE` contract
- **THEN** the system returns HTTP 422 with the message of `dependent.open_contract`
- **AND** the dependent is unchanged

#### Scenario: Correcting the name under a contract

- **WHEN** a client sends `PATCH /api/dependent/{token}` with only `name` for a dependent that is in an `ACTIVE` contract
- **THEN** the system returns HTTP 200 with the new name

### Requirement: Dependent soft delete

The system MUST allow `DELETE /api/dependent/{token}` via `repository.delete(entity)`, which under `@SoftDelete` sets `deleted_at` to the current timestamp without removing the record from the database.

A dependent that is in an open service request (`OPEN` or `ANSWERED`) MUST NOT be removed: the system MUST return HTTP 422 with the message of `dependent.open_negotiation` and keep the dependent unchanged. Once the request is withdrawn, declined, expired or converted, the dependent can be removed again.

A dependent that is in a contract that is `AWAITING_SIGNATURES`, `SIGNED`, `ACTIVE` or `SUSPENDED` MUST NOT be removed either: the system MUST return HTTP 422 with the message of `dependent.open_contract` and keep the dependent, the contract and the driver's route unchanged. Once each of its contracts is `ENDED`, `TERMINATED`, `CANCELLED` or `SUPERSEDED`, the dependent can be removed. Restoring a dependent is never locked.

#### Scenario: Successful delete

- **WHEN** the owner or admin sends `DELETE /api/dependent/{token}` for an active dependent
- **THEN** the system returns HTTP 204
- **AND** `deleted_at` is set by Hibernate

#### Scenario: Delete of already deleted record

- **WHEN** attempting to delete an already soft-deleted dependent
- **THEN** the system returns HTTP 404

#### Scenario: A dependent in an open service request

- **WHEN** a client removes a dependent that is in a service request waiting for the driver's answer
- **THEN** the system returns HTTP 422 with the message of `dependent.open_negotiation`
- **AND** the dependent and the service request are unchanged

#### Scenario: The request was withdrawn

- **WHEN** a client withdraws the only open service request of a dependent and then removes the dependent
- **THEN** the system returns HTTP 204

#### Scenario: A dependent in an active contract

- **WHEN** a client removes a dependent that is in an `ACTIVE` contract
- **THEN** the system returns HTTP 422 with the message of `dependent.open_contract`
- **AND** the contract still lists the dependent's item and the driver's route still lists the dependent

#### Scenario: The contract has ended

- **WHEN** a client removes a dependent whose only contract is `ENDED`
- **THEN** the system returns HTTP 204

### Requirement: Input validation via DTOs

The system MUST validate requests with Bean Validation on dedicated DTOs (`@Valid` on the controller). The system MUST NOT accept direct request-body binding to a JPA entity.

`document`, when present on create or update, MUST be a valid CPF (11 digits with valid check digits, with or without the mask), otherwise HTTP 400 with the message of `dependent.document.invalid`. The system MUST store it as digits only. Sending `document: null` on update MUST clear it.

#### Scenario: Duplicate document

- **WHEN** a create or update sets `document` already used by another active dependent
- **THEN** the system returns HTTP 409 with a message in English

#### Scenario: Invalid CPF

- **WHEN** a client creates or updates a dependent with a `document` whose check digits are wrong
- **THEN** the system returns HTTP 400 with the message of `dependent.document.invalid`
- **AND** nothing is stored

#### Scenario: Masked CPF

- **WHEN** a client sends `document` as `529.982.247-25`
- **THEN** the dependent is stored with `52998224725`
