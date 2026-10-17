## Purpose

Only a client and a driver who actually had a contract running can rate each other, so a conversation or an unanswered request never turns into a public rating.

## ADDED Requirements

### Requirement: Rating requires a contract that reached ACTIVE

Creating a driver rating and creating a client rating MUST require that the pair's link has at least one non-deleted contract whose status is `ACTIVE`, `SUSPENDED`, `ENDED`, `TERMINATED` or `SUPERSEDED`. These are the statuses a contract can only hold after it was `ACTIVE`. A link whose contracts are all `AWAITING_SIGNATURES`, `SIGNED` or `CANCELLED`, or that has no contract, MUST NOT allow a rating.

The current link status MUST NOT decide eligibility: a link that is `INACTIVE` or `BLOCKED` because its contract ended still allows the rating that its past contract earned.

| Rating | Refusal |
|---|---|
| client rates driver | HTTP 422 `driver_rating.link.never_contracted` |
| driver rates client | HTTP 422 `client_rating.link.never_contracted` |

The other existing rules stay as they are: no link is HTTP 404, rating oneself is HTTP 400, a second rating of the same link is HTTP 409, and the driver rating keeps the five-minute minimum link age.

#### Scenario: A client rates a driver after the contract ended

- **WHEN** a client rates the driver of a link whose only contract is `ENDED`
- **THEN** the system returns HTTP 201

#### Scenario: A client cannot rate a driver they only negotiated with

- **WHEN** a client rates the driver of a `PENDING` link that has a service request and no contract
- **THEN** the system returns HTTP 422 with the message of `driver_rating.link.never_contracted`
- **AND** no rating is stored

#### Scenario: A driver cannot rate a client without a contract

- **WHEN** a driver rates the client of a link with no contract
- **THEN** the system returns HTTP 422 with the message of `client_rating.link.never_contracted`
- **AND** no rating is stored

#### Scenario: A contract that never started does not count

- **WHEN** a client rates the driver of a link whose only contract is `CANCELLED`
- **THEN** the system returns HTTP 422

#### Scenario: A deleted contract does not count

- **WHEN** the only contract of a link reached `ACTIVE` and was then deleted by an admin
- **THEN** rating either party of that link returns HTTP 422

### Requirement: The rating status reflects the contract rule

`GET` of the client's rating status for a driver MUST report `canRate = false` whenever the rule above refuses the rating, and `canRate = true` only when a rating would be accepted.

#### Scenario: Rating status before any contract

- **WHEN** a client with a `PENDING` link and no contract reads the rating status for that driver
- **THEN** the response has `canRate = false`
