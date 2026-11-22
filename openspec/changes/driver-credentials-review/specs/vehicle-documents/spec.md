## Purpose

The documents of a van belong to the van: each one names its vehicle, follows it, and keeps a history, so a driver with several vans can prove which van is registered, inspected and authorized for school transport.

## ADDED Requirements

### Requirement: Vehicle documents have their own types and a mandatory vehicle

A vehicle document MUST belong to exactly one vehicle and have one of the types `CRLV`, `VEHICLE_INSPECTION` or `SCHOOL_VEHICLE_AUTHORIZATION`. An approved vehicle document MUST have an expiry date, and an approved `SCHOOL_VEHICLE_AUTHORIZATION` MUST also have a document number, both enforced by the database. A document number MUST have at most 64 characters and MUST be stored trimmed.

#### Scenario: A person's document type is refused

- **WHEN** the owner sends a vehicle document with `documentType = CRIMINAL_RECORD`
- **THEN** the response is HTTP 400

### Requirement: The owner sends only the type and the file

The owner MUST send a document with `POST /api/vehicles/{token}/documents` as `multipart/form-data` with only `documentType` and `file`, and it MUST be created `PENDING` with that file. The number and the expiry MUST be filled by the reviewer on approval (see `document-review`) and stay `null` while it is `PENDING`. A missing type or file MUST be rejected with HTTP 400 and nothing persisted. There MUST be no `PUT /api/vehicle-documents/{token}`.

#### Scenario: The driver sends the inspection of one van

- **WHEN** the owner of a van calls `POST /api/vehicles/{token}/documents` with `documentType = VEHICLE_INSPECTION` and a PDF
- **THEN** the response is HTTP 201 with the document `PENDING`, its `vehicleToken`, the `file`, and `documentNumber` and `expiresAt` `null`

#### Scenario: A document without file is refused

- **WHEN** the owner calls `POST /api/vehicles/{token}/documents` with `documentType = CRLV` and no file
- **THEN** the response is HTTP 400 and no document is created

### Requirement: Only the van's owner and authorized admins manage its documents

`GET` and `POST /api/vehicles/{token}/documents` and `GET`, `DELETE`, `POST /file` and `GET /file` on `/api/vehicle-documents/{token}` MUST be allowed to the driver who owns the vehicle and to callers with the matching `*_vehicle_document(s)` permission. Restore MUST require `restore_vehicle_document`. Anyone else MUST get HTTP 403, an unauthenticated caller HTTP 401, and an unknown or removed vehicle or document HTTP 404. A document of a removed vehicle MUST be unreachable until the vehicle is restored.

#### Scenario: Another driver is refused

- **WHEN** a driver calls `GET /api/vehicles/{token}/documents` for a van of another driver
- **THEN** the response is HTTP 403

#### Scenario: The van is removed

- **WHEN** the owner removes the van and then calls `GET /api/vehicles/{token}/documents`
- **THEN** the response is HTTP 404

### Requirement: The van keeps the history of its documents

A van MAY have several submissions of the same type over time (one inspection per semester), following the `document-review` rules: at most one approved submission in force and one newer submission, pending or rejected, per type. Listing MUST return the live submissions that are not superseded, newest first, filterable by type and by status. Removing a pending submission MUST be a soft delete, and restoring it MUST bring it back with its review.

#### Scenario: Renewing the inspection

- **WHEN** a van has an approved inspection valid until `2026-12-31` and the owner sends another one
- **THEN** listing the van's documents returns both, the newer first
- **AND** after the admin approves the newer one, listing returns only the newer one

### Requirement: The file is private and stored per document

The file MUST be a JPEG, PNG, WEBP or PDF within the configured document size, sent with the document and read with `GET /api/vehicle-documents/{token}/file`, only through the same authorization. `POST /api/vehicle-documents/{token}/file` MUST replace the file while the document is `PENDING`, and MUST be refused once it is reviewed (see `document-review`).

#### Scenario: The owner sends the CRLV as PDF

- **WHEN** the owner sends a `CRLV` with a PDF
- **THEN** the response is HTTP 201 and `GET /api/vehicle-documents/{token}/file` returns it to the owner with `Content-Type: application/pdf`

#### Scenario: The owner sent the wrong photo

- **WHEN** the owner replaces the file of a `PENDING` `CRLV`
- **THEN** the document keeps the new file and stays `PENDING`

### Requirement: The vehicle has no document photo of its own

The vehicle MUST NOT have a document photo besides its documents: `POST` and `GET /api/vehicles/{token}/photo-document` MUST no longer exist, and the vehicle response MUST NOT include `photoDocumentUrl`. When this change is applied, the vehicle document photo column MUST be dropped without converting the photo into a document: no app ever uploaded one.

#### Scenario: The old route is gone

- **WHEN** a client calls `POST /api/vehicles/{token}/photo-document`
- **THEN** the response is HTTP 404
