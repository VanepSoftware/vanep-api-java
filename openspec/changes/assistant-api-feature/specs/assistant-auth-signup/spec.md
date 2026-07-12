## ADDED Requirements

### Requirement: User type and role ASSISTANT exist
The system SHALL support `UserType.ASSISTANT` and `RoleName.ASSISTANT` with a seeded permission bundle for own profile and linking actions only (no checklist/chat/route permissions, no email-invite permissions).

#### Scenario: Seed creates ASSISTANT role
- **WHEN** the application starts with DataSeeder enabled
- **THEN** role `ASSISTANT` exists with profile permissions such as `show_assistant` and `update_assistant`

#### Scenario: DRIVER receives assistant-management permissions
- **WHEN** DataSeeder runs
- **THEN** role `DRIVER` includes permissions to list, pause, resume, revoke assistants and manage driver link codes (not email invite)

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

### Requirement: Thymeleaf signup creates ASSISTANT with optional link code
The system SHALL expose `GET` and `POST /signup/assistant` using `AssistantSignupForm` with optional visible `linkCode` (same `driver_link_code` artifact as the authenticated consume API — not an email invite). Without `linkCode`, the assistant MUST be created as `UNLINKED`. With a valid ACTIVE non-expired code, the system MUST consume it atomically and create the assistant as `ACTIVE` linked to that driver with `activated_at` set. The signup form MUST present `linkCode` as a visible optional field (not a hidden-only deep-link field in this MVP). Strong rate limiting MUST apply to signup attempts.

#### Scenario: Signup without link code
- **WHEN** a visitor submits valid `/signup/assistant` without `linkCode`
- **THEN** the system creates the user and an `assistant` with status `UNLINKED` and no `driver_id`

#### Scenario: Signup with valid link code
- **WHEN** a visitor submits valid `/signup/assistant` with a valid ACTIVE non-expired `linkCode`
- **THEN** the code becomes `CONSUMED`, the assistant is created `ACTIVE` with that `driver_id` and `activated_at` set

#### Scenario: Signup with invalid or expired link code
- **WHEN** a visitor submits `/signup/assistant` with an invalid, cancelled, consumed, or expired `linkCode`
- **THEN** the system rejects registration with a localized generic invalid/expired code error and does not create the user/assistant as a successful linked signup

#### Scenario: Signup rate limited
- **WHEN** a client exceeds the configured signup rate limit for `/signup/assistant`
- **THEN** the system rejects further attempts until the window resets

#### Scenario: Unauthenticated access to signup routes
- **WHEN** an unauthenticated client requests `/signup/assistant`
- **THEN** the system allows the request (public signup routes in SecurityConfig)

---

### Requirement: OAuth signup complete creates UNLINKED ASSISTANT
The system SHALL allow selecting `ASSISTANT` on OAuth `/signup/complete` and create the `assistant` profile as `UNLINKED` without accepting a link code in that flow. Linking after OAuth MUST use the authenticated `POST /api/driver-link-codes/consume` endpoint.

#### Scenario: OAuth complete as ASSISTANT
- **WHEN** an OAuth user completes signup choosing `ASSISTANT`
- **THEN** the system sets user type ASSISTANT, assigns role ASSISTANT, and creates an `assistant` row with status `UNLINKED`

#### Scenario: OAuth does not consume link code at complete
- **WHEN** an OAuth user completes signup as ASSISTANT
- **THEN** no `driver_link_code` is consumed during `/signup/complete`
