## ADDED Requirements

### Requirement: Driver generates and cancels open link codes
The system SHALL allow a driver to create a six-character alphanumeric open link code (excluding `0`, `O`, `1`, `I`) via `POST /api/driver-link-codes`, cancelling any previous `ACTIVE` code for that driver, with TTL **48 hours**, returning plaintext `code` once. Each code MUST be single-use. `DELETE /api/driver-link-codes/current` SHALL cancel the current `ACTIVE` code. Persistence and types for `driver_link_code` MUST live in the `driver` feature package (`br.com.vanep.driver`).

#### Scenario: Generate code invalidates previous ACTIVE
- **WHEN** driver generates a new link code while another is ACTIVE
- **THEN** the previous code status becomes `CANCELLED` and the new code is `ACTIVE` with `expires_at` approximately 48 hours ahead

#### Scenario: Cancel current code
- **WHEN** driver deletes `/api/driver-link-codes/current`
- **THEN** the ACTIVE code for that driver becomes `CANCELLED`

---

### Requirement: Assistant consumes driver link code atomically after login
The system SHALL allow an eligible authenticated ASSISTANT (`UNLINKED`) to consume a code via `POST /api/driver-link-codes/consume` with `{ "code" }`. The same atomic consume rules MUST apply as in password signup (`UPDATE` where status is ACTIVE and not expired). A successful consume MUST set assistant to `ACTIVE` for that driver and set `activated_at`. Zero updated rows MUST yield a generic localized error for invalid or expired code. Duplicate consume MUST fail with the same generic error. Strong rate limiting MUST apply to consume attempts.

#### Scenario: Successful consume when authenticated
- **WHEN** an UNLINKED ASSISTANT with a valid Bearer token posts a valid ACTIVE non-expired code
- **THEN** the code becomes `CONSUMED` and assistant becomes `ACTIVE` linked to that driver with `activated_at` set

#### Scenario: Consume requires authentication
- **WHEN** an unauthenticated client calls `POST /api/driver-link-codes/consume`
- **THEN** the system returns `401 Unauthorized`

#### Scenario: Invalid or expired code
- **WHEN** assistant posts an unknown, cancelled, consumed, or expired code
- **THEN** the system returns an error with a generic message (invalid or expired) without revealing which case

#### Scenario: Assistant not eligible
- **WHEN** an assistant already `ACTIVE` or `INACTIVE` attempts consume
- **THEN** the system returns `409 Conflict`

#### Scenario: Rate limited consume
- **WHEN** an assistant exceeds the configured consume rate limit
- **THEN** the system rejects further attempts until the window resets

#### Scenario: Same code cannot be used twice across contexts
- **WHEN** a code was already consumed during signup
- **THEN** a later authenticated `/consume` of that code fails with the generic invalid or expired error

---

### Requirement: Driver pauses and resumes ACTIVE assistant
The system SHALL allow only the linked driver to pause (`ACTIVE` → `INACTIVE`) and resume (`INACTIVE` → `ACTIVE`) via `POST /api/assistants/{token}/pause` and `/resume`.

#### Scenario: Driver pauses ACTIVE
- **WHEN** the owning driver calls pause on an `ACTIVE` assistant
- **THEN** status becomes `INACTIVE`

#### Scenario: Driver resumes INACTIVE
- **WHEN** the owning driver calls resume on an `INACTIVE` assistant
- **THEN** status becomes `ACTIVE`

#### Scenario: Assistant cannot pause
- **WHEN** the assistant calls a driver-only pause endpoint for their token
- **THEN** the system returns `403 Forbidden`

---

### Requirement: Either party can revoke ACTIVE link
The system SHALL allow the linked driver (`POST /api/assistants/{token}/revoke`) or the assistant (`POST /api/assistants/me/revoke`) to revoke an `ACTIVE` link, resulting in `UNLINKED` with `driver_id` cleared.

#### Scenario: Driver revokes
- **WHEN** the owning driver revokes an `ACTIVE` assistant
- **THEN** assistant becomes `UNLINKED` and is detached from the driver

#### Scenario: Assistant revokes
- **WHEN** an `ACTIVE` assistant calls `/api/assistants/me/revoke`
- **THEN** assistant becomes `UNLINKED` and is detached from the driver
