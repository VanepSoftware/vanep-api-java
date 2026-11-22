## Purpose

An approved submission means an admin saw that exact file and read its number and expiry from it. The driver only sends the file; the data printed on the document is filled once, by the reviewer, on approval. The same review rules apply to the driver's documents, the van's documents and the driver's CNH: a reviewed submission never changes, a renewal is a new submission that lives next to the approval still in force, and no decision is recorded over something the admin did not see.

## ADDED Requirements

### Requirement: Only a reviewer decides

Changing the status of a driver document (`PUT /api/driver-documents/{token}/status`), a vehicle document (`PUT /api/vehicle-documents/{token}/status`) or a CNH (`PUT /api/driver-cnhs/{token}/status`) MUST require the `review_documents` permission. The owner, and a caller that can only edit documents, MUST get HTTP 403. The review MUST record the reviewer, the time and the method. The status MUST be `APPROVED` or `REJECTED`: any other value MUST be rejected with HTTP 400.

#### Scenario: The owner tries to approve their own document

- **WHEN** the owner of a vehicle document calls `PUT /api/vehicle-documents/{token}/status` with `APPROVED`
- **THEN** the response is HTTP 403 and the status is unchanged

#### Scenario: An admin that can edit but not review

- **WHEN** a caller with `update_driver_document` and without `review_documents` approves a driver document
- **THEN** the response is HTTP 403

### Requirement: A rejection has a structured reason

Rejecting MUST require a `rejectionReasonCode` among `ILLEGIBLE`, `EXPIRED`, `DATA_MISMATCH`, `WRONG_TYPE`, `INCOMPLETE`, `NOT_ELIGIBLE` (a true document that does not allow school transport, such as a CNH without `D` or `E`) and `OTHER` (HTTP 400 `document.review.reason_code_required` otherwise). The `rejectionReason` message MUST be optional, at most 255 characters, and required only with `OTHER` (HTTP 400 `document.review.reason_required`). Approving MUST clear both.

#### Scenario: Rejecting an unreadable file

- **WHEN** an admin rejects a document with `rejectionReasonCode = ILLEGIBLE` and no message
- **THEN** the document is `REJECTED` with that code

#### Scenario: Rejecting for another reason without explaining

- **WHEN** an admin rejects with `rejectionReasonCode = OTHER` and a blank `rejectionReason`
- **THEN** the response is HTTP 400 with the message resolved from `document.review.reason_required`

### Requirement: Approving requires a file

Approving a submission without a file MUST be rejected with HTTP 422 (`document.review.file_required`) and nothing changed. A submission sent by the owner always has a file; the rule protects a submission created before this change and a file removed by a failure.

#### Scenario: Approving a document with no file

- **WHEN** an admin approves a vehicle document that has no file
- **THEN** the response is HTTP 422 with the message resolved from `document.review.file_required`

### Requirement: Approving fills the document's data

The status update with `APPROVED` MUST carry the data read from the file, and the approval MUST store it with the decision, in the same transaction:

| Type | `documentNumber` | `expiresAt` |
|---|---|---|
| `CRLV` | optional | required |
| `VEHICLE_INSPECTION` | optional | required |
| `SCHOOL_VEHICLE_AUTHORIZATION` | required | required |
| `MUNICIPAL_AUTHORIZATION` | required | optional |
| `SCHOOL_DRIVER_COURSE` | optional | optional |
| `CRIMINAL_RECORD` | optional | optional |

A required field that is missing MUST be rejected with HTTP 400, `code` `document.review.field_required` and the `field` property, and nothing changed. A rejection MUST NOT store the data of its body. A document number MUST have at most 64 characters and MUST be stored trimmed. The data of a `PENDING` submission MUST be `null`.

#### Scenario: Approving the inspection with its expiry

- **WHEN** an admin approves a `PENDING` `VEHICLE_INSPECTION` with `expiresAt` in five months and the version they saw
- **THEN** the inspection is `APPROVED` with that expiry and the reviewer is the admin

#### Scenario: Approving an authorization without number

- **WHEN** an admin approves a `SCHOOL_VEHICLE_AUTHORIZATION` with `expiresAt` and without `documentNumber`
- **THEN** the response is HTTP 400 with `code` `document.review.field_required` and `field` `documentNumber`
- **AND** the authorization is still `PENDING`

#### Scenario: Rejecting ignores the data

- **WHEN** an admin rejects a `CRLV` as `ILLEGIBLE` with an `expiresAt` in the body
- **THEN** the `CRLV` is `REJECTED` and its `expiresAt` is `null`

### Requirement: The reviewer corrects the data of an approved submission

