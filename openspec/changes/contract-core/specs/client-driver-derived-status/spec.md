## ADDED Requirements

### Requirement: The link status is derived from its contracts

`client_driver.status` MUST be computed from the statuses of the link's non-deleted contracts by a policy class with no Spring context, JPA model or servlet type:

| Contracts of the link | Link status |
|---|---|
| at least one `SIGNED`, `ACTIVE` or `SUSPENDED` | `ACTIVE` |
| none of those, but at least one contract | `INACTIVE` |
| no contract | `PENDING` |

A link with `status = BLOCKED` MUST keep `BLOCKED` regardless of its contracts.

The contract service MUST recompute the link status in the same transaction as every contract write: create, status change, delete and restore.

#### Scenario: Creating a contract activates the link

- **WHEN** an admin creates an `ACTIVE` contract for a `PENDING` link
- **THEN** the link status becomes `ACTIVE`

#### Scenario: Ending the only contract deactivates the link

- **WHEN** the only `ACTIVE` contract of a link changes to `ENDED`
- **THEN** the link status becomes `INACTIVE`

#### Scenario: A suspended contract keeps the link active

- **WHEN** the only `ACTIVE` contract of a link changes to `SUSPENDED`
- **THEN** the link status remains `ACTIVE`

#### Scenario: Deleting the only contract returns the link to pending

- **WHEN** an admin deletes the only contract of a link
- **THEN** the link status becomes `PENDING`

#### Scenario: A new contract for the same pair reuses the link

- **WHEN** a link is `INACTIVE` because its contract ended
- **AND** an admin creates a new `ACTIVE` contract for the same client and driver
- **THEN** the system reuses the existing link
- **AND** the link status becomes `ACTIVE`

### Requirement: The link status has no direct write path

`POST /api/client-drivers` MUST NOT accept `status`; every link MUST be created with `status = PENDING`. `PATCH /api/client-drivers/{token}` MUST NOT exist. The `update_client_driver` permission MUST be removed from `PermissionEnum` and from every role bundle.

#### Scenario: A status sent on creation is ignored

- **WHEN** an admin creates a link with a body containing `"status": "ACTIVE"`
- **THEN** the system returns HTTP 201
- **AND** the link status is `PENDING`

#### Scenario: A party cannot activate its own link

- **WHEN** the client of a link sends `PATCH /api/client-drivers/{token}` with `"status": "ACTIVE"`
- **THEN** the system returns HTTP 405
- **AND** the link status remains derived from its contracts

### Requirement: Existing links are brought in line with the rule

A Flyway migration MUST set `status = 'PENDING'` on every non-deleted `client_driver` row with `status = 'ACTIVE'` that has no contract, including the rows created by V38 and V39. The development seeder MUST create the link as `PENDING` and a separate per-feature `ContractSeeder` MUST create the `ACTIVE` contract that activates it.

#### Scenario: A legacy active link without contract becomes pending

- **WHEN** the database holds an `ACTIVE` link without any contract before the migration runs
- **THEN** after the migration the link status is `PENDING`

#### Scenario: The seeded link is active through a contract

- **WHEN** the application starts with seeding enabled on an empty database
- **THEN** the seeded link has an `ACTIVE` contract
- **AND** the link status is `ACTIVE`
