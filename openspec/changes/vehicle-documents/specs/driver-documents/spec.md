## Purpose

A driver's documents are only the documents of the person, with a number when the document has one, so the contract can cite the driver's own credentials and the van's documents live with the van.

## ADDED Requirements

### Requirement: Driver documents have only the person's types

A driver document MUST have one of the types `MUNICIPAL_AUTHORIZATION`, `SCHOOL_DRIVER_COURSE`, `CRIMINAL_RECORD`, `RESIDENCE_PROOF` or `OTHER`. The types `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO` and `VEHICLE_PHOTO` MUST be rejected with HTTP 400. A `MUNICIPAL_AUTHORIZATION` MUST have a `documentNumber` (HTTP 400 `driver_document.number.required` otherwise). A document number MUST have at most 64 characters and MUST be stored trimmed.

#### Scenario: The driver registers the specialized course

- **WHEN** the driver calls `POST /api/driver-documents` with `documentType = SCHOOL_DRIVER_COURSE` and `documentNumber = "CERT-2024-118"`
- **THEN** the response is HTTP 201 with the document `PENDING` and that number

#### Scenario: A van's document type is refused

- **WHEN** the driver calls `POST /api/driver-documents` with `documentType = CRLV`
- **THEN** the response is HTTP 400

### Requirement: Legacy documents of the wrong types are removed

When this change is applied, every existing driver document of type `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO` or `VEHICLE_PHOTO` MUST be soft deleted, without being moved to a van: no app ever sent them, and the seed creates the van's documents in their own table. The documents of the person's types MUST stay as they are.

#### Scenario: A legacy CRLV

- **WHEN** the migration runs for a driver with a `CRLV` driver document
- **THEN** the driver has no live `CRLV` driver document and no vehicle document was created from it
- **AND** the driver's `CRIMINAL_RECORD` is unchanged

### Requirement: The caller is the token's user

The driver document endpoints MUST identify the caller by the user token in the access token (the `uid` claim), the same way as the rest of the API, and MUST NOT look the caller up by the token subject. The subject is the e-mail the user had when the token was issued: after an e-mail change it no longer matches the account, and the freed address can later belong to another account.

#### Scenario: The driver changed their e-mail

- **WHEN** a driver changes their e-mail and lists their documents with a token issued before the change
- **THEN** the response is HTTP 200 with their own documents
