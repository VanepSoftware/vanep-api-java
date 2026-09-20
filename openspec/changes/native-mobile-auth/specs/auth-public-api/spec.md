## Purpose

Expose unauthenticated JSON endpoints for account lifecycle steps the native app performs before it has tokens: sign-up by account type, completing a Google sign-up, e-mail verification and password reset by code. These endpoints never issue tokens.

## ADDED Requirements

### Requirement: Auth API routes are public and never issue tokens

Every route under `/api/auth/**` SHALL be reachable without a Bearer token, even when the request carries an invalid or expired `Authorization` header. No response from these routes MAY contain an access token, refresh token or ID token. All other `/api/**` routes MUST still require authentication.

#### Scenario: Public route without token

- **WHEN** a request without `Authorization` posts to `/api/auth/password/forgot`
- **THEN** the system does not return `401`

#### Scenario: Stale bearer on public route

- **WHEN** a request with an expired Bearer token posts to `/api/auth/email/verify/resend`
- **THEN** the system does not return `401`

#### Scenario: Other API routes stay protected

- **WHEN** a request without `Authorization` calls `GET /api/user/me`
- **THEN** the system returns `401`

---

### Requirement: Auth API errors use one JSON envelope

Errors from `/api/auth/**` (other than `429`) SHALL be JSON bodies with:
- `code`: lowercase snake_case;
- `message`: pt-BR text resolved from message keys;
- `errors`: a list of `{field, message}` for field validation failures, empty otherwise.

Bean Validation failures MUST return `400` with `code=validation_error` and one entry per invalid field.

#### Scenario: Several invalid fields

- **WHEN** a client sign-up request has a blank `name` and an invalid `document`
- **THEN** the system returns `400` with `code=validation_error`
- **AND** `errors` contains entries for `name` and `document` with pt-BR messages

---

### Requirement: Sign-up by account type

The system SHALL expose `POST /api/auth/signup/client`, `POST /api/auth/signup/driver` and `POST /api/auth/signup/assistant`. They MUST accept the same fields and apply the same validation as the web sign-up forms:
- **Required:** `name`, `email` (valid format), `password` (at least 6 characters, one uppercase letter and one special character), `document` (valid CPF), and `acceptTerms` equal to `true`.
- **Optional:** `phone`, `birthDate` (ISO date) and `gender`.
- **Driver only:** optional `cnpj` and `experienceYears`, plus a required positive `basePrice`.

On success the system MUST:
- create the account unverified, with the role record for its type;
- send the verification e-mail (code and link);
- return `201` with `email` and `emailVerified=false`.

#### Scenario: Client sign-up

- **WHEN** a valid client sign-up is posted
- **THEN** the system returns `201` with the e-mail and `emailVerified=false`
- **AND** a verification e-mail is sent
- **AND** a password grant with the new credentials returns `email_not_verified`

#### Scenario: Driver without base price

- **WHEN** a driver sign-up is posted without `basePrice`
- **THEN** the system returns `400` with `code=validation_error` and an entry for `basePrice`

#### Scenario: Weak password

- **WHEN** a sign-up is posted with a password that has no uppercase letter and no special character
- **THEN** the system returns `400` with `code=validation_error`
- **AND** `errors` contains one `password` entry for each missing requirement

#### Scenario: Terms not accepted

- **WHEN** a sign-up is posted with `acceptTerms=false`
- **THEN** the system returns `400` with `code=validation_error` and an entry for `acceptTerms`

---

### Requirement: Sign-up rejects duplicate e-mail and CPF

When the e-mail or the normalized CPF already belongs to an account, sign-up SHALL return `409` with `code=email_duplicate` or `code=document_duplicate` and create nothing. Revealing that an e-mail or CPF is registered is an accepted trade-off. The web sign-up forms MUST keep showing the same duplicate messages as today.

#### Scenario: Duplicate e-mail

- **WHEN** a sign-up is posted with an e-mail that is already registered
- **THEN** the system returns `409` with `code=email_duplicate`

#### Scenario: Duplicate CPF with formatting

- **WHEN** a sign-up is posted with a CPF that matches an existing account once punctuation is removed
- **THEN** the system returns `409` with `code=document_duplicate`

---

### Requirement: Completing a Google sign-up

The system SHALL expose `POST /api/auth/signup/complete`. It accepts:
- `signupTicket`;
- `type` (`CLIENT`, `DRIVER` or `ASSISTANT`);
- `document`, `acceptTerms`, and optional `phone`, `birthDate`, `gender`;
- when `type=DRIVER`, the driver fields with the same rules as driver sign-up.

With a valid ticket the system MUST:
- create a verified account with the ticket's e-mail and name, and without a local password;
- create the role record for the type;
- link the Google identity from the ticket;
- consume the ticket;
- return `201` with `email` and `emailVerified=true`.

The app then obtains tokens by repeating the Google grant.

#### Scenario: Client completes Google sign-up

- **WHEN** a valid ticket and valid client data are posted
- **THEN** the system returns `201`
- **AND** a Google grant for the same subject returns tokens with `roles` containing `ROLE_CLIENT`

