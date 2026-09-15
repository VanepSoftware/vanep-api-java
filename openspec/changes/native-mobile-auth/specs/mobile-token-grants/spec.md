## Purpose

Issue access and refresh tokens to the first-party native mobile app through the existing OAuth2 token endpoint, using password and Google ID-token grants, without a browser or WebView and without changing token contents.

## ADDED Requirements

### Requirement: Public mobile client authenticates with client_id only

The token endpoint SHALL authenticate the configured mobile client (`vanep-mobile` by default) by `client_id` alone, without a client secret, for the grant types `urn:vanep:params:oauth:grant-type:password`, `urn:vanep:params:oauth:grant-type:google` and `refresh_token`. Any other client that sends one of these grants without valid client authentication MUST be rejected. The configured web client (`vanep-frontend`) MUST keep its current authentication behavior.

#### Scenario: Refresh with only client_id

- **WHEN** the mobile app posts `grant_type=refresh_token`, a valid `refresh_token` and `client_id=vanep-mobile` to `/oauth2/token`
- **THEN** the system returns `200` with a new `access_token` and a new `refresh_token`

#### Scenario: Unknown client id

- **WHEN** a request posts `grant_type=urn:vanep:params:oauth:grant-type:password` with `client_id=unknown-app`
- **THEN** the system returns `401` with `error=invalid_client`

---

### Requirement: Mobile grants are restricted to the mobile client

The password and Google grants SHALL be enabled only on the mobile client registration. A registered client without these grant types MUST receive `unauthorized_client`.

#### Scenario: Web client tries the password grant

- **WHEN** a request posts `grant_type=urn:vanep:params:oauth:grant-type:password` with `client_id=vanep-frontend` and valid credentials
- **THEN** the system returns `400` with `error=unauthorized_client`
- **AND** issues no token

---

### Requirement: Mobile client receives rotating refresh tokens

Every successful token response to the mobile client SHALL include a `refresh_token`. This covers the password grant, the Google grant, `refresh_token`, and the legacy `authorization_code` grant while that grant stays enabled. Refresh tokens MUST NOT be reusable: a successful refresh MUST invalidate the refresh token that was presented. Refresh-token TTL and revocation through `/oauth2/revoke` MUST keep working as they do today.

#### Scenario: Password grant returns refresh token

- **WHEN** the mobile app completes a successful password grant
- **THEN** the response contains `access_token`, `refresh_token`, `token_type=Bearer` and `expires_in`

#### Scenario: Rotated refresh token cannot be reused

- **WHEN** the mobile app refreshes with refresh token R1 and receives R2
- **AND** later posts `grant_type=refresh_token` with R1 again
- **THEN** the system returns `400` with `error=invalid_grant`

#### Scenario: Revoked refresh token

- **WHEN** the mobile app revokes its refresh token through `/oauth2/revoke`
- **AND** later tries to refresh with it
- **THEN** the system returns `400` with `error=invalid_grant`

---

### Requirement: Tokens from mobile grants have the same claims

Access tokens issued by the password and Google grants SHALL use the account e-mail as the principal. They SHALL carry the same claims as tokens issued through `authorization_code` for the same account: `uid`, `user_type`, `roles`, `permissions`, and `driver_status` / `assistant_status` when applicable. They MUST be accepted by every existing `/api/**` resource.

#### Scenario: Driver logs in with password grant

- **WHEN** a verified driver account completes the password grant
- **THEN** the access token contains `uid` equal to the user token, `roles` containing `ROLE_DRIVER`, the role's `permissions`, and `driver_status`
- **AND** `GET /api/user/me` with that token returns `200`

---

### Requirement: Password grant authenticates local credentials

The password grant SHALL accept `username` (account e-mail) and `password`. It SHALL verify them against the stored local password with the same password encoding the web login uses. On success the system MUST update the account's `last_login_at` and reset the failed-attempt counter for that e-mail.

#### Scenario: Successful login

- **WHEN** a verified account posts its correct e-mail and password to the password grant
- **THEN** the system returns `200` with tokens
- **AND** `last_login_at` is set to the time of the request
- **AND** earlier failed attempts for that e-mail no longer count towards the lockout

---

### Requirement: Password grant does not reveal whether an account exists

For an e-mail that is not registered, a wrong password, or an account with no local password (Google-only), the password grant SHALL return the same `400` response with `error=invalid_grant` and the same `error_description`. Each of these failures MUST count as a failed attempt for the submitted e-mail.

#### Scenario: Unknown e-mail and wrong password are indistinguishable

- **WHEN** one request uses an unregistered e-mail and another uses a registered e-mail with a wrong password
- **THEN** both responses have status `400`, `error=invalid_grant` and identical `error_description`

#### Scenario: Google-only account

