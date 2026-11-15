## Purpose

The admin finds who needs attention, sees one driver's whole situation on one page, and reviews each submission against the registration, without the API exposing more personal data than each screen needs.

## ADDED Requirements

### Requirement: Reading is for reviewers and approvers

`GET /api/drivers/compliance`, `GET /api/drivers/compliance/counts` and `GET /api/drivers/{token}/compliance` MUST require `review_documents` or `approve_driver`. Anyone else MUST get HTTP 403, and without authentication HTTP 401. Both permissions MUST be in the `ADMIN` bundle.

#### Scenario: A driver tries to read the queue

- **WHEN** a driver calls `GET /api/drivers/compliance`
- **THEN** the response is HTTP 403

### Requirement: The queue by view

`GET /api/drivers/compliance?view=&q=&page=&size=` MUST return a page of drivers, each evaluated as in `driver-compliance`:

| `view` | Drivers | Order |
|---|---|---|
| `AWAITING_DECISION` | registration `UNDER_REVIEW` | `READY_TO_APPROVE` and `ADMIN` first, `DRIVER` last; each group by submission time, oldest first |
| `PENDING_DOCUMENTS` | at least one pending submission, any registration status | oldest pending first |
| `EXPIRING` | an expired requirement or an expiry within 30 days of the service date | nearest expiry first |
| `ALL` (default) | every driver | newest driver first |

Each row MUST have:
- the driver's `token`, `name`, `email`, `city` (from the personal address) and `vehicleCount`;
- the registration: `approvalStatus`, `submittedAt` and the driver's `createdAt`;
- the situation: `nextAction`, `canApprove`, `pendingReviewCount`, `oldestPendingAt`, `rejectedCount`, `missingCount`, `expiredCount` and `nextExpiryOn`.

Ties MUST be broken by the driver, so the same request returns the same page. A row MUST NOT include the CPF or the phone. `q` MUST match the name, the e-mail or an active van's plate, ignoring case, and a plate with or without hyphen. The page MUST be read in a constant number of queries, whatever the number of drivers.

#### Scenario: Waiting for the driver goes last

- **WHEN** two drivers are under review, one with a pending document and one with a rejected document and nothing pending
- **THEN** in `AWAITING_DECISION` the one with the pending document comes first

#### Scenario: Searching by plate

- **WHEN** the admin searches `abc-1d23`
- **THEN** the driver whose van is `ABC1D23` is listed

### Requirement: The queue is current at every read

The views, the counts and the badge MUST reflect the stored submissions and the service date at the moment of the request, with no write or scheduled run needed in between.

#### Scenario: A document expires overnight

- **WHEN** a van's only inspection is valid until yesterday and nobody touched the driver since
- **THEN** today the driver is listed in `EXPIRING` with one expired requirement

### Requirement: The counts of the views

`GET /api/drivers/compliance/counts` MUST return:
- `awaitingDecision`, `pendingDocuments`, `expiring` and `all`, each the total of the view with the same filter;
- `awaitingDriver`, the drivers of `AWAITING_DECISION` whose `nextAction` is `DRIVER`;
- `badge`, the drivers whose `nextAction` is `READY_TO_APPROVE` or `ADMIN`;
- `asOf`, the service date.

#### Scenario: The badge

- **WHEN** one driver is ready to approve, one has a pending renewal and one waits for the driver
- **THEN** `badge` is 2

#### Scenario: Waiting for the driver is counted apart

- **WHEN** three drivers are under review, two waiting for the admin and one waiting for the driver
- **THEN** `awaitingDecision` is 3 and `awaitingDriver` is 1

### Requirement: The driver's page

`GET /api/drivers/{token}/compliance` MUST return:
- `asOf`, the service date;
- the driver: identity, `document` (CPF), `birthDate`, `phone`, city, `experienceYears`, `createdAt`, registration status with the reason code, message, reviewer and dates;
- the decision (`canApprove`, `nextAction`, `pendingReviewCount`, `blockers`);
- the driver's requirements, and each active van (`token`, `plate`, `brand`, `model`, `year`, `capacity`, `qualified`) with its requirements;
- the review queue of pending submissions in checklist order: the driver's requirements, then each van in the order of the vans;
- the history.

Each requirement MUST carry its state, the submission that counts, the pending renewal and the last decision. The last decision is the newest decision of the requirement that is neither the submission that counts nor the pending one, without a file: a superseded submission's, or a rejection not yet superseded next to the approval in force. Inactive submissions MUST NOT be on the page.

A submission MUST carry:
- `kind`, `token`, `documentNumber`, `expiresAt`, both `null` while it is `PENDING`: the reviewer reads them from the file and sends them with the approval (see `document-review`);
- the review: status, method, reason code and message, reviewer name, time, and the last data correction (`dataCorrectedAt` and who corrected);
- `createdAt`, `version`;
- the file: `fileUrl`, `mimeType`, `sizeBytes` and `originalName`.

The CNH MUST also carry `category`, `issueDate`, the whole `registrationNumber`, `firstLicenseDate`, `securityNumber` and `issuingState`, all `null` while it is `PENDING`, so a correction starts from the data in force. An unknown or removed driver MUST get HTTP 404. The page MUST be read in a constant number of queries, whatever the number of vans and documents.

#### Scenario: Reviewing one pending of three

- **WHEN** a driver has a pending CNH and two pending van documents
- **THEN** the review queue lists the three in checklist order, the CNH first

#### Scenario: A rejection next to the approval in force

- **WHEN** the criminal record in force is approved and the driver's newer one was rejected as `ILLEGIBLE`
- **THEN** `CRIMINAL_RECORD` shows the approved one as the submission that counts and a last decision `REJECTED` with `ILLEGIBLE`

#### Scenario: The last decision after a resend

- **WHEN** the criminal record was rejected as `ILLEGIBLE` and the driver sent a new one
- **THEN** `CRIMINAL_RECORD` shows the new submission `PENDING` and a last decision `REJECTED` with `ILLEGIBLE`, without file

### Requirement: The history from existing dates

The page's `history` MUST list, newest first and at most 50, the submissions sent, approved, rejected and with corrected data (superseded ones included) and the driver's onboarding submission and decision, built from the stored dates, with the requirement, the van's plate when there is one, the actor and the reason code. Events at the same instant MUST list the decision before the sending. The actor MUST be the reviewer (`token`, `name`) on decisions, the one who corrected on data corrections, and null on sendings, which belong to the driver.

#### Scenario: A resend in the history

- **WHEN** a document was rejected and sent again
- **THEN** the history has the first sending, the rejection with its code and the second sending

### Requirement: File URLs are cacheable

A file route MUST answer `Cache-Control: private, max-age=86400, immutable` when the request's `v` is the token of the current file, and `private, no-cache` otherwise.

#### Scenario: Reopening the same document

- **WHEN** the admin opens a submission's `fileUrl` twice
- **THEN** the second response may come from the browser cache
