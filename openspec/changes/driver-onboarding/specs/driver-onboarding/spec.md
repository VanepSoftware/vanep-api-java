## ADDED Requirements

### Requirement: Document Types Expansion for School Transport

The system SHALL expand `DocumentTypeEnum` to include `VEHICLE_INSPECTION` (vistoria veicular) and `MUNICIPAL_AUTHORIZATION` (autorização municipal de transporte escolar) in addition to existing types. The system MUST accept documents of these types through the existing `POST /api/driver-documents` endpoint.

#### Scenario: Registering a vehicle inspection document

- **WHEN** an authenticated driver sends `POST /api/driver-documents` with `documentType` equal to `VEHICLE_INSPECTION`
- **THEN** the system returns `201 Created` with the registered document response containing `documentType: "VEHICLE_INSPECTION"` and `status: "PENDING"`

#### Scenario: Registering a municipal authorization document

- **WHEN** an authenticated driver sends `POST /api/driver-documents` with `documentType` equal to `MUNICIPAL_AUTHORIZATION`
- **THEN** the system returns `201 Created` with the registered document response containing `documentType: "MUNICIPAL_AUTHORIZATION"` and `status: "PENDING"`

---

### Requirement: Driver Approval Status Lifecycle

The system SHALL support the status `UNDER_REVIEW` in `DriverApprovalStatus` alongside `PENDING`, `APPROVED`, and `REJECTED`. The system MUST persist the submission and review timestamps (`submitted_at`, `reviewed_at`), the reviewer user reference (`reviewed_by`), and any rejection justification (`rejection_reason`) on the `driver` table.

#### Scenario: Initial driver status is PENDING

- **WHEN** a driver account is created via registration or OAuth
- **THEN** the associated driver model has `approvalStatus` set to `PENDING`
- **AND** `submittedAt`, `rejectionReason`, `reviewedAt`, and `reviewedBy` are null

---

### Requirement: Automatic Driver Model Initialization in OAuth Flow

The system SHALL automatically create an initial `DriverModel` associated with the created `UserModel` when a user completes registration via OAuth with `UserType.DRIVER`. The initial model MUST have `approvalStatus` set to `PENDING` and `basePrice` set to `0.00`.

#### Scenario: OAuth registration as driver initializes driver profile

- **WHEN** an OAuth user completes signup with `form.type` equal to `DRIVER`
- **THEN** the system persists a new `UserModel` with `UserType.DRIVER`
- **AND** persists a new `DriverModel` linked to the user with `approvalStatus = PENDING` and `basePrice = 0.00`
- **AND** subsequent calls to `GET /api/drivers/me` succeed without `404 Not Found`

---

### Requirement: Driver Onboarding Status Tracking

The system SHALL expose `GET /api/drivers/me/onboarding` for authenticated drivers. The endpoint MUST return a comprehensive progress summary containing the overall `approvalStatus`, `canSubmit` boolean, `submittedAt`, `rejectionReason`, and the status of each mandatory onboarding step: `profileStep`, `vehicleStep`, `cnhStep`, and `documentsStep`.

#### Scenario: Checking status of an incomplete onboarding

- **WHEN** an authenticated driver who has not registered any vehicle or documents calls `GET /api/drivers/me/onboarding`
- **THEN** the system returns `200 OK`
- **AND** `canSubmit` is `false`
- **AND** `approvalStatus` is `PENDING`
- **AND** `vehicleStep.completed` is `false`
- **AND** `documentsStep.completed` is `false` with `missingTypes` containing `["CRLV", "VEHICLE_INSPECTION", "MUNICIPAL_AUTHORIZATION"]`

#### Scenario: Checking status when all steps are completed

- **WHEN** an authenticated driver has completed profile with city and basePrice > 0, has an active vehicle, has a valid non-expired CNH with photo uploaded, and has uploaded files for all 3 mandatory document types
- **THEN** the system returns `200 OK`
- **AND** `canSubmit` is `true`
- **AND** `profileStep.completed` is `true`
- **AND** `vehicleStep.completed` is `true`
- **AND** `cnhStep.completed` is `true`
- **AND** `documentsStep.completed` is `true` with empty `missingTypes`

#### Scenario: Checking status when documents exist but files have not been uploaded

- **WHEN** an authenticated driver created document records for all 3 mandatory types, but has not uploaded the file for `CRLV` via `POST /api/driver-documents/{token}/file`
- **THEN** the system returns `200 OK`
- **AND** `documentsStep.completed` is `false`
- **AND** `documentsStep.missingTypes` contains `["CRLV"]`
- **AND** `canSubmit` is `false`

#### Scenario: Checking status while under review (Screen S06)

- **WHEN** an authenticated driver has already submitted their onboarding
- **THEN** the system returns `200 OK`
- **AND** `approvalStatus` is `UNDER_REVIEW`
- **AND** `submittedAt` contains the submission timestamp
- **AND** `canSubmit` is `false`

