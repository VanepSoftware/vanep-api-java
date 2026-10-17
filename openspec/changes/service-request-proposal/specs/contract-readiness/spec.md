## Purpose

Before a client asks for a quote, the system checks that the client and the chosen dependents already have every datum the contract will need, so that accepting a proposal never stops to ask for more data.

## ADDED Requirements

### Requirement: The client reads what is missing for a contract

`GET /api/clients/me/contract-readiness?dependentTokens=...` MUST be available to an authenticated user whose type is `CLIENT`; any other type MUST get HTTP 403. Each dependent token MUST belong to an active dependent of the caller, otherwise HTTP 404. `dependentTokens` is optional: without it, only the client's data is checked. A token sent twice MUST be checked once.

The response MUST have `ready` (boolean) and `missing`, a list of entries with `subject` (`CLIENT` or `DEPENDENT`), `dependentToken` (only for `DEPENDENT`) and `field`:

| Subject | Required | `field` when missing |
|---|---|---|
| client | CPF (`users.document`) | `DOCUMENT` |
| client | phone | `PHONE` |
| client | personal address | `ADDRESS` |
| dependent | birth date | `BIRTH_DATE` |
| dependent | school | `SCHOOL` |
| dependent | address | `ADDRESS` |
| dependent | at least one day in the weekly school grid | `SCHOOL_DAYS` |

`ready` MUST be `true` exactly when `missing` is empty.

#### Scenario: Everything in place

- **WHEN** a client with CPF, phone and personal address asks about a dependent with birth date, school, address and a weekly school grid
- **THEN** the system returns HTTP 200 with `ready = true` and an empty `missing`

#### Scenario: A dependent without birth date

- **WHEN** a client asks about a dependent with no birth date
- **THEN** the response has `ready = false` and an entry `{subject: DEPENDENT, dependentToken, field: BIRTH_DATE}`

#### Scenario: A dependent without a school grid

- **WHEN** a client asks about a dependent whose weekly school grid is empty
- **THEN** the response has `ready = false` and an entry `{subject: DEPENDENT, dependentToken, field: SCHOOL_DAYS}`

#### Scenario: Without dependent tokens

- **WHEN** a client with no phone asks without `dependentTokens`
- **THEN** the response has `ready = false` and only the entry `{subject: CLIENT, field: PHONE}`

#### Scenario: Another client's dependent

- **WHEN** a client asks about a dependent token that belongs to another client
- **THEN** the system returns HTTP 404

### Requirement: A service request is sent only when the data is ready

Creating a service request MUST apply the same check to the caller and to every dependent in the request, and MUST return HTTP 422 `service_request.not_ready` when anything is missing, creating nothing.

#### Scenario: Request with a client who has no phone

- **WHEN** a client with no phone sends a service request
- **THEN** the system returns HTTP 422 with the message of `service_request.not_ready`
- **AND** no service request and no link are created
