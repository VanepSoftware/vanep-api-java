## Purpose

The documents of a van belong to the van: each one names its vehicle, follows it, and keeps a history, so a driver with several vans can prove which van is registered, inspected and authorized for school transport.

## ADDED Requirements

### Requirement: Vehicle documents have their own types and a mandatory vehicle

A vehicle document MUST belong to exactly one vehicle and have one of the types `CRLV`, `VEHICLE_INSPECTION` or `SCHOOL_VEHICLE_AUTHORIZATION`. It MUST have an expiry date (enforced by the database), and a `SCHOOL_VEHICLE_AUTHORIZATION` MUST also have a document number. A document number MUST have at most 64 characters and MUST be stored trimmed. Missing or invalid fields MUST be rejected with HTTP 400 and nothing persisted.

#### Scenario: The driver registers the inspection of one van

- **WHEN** the owner of a van calls `POST /api/vehicles/{token}/documents` with `documentType = VEHICLE_INSPECTION` and `expiresAt = 2027-05-31`
- **THEN** the response is HTTP 201 with the document `PENDING`, its `vehicleToken` and no file

#### Scenario: An authorization without number is refused

- **WHEN** the owner creates a `SCHOOL_VEHICLE_AUTHORIZATION` without `documentNumber`
- **THEN** the response is HTTP 400 with the message resolved from `vehicle_document.number.required`

#### Scenario: A person's document type is refused

- **WHEN** the owner creates a vehicle document with `documentType = CRIMINAL_RECORD`
- **THEN** the response is HTTP 400

### Requirement: Only the van's owner and authorized admins manage its documents

`GET` and `POST /api/vehicles/{token}/documents` and `GET`, `PUT`, `DELETE`, `POST /file` and `GET /file` on `/api/vehicle-documents/{token}` MUST be allowed to the driver who owns the vehicle and to callers with the matching `*_vehicle_document(s)` permission. Restore MUST require `restore_vehicle_document`. Anyone else MUST get HTTP 403, an unauthenticated caller HTTP 401, and an unknown or removed vehicle or document HTTP 404. A document of a removed vehicle MUST be unreachable until the vehicle is restored.

#### Scenario: Another driver is refused

- **WHEN** a driver calls `GET /api/vehicles/{token}/documents` for a van of another driver
- **THEN** the response is HTTP 403

#### Scenario: The van is removed

- **WHEN** the owner removes the van and then calls `GET /api/vehicles/{token}/documents`
- **THEN** the response is HTTP 404

### Requirement: The van keeps the history of its documents

A van MAY have several documents of the same type (one inspection per semester). Listing MUST return them newest first, filterable by type and by status. Removing a document MUST be a soft delete, and restoring it MUST bring it back with its review.

#### Scenario: Two inspections

- **WHEN** a van has an approved inspection valid until `2026-12-31` and the owner adds another valid until `2027-06-30`
- **THEN** listing the van's documents returns both, the newer first

### Requirement: The file is private and stored per document

The file MUST be uploaded with `POST /api/vehicle-documents/{token}/file` (JPEG, PNG, WEBP or PDF, within the configured document size) and read with `GET /api/vehicle-documents/{token}/file`, only through the same authorization. Uploading MUST replace the previous file.

#### Scenario: The owner uploads the CRLV as PDF

- **WHEN** the owner uploads a PDF to a `CRLV` document
- **THEN** the response is HTTP 201 and `GET /api/vehicle-documents/{token}/file` returns it to the owner with `Content-Type: application/pdf`

### Requirement: The vehicle has no document photo of its own

The vehicle MUST NOT have a document photo besides its documents: `POST` and `GET /api/vehicles/{token}/photo-document` MUST no longer exist, and the vehicle response MUST NOT include `photoDocumentUrl`. When this change is applied, the vehicle document photo column MUST be dropped without converting the photo into a document: no app ever uploaded one.

#### Scenario: The old route is gone

- **WHEN** a client calls `POST /api/vehicles/{token}/photo-document`
- **THEN** the response is HTTP 404
