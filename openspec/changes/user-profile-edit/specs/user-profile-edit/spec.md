## ADDED Requirements

### Requirement: Authenticated partial profile update

The system SHALL expose `PATCH /api/user/me` for the authenticated caller identified by JWT `uid`. The request MUST accept partial updates of `name`, `phone`, and `gender` using a request DTO that distinguishes absent fields from explicit null via `JsonNullable` (or equivalent). The system MUST NOT accept updates to `document`, `birthDate`, `email`, `password`, or `username` on this endpoint. Controllers MUST remain thin; business rules MUST live in a `@Service`. Public identifiers in responses MUST remain opaque `token` strings.

#### Scenario: Update name phone and gender

- **WHEN** an authenticated user sends `PATCH /api/user/me` with a new non-blank `name`, non-blank `phone`, and a `gender` value, and no cooldown blocks those fields
- **THEN** the system returns `200 OK` with the updated `UserMeResponseDTO`
- **AND** persists the new values on `users`

#### Scenario: Absent field is no-op

- **WHEN** an authenticated user sends `PATCH /api/user/me` omitting `phone`
- **THEN** the system leaves the stored phone unchanged

#### Scenario: Explicit null rejected

- **WHEN** an authenticated user sends `PATCH /api/user/me` with `"name": null` or `"phone": null` or `"gender": null`
- **THEN** the system returns `400 Bad Request`
- **AND** does not change the account

#### Scenario: Blank phone rejected

- **WHEN** an authenticated user sends `PATCH /api/user/me` with `"phone": ""`
- **THEN** the system returns `400 Bad Request`
- **AND** does not clear or change the phone

#### Scenario: Unauthenticated patch

- **WHEN** a request without a valid Bearer token calls `PATCH /api/user/me`
- **THEN** the system returns `401 Unauthorized`

#### Scenario: Same value does not refresh cooldown

- **WHEN** an authenticated user sends `PATCH /api/user/me` with `name` equal to the current name
- **THEN** the system returns `200 OK` without changing `last_name_change_at`

---

### Requirement: Field cooldowns of configurable duration

The system SHALL enforce a cooldown of `vanep.profile.change-cooldown-days` (default 30) calendar days after an effective change to `name`, `phone`, or `email`. The cooldown MUST be tracked with `last_name_change_at`, `last_phone_change_at`, and `last_email_change_at` on `users`. `gender` MUST NOT be subject to cooldown. Cooldown for email MUST start only when the email change is confirmed (pending promoted to `email`), never when the change is merely requested. When a cooldown is active and the client attempts a change, the system MUST respond with `409 Conflict` using the feature's structured conflict body: `message` (localized), `code` equal to `cooldown`, `field` (`name` \| `phone` \| `email`), and `retryAfter` as an ISO-8601 timestamp.

#### Scenario: Name cooldown blocks early change

- **WHEN** the user successfully changed `name` fewer than the configured cooldown days ago
- **AND** sends `PATCH /api/user/me` with a different `name`
- **THEN** the system returns `409 Conflict`
- **AND** the body includes `code` equal to `cooldown`, `field` equal to `name`, and `retryAfter` as an ISO-8601 instant when the change becomes allowed

#### Scenario: Phone cooldown independent of name

- **WHEN** `last_name_change_at` is within the cooldown window but `last_phone_change_at` is null or older than the cooldown
- **AND** the user patches only `phone` to a new non-blank value
- **THEN** the system accepts the phone update (`200 OK`)

#### Scenario: Gender always editable

- **WHEN** the user patches only `gender` to a new value regardless of any `last_*_change_at`
- **THEN** the system accepts the update without checking cooldown

#### Scenario: Email cooldown counted from confirmation

- **WHEN** the user requests an email change and has not yet confirmed
- **THEN** `last_email_change_at` is unchanged
- **WHEN** the user later confirms successfully
- **THEN** `last_email_change_at` is set to the confirmation time
- **AND** a subsequent email-change request before the cooldown ends returns `409` with `code` equal to `cooldown` and `field` equal to `email`

---

### Requirement: Structured profile conflict responses

