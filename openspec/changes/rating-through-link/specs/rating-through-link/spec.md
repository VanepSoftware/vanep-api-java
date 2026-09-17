## ADDED Requirements

### Requirement: Client ratings are soft-deleted like driver ratings

`client_rating` MUST become soft-deletable. A Flyway migration MUST add `deleted_at` and, **in the same migration**, replace the total unique index on `(driver_id, client_id)` with a partial one restricted to `WHERE deleted_at IS NULL`.

`ClientRatingModel` MUST carry `@SoftDelete(columnName = "deleted_at", strategy = TIMESTAMP)`. Deleting a client rating MUST NOT destroy the row.

Adding `deleted_at` without making the index partial MUST NOT happen: the row would stay while the total index still rejects a new rating for that pair, locking the pair permanently.

#### Scenario: Deleting a client rating keeps the row

- **WHEN** a client rating is deleted
- **THEN** looking it up by token returns empty
- **AND** the row is still stored with `deleted_at` set

#### Scenario: A removed pair can be rated again

- **WHEN** a client rating is deleted
- **AND** the same driver rates the same client again
- **THEN** the new rating is persisted

#### Scenario: A removed client rating can be restored

- **WHEN** a caller holding `restore_client_rating` restores a deleted rating
- **THEN** the system returns HTTP 200
- **AND** the rating is visible again

#### Scenario: Restoring an active client rating is refused

- **WHEN** a caller holding `restore_client_rating` restores a rating that was never deleted
- **THEN** the system returns HTTP 409
- **AND** the message is resolved from MessageSource key `client_rating.already_active`

## MODIFIED Requirements

### Requirement: A rating hangs on the client-driver link

`driver_rating` and `client_rating` MUST reference `client_driver` through a `client_driver_id` foreign key, and MUST NOT carry `client_id` or `driver_id` of their own. The pair MUST exist in one place only.

Flyway migrations MUST backfill the links, one per rating table: for every distinct pair in that table with no active link, a `client_driver` row MUST be created with status `ACTIVE`. A pair rated in both directions MUST produce exactly one link — the second migration MUST reuse the link the first one created.

#### Scenario: An existing rating keeps working after the migration

- **WHEN** a rating existed before the migration
- **THEN** after it, the rating points at a `client_driver` row
- **AND** that row holds the same client and driver the rating used to carry

#### Scenario: A pair rated in both directions shares one link

- **WHEN** a client rated a driver and that driver rated that client
- **THEN** the backfill creates a single link for the pair
- **AND** both ratings point at it

#### Scenario: A backfilled link is active

- **WHEN** the backfill creates a link for an already rated pair
- **THEN** its status is `ACTIVE`

### Requirement: Rating requires an existing link

Creating a rating MUST require an existing `client_driver` row for the pair. When no link exists, the system MUST return HTTP 404 with the message resolved from `driver_rating.link.not_found` or `client_rating.link.not_found`.

This replaces the TODO carried by both services. Rating someone the caller has no relationship with MUST NOT be possible.

#### Scenario: Rating a driver the client is linked to

- **WHEN** a client holding `create_driver_rating` rates a driver they are linked to
- **THEN** the system returns HTTP 201

#### Scenario: Rating without a link is refused

- **WHEN** a client holding `create_driver_rating` rates a driver they have no link with
- **THEN** the system returns HTTP 404
- **AND** no rating is persisted

#### Scenario: A driver rating a client without a link is refused

- **WHEN** a driver holding `create_client_rating` rates a client they have no link with
- **THEN** the system returns HTTP 404

### Requirement: One active rating per link per direction

The unique index on each rating table MUST be a partial unique index on `(client_driver_id)` restricted to `WHERE deleted_at IS NULL`, replacing the index on `(driver_id, client_id)`.

A link that is removed and created again is a different link, so a new rating MUST be allowed against it while the previous rating stays attached to the previous link.

#### Scenario: The same link cannot be rated twice in one direction

- **WHEN** a rating already exists for a link
- **AND** the same party rates that link again
- **THEN** the system returns HTTP 409

#### Scenario: Relinking allows a new rating

- **WHEN** a link is deleted and the same pair is linked again
- **AND** the party rates the new link
- **THEN** the rating is persisted
- **AND** the previous rating still points at the previous link

### Requirement: The rating response shape is unchanged

`DriverRatingResponseDTO` and `ClientRatingResponseDTO` MUST keep exposing `driverToken`, `driverName`, `clientToken` and `clientName`. The mapper MUST read them through the link.

The HTTP contract MUST NOT change: a data model migration that breaks the mobile app is a migration that does not ship.

#### Scenario: The response still carries both parties

- **WHEN** a rating is returned
- **THEN** the body holds the driver token and name, and the client token and name
- **AND** no `id` is exposed for the rating, the link, the client or the driver

#### Scenario: Listings do not fall into N+1

- **WHEN** a page of ratings is listed
- **THEN** the link, its client user and its driver user are loaded with a fetch join