`PUT /api/driver-documents/{token}/data`, `PUT /api/vehicle-documents/{token}/data` and `PUT /api/driver-cnhs/{token}/data` MUST require `review_documents` and MUST replace the data of an `APPROVED`, not superseded submission with the data of the body, under the same rules as the approval, except that the expiry MAY be before today. The body MUST carry the `version` the reviewer saw (HTTP 409 `document.review.stale` when it differs). A submission that is not approved, or is superseded, MUST be rejected with HTTP 409 (`document.data.not_approved`). The correction MUST record who corrected and when, MUST keep the decision, its reviewer and the file, and MUST NOT notify the driver.

#### Scenario: Correcting a mistyped expiry

- **WHEN** an approved `VEHICLE_INSPECTION` has `expiresAt` a year off and the admin corrects it with the version they saw
- **THEN** the inspection keeps `APPROVED`, its file and its reviewer, has the new `expiresAt`, and records the admin as the one who corrected it

#### Scenario: Correcting a pending submission

- **WHEN** the admin corrects the data of a `PENDING` document
- **THEN** the response is HTTP 409 with `code` `document.data.not_approved`

#### Scenario: The owner tries to correct

- **WHEN** the owner calls `PUT /api/vehicle-documents/{token}/data`
- **THEN** the response is HTTP 403

### Requirement: The decision names the version it saw

Every submission MUST expose a `version` that changes on every write. The status update MUST carry the `version` the reviewer saw and MUST be rejected with HTTP 409 (`document.review.stale`) when it differs from the stored one, including when two reviews race to commit. A lost race on any other record MUST be HTTP 409 (`request.conflict`).

#### Scenario: The owner swaps the file while the admin looks at it

- **WHEN** the admin opens a `PENDING` document at version 3, the owner uploads another file, and the admin approves with version 3
- **THEN** the response is HTTP 409 with the message resolved from `document.review.stale`
- **AND** the document is still `PENDING`

#### Scenario: Two admins on the same document

- **WHEN** two admins decide the same submission with the same version
- **THEN** one decision is recorded and the other gets HTTP 409

### Requirement: A reviewed submission is final

A `PENDING` submission MAY have its file replaced or be removed by its owner, and it stays `PENDING` with no reviewer. An `APPROVED` or `REJECTED` submission MUST NOT change: uploading a file to it, deciding it again, or removing it as its owner MUST be rejected with HTTP 409 (`document.submission.reviewed`). A superseded submission MUST NOT change either.

#### Scenario: Replacing the file of an approved document

- **WHEN** the owner uploads another file to an `APPROVED` document
- **THEN** the response is HTTP 409 and the document is unchanged

#### Scenario: Fixing a pending document

- **WHEN** the owner uploads another file to a `PENDING` document
- **THEN** the document keeps the new file and stays `PENDING`

### Requirement: Renewing is a new submission

Renewing or resending a document MUST be a new submission of the same type for the same owner. An owner MUST have at most one `PENDING` submission per type: a second one MUST be rejected with HTTP 409 (`document.submission.pending_exists`), enforced by the database as well. An approved submission MUST keep counting until a newer one is approved.

#### Scenario: Renewing the inspection before it expires

- **WHEN** a van has an approved inspection valid until next week and the owner sends a new one
- **THEN** the new inspection is `PENDING` and the van's `VEHICLE_INSPECTION` credential is still satisfied by the approved one

#### Scenario: Two pending submissions of the same type

- **WHEN** the owner sends a second `CRIMINAL_RECORD` while the first one is `PENDING`
- **THEN** the response is HTTP 409 with the message resolved from `document.submission.pending_exists`

### Requirement: A decided renewal replaces the previous submission

When a submission is approved, every other live submission of the same owner and type MUST be marked superseded. When a new submission is created, the live `REJECTED` submission of the same owner and type MUST be marked superseded. A superseded submission MUST lose its file, MUST keep its decision (status, reason code, message, reviewer and dates), MUST NOT be listed, MUST NOT count for any credential and MUST NOT change again. The file MUST be deleted from storage only after the transaction commits.

#### Scenario: Approving the renewal

- **WHEN** the admin approves the new inspection of a van that has an older approved one
- **THEN** the older inspection is superseded, has no file, and the van's listing shows only the new one

#### Scenario: Resending after a rejection

- **WHEN** the owner's `CRIMINAL_RECORD` was rejected as `ILLEGIBLE` and the owner sends a new one
- **THEN** the rejected one is superseded and keeps its decision, and the new one is `PENDING`

### Requirement: Restoring follows the same rules

