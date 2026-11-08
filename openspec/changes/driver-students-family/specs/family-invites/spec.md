## ADDED Requirements

### Requirement: The driver invites the family of an unlinked passenger

`POST /api/drivers/me/unlinked-passengers/{token}/invite` MUST create the passenger's invite or, when one is live, refresh its `sent_at` and keep its code. The response MUST carry `code`, `inviteUrl` (`${vanep.app.invite-base-url}/{code}`) and `sentAt`. Concurrent first invites MUST NOT answer 500: the loser answers 409 `family_invite.concurrent_request`. The unlinked passenger response MUST carry `invitedAt`. A token of another driver MUST return 404. A merged passenger MUST return 409 `family_invite.already_linked`.

#### Scenario: First invite

- **WHEN** the driver invites the family of a passenger without an invite
- **THEN** the system returns HTTP 200 with an `inviteUrl` and `sentAt`
- **AND** the passenger's `invitedAt` equals `sentAt`

#### Scenario: Resend keeps the link

- **WHEN** the driver invites the same family again
- **THEN** the `inviteUrl` is the same and `sentAt` is newer

### Requirement: The driver has one link for the parents' group

`GET /api/drivers/me/invite-link` MUST return the driver's group invite (no passenger), creating it on the first call, with `inviteUrl`. There MUST be at most one live group invite per driver.

#### Scenario: The same link twice

- **WHEN** the driver asks for the link twice
- **THEN** both responses carry the same `inviteUrl`

### Requirement: Anyone with the link sees a minimal preview

`GET /api/family-invites/{code}` MUST be public and return only the driver's first name, and for a passenger invite the school name and the passenger's first name. It MUST NOT return addresses, schedules or contact data. An unknown code MUST return 404 `family_invite.not_found`.

#### Scenario: Preview without login

- **WHEN** an anonymous caller opens a passenger invite code
- **THEN** the system returns HTTP 200 with the driver's first name, the school and the student's first name only

### Requirement: The guardian accepts the invite for one dependent

`POST /api/family-invites/{code}/accept` with `dependentToken` MUST require a `CLIENT` who owns the dependent. In one transaction it MUST create the pair's `client_driver` as `PENDING` when none is live, and create the family match described in `family-match`. Repeating the same acceptance MUST return HTTP 200 with the same match.

#### Scenario: Accepting a passenger invite

- **WHEN** a client accepts a passenger invite for their dependent
- **THEN** the pair has a `PENDING` `client_driver`
- **AND** the passenger has a `PENDING` family match with that dependent

#### Scenario: Another client's dependent

- **WHEN** the dependent belongs to another client
- **THEN** the system returns HTTP 404 and creates nothing
