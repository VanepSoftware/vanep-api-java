## Purpose

An approved document means an admin saw that exact file with that number and expiry. The same review rules apply to the driver's and the van's documents, so no edit or file swap keeps an approval that no longer matches what was reviewed.

## ADDED Requirements

### Requirement: Only an admin reviews

Changing the status of a driver document (`PUT /api/driver-documents/{token}/status`) or of a vehicle document (`PUT /api/vehicle-documents/{token}/status`) MUST require the matching `update_*_document` permission. The owner MUST get HTTP 403. The review MUST record the reviewer, the time, the method and, for `REJECTED`, a non-blank reason (HTTP 400 `document.review.reason_required` otherwise).

#### Scenario: The owner tries to approve their own document

- **WHEN** the owner of a vehicle document calls `PUT /api/vehicle-documents/{token}/status` with `APPROVED`
- **THEN** the response is HTTP 403 and the status is unchanged

#### Scenario: Rejecting without reason

- **WHEN** an admin rejects a document with a blank `rejectionReason`
- **THEN** the response is HTTP 400 with the message resolved from `document.review.reason_required`

### Requirement: Approving requires a file

Approving a document without a file MUST be rejected with HTTP 422 (`document.review.file_required`) and nothing changed.

#### Scenario: Approving a document with no upload

- **WHEN** an admin approves a vehicle document that has no file
- **THEN** the response is HTTP 422 with the message resolved from `document.review.file_required`

### Requirement: Any change by the owner returns the document to pending

Editing a document (`PUT`) or uploading a new file MUST set its status to `PENDING` and clear the reviewer, the review time, the method and the rejection reason, for both driver and vehicle documents.

#### Scenario: Swapping the file of an approved document

- **WHEN** the owner uploads a new file to an `APPROVED` document
- **THEN** the document is `PENDING` with no reviewer

#### Scenario: Editing the expiry of an approved document

- **WHEN** the owner changes the `expiresAt` of an `APPROVED` document
- **THEN** the document is `PENDING`