#### Scenario: Driver completes Google sign-up

- **WHEN** a valid ticket, `type=DRIVER` and a positive `basePrice` are posted
- **THEN** the account has a driver record with approval status `PENDING`
- **AND** its access token carries `driver_status`

#### Scenario: Invalid ticket

- **WHEN** the ticket is unknown, expired or already used
- **THEN** the system returns `400` with `code=invalid_signup_ticket`
- **AND** creates nothing

#### Scenario: E-mail registered while ticket was open

- **WHEN** an account with the ticket's e-mail was created after the ticket was issued
- **THEN** the system returns `409` with `code=email_duplicate`

---

### Requirement: Google sign-up completion creates the role record in every channel

Completing a Google sign-up, through the API or through the web `/signup/complete` page, SHALL create the client, driver or assistant record exactly as regular sign-up does. A driver completion without the required driver fields MUST be rejected with a validation error instead of creating an account without a driver record.

#### Scenario: Web completion as client

- **WHEN** a new Google user completes `/signup/complete` on the web choosing client
- **THEN** the account has a client record

#### Scenario: Web completion as driver without driver fields

- **WHEN** a new Google user submits `/signup/complete` on the web choosing driver without a base price
- **THEN** the page shows a validation error
- **AND** no account is created

---

### Requirement: Verify e-mail by code

The system SHALL expose `POST /api/auth/email/verify` with `email` and `code`. When the code is the active verification code of an unverified account that has no pending e-mail change, the system MUST mark the account verified, consume the code and return `204`. In every other case the system MUST return the same `400` with `code=invalid_code`. This includes an unknown e-mail, a wrong, expired, replaced or exhausted code, an already verified account, and an account with a pending e-mail change.

#### Scenario: Correct code

- **WHEN** an unverified account posts its active code
- **THEN** the system returns `204`
- **AND** a password grant with that account's correct credentials returns tokens

#### Scenario: Unknown e-mail

- **WHEN** a code is posted for an unregistered e-mail
- **THEN** the system returns `400` with `code=invalid_code`

#### Scenario: Pending e-mail change is not confirmed here

- **WHEN** a code is posted for an account that has a pending e-mail change
- **THEN** the system returns `400` with `code=invalid_code`
- **AND** the account e-mail is unchanged

---

### Requirement: Resend verification code

The system SHALL expose `POST /api/auth/email/verify/resend` with `email`. It MUST always return `202`, whether or not a code was sent. Code issuing follows the `auth-email-codes` rules.

#### Scenario: Unknown e-mail

- **WHEN** a resend is posted for an unregistered e-mail
- **THEN** the system returns `202`
- **AND** no e-mail is sent

---

### Requirement: Request password reset

The system SHALL expose `POST /api/auth/password/forgot` with `email`. It MUST always return `202`, whether or not a code was sent. Code issuing follows the `auth-email-codes` rules.

#### Scenario: Registered account

- **WHEN** a forgot-password request is posted for an account with a local password
- **THEN** the system returns `202`
- **AND** a reset e-mail with code and link is sent

#### Scenario: Unknown e-mail

- **WHEN** a forgot-password request is posted for an unregistered e-mail
- **THEN** the system returns `202`
- **AND** no e-mail is sent

---

### Requirement: Reset password by code

The system SHALL expose `POST /api/auth/password/reset` with `email`, `code` and `newPassword`. `newPassword` MUST have at least 8 characters, the same minimum the web reset form enforces today. Sign-up keeps its 6-character minimum; unifying the two is out of scope. With the active reset code, the system MUST store the new password, consume the code and its link, and return `204`. In every other case the system MUST return the same `400` with `code=invalid_code`. This includes an unknown e-mail, a wrong, expired, replaced or exhausted code, and an account without a local password.

#### Scenario: Successful reset

- **WHEN** the active reset code and a valid new password are posted
- **THEN** the system returns `204`
- **AND** the password grant accepts the new password and rejects the old one

#### Scenario: Weak new password

- **WHEN** a reset is posted with a 7-character `newPassword`
- **THEN** the system returns `400` with `code=validation_error`
- **AND** the code is not consumed

#### Scenario: Minimum length accepted

- **WHEN** a reset is posted with the active code and an 8-character `newPassword`
- **THEN** the system returns `204`

---

### Requirement: Account endpoints are rate limited by trusted client address

Every `POST` under `/api/auth/**`, together with the existing limited web and token routes, SHALL be rate limited per client address and path, answering `429` once the limit is exceeded. The client address MUST come from the connection. It MAY come from a forwarding header only when the direct peer is a configured trusted proxy. A forwarding header sent by an untrusted peer MUST NOT change the rate-limit key.

#### Scenario: Limit exceeded

- **WHEN** more requests than the configured capacity are posted to `/api/auth/password/forgot` from one address within the window
- **THEN** the extra requests receive `429`

#### Scenario: Spoofed forwarding header

- **WHEN** a direct, untrusted client exceeds the limit on `/oauth2/token` while sending a different `X-Forwarded-For` value on each request
- **THEN** the extra requests still receive `429`
