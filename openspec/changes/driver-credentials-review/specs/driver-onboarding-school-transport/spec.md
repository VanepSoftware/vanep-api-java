## Purpose

A driver only becomes approved with the legal set of school transport documents reviewed and one van ready to work, so every approved driver can offer at least one van as soon as the approval comes.

## ADDED Requirements

### Requirement: The onboarding asks for the legal set and one complete van

The documents step of `GET /api/drivers/me/onboarding` MUST be complete only when the driver has, each sent and either `PENDING` or `APPROVED` and not expired (see `driver-compliance`): a `SCHOOL_DRIVER_COURSE` and a `CRIMINAL_RECORD` driver document, and at least one active van with a `CRLV`, a `VEHICLE_INSPECTION` and a `SCHOOL_VEHICLE_AUTHORIZATION`. A requirement that is missing, rejected or expired MUST be listed as missing. `MUNICIPAL_AUTHORIZATION` MUST NOT be required. The step MUST list the missing driver document types and, per active van, the missing vehicle document types. Other vans without documents MUST NOT make the step incomplete.

#### Scenario: One complete van out of two

- **WHEN** the driver has the course and the criminal record, van A with the three documents and van B with none
- **THEN** the documents step is complete

#### Scenario: Missing the course

- **WHEN** the driver has every document except the `SCHOOL_DRIVER_COURSE`
- **THEN** the documents step is incomplete and lists `SCHOOL_DRIVER_COURSE` as missing

### Requirement: Documents are sent one at a time

The driver MUST be able to send each document (its type and its file) separately, in any order and across sessions, before and after submitting the onboarding and after a rejection. An incomplete step MUST only report what is missing and MUST NOT block saving any document. Only `POST /api/drivers/me/submit-onboarding` MUST require every step complete: it MUST reject with HTTP 422, listing the missing driver document types and, per active van, the missing vehicle document types, and MUST keep every document already saved.

#### Scenario: Sending the CRLV today and the course later

- **WHEN** the driver sends only the van's CRLV and reads the onboarding
- **THEN** the documents step is incomplete, lists the remaining types, and the CRLV is kept
- **AND** a week later the driver adds the course and the step reflects it

#### Scenario: Submitting with documents missing

- **WHEN** the driver submits the onboarding without the `SCHOOL_VEHICLE_AUTHORIZATION` of any van
- **THEN** the response is HTTP 422 naming `SCHOOL_VEHICLE_AUTHORIZATION` and the van's plate
- **AND** the driver's approval status and saved documents are unchanged

#### Scenario: Fixing a document after a rejection

- **WHEN** the admin rejects the van's inspection as `ILLEGIBLE` and the driver, as `REJECTED`, sends a new inspection and submits again
- **THEN** the submission is accepted, the driver is `UNDER_REVIEW` and the new inspection is `PENDING`

### Requirement: The CNH step requires category D or E

The CNH step MUST be complete only with an active CNH, `PENDING` or `APPROVED`, not expired, and without a category that lacks `D` and `E`. A `PENDING` CNH has no category nor expiry until the reviewer approves it (see `driver-cnh-review`), and MUST count as sent. The category MUST be checked on approval, where only `D`, `E`, `AD` and `AE` are accepted. A CNH saved before this change with another category MUST still be readable and MUST be reported as `cnh_category`, an approved CNH that is expired as `cnh_expired`, and a CNH whose latest submission is `REJECTED` (for instance as `NOT_ELIGIBLE`) as `cnh_rejected`.

#### Scenario: The CNH was rejected

- **WHEN** the admin rejects the driver's only CNH
- **THEN** the CNH step is incomplete with `cnh_rejected`

#### Scenario: The CNH just sent

- **WHEN** the driver's only CNH is `PENDING`, with its file and no data yet
- **THEN** the CNH step is complete

#### Scenario: Category B

- **WHEN** the driver's only CNH, saved before this change, has category `B`
- **THEN** the CNH step is incomplete with `cnh_category`

### Requirement: Approving the driver requires the reviewed documents

`POST /api/drivers/{token}/approve` MUST lock the driver, MUST recompute the driver's compliance (see `driver-compliance`) inside the same transaction, and MUST reject the approval with HTTP 422 (`driver.onboarding.documents_not_approved`) unless `canApprove` is true: the driver is `UNDER_REVIEW`, the driver's own requirements are satisfied and at least one active van is qualified on the current date. The 422 body MUST carry the `blockers` of the compliance. Two concurrent decisions on the same driver MUST be serialized: the second gets HTTP 400 (`driver.onboarding.not_under_review`). Drivers approved before this change MUST stay approved.

#### Scenario: Approving with a pending inspection

- **WHEN** an admin approves a driver under review whose only van has the inspection still `PENDING`
- **THEN** the response is HTTP 422 with the message resolved from `driver.onboarding.documents_not_approved` and a blocker for `VEHICLE_INSPECTION` of that van
- **AND** the driver is still under review

#### Scenario: Approving with everything reviewed

- **WHEN** the admin approves the documents and then the driver
- **THEN** the driver is `APPROVED`

#### Scenario: Approve and reject at the same time

- **WHEN** one admin approves and another rejects the same driver at the same moment
- **THEN** exactly one decision is recorded and the other request gets HTTP 400

### Requirement: Rejecting the driver has a structured reason

`POST /api/drivers/{token}/reject` MUST require a `reasonCode` among `DATA_MISMATCH`, `NOT_ELIGIBLE`, `DUPLICATE_ACCOUNT` and `OTHER` (HTTP 400 otherwise). The `reason` message MUST be required only with `OTHER` (HTTP 400 `driver.rejection.reason.required`). The driver MUST store both.

#### Scenario: Rejecting a duplicate account

- **WHEN** an admin rejects a driver under review with `reasonCode = DUPLICATE_ACCOUNT` and no message
- **THEN** the driver is `REJECTED` with that code

### Requirement: Approval errors carry a stable code

The errors of approving and rejecting the driver MUST carry a `code` property equal to their message key: `driver.onboarding.documents_not_approved` (with the `blockers`), `driver.onboarding.not_under_review` and `driver.rejection.reason.required`.

#### Scenario: Approving a driver already decided

- **WHEN** an admin approves a driver that another admin already approved
- **THEN** the response is HTTP 400 with `code` `driver.onboarding.not_under_review`
