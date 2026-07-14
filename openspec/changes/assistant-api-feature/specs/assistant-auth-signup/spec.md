## ADDED Requirements

### Requirement: User type and role ASSISTANT exist
The system SHALL support `UserType.ASSISTANT` and `RoleName.ASSISTANT` with a seeded permission bundle for own profile and revoke-own-link only (no checklist/chat/route permissions). The DRIVER role SHALL include permissions to list, pause, resume, revoke assistants and to create/cancel assistant invites (no driver-link-code permissions).

#### Scenario: Seed creates ASSISTANT role
- **WHEN** the application starts with DataSeeder enabled
- **THEN** role `ASSISTANT` exists with profile permissions such as `show_assistant` and `update_assistant`

#### Scenario: DRIVER receives assistant-management permissions
- **WHEN** DataSeeder runs
- **THEN** role `DRIVER` includes permissions to list, pause, resume, revoke assistants and manage email invites (`create`/`cancel`), and MUST NOT include driver-link-code permissions

---

### Requirement: JWT includes assistant_status for ASSISTANT users
The system SHALL add claim `assistant_status` to access tokens when the authenticated user type is `ASSISTANT`, mirroring `driver_status` for drivers.

#### Scenario: ASSISTANT token carries status
- **WHEN** an ASSISTANT user obtains an access token
- **THEN** the JWT contains `assistant_status` equal to the current `assistant.status` enum name

#### Scenario: Non-ASSISTANT token omits assistant_status
- **WHEN** a CLIENT or DRIVER user obtains an access token
- **THEN** the JWT does not include `assistant_status`

---

### Requirement: Thymeleaf signup creates UNLINKED ASSISTANT without invite fields
The system SHALL expose `GET` and `POST /signup/assistant` using `AssistantSignupForm` with the same fields pattern as client/driver signup. The form MUST NOT include `linkCode`, invite token, or any linking-related field. Successful signup MUST create the assistant as `UNLINKED` with `driver_id` null. Linking MUST only happen later via email invite after the account exists.

#### Scenario: Signup without any invite field
- **WHEN** a visitor submits valid `/signup/assistant`
- **THEN** the system creates the user and an `assistant` with status `UNLINKED` and no `driver_id`

#### Scenario: Signup form has no link or invite input
- **WHEN** the signup assistant template is rendered
- **THEN** there is no visible or hidden field for link code or invite

#### Scenario: Unauthenticated access to signup routes
- **WHEN** an unauthenticated client requests `/signup/assistant`
- **THEN** the system allows the request (public signup routes in SecurityConfig)

---

### Requirement: OAuth signup complete creates UNLINKED ASSISTANT
The system SHALL allow selecting `ASSISTANT` on OAuth `/signup/complete` and create the `assistant` profile as `UNLINKED` without accepting any invite or link code in that flow. Linking after OAuth MUST use the email invite flow initiated by a driver.

#### Scenario: OAuth complete as ASSISTANT
- **WHEN** an OAuth user completes signup choosing `ASSISTANT`
- **THEN** the system sets user type ASSISTANT, assigns role ASSISTANT, and creates an `assistant` row with status `UNLINKED`

#### Scenario: OAuth does not bind an invite at complete
- **WHEN** an OAuth user completes signup as ASSISTANT
- **THEN** no `assistant_invite` is created or accepted during `/signup/complete`
