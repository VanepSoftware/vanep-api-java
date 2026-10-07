## ADDED Requirements

### Requirement: Consolidated document compliance
The system SHALL calculate and persist one document-compliance result for each driver from the active CNH and the mandatory driver documents. The result MUST distinguish pending review, rejection, expiration, and missing documents, and MUST expose document-derived blocking reasons separately from plan-derived blocking reasons.

#### Scenario: Replaced CNH suspends an approved driver
- **WHEN** an approved driver's CNH data or photo is replaced
- **THEN** the compliance result becomes `PENDING_REVIEW` and the driver is moved to `UNDER_REVIEW`

#### Scenario: Expired approved document remains irregular
- **WHEN** an approved active driver document has an expiration date before the current `America/Sao_Paulo` date
- **THEN** the compliance result marks the driver as document-irregular with an expiration reason

#### Scenario: Plan and document reasons remain distinguishable
- **WHEN** a driver has both a document blocking reason and a plan blocking reason
- **THEN** the consolidated response includes each reason in its respective category

### Requirement: Daily expiration notifications
The system SHALL run a daily expiration scan over `driver_cnh.valid_until` and `driver_document.expires_at`. For each active resource with a validity date, it MUST create and dispatch at most one notification for D-60, D-30, and D0, and record the notification timestamp in a unique alert event.

#### Scenario: Sixty-day alert is emitted once
- **WHEN** the daily scan runs for an approved active document whose validity date is 60 days away
- **THEN** it records and dispatches exactly one D-60 notification

#### Scenario: Repeated daily execution is idempotent
- **WHEN** the daily scan runs twice for the same document and milestone
- **THEN** only one notification marker and one notification dispatch are created

#### Scenario: Missed scan does not lose an alert
- **WHEN** a daily scan did not run on a due milestone date
- **AND** the following scan finds that milestone unrecorded
- **THEN** it dispatches the pending milestone once without duplicating any recorded milestone

### Requirement: Expiration blocks future opportunities only
The system SHALL exclude a document-irregular driver from location search, recommended-driver search, and public driver profile results. It SHALL reject creation of a new `client_driver` relationship and activation of a pending relationship for that driver. It MUST NOT cancel, modify, or hide an existing active relationship because of document irregularity.

#### Scenario: Expired driver is absent from search
- **WHEN** a driver has an active document that expired before today
- **THEN** driver search does not return that driver

#### Scenario: Existing active relationship is preserved
- **WHEN** a driver's document becomes expired while an existing `client_driver` relationship is `ACTIVE`
- **THEN** the relationship remains unchanged

#### Scenario: Approval after renewal restores eligibility
- **WHEN** a driver replaces an expired document and an Admin approves it
- **AND** no other document blocking reason remains
- **THEN** the driver becomes eligible for search and new proposals automatically

### Requirement: Directly linked document replacement requires review
The system SHALL set a driver document to `PENDING` whenever its content, file, or validity is replaced. Replacing CNH content, CNH photo, or a mandatory driver document SHALL set an approved driver to `UNDER_REVIEW` and keep the driver document-irregular until the Admin approval flow succeeds. A rejected driver document MUST have a non-blank rejection reason.

#### Scenario: CNH update requires fresh approval
- **WHEN** an approved driver's CNH is updated
- **THEN** the driver status becomes `UNDER_REVIEW` and the driver is excluded from new opportunities until approval

#### Scenario: Rejection without reason is rejected
- **WHEN** an Admin submits `REJECTED` status without a rejection reason
- **THEN** the system rejects the request and preserves the existing review state

#### Scenario: Driver sees rejection reason
- **WHEN** a driver reads a rejected CNH or driver document they own
- **THEN** the response includes its rejection reason

### Requirement: Compliance endpoint
The system SHALL expose an authenticated endpoint for a driver to retrieve their persisted document-compliance result and reasons. An Admin MAY retrieve the same result for a selected driver through an explicitly authorized endpoint. Plan-derived reasons MUST be returned in a separate category when the plan module provides them.

#### Scenario: Driver reads own compliance
- **WHEN** an authenticated driver requests their compliance state
- **THEN** the endpoint returns the same status and blocking reasons used by search and proposal eligibility

#### Scenario: Unauthorized user cannot read another driver's compliance
- **WHEN** a non-Admin caller requests the compliance state for another driver
- **THEN** the system denies the request
