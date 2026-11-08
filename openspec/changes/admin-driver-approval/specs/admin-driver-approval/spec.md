## ADDED Requirements

### Requirement: Listing Pending Driver Registrations for Moderation

The system SHALL expose an endpoint `GET /api/drivers/pending` accessible exclusively to users with `ROLE_ADMIN` or the authority `approve_driver`. The endpoint MUST return a paginated list of drivers who have submitted their onboarding and are awaiting moderation (`approvalStatus = UNDER_REVIEW`).

#### Scenario: Admin successfully lists pending driver registrations

- **WHEN** an authenticated user with `ROLE_ADMIN` or authority `approve_driver` requests `GET /api/drivers/pending`
- **THEN** the system returns `200 OK`
- **AND** the response body contains a paginated list of `DriverResponseDTO` records whose `approvalStatus` is `UNDER_REVIEW`

#### Scenario: Non-admin caller is forbidden from listing pending drivers

- **WHEN** an authenticated caller with only `ROLE_DRIVER` or `ROLE_CLIENT` requests `GET /api/drivers/pending`
- **THEN** the system returns `403 Forbidden`

#### Scenario: Unauthenticated caller is rejected

- **WHEN** an unauthenticated request is made to `GET /api/drivers/pending`
- **THEN** the system returns `401 Unauthorized`

---

### Requirement: Driver Notification upon Approval or Rejection

The system SHALL automatically send an e-mail notification to the driver's registered e-mail address when their registration is approved or rejected by an administrator.

#### Scenario: Notification sent on driver approval

- **WHEN** an administrator sends `POST /api/drivers/{token}/approve` for a driver with status `UNDER_REVIEW`
- **THEN** the driver's `approvalStatus` becomes `APPROVED`
- **AND** the system triggers an approval email notification to the driver's email containing their name and confirmation that their account is active to receive proposals

#### Scenario: Notification sent on driver rejection with reason

- **WHEN** an administrator sends `POST /api/drivers/{token}/reject` with a non-blank reason for a driver with status `UNDER_REVIEW`
- **THEN** the driver's `approvalStatus` becomes `REJECTED`
- **AND** the system triggers a rejection email notification to the driver's email containing their name and the rejection justification

---

### Requirement: Proposals Block for Unapproved Drivers (RN02)

The system SHALL strictly enforce business rule RN02, ensuring that a driver can only receive and accept transport proposals when their status is `APPROVED`.

#### Scenario: Creating a client-driver link for an approved driver succeeds

- **WHEN** a proposal/link creation request `POST /api/client-drivers` is submitted for a driver whose `approvalStatus` is `APPROVED`
- **THEN** the system returns `201 Created` with the persisted link in `PENDING` status

#### Scenario: Creating a proposal for an unapproved driver is blocked

- **WHEN** a proposal/link creation request `POST /api/client-drivers` is submitted for a driver whose `approvalStatus` is `PENDING`, `UNDER_REVIEW`, or `REJECTED`
- **THEN** the system returns `422 Unprocessable Entity`
- **AND** the error message indicates that the driver is not approved to receive proposals

#### Scenario: Activating a link for an unapproved driver is blocked

- **WHEN** a patch request `PATCH /api/client-drivers/{token}` attempts to update `status` to `ACTIVE` for a link whose driver is not `APPROVED`
- **THEN** the system returns `422 Unprocessable Entity`
