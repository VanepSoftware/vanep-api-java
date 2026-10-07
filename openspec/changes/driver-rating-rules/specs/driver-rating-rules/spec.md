## ADDED Requirements

### Requirement: Only a client with an active link rates the driver

A client MUST be able to rate a driver only through a `client_driver` link in status `ACTIVE`, created at least five minutes before the rating. The `CLIENT` role bundle MUST hold `create_driver_rating`, and MUST NOT hold `list_driver_ratings`.

A rating request with no link MUST keep returning HTTP 404 `driver_rating.link.not_found`. A rating request whose link exists in any other status MUST return HTTP 422 `driver_rating.link.not_active`.

#### Scenario: A client with an active link rates the driver

- **WHEN** a client with an `ACTIVE` link to a driver rates that driver
- **THEN** the system returns HTTP 201

#### Scenario: A link that is not active cannot rate

- **WHEN** a client whose link to the driver is `PENDING`, `INACTIVE` or `BLOCKED` rates that driver
- **THEN** the system returns HTTP 422
- **AND** no rating is stored

#### Scenario: A link younger than five minutes cannot rate yet

- **WHEN** a client whose `ACTIVE` link to the driver was created less than five minutes ago rates that driver
- **THEN** the system returns HTTP 422 `driver_rating.link.too_recent`
- **AND** no rating is stored

#### Scenario: A link exactly five minutes old can rate

- **WHEN** a client whose `ACTIVE` link was created five minutes ago or more rates that driver
- **THEN** the system returns HTTP 201

#### Scenario: A client without a link cannot rate

- **WHEN** a client with no link to the driver rates that driver
- **THEN** the system returns HTTP 404

### Requirement: A rating is immutable for its author

No endpoint MUST allow editing a driver rating. `update_driver_rating` MUST NOT exist.

Deleting a driver rating MUST require `delete_driver_rating`, held only by the `ADMIN` bundle. The author of the rating MUST NOT be able to delete it.

#### Scenario: The author cannot delete their rating

- **WHEN** the client who wrote a rating deletes it
- **THEN** the system returns HTTP 403
- **AND** the rating still exists

#### Scenario: The admin removes a rating

- **WHEN** an admin deletes a rating
- **THEN** the row no longer exists
- **AND** the driver's average is recalculated without it

### Requirement: The driver never reaches an individual rating

No endpoint reachable by the rated driver MUST expose an individual rating, its author, or whether a given client has rated.

#### Scenario: The driver cannot list their ratings

- **WHEN** a driver lists driver ratings, filtered by their own token or not
- **THEN** the system returns HTTP 403

#### Scenario: The driver cannot open a rating

- **WHEN** a driver requests a rating of themselves by its token
- **THEN** the system returns HTTP 403

#### Scenario: The link response does not reveal who rated

- **WHEN** a driver reads their client-driver links
- **THEN** no field tells whether the client of a link has rated

### Requirement: The client knows whether to show the rate button

The system MUST expose, to the client only, whether they have rated a given driver and whether they can rate them now.

#### Scenario: A client with an active, unrated link can rate

- **WHEN** a client with an `ACTIVE` link that has no rating asks for the rating status of that driver
- **THEN** the response has `rated` false and `canRate` true

#### Scenario: A client who already rated cannot rate again

- **WHEN** a client who already rated the driver asks for the rating status
- **THEN** the response has `rated` true and `canRate` false

#### Scenario: A client whose link is not active cannot rate

- **WHEN** a client whose link is not `ACTIVE`, is younger than five minutes, or who has no link, asks for the rating status
- **THEN** the response has `canRate` false

### Requirement: The driver's average is the average of existing ratings, or null

`driver.rating` MUST equal the average of the driver's existing ratings, rounded to two decimals. A driver with no rating MUST have `driver.rating` null, never `0.00` or `5.00`. Every write path — create, admin delete and seeding — MUST recalculate it.

A Flyway migration MUST recalculate `driver.rating` for every driver once, from the ratings that exist.

#### Scenario: Deleting the last rating leaves the driver without a rating

- **WHEN** an admin deletes the only rating of a driver
- **THEN** `driver.rating` is null

#### Scenario: A driver never rated shows no rating

- **WHEN** a client reads the profile or search result of a driver with no rating
- **THEN** the response carries `rating` as null

#### Scenario: A rating on a removed link does not count

- **WHEN** the link of a rating is soft-deleted
- **THEN** that rating does not count towards the driver's average
