## Purpose

Every client–driver link records where the pair came from, once, so that later phases can charge the commission by origin without anyone being able to change it.

## ADDED Requirements

### Requirement: Every link has an immutable origin

`client_driver.origin` MUST be `NOT NULL` and hold a `LinkOrigin` value: `CLIENT_SEARCH` (the client found the driver in the app) or `DRIVER_INVITE` (the driver invited the family). The origin MUST be written when the link is created and MUST have no update path: no endpoint, DTO or service method changes it afterwards. Reusing an existing link MUST keep its original origin.

Link responses MUST include `origin`.

#### Scenario: A reused link keeps its origin

- **WHEN** a client sends a service request to a driver whose link with them already exists with origin `DRIVER_INVITE`
- **THEN** the same link is reused
- **AND** its origin stays `DRIVER_INVITE`

#### Scenario: The origin is not writable

- **WHEN** any request body sent to an existing link carries `origin`
- **THEN** the stored origin does not change

### Requirement: The first service request creates the link

When a client sends a service request to a driver and the pair has no link, the system MUST create the link in the same transaction, with status `PENDING` and origin `CLIENT_SEARCH`. If two first requests of the same pair race, the one that loses MUST get HTTP 409 and leave nothing behind.

#### Scenario: First request of a pair

- **WHEN** a client with no link to a driver sends that driver a service request
- **THEN** a link is created with status `PENDING` and origin `CLIENT_SEARCH`
- **AND** the service request points to it

### Requirement: The admin creates links only with an explicit origin

`POST /api/client-drivers` stays restricted to `create_client_driver` and MUST require `origin` in the body. A missing or unknown `origin` MUST return HTTP 400. The link is still created `PENDING`.

#### Scenario: Admin creates a link without origin

- **WHEN** an admin sends `POST /api/client-drivers` with client and driver tokens and no `origin`
- **THEN** the system returns HTTP 400
- **AND** no link is created

#### Scenario: Admin creates a link with origin

- **WHEN** an admin sends `POST /api/client-drivers` with `origin = DRIVER_INVITE`
- **THEN** the system returns HTTP 201 with `status = PENDING` and `origin = DRIVER_INVITE`

### Requirement: Existing links get an origin and no orphan block

The migration that adds the column MUST set `origin = CLIENT_SEARCH` on every existing link before making it `NOT NULL`. A link that is `BLOCKED` with no recorded blocker MUST get the status the derived-status rule gives its contracts, because no party could ever unblock it.

#### Scenario: Legacy blocked link without blocker

- **WHEN** the migration runs on a `BLOCKED` link with no contract and no blocker
- **THEN** the link becomes `PENDING` with origin `CLIENT_SEARCH`
