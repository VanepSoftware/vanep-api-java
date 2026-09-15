## Purpose

Let people prove control of their e-mail with a short numeric code typed into the native app, as an alternative to the existing link, for account verification and password reset, with limits that keep brute force impractical.

## ADDED Requirements

### Requirement: Verification and reset e-mails carry a code and a link

Every account-verification e-mail and every password-reset e-mail SHALL contain both a 6-digit numeric code and the existing web link. The code and the link are one credential: using either one successfully MUST invalidate the other. Each e-mail MUST state how long the credential stays valid, using the configured TTL. E-mail-change confirmation e-mails SHALL stay link-only.

#### Scenario: Reset e-mail contents

- **WHEN** a password reset is requested for an account with a local password
- **THEN** the e-mail sent contains a 6-digit code, the `/reset-password?token=` link, and the configured validity in minutes

#### Scenario: Link used after code

- **WHEN** a reset is completed with the code
- **AND** the link from the same e-mail is opened afterwards
- **THEN** the web page reports the link as invalid

---

### Requirement: Codes are random 6-digit values that cannot be read back

Each code SHALL be exactly 6 decimal digits (`000000`–`999999`, leading zeros allowed), drawn from a cryptographically secure random source. The system MUST NOT store the code in plaintext. Reading the stored value without the server-side secret MUST NOT reveal the code.

#### Scenario: Leading zeros

- **WHEN** a generated code has a numeric value below 100000
- **THEN** it is delivered and accepted as a 6-character string with leading zeros

#### Scenario: Stored value

- **WHEN** a code is issued
- **THEN** the stored record contains no column equal to the code in plaintext

---

### Requirement: Codes expire

A verification code SHALL expire after the verification TTL (default 24 hours). A reset code SHALL expire after the reset TTL, whose default changes from 60 to **15 minutes**. The reset TTL applies to both the code and the web reset link. Both TTLs MUST be configurable through the environment.

#### Scenario: Expired reset code

- **WHEN** a reset code is submitted 16 minutes after it was issued, with the default TTL
- **THEN** the submission fails as an invalid code

---

### Requirement: A new code replaces the previous one

Issuing a new code for an account and purpose (verification or reset) SHALL invalidate every earlier unused code and link for that same account and purpose.

#### Scenario: Old code after resend

- **WHEN** code A is issued, then code B is issued for the same account and purpose
- **AND** code A is submitted
- **THEN** the submission fails as an invalid code

---

### Requirement: Wrong submissions are capped per code

Each code SHALL accept at most the configured number of wrong submissions (default 5). When the cap is reached, the code and its link MUST be invalidated. Any later submission of that code, including the correct value, MUST then fail as an invalid code.

#### Scenario: Correct code after too many failures

- **WHEN** five wrong codes are submitted for an account's active reset code
- **AND** the correct code is submitted next
- **THEN** the submission fails as an invalid code
- **AND** the password is not changed

---

### Requirement: Resend has a cooldown

When a new verification or reset code is requested for an account within the configured cooldown (default 60 seconds) after the last code issued for that account and purpose, the system SHALL NOT issue or send a new code. The response to the requester MUST be the same as when a code is sent.

#### Scenario: Two requests within cooldown

- **WHEN** a reset is requested for an account and requested again 10 seconds later
- **THEN** exactly one reset e-mail is sent
- **AND** both requests receive the same response

---

### Requirement: Codes are capped per account per day

The system SHALL issue at most the configured number of codes (default 10) per account and purpose within any rolling 24-hour window. The count includes the code sent at sign-up and codes requested through the web forms. Requests beyond the cap MUST NOT issue or send a code, and MUST get the same response as a request that does.

#### Scenario: Eleventh reset request

- **WHEN** ten reset codes have been issued for an account in the last 24 hours, respecting the cooldown
- **AND** an eleventh reset is requested
- **THEN** no e-mail is sent
- **AND** the requester receives the same response as for a sent code

---

### Requirement: Codes are only issued to eligible accounts

A verification code SHALL be issued only for an existing account that is not yet verified. A reset code SHALL be issued only for an existing account that has a local password. For any other e-mail the system MUST send nothing, and MUST give the same response as for an eligible account.

#### Scenario: Reset for Google-only account

- **WHEN** a reset is requested for an account without a local password
- **THEN** no e-mail is sent

#### Scenario: Resend for verified account

- **WHEN** a verification resend is requested for an already verified account
- **THEN** no e-mail is sent
