## Purpose

The CNH is reviewed like any other document, so the license the contract cites is one an admin saw and read: the driver sends only the file, the reviewer fills the license's data on approval, and a driver renewing it keeps driving with the current one until the renewal is approved.

## ADDED Requirements

### Requirement: The CNH is reviewed by an admin

A CNH MUST start `PENDING` and follow every rule of `document-review`: `PUT /api/driver-cnhs/{token}/status` with `review_documents`, a structured reason to reject, its file as the reviewed file, the version the reviewer saw, and a reviewed CNH that never changes. Only an `APPROVED` CNH MUST satisfy the `DRIVER_LICENSE` credential.

#### Scenario: Approving the CNH

- **WHEN** an admin approves a driver's CNH with its file, using the version they saw
- **THEN** the CNH is `APPROVED` and `DRIVER_LICENSE` is satisfied for that driver

#### Scenario: Approving a CNH without file

- **WHEN** an admin approves a CNH that has no file
- **THEN** the response is HTTP 422 with the message resolved from `document.review.file_required`

#### Scenario: Replacing the file of an approved CNH

- **WHEN** the owner uploads another file to an `APPROVED` CNH
- **THEN** the response is HTTP 409 with the message resolved from `document.submission.reviewed`

### Requirement: The driver sends only the file

The driver MUST send a CNH with `POST /api/driver-cnhs` as `multipart/form-data` with only `file`, and the CNH MUST be created `PENDING` with that file and with `registrationNumber`, `category`, `issueDate`, `validUntil`, `firstLicenseDate`, `securityNumber` and `issuingState` `null`. A request without file MUST be rejected with HTTP 400 and nothing persisted. There MUST be no `PUT /api/driver-cnhs/{token}`.

#### Scenario: Sending the CNH

- **WHEN** a driver without CNH sends a photo of their license
- **THEN** the response is HTTP 201 with the CNH `PENDING`, its `file`, and no number, category or dates

### Requirement: Approving fills the CNH's data

`PUT /api/driver-cnhs/{token}/status` with `APPROVED` MUST carry the data read from the file: `registrationNumber` (9 to 11 digits), `category` (`D`, `E`, `AD` or `AE`, stored trimmed and in upper case), `issueDate` and `validUntil`, all required, and `firstLicenseDate`, `securityNumber` and `issuingState` (two upper-case letters), optional. A missing required field MUST be rejected with HTTP 400, `code` `document.review.field_required` and the `field` property. A field in the wrong format MUST be rejected with HTTP 400 on that field; another CNH category, such as `B`, with `driver_cnh.category.invalid`. A CNH that is not category `D` or `E` MUST be rejected by the reviewer with `NOT_ELIGIBLE`. Nothing MUST change on a refused approval, and a rejection MUST NOT store the data of its body. An approved CNH MUST have the four required fields, enforced by the database.

#### Scenario: Approving the CNH with its data

- **WHEN** an admin approves a `PENDING` CNH with number, category `D`, issue date and expiry, and the version they saw
- **THEN** the CNH is `APPROVED` with that data and `DRIVER_LICENSE` is satisfied

#### Scenario: Approving without the category

- **WHEN** an admin approves a CNH without `category`
- **THEN** the response is HTTP 400 with `code` `document.review.field_required` and `field` `category`, and the CNH is still `PENDING`

#### Scenario: Approving a category B license

- **WHEN** an admin approves a CNH with `category` `B`
- **THEN** the response is HTTP 400 with `driver_cnh.category.invalid` on the field `category`

### Requirement: The CNH file is an image or a PDF

The file of a CNH MUST be sent with the CNH, replaced while it is `PENDING` with `POST /api/driver-cnhs/{token}/file`, and read with `GET /api/driver-cnhs/{token}/file`. It MUST accept the same types as a document file: JPEG, PNG, WebP or PDF. The CNH response MUST carry it as `fileUrl`.

#### Scenario: Sending the digital CNH

- **WHEN** the owner sends the CNH with a PDF
- **THEN** the response is HTTP 201, the CNH `fileUrl` points to `/api/driver-cnhs/{token}/file`, and reading it returns `application/pdf`

#### Scenario: A file that is not an image or a PDF

- **WHEN** the owner replaces the file of a `PENDING` CNH with a text file
- **THEN** the response is HTTP 400 and the CNH keeps its previous file

### Requirement: Renewing the CNH keeps the current one

The driver MUST be able to send a new CNH with `POST /api/driver-cnhs` while an approved one is in force, and the reviewer MUST be able to approve it with the same registration number. A second `PENDING` CNH MUST be rejected with HTTP 409 (`document.submission.pending_exists`). Approving the new CNH MUST supersede the previous one. The owner MUST be able to restore only a `PENDING` CNH: restoring a reviewed one MUST require `restore_driver_cnh` (HTTP 409 `document.submission.reviewed` otherwise), as removing it requires `delete_driver_cnh`.

#### Scenario: Renewal with the same number

- **WHEN** a driver with an approved CNH number `01234567890` sends a renewed CNH
- **THEN** the response is HTTP 201 with the new CNH `PENDING`, and `DRIVER_LICENSE` is still satisfied by the approved one
- **WHEN** the admin approves the renewal with the number `01234567890`
- **THEN** the renewal is `APPROVED` and the previous CNH is superseded

### Requirement: A registration number belongs to one driver

A CNH registration number MUST NOT belong to two drivers, enforced by the database. Approving or restoring a CNH with a number of another driver's live CNH MUST be rejected with HTTP 409 (`driver_cnh.registration.duplicate`), also when two CNHs of different drivers are approved with the same new number at the same time.

#### Scenario: Another driver's number

- **WHEN** an admin approves a CNH with a number that is on a live CNH of another driver
- **THEN** the response is HTTP 409 with `code` `driver_cnh.registration.duplicate` and the CNH is still `PENDING`

### Requirement: The CNH dates stay within limits

Approving a CNH MUST reject an `issueDate` after today with HTTP 400 and `code` `driver_cnh.issue_date.future` (`field` `issueDate`), a `validUntil` before today with HTTP 400 and `code` `document.expires_at.past`, and a `validUntil` more than 10 years after `issueDate` with HTTP 400 and `code` `document.expires_at.too_far`, both with `field` `validUntil`. Today MUST be the current date in `America/Sao_Paulo`, and nothing MUST change on a refused approval. An expired CNH is rejected with `EXPIRED`, not approved.

#### Scenario: A CNH valid for eleven years

- **WHEN** an admin approves a CNH with `issueDate` `2020-03-10` and `validUntil` `2031-03-11`
- **THEN** the response is HTTP 400 with `code` `document.expires_at.too_far` and `field` `validUntil`

#### Scenario: An expired CNH

- **WHEN** an admin approves a CNH with `validUntil` yesterday
- **THEN** the response is HTTP 400 with `code` `document.expires_at.past` and the CNH is still `PENDING`

### Requirement: The CNH response describes the file

The CNH response MUST carry `file` with `mimeType`, `sizeBytes` and `originalName` of its file, or `null` when the CNH has no file (a superseded one, or one created before this change).

#### Scenario: The CNH just sent

- **WHEN** the driver sends a CNH with `cnh.jpg` and reads it
- **THEN** `file.originalName` is `cnh.jpg` and `file.mimeType` is `image/jpeg`
