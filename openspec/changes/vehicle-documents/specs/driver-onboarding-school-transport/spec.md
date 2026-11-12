## Purpose

A driver only becomes approved with the legal set of school transport documents reviewed and one van ready to work, so every approved driver can offer at least one van as soon as the approval comes.

## ADDED Requirements

### Requirement: The onboarding asks for the legal set and one complete van

The documents step of `GET /api/drivers/me/onboarding` MUST be complete only when the driver has, each with a file and not `REJECTED`: a `SCHOOL_DRIVER_COURSE` and a `CRIMINAL_RECORD` driver document, and at least one active van with a `CRLV`, a `VEHICLE_INSPECTION` and a `SCHOOL_VEHICLE_AUTHORIZATION`. `MUNICIPAL_AUTHORIZATION` MUST NOT be required. The step MUST list the missing driver document types and, per active van, the missing vehicle document types. Other vans without documents MUST NOT make the step incomplete.

#### Scenario: One complete van out of two

- **WHEN** the driver has the course and the criminal record, van A with the three documents and van B with none
- **THEN** the documents step is complete

#### Scenario: Missing the course

- **WHEN** the driver has every document except the `SCHOOL_DRIVER_COURSE`
- **THEN** the documents step is incomplete and lists `SCHOOL_DRIVER_COURSE` as missing

### Requirement: Documents are sent one at a time

The driver MUST be able to create and upload each document separately, in any order and across sessions, before and after submitting the onboarding and after a rejection. An incomplete step MUST only report what is missing and MUST NOT block saving any document. Only `POST /api/drivers/me/submit-onboarding` MUST require every step complete: it MUST reject with HTTP 422, listing the missing driver document types and, per active van, the missing vehicle document types, and MUST keep every document already saved.

#### Scenario: Sending the CRLV today and the course later

- **WHEN** the driver saves only the van's CRLV with its file and reads the onboarding
- **THEN** the documents step is incomplete, lists the remaining types, and the CRLV is kept
- **AND** a week later the driver adds the course and the step reflects it

#### Scenario: Submitting with documents missing

- **WHEN** the driver submits the onboarding without the `SCHOOL_VEHICLE_AUTHORIZATION` of any van
- **THEN** the response is HTTP 422 naming `SCHOOL_VEHICLE_AUTHORIZATION` and the van's plate
- **AND** the driver's approval status and saved documents are unchanged

#### Scenario: Fixing a document after a rejection

- **WHEN** the admin rejects the driver because the inspection is unreadable and the driver uploads a new file and submits again
- **THEN** the submission is accepted and the driver is `UNDER_REVIEW`

### Requirement: The CNH step requires category D or E

The CNH step MUST be complete only with an active CNH, not expired, whose category contains `D` or `E`. A CNH with another category MUST be reported as `cnh_category`.

#### Scenario: Category B

- **WHEN** the driver's only CNH has category `B`
- **THEN** the CNH step is incomplete with `cnh_category`

### Requirement: Approving the driver requires the reviewed documents

`POST /api/drivers/{token}/approve` MUST reject the approval with HTTP 422 (`driver.onboarding.documents_not_approved`) unless the driver's credentials (see `school-transport-credentials`) are satisfied on the current date for at least one of their active vans. Drivers approved before this change MUST stay approved.

#### Scenario: Approving with a pending inspection

- **WHEN** an admin approves a driver under review whose only van has the inspection still `PENDING`
- **THEN** the response is HTTP 422 with the message resolved from `driver.onboarding.documents_not_approved`
- **AND** the driver is still under review

#### Scenario: Approving with everything reviewed

- **WHEN** the admin approves the documents and then the driver
- **THEN** the driver is `APPROVED`