API endpoints in this capability that return `409 Conflict` (cooldown on `PATCH` / `POST .../email-change`, and email duplicate on `POST .../email-change` or API-facing confirm when applicable) MUST use one shared JSON body shape: `message` (localized string), `code` (`cooldown` \| `email_duplicate`), `field` (string), and `retryAfter` (ISO-8601 instant, required for `cooldown`, null or omitted for `email_duplicate`). The system MUST NOT return a bare `ResponseStatusException` reason-only body for these conflicts. Clients MUST discriminate causes using `code`, not by probing for missing properties.

#### Scenario: Cooldown and duplicate share the same JSON shape

- **WHEN** the client receives a `409` from profile edit for cooldown and another `409` for email duplicate
- **THEN** both bodies include `message`, `code`, and `field`
- **AND** only the cooldown body includes a non-null `retryAfter`
- **AND** the `code` values differ (`cooldown` vs `email_duplicate`)

---

### Requirement: Immutable document and birth date

The system MUST NOT allow changing `document` or `birthDate` through profile-edit endpoints in this capability. Requests that attempt to set those fields on `PATCH /api/user/me` MUST be ignored by contract (fields absent from the DTO) or rejected if somehow supplied; the stored values MUST remain unchanged.

#### Scenario: Document unchanged after profile patch

- **WHEN** an authenticated user successfully patches name and/or phone and/or gender
- **THEN** `users.document` and `users.birth_date` remain equal to their previous values

---

### Requirement: Email change with pending_email and re-verification

The system SHALL expose `POST /api/user/me/email-change` with body `{ "email": "<new>" }` for the authenticated caller. On success the system MUST set `users.pending_email` to the new address (always replacing any previous pending value — the system MUST NOT reject the request solely because a pending change already exists), MUST NOT change `users.email` or clear verification of the current email for login purposes beyond existing rules, MUST send a verification message to the **new** address, and MUST NOT set `last_email_change_at` at request time. The system MUST NOT create a unique constraint on `pending_email`. OAuth-only accounts MUST be allowed to request a local email change; `users.email` remains the Vanep contact/login email and MUST NOT be required to mirror the OAuth provider email. Duplicate primary-email conflicts on API endpoints MUST return `409 Conflict` with the **same** structured conflict body shape as cooldown errors: `message` resolved from `auth.signup.email.duplicate`, `code` equal to `email_duplicate`, `field` equal to `email`, and `retryAfter` omitted or null. The Flutter client MUST be able to distinguish conflict causes via `code`, not via differing JSON shapes.

#### Scenario: Start email change

- **WHEN** an authenticated user posts a new unused email and no email cooldown is active
- **THEN** the system returns success (e.g. `204 No Content` or `200` with updated me including `pendingEmail`)
- **AND** `users.pending_email` equals the new email
- **AND** `users.email` is unchanged
- **AND** a verification email is sent to the new address

#### Scenario: Duplicate primary email rejected with signup key

- **WHEN** an authenticated user posts an email that already belongs to another active account as `users.email`
- **THEN** the system returns `409 Conflict`
- **AND** the body includes `code` equal to `email_duplicate` and `field` equal to `email`
- **AND** `retryAfter` is null or omitted
- **AND** the user-facing `message` resolves from `auth.signup.email.duplicate`

#### Scenario: Same as current email rejected

- **WHEN** an authenticated user posts the same address as their current `users.email`
- **THEN** the system returns `400 Bad Request`
- **AND** does not create a new verification for that no-op

#### Scenario: Replace previous pending without blocking

- **WHEN** the user already has a `pending_email` (active or not) and posts a different new email
- **THEN** the system accepts the request (MUST NOT return an “change already in progress” error)
- **AND** `pending_email` becomes the latest value
- **AND** a new verification email is sent to that latest address

---

### Requirement: Invalidate prior verification tokens on new email challenge

When the system issues a new email-verification challenge for a user in the email-change flow (including replacing `pending_email`), it MUST mark all existing open verification tokens for that user as consumed (`consumed_at` set) before creating the new token. An open token is one with `consumed_at` null. This MUST prevent an older unexpired link from confirming a newer `pending_email` that the original link did not represent.

