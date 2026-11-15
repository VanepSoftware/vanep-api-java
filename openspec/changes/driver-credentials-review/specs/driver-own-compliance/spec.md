## Purpose

The driver sees, in one call, what each requirement of their registration and of each active van needs, judged by the same policy the admin uses, and the professional summary the app shows on their profile.

## ADDED Requirements

### Requirement: The driver reads their own compliance

`GET /api/drivers/me/compliance` MUST return, to the calling driver resolved by the token's `uid`, `asOf` (today in `America/Sao_Paulo`), `approvalStatus`, `driverRequirements` and `vehicles` (each active van with `token`, `plate`, `brand`, `model`, `qualified` and `requirements`), evaluated by the `driver-compliance` policy on `asOf`. Each requirement MUST carry `requirement`, `optional`, `satisfied`, `state`, `current`, `pending` and `expiringSoon`, true when the requirement is `APPROVED` and the `current` submission expires within 30 days. A caller who is not a driver MUST get HTTP 403, and an unauthenticated caller HTTP 401.

#### Scenario: A new driver with nothing sent

- **WHEN** a driver without CNH, documents or vans calls `GET /api/drivers/me/compliance`
- **THEN** every driver requirement has `state` `MISSING`, `current` and `pending` are `null`, `MUNICIPAL_AUTHORIZATION` has `optional` `true`, and `vehicles` is empty

#### Scenario: An inspection about to expire

- **WHEN** the approved inspection of the driver's van expires in 20 days
- **THEN** that requirement has `state` `APPROVED`, `satisfied` `true` and `expiringSoon` `true`

#### Scenario: A client calls the route

- **WHEN** a client calls `GET /api/drivers/me/compliance`
- **THEN** the response is HTTP 403

### Requirement: The driver's view hides the reviewer

Each submission in the driver's compliance MUST carry `kind`, `token`, `documentNumber`, `expiresAt`, `status`, `rejectionReasonCode`, `rejectionReason`, `reviewedAt`, `createdAt`, `version`, `fileUrl` and `file` (`null` without file), and on the CNH also `category`, `issueDate` and the whole `registrationNumber`. The data filled on approval (`documentNumber`, `expiresAt`, `category`, `issueDate`, `registrationNumber`) MUST be `null` while the submission is `PENDING`. It MUST NOT carry who reviewed it, and the response MUST NOT carry the driver's CPF, e-mail or decision history.

#### Scenario: A document just sent

- **WHEN** the driver sends the van's inspection and reads their compliance
- **THEN** `VEHICLE_INSPECTION` has `state` `PENDING` and its `current` has the `file` and `expiresAt` `null`

#### Scenario: A rejected course

- **WHEN** an admin rejects the driver's `SCHOOL_DRIVER_COURSE` as `ILLEGIBLE`
- **THEN** the course requirement has `state` `REJECTED` and its `current` has `rejectionReasonCode` `ILLEGIBLE` and no reviewer

### Requirement: The driver's compliance in constant queries

The driver's compliance MUST be read in the same number of queries whatever the number of vans and documents of the driver.

#### Scenario: One van and three vans

- **WHEN** a driver with one van and a driver with three vans, each with its documents, read their compliance
- **THEN** both take the same number of queries

### Requirement: The driver's summary shows the rating count and the experience

`GET /api/drivers/me` MUST also return `ratingCount`, the number of ratings of the driver, and `experienceYears`, the years of experience stored on the driver.

#### Scenario: A driver rated three times

- **WHEN** a driver with 8 years of experience has three ratings and calls `GET /api/drivers/me`
- **THEN** `ratingCount` is 3 and `experienceYears` is 8