#### Scenario: Checking status after rejection

- **WHEN** an authenticated driver had their onboarding rejected by an administrator
- **THEN** the system returns `200 OK`
- **AND** `approvalStatus` is `REJECTED`
- **AND** `rejectionReason` contains the administrator's feedback message
- **AND** `canSubmit` is recalculated based on whether the driver has updated the missing/corrected items

---

### Requirement: Submitting Driver Onboarding for Review

The system SHALL expose `POST /api/drivers/me/submit-onboarding` for authenticated drivers. The endpoint MUST validate all 4 prerequisite steps before transitioning the driver to `UNDER_REVIEW`. If any requirement is missing or invalid, the system MUST return `422 Unprocessable Entity` with a detailed error payload. If all requirements are met, the system MUST set `approvalStatus` to `UNDER_REVIEW`, update `submitted_at` to the current timestamp, clear `rejection_reason`, and return the updated onboarding status.

#### Scenario: Successfully submitting completed onboarding

- **WHEN** an authenticated driver with all 4 steps completed sends `POST /api/drivers/me/submit-onboarding`
- **THEN** the system returns `200 OK`
- **AND** the driver's `approvalStatus` becomes `UNDER_REVIEW`
- **AND** `submittedAt` is recorded
- **AND** `rejectionReason` is null

#### Scenario: Submitting with missing vehicle

- **WHEN** an authenticated driver without any active vehicle sends `POST /api/drivers/me/submit-onboarding`
- **THEN** the system returns `422 Unprocessable Entity`
- **AND** the error details indicate that an active vehicle is required
- **AND** the driver remains in `PENDING` status

#### Scenario: Submitting with expired CNH

- **WHEN** an authenticated driver whose registered CNH has `validUntil` before the current date sends `POST /api/drivers/me/submit-onboarding`
- **THEN** the system returns `422 Unprocessable Entity`
- **AND** the error details indicate that a valid non-expired CNH is required

#### Scenario: Submitting with missing mandatory documents

- **WHEN** an authenticated driver who has not created the `MUNICIPAL_AUTHORIZATION` sends `POST /api/drivers/me/submit-onboarding`
- **THEN** the system returns `422 Unprocessable Entity`
- **AND** the error details list `MUNICIPAL_AUTHORIZATION` as a missing mandatory document

#### Scenario: Submitting when mandatory document has no file uploaded

- **WHEN** an authenticated driver has registered records for all mandatory types, but the `CRLV` document has no associated file (`file_media_id` is null)
- **THEN** the system returns `422 Unprocessable Entity`
- **AND** the error details indicate that `CRLV` is missing its uploaded file

#### Scenario: Submitting with CNH missing photo

- **WHEN** an authenticated driver whose registered CNH does not have a photo uploaded (`photo_media_id` is null) sends `POST /api/drivers/me/submit-onboarding`
- **THEN** the system returns `422 Unprocessable Entity`
- **AND** the error details indicate that the CNH photo is required

#### Scenario: Cannot submit when already under review or approved

- **WHEN** a driver with `approvalStatus` equal to `UNDER_REVIEW` or `APPROVED` calls `POST /api/drivers/me/submit-onboarding`
- **THEN** the system returns `400 Bad Request`

---

### Requirement: Administrative Onboarding Review (Approve / Reject)

The system SHALL expose `POST /api/drivers/{token}/approve` and `POST /api/drivers/{token}/reject` accessible only to callers with permission `approve_driver` or `ROLE_ADMIN`.

#### Scenario: Admin approves driver onboarding

- **WHEN** an administrator sends `POST /api/drivers/{token}/approve` for a driver with status `UNDER_REVIEW`
- **THEN** the system returns `200 OK`
- **AND** `approvalStatus` becomes `APPROVED`
- **AND** `active` is set to `true`
- **AND** `reviewedAt` and `reviewedBy` are recorded

#### Scenario: Admin rejects driver onboarding with reason

- **WHEN** an administrator sends `POST /api/drivers/{token}/reject` with body `{"reason": "Foto da CNH ilegível, favor reenviar"}` for a driver with status `UNDER_REVIEW`
- **THEN** the system returns `200 OK`
- **AND** `approvalStatus` becomes `REJECTED`
- **AND** `rejectionReason` is persisted with the provided string
- **AND** `reviewedAt` and `reviewedBy` are recorded

#### Scenario: Rejecting without reason fails validation

- **WHEN** an administrator sends `POST /api/drivers/{token}/reject` with an empty or blank `reason`
- **THEN** the system returns `400 Bad Request` with Bean Validation error message

#### Scenario: Non-admin caller is forbidden

- **WHEN** a regular client or driver calls `POST /api/drivers/{token}/approve` or `POST /api/drivers/{token}/reject`
- **THEN** the system returns `403 Forbidden`