#### Scenario: Old link cannot confirm replaced pending

- **WHEN** the user requests email change to address A (token_A issued)
- **AND** then requests email change to address B without confirming A (token_B issued; open tokens including token_A consumed)
- **AND** someone submits token_A
- **THEN** verification fails (token_A is consumed or otherwise invalid)
- **AND** `users.email` remains unchanged
- **AND** `pending_email` remains B until token_B is confirmed

#### Scenario: Only latest link confirms B

- **WHEN** after replacing pending A with B as above
- **AND** a valid unused token_B is submitted and B is not taken by another account
- **THEN** `users.email` becomes B and `pending_email` is cleared

---

### Requirement: Confirm pending email on verification

When an email verification token is consumed successfully and the user has a non-null `pending_email`, the system MUST attempt to set `users.email` to that pending value, clear `pending_email`, set `verified` to true, and set `last_email_change_at` to now. If another active account already owns that email, the system MUST NOT overwrite `users.email`, MUST leave the conflict detectable to the client, and MUST use message key `auth.signup.email.duplicate`. For API consumers the conflict MUST use the same structured `409` body as other profile conflicts (`code` = `email_duplicate`). For the web verify flow the system MUST surface an explicit error outcome (not a silent failure). Concurrent confirms for the same address MUST be resolved by the unique constraint on `users.email`: the first successful commit wins; the second receives the duplicate conflict. When `pending_email` is null, existing signup verification behavior (set `verified=true` only) MUST remain. Verification MUST resolve by token hash and MUST NOT depend on the GET-only `activePendingEmail` helper.

#### Scenario: Confirm promotes pending email

- **WHEN** a valid unused verification token is submitted for a user with `pending_email` set to an address not used by another account
- **THEN** `users.email` becomes that address
- **AND** `pending_email` is null
- **AND** `verified` is true
- **AND** `last_email_change_at` is set

#### Scenario: Confirm loses race on unique email

- **WHEN** two users hold the same `pending_email` and the first confirms successfully
- **AND** the second confirms afterward
- **THEN** the second confirmation does not change the second user's `users.email`
- **AND** the second outcome communicates duplicate email via `auth.signup.email.duplicate`

#### Scenario: Classic verify without pending

- **WHEN** a valid verification token is submitted for a user with `pending_email` null
- **THEN** the system sets `verified` to true without changing `email`

---

### Requirement: Enriched me response for profile UI

`GET /api/user/me` MUST continue to return account fields and MUST additionally expose `pendingEmail` (nullable) and cooldown hints `nameChangeAvailableAt`, `phoneChangeAvailableAt`, and `emailChangeAvailableAt` as nullable ISO-8601 instants: when the corresponding `last_*_change_at` is set and still within the cooldown window, the value MUST be the instant when a new change is allowed; otherwise null.

`pendingEmail` in the response MUST be the result of an active-pending resolution used **only** for this response shape: return `users.pending_email` if and only if it is non-null **and** the user has at least one email verification token that is not consumed and not expired; otherwise return null (even if the column still holds a stale value). This helper MUST NOT be used by `POST /api/user/me/email-change` to block replacement, and MUST NOT be used by the verify path.

#### Scenario: Me includes active pending email

- **WHEN** the authenticated user has `pending_email` set
- **AND** has at least one open (unconsumed, unexpired) verification token
- **AND** calls `GET /api/user/me`
- **THEN** the response includes `pendingEmail` equal to that pending value

#### Scenario: Me hides ghost pending after token expiry

- **WHEN** the authenticated user has `pending_email` set
- **AND** has no open verification token (all expired or consumed)
- **AND** calls `GET /api/user/me`
- **THEN** `pendingEmail` in the response is null

#### Scenario: Me includes retry hint while cooling down

- **WHEN** the user changed name recently within the cooldown window
- **AND** calls `GET /api/user/me`
- **THEN** `nameChangeAvailableAt` is a non-null ISO-8601 instant in the future
- **AND** `phoneChangeAvailableAt` may still be null if phone was never changed or cooldown elapsed
