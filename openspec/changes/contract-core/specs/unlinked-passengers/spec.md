## ADDED Requirements

### Requirement: The driver manages their own unlinked passengers

An approved or pending driver MUST be able to create, list, read, update, replace the schedule of, and delete their own unlinked passengers under `/api/drivers/me/unlinked-passengers`. The driver MUST be resolved from the `Authentication`, never from the request body or the URL. A caller whose `UserType` is not `DRIVER` MUST be rejected with HTTP 403.

A token in the URL MUST be resolved within the caller's own passengers: a token that belongs to another driver MUST return HTTP 404.

| Method | Path | Effect |
|---|---|---|
| `GET` | `/api/drivers/me/unlinked-passengers` | list |
| `POST` | `/api/drivers/me/unlinked-passengers` | create with schedule |
| `GET` | `/api/drivers/me/unlinked-passengers/{token}` | read |
| `PATCH` | `/api/drivers/me/unlinked-passengers/{token}` | partial update (rule 16) |
| `PUT` | `/api/drivers/me/unlinked-passengers/{token}/schedule` | replace the schedule |
| `DELETE` | `/api/drivers/me/unlinked-passengers/{token}` | soft delete |

#### Scenario: A driver registers a student they already transport

- **WHEN** a driver creates an unlinked passenger with name, school, school shift, a pickup address (city token, street, zip code) and a schedule
- **THEN** the system returns HTTP 201 with the passenger token
- **AND** the passenger appears in the driver's list

#### Scenario: Another driver's passenger is not found

- **WHEN** driver B requests `GET /api/drivers/me/unlinked-passengers/{token}` with a token that belongs to driver A
- **THEN** the system returns HTTP 404

#### Scenario: A client cannot manage unlinked passengers

- **WHEN** an authenticated `CLIENT` requests `GET /api/drivers/me/unlinked-passengers`
- **THEN** the system returns HTTP 403

#### Scenario: A single-field PATCH keeps the other fields

- **WHEN** a driver patches an unlinked passenger with only `notes`
- **THEN** name, school, school shift, address and schedule remain unchanged

### Requirement: Unlinked passengers hold only what the route needs

An unlinked passenger MUST have `name`, `school_id`, `school_shift` (`SchoolShift`), `address_id` and `schedule_id`, and MAY have `notes`. It MUST NOT store birth date, document, phone or e-mail. Its address MUST be an owned row of the `address` table, created through the same catalog flow as the dependent's address (`cityToken`, street, zip code), and a partial unique index on `address_id` `WHERE deleted_at IS NULL` MUST keep the single-owner rule.

There MUST be no limit on the number of unlinked passengers per driver and no end date.

#### Scenario: Unknown personal fields are not stored

- **WHEN** a driver creates an unlinked passenger with a body that also carries `birthDate` and `document`
- **THEN** the system returns HTTP 201
- **AND** neither value is persisted

### Requirement: Unlinked passengers never notify a guardian and are never physically deleted

No notification to a guardian MUST be triggered by an unlinked passenger. Deleting an unlinked passenger MUST be a soft delete, so that operation records that reference it remain valid.

#### Scenario: Deleting keeps the row

- **WHEN** a driver deletes an unlinked passenger
- **THEN** the system returns HTTP 204
- **AND** the passenger no longer appears in the driver's list
- **AND** the row remains in the database with `deleted_at` set
