## Purpose

A driver's documents are only the documents of the person, sent by the driver as a file, with the number the reviewer reads from it when the document has one, so the contract can cite the driver's own credentials and the van's documents live with the van.

## ADDED Requirements

### Requirement: Driver documents have only the person's types

A driver document MUST have one of the types `MUNICIPAL_AUTHORIZATION`, `SCHOOL_DRIVER_COURSE` or `CRIMINAL_RECORD`. The types `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO`, `VEHICLE_PHOTO`, `RESIDENCE_PROOF` and `OTHER` MUST be rejected with HTTP 400.

The driver MUST send a document with `POST /api/driver-documents` as `multipart/form-data` with only `documentType` and `file`, and it MUST be created `PENDING` with that file and with `documentNumber` and `expiresAt` `null`. The number and the expiry MUST be filled by the reviewer on approval (see `document-review`). There MUST be no `PUT /api/driver-documents/{token}`.

An approved `MUNICIPAL_AUTHORIZATION` MUST have a `documentNumber`, enforced by the database. A document number MUST have at most 64 characters and MUST be stored trimmed.

#### Scenario: The driver sends the specialized course

- **WHEN** the driver calls `POST /api/driver-documents` with `documentType = SCHOOL_DRIVER_COURSE` and the certificate's PDF
- **THEN** the response is HTTP 201 with the document `PENDING`, the `file`, and no number

#### Scenario: A van's document type is refused

- **WHEN** the driver sends a driver document with `documentType = CRLV`
- **THEN** the response is HTTP 400

#### Scenario: A residence proof or a free type is refused

- **WHEN** the driver sends a driver document with `documentType = RESIDENCE_PROOF` or `OTHER`
- **THEN** the response is HTTP 400 and nothing is stored

### Requirement: Legacy documents of the wrong types are removed

When this change is applied, every existing driver document of type `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO`, `VEHICLE_PHOTO`, `RESIDENCE_PROOF` or `OTHER` MUST be soft deleted, without being moved to a van: no app ever sent the van's types, the seed creates the van's documents in their own table, and no approval rule reads a residence proof or a free type. The documents of the person's types MUST stay as they are, except an approved `MUNICIPAL_AUTHORIZATION` without number, which MUST go back to `PENDING` without reviewer so the reviewer approves it again with its number.

#### Scenario: A legacy CRLV

- **WHEN** the migration runs for a driver with a `CRLV` driver document
- **THEN** the driver has no live `CRLV` driver document and no vehicle document was created from it
- **AND** the driver's `CRIMINAL_RECORD` is unchanged

#### Scenario: A legacy residence proof

- **WHEN** the migration runs for a driver with a `RESIDENCE_PROOF` and an `OTHER` driver document
- **THEN** both are soft deleted and their files stay in `media_file`

### Requirement: The list filters combine

`GET /api/driver-documents` MUST apply every filter it receives at the same time. `documentType` and `status` MUST narrow the list together, for a driver (their own documents) and for an admin (every driver, or the one in `driverToken`). A filter that is not sent MUST NOT narrow the list.

#### Scenario: The driver filters by type and status

- **GIVEN** a driver with an approved and a rejected `SCHOOL_DRIVER_COURSE` and a rejected `CRIMINAL_RECORD`
- **WHEN** the driver lists with `documentType = SCHOOL_DRIVER_COURSE` and `status = REJECTED`
- **THEN** only the rejected course is listed

#### Scenario: The admin filters every driver's documents

- **GIVEN** two drivers with documents of different types and statuses
- **WHEN** an admin lists without `driverToken`, with `documentType = CRIMINAL_RECORD`, and then with `status = PENDING`
- **THEN** the first list has only the criminal records and the second only the pending documents, of both drivers

### Requirement: The caller is the token's user

The driver document endpoints MUST identify the caller by the user token in the access token (the `uid` claim), the same way as the rest of the API, and MUST NOT look the caller up by the token subject. The subject is the e-mail the user had when the token was issued: after an e-mail change it no longer matches the account, and the freed address can later belong to another account.

#### Scenario: The driver changed their e-mail

- **WHEN** a driver changes their e-mail and lists their documents with a token issued before the change
- **THEN** the response is HTTP 200 with their own documents