Restoring a removed submission that is not superseded MUST leave the same state a new submission or a decision would. A restored `PENDING` MUST act as a new submission: another `PENDING` of the same owner and type MUST give HTTP 409 (`document.submission.pending_exists`), and the live `REJECTED` one MUST be superseded. A restored `APPROVED` MUST be rejected with HTTP 409 (`document.submission.restore_conflict`) when another live `APPROVED` exists, and a restored `REJECTED` when a live `PENDING` or `REJECTED` exists. A restore that is rejected MUST leave the submission removed.

#### Scenario: An approval removed by mistake

- **WHEN** an admin removes an approved CRLV, the owner sends a renewal, and the admin restores the approved one
- **THEN** the approved CRLV is back and the renewal is still `PENDING`

#### Scenario: Two approvals

- **WHEN** an admin restores an approved CRLV while another approved CRLV of the same van is live
- **THEN** the response is HTTP 409 with `code` `document.submission.restore_conflict` and the restored one stays removed

#### Scenario: A failed transaction keeps the file

- **WHEN** approving a submission fails after the previous one was marked superseded
- **THEN** nothing is superseded and the previous file still exists in storage

### Requirement: Decision errors carry a stable code

Every error of a decision or of a submission rule MUST carry a `code` property in the problem response, equal to its message key, that does not change with the language: `document.review.reason_code_required`, `document.review.reason_required`, `document.review.file_required`, `document.review.field_required`, `document.data.not_approved`, `document.review.stale`, `document.submission.reviewed`, `document.submission.pending_exists` and `document.submission.restore_conflict`. The `detail` MUST be the translated text.

#### Scenario: Another admin decided first

- **WHEN** two admins decide the same submission with the same version
- **THEN** the second gets HTTP 409 with `code` `document.review.stale`

### Requirement: Expiry dates stay within the type's limits

Approving a driver or vehicle document MUST reject an `expiresAt` before today with HTTP 400 and `code` `document.expires_at.past`, and an `expiresAt` after the type's limit with HTTP 400 and `code` `document.expires_at.too_far`, both with `field` `expiresAt` and nothing changed. An expired document is rejected with `EXPIRED`, not approved. Today MUST be the current date in `America/Sao_Paulo`. The limits MUST be: `CRLV` until December 31 of next year; `VEHICLE_INSPECTION` until today plus 7 months; `SCHOOL_VEHICLE_AUTHORIZATION` until today plus 2 years; `SCHOOL_DRIVER_COURSE` and `MUNICIPAL_AUTHORIZATION` until today plus 5 years; `CRIMINAL_RECORD` without upper limit. An approval without `expiresAt`, where the type allows it, MUST NOT be checked.

#### Scenario: An inspection that expired yesterday

- **WHEN** an admin approves a `VEHICLE_INSPECTION` with `expiresAt` yesterday
- **THEN** the response is HTTP 400 with `code` `document.expires_at.past` and `field` `expiresAt`, and the inspection is still `PENDING`

#### Scenario: The last day of the limit

- **WHEN** an admin approves a `CRLV` with `expiresAt` on December 31 of next year
- **THEN** the response is HTTP 200
- **WHEN** an admin approves another `CRLV` with `expiresAt` on January 1 of the year after
- **THEN** the response is HTTP 400 with `code` `document.expires_at.too_far`

#### Scenario: A criminal record far ahead

- **WHEN** an admin approves a `CRIMINAL_RECORD` with `expiresAt` ten years ahead
- **THEN** the response is HTTP 200

### Requirement: A file within the limit always reaches the size rule

The upload limit of the HTTP layer (`MEDIA_MAX_UPLOAD_SIZE_MB`) MUST be larger than every media limit, so a file up to the document limit (`MEDIA_MAX_DOCUMENT_SIZE_MB`, 10 MB of 1024 × 1024 bytes) is never refused before the size rule. The application MUST NOT start when it is not. A file above its type's limit, and a request above the upload limit, MUST be rejected with HTTP 413 and `code` `media.file.too_large`.

#### Scenario: A document file just under the limit

- **WHEN** the owner sends a document with a 9.9 MB PDF
- **THEN** the response is HTTP 201

#### Scenario: A document file just over the limit

- **WHEN** the owner sends a document with a 10.1 MB PDF
- **THEN** the response is HTTP 413 with `code` `media.file.too_large`

#### Scenario: A misconfigured upload limit

- **WHEN** the upload limit is not larger than the document limit
- **THEN** the application does not start

### Requirement: Responses describe the file

The responses of driver and vehicle documents MUST carry `file` with `mimeType`, `sizeBytes` and `originalName` of the current file, or `null` when the document has no file (a superseded one, or one created before this change).

#### Scenario: A document with its PDF

- **WHEN** the owner sends a `CRLV` with `crlv.pdf` and reads the document
- **THEN** `file.mimeType` is `application/pdf`, `file.originalName` is `crlv.pdf` and `file.sizeBytes` is the size sent