- **WHEN** a request uses the e-mail of an account that has no local password
- **THEN** the system returns `400` with `error=invalid_grant`

---

### Requirement: Unverified account is reported only after the password is correct

When the credentials are correct but the account e-mail is not verified, the password grant SHALL return `400` with `error=email_not_verified` and issue no token. When the password is wrong, an unverified account MUST get `invalid_grant`, exactly like any other wrong password.

#### Scenario: Unverified account with correct password

- **WHEN** an unverified account posts its correct password
- **THEN** the system returns `400` with `error=email_not_verified`

#### Scenario: Unverified account with wrong password

- **WHEN** an unverified account posts a wrong password
- **THEN** the system returns `400` with `error=invalid_grant`

---

### Requirement: Lockout is uniform for every submitted e-mail

After the configured number of failed attempts (default 5) for a submitted e-mail (case-insensitive) within the lock window (default 15 minutes), the password grant SHALL return `400` with `error=account_locked`. It MUST do so whether or not an account with that e-mail exists, whether or not it has a local password, and even when the password is correct. The lock MUST end once the lock window has passed since the last failure.

#### Scenario: Existing account is locked

- **WHEN** five wrong passwords are posted for a registered e-mail
- **AND** a sixth request posts the correct password
- **THEN** the system returns `400` with `error=account_locked`

#### Scenario: Unknown e-mail is locked the same way

- **WHEN** five requests are posted for an unregistered e-mail
- **AND** a sixth request is posted for the same e-mail
- **THEN** the system returns `400` with `error=account_locked`

#### Scenario: Lock expires

- **WHEN** an e-mail is locked and the lock window has passed since its last failure
- **THEN** a request with the correct password succeeds

---

### Requirement: Google grant validates the ID token

The Google grant SHALL accept an `id_token` issued by Google to the native app. The system MUST reject the grant with `400` and `error=invalid_grant` when any of these holds:
- the signature does not verify against Google's published keys;
- `iss` is neither `accounts.google.com` nor `https://accounts.google.com`;
- `aud` is not in the configured list of allowed client IDs;
- the token is expired;
- `email_verified` is not `true`.

When the allowed-audience list is empty, the system MUST reject every Google grant request with `invalid_grant`.

#### Scenario: Audience not allowed

- **WHEN** the app posts an ID token whose `aud` is not configured
- **THEN** the system returns `400` with `error=invalid_grant`

#### Scenario: Expired token

- **WHEN** the app posts an ID token whose `exp` is in the past
- **THEN** the system returns `400` with `error=invalid_grant`

#### Scenario: Unverified Google e-mail

- **WHEN** the app posts an otherwise valid ID token with `email_verified=false`
- **THEN** the system returns `400` with `error=invalid_grant`

---

### Requirement: Google grant resolves the Vanep account

For a valid ID token, the system SHALL resolve the account the same way web Google login does:
- **Google identity already linked:** issue tokens for that account.
- **Not linked, and a registered account has the same e-mail:** link the identity to it and issue tokens.
- **Linked account deactivated:** return `400` with `error=account_disabled`.

#### Scenario: Linked account

- **WHEN** the ID token's subject is already linked to an active account
- **THEN** the system returns `200` with tokens for that account

#### Scenario: Existing account with same e-mail

- **WHEN** no account is linked to the subject and an account with the token's verified e-mail exists
- **THEN** the system links the Google identity to that account
- **AND** returns `200` with tokens for it

---

### Requirement: New Google user receives a signup ticket

When a valid ID token matches no linked identity and no registered e-mail, the Google grant SHALL return `400` with `error=registration_required`. The response body MUST also contain a `signup_ticket`, plus `email` and `name` from the ID token. The ticket MUST:
- be single-use;
- expire after a configurable TTL (default 15 minutes);
- be stored only as a hash;
- be bound to the provider, the Google subject, the e-mail and the name.

#### Scenario: First Google login

- **WHEN** a person who has never used Vanep posts a valid ID token
- **THEN** the system returns `400` with `error=registration_required`, a non-empty `signup_ticket`, `email` and `name`
- **AND** issues no token

#### Scenario: Retry after completing registration

- **WHEN** the person completes registration with the ticket and posts a new valid ID token for the same Google subject
- **THEN** the system returns `200` with tokens

---

### Requirement: Token endpoint errors follow OAuth format

Errors from the mobile grants SHALL use the standard OAuth 2.0 JSON error body (`error`, optional `error_description`) with status `400`. The only exceptions are client authentication failures (`401`) and rate limiting (`429`). The `registration_required` response is the only one that adds fields. The `/oauth2/token` endpoint MUST stay rate limited per client address.

#### Scenario: Error body shape

- **WHEN** the password grant fails with wrong credentials
- **THEN** the response has `Content-Type: application/json` and a body containing `"error":"invalid_grant"`
