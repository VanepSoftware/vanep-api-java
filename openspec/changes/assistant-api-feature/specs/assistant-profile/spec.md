## ADDED Requirements

### Requirement: Assistant can read and update own profile
The system SHALL expose `GET /api/assistants/me` and `PUT /api/assistants/me` for the authenticated ASSISTANT, returning and updating profile fields such as `photo` via request/response DTOs (never raw JPA models). Public identifier in responses MUST be `token`.

#### Scenario: Get own profile
- **WHEN** an authenticated ASSISTANT calls `GET /api/assistants/me`
- **THEN** the system returns `200 OK` with the assistant profile DTO including `token` and `status`

#### Scenario: Update photo
- **WHEN** an authenticated ASSISTANT calls `PUT /api/assistants/me` with a valid body updating `photo`
- **THEN** the system returns `200 OK` with the updated profile DTO

#### Scenario: Non-ASSISTANT cannot use /me
- **WHEN** a DRIVER or CLIENT calls `GET /api/assistants/me`
- **THEN** the system returns `403 Forbidden`

#### Scenario: Unauthenticated access
- **WHEN** a request without Bearer token calls `/api/assistants/me`
- **THEN** the system returns `401 Unauthorized`

---

### Requirement: Driver can list linked assistants
The system SHALL expose `GET /api/assistants` for the authenticated DRIVER, listing assistants associated with that driver (including ACTIVE and INACTIVE as applicable), shaped as response DTOs with public `token` identifiers and all fields needed for the driver UI (no separate detail fetch required in this MVP). The system MUST NOT expose `GET /api/assistants/{token}` in this change.

#### Scenario: Driver lists own assistants
- **WHEN** a DRIVER with linked assistants calls `GET /api/assistants`
- **THEN** the system returns `200 OK` with only assistants tied to that driver's id, each item including at least `token`, `status`, and profile fields used by list actions

#### Scenario: Empty list
- **WHEN** a DRIVER with no assistants calls `GET /api/assistants`
- **THEN** the system returns `200 OK` with an empty collection

#### Scenario: No unit GET by token
- **WHEN** a DRIVER calls `GET /api/assistants/{token}`
- **THEN** the system does not provide a dedicated detail endpoint for that path in this MVP (e.g. 404 or no mapped handler)

#### Scenario: Assistant cannot list via driver endpoint
- **WHEN** an ASSISTANT calls `GET /api/assistants`
- **THEN** the system returns `403 Forbidden`

---

### Requirement: Ownership enforced via AssistantSecurityService
The system SHALL resolve the caller from JWT (`uid` / user id) and enforce ownership so a driver only acts on assistants linked to their driver row, and an assistant only acts on their own profile. Unauthorized cross-access MUST return `403`; missing tokens MUST return `404`.

#### Scenario: Driver acts on foreign assistant token
- **WHEN** a DRIVER calls pause/revoke on an assistant `{token}` belonging to another driver
- **THEN** the system returns `403 Forbidden`

#### Scenario: Unknown assistant token
- **WHEN** a DRIVER calls an action on a non-existent assistant `{token}`
- **THEN** the system returns `404 Not Found`

#### Scenario: AssistantSecurityService used by controllers
- **WHEN** assistant profile or link endpoints authorize a request
- **THEN** authorization decisions go through `AssistantSecurityService` (or equivalent ownership helper), not ad-hoc checks only in the controller
