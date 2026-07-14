## ADDED Requirements

### Requirement: Driver invites an existing UNLINKED assistant by email
The system SHALL allow an authenticated DRIVER to create an addressed invite via `POST /api/assistants/invites` with `{ "email" }`. The system MUST look up `user.email` (unique): only an existing `UserType.ASSISTANT` whose `assistant.status` is `UNLINKED` and who is outside the post-rejection cooldown MAY be invited. On success the system MUST create `assistant_invite` (`PENDING`, `expires_at` ≈ now+72h, `token_hash` of a one-time secret), set `assistant.status` to `PENDING` leaving `driver_id` null, and send a real email via MailService (Mailpit in MVP) containing a link `{baseUrl}/assistant-invite/{rawSecret}` plus driver name and expiry. Persistence and types for invites MUST live in `br.com.vanep.assistant`. The system MUST NOT provide `driver_link_code` or `/api/driver-link-codes/**`.

#### Scenario: Successful invite
- **WHEN** a DRIVER posts an email of an existing UNLINKED ASSISTANT outside cooldown
- **THEN** an `assistant_invite` is created `PENDING`, the assistant becomes `PENDING` with `driver_id` null, and MailService sends the invite email with the raw secret link

#### Scenario: Email not found
- **WHEN** a DRIVER posts an email with no matching user
- **THEN** the system returns a clear localized error and creates no invite and no user

#### Scenario: Email belongs to non-ASSISTANT
- **WHEN** a DRIVER posts an email of a CLIENT or DRIVER user
- **THEN** the system returns `409 Conflict`

#### Scenario: Assistant already PENDING, ACTIVE, or INACTIVE
- **WHEN** a DRIVER posts an email of an ASSISTANT not in `UNLINKED`
- **THEN** the system returns `409 Conflict`

#### Scenario: Resend while PENDING from same driver
- **WHEN** a DRIVER invites the same email while their previous invite to that assistant is still `PENDING`
- **THEN** the previous invite becomes `CANCELLED`, a new invite is created (new secret/`token_hash`, new `expires_at`), `assistant.status` remains `PENDING`, and a new email is sent

#### Scenario: Cooldown after rejection by same driver
- **WHEN** a DRIVER invites an assistant who `REJECTED` that same driver's invite within the last 7 days
- **THEN** the system returns `409 Conflict` with a message that they cannot re-invite yet

#### Scenario: Other driver may invite after rejection
- **WHEN** assistant is `UNLINKED` after rejecting driver A, and driver B invites within 7 days
- **THEN** the invite for driver B succeeds (cooldown is per driver–assistant pair)

---

### Requirement: Driver can cancel a PENDING invite
The system SHALL allow the owning DRIVER to cancel via `DELETE /api/assistants/invites/{token}` where `{token}` is the invite's public opaque token. Success MUST set invite status to `CANCELLED` and revert the assistant to `UNLINKED` when still `PENDING` for that invite.

#### Scenario: Cancel pending invite
- **WHEN** the owning DRIVER deletes their PENDING invite by public token
- **THEN** the invite becomes `CANCELLED` and the assistant becomes `UNLINKED`

#### Scenario: Non-owner cannot cancel
- **WHEN** another DRIVER attempts to delete the invite
- **THEN** the system returns `403 Forbidden`

---

### Requirement: Lazy expiry of PENDING invites
The system SHALL treat `PENDING` invites with `expires_at` in the past as expired at every relevant entry point (invite page GET, accept/reject POST, and new-invite eligibility). Lazily marking MUST set invite to `EXPIRED` and revert assistant to `UNLINKED` when applicable. No scheduled expiry job is required in this MVP.

#### Scenario: Expired invite on page view
- **WHEN** a client opens `GET /assistant-invite/{token}` for a PENDING invite past `expires_at`
- **THEN** the invite is marked `EXPIRED`, the assistant returns to `UNLINKED` if still PENDING, and the page shows an expired state

#### Scenario: Expired pending does not block a new invite
- **WHEN** a DRIVER invites an email whose only blocking invite is PENDING but past `expires_at` and not yet marked
- **THEN** the system expires that invite lazily and allows creating a new invite if the assistant is otherwise eligible

---

### Requirement: Web accept and reject require matching authenticated assistant
The system SHALL expose Thymeleaf `GET /assistant-invite/{token}` (lookup by hashing the raw secret) showing driver info (name, photo, city, rating) and Accept/Reject actions when the invite is `PENDING` and not expired. Accept and reject MUST be form POSTs on the web surface only (`POST /assistant-invite/{token}/accept` and `/reject`) — no REST accept/reject in this MVP. Actions MUST require the logged-in user to be the invite's assistant (`assistant.user_id`); the email secret alone MUST NOT authorize the action. Accept MUST set assistant `ACTIVE` with `driver_id` and `activated_at`, and invite `ACCEPTED`. Reject MUST set invite `REJECTED` with `responded_at` and assistant `UNLINKED`.

#### Scenario: Accept when logged in as the invited assistant
- **WHEN** the invited ASSISTANT is authenticated and posts accept on a valid PENDING invite
- **THEN** assistant becomes `ACTIVE` linked to that driver with `activated_at`, and the invite becomes `ACCEPTED`

#### Scenario: Reject when logged in as the invited assistant
- **WHEN** the invited ASSISTANT posts reject on a valid PENDING invite
- **THEN** the invite becomes `REJECTED` and the assistant becomes `UNLINKED`

#### Scenario: Unauthenticated user cannot complete accept
- **WHEN** an unauthenticated client attempts accept
- **THEN** the system requires login before processing (redirect or equivalent gate)

#### Scenario: Wrong authenticated user cannot accept
- **WHEN** a different authenticated user (another assistant, client, or driver) posts accept
- **THEN** the system rejects the action without activating the link

#### Scenario: No REST accept/reject in MVP
- **WHEN** a client calls a hypothetical `/api/assistant-invites/{token}/accept`
- **THEN** the system does not provide that REST accept/reject API in this change

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
