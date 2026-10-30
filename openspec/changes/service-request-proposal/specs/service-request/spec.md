## Purpose

A client asks a driver for a quote through a structured service request: which dependents and, for each school day, whether the van is needed on the way to school, on the way back, or both, plus the desired start. The school period of each day comes from the dependent's weekly school grid. The driver sees only what is needed to price it, and the request has a clear state and deadline.

## ADDED Requirements

### Requirement: The client and the driver use their own surfaces

Service requests MUST be reached only through the `/me` surfaces below, with the caller resolved from the `Authentication`. A caller of the wrong `UserType` MUST get HTTP 403. A token in the URL MUST be resolved within the caller's own requests: a token of another user's request MUST return HTTP 404.

| Method | Path | Who | Effect |
|---|---|---|---|
| `POST` | `/api/clients/me/service-requests` | client | send |
| `GET` | `/api/clients/me/service-requests` | client | list, paginated, optional `status` filter (one or more statuses) |
| `GET` | `/api/clients/me/service-requests/{token}` | client | read |
| `POST` | `/api/clients/me/service-requests/{token}/withdraw` | client | withdraw |
| `GET` | `/api/drivers/me/service-requests` | driver | list, paginated, optional `status` filter (one or more statuses) |
| `GET` | `/api/drivers/me/service-requests/{token}` | driver | read (restricted view) |
| `POST` | `/api/drivers/me/service-requests/{token}/decline` | driver | decline, optional `reason` up to 500 characters |

#### Scenario: A driver cannot send a service request

- **WHEN** an authenticated `DRIVER` calls `POST /api/clients/me/service-requests`
- **THEN** the system returns HTTP 403

#### Scenario: Another driver's request is not found

- **WHEN** driver B reads `GET /api/drivers/me/service-requests/{token}` with a token of a request sent to driver A
- **THEN** the system returns HTTP 404

### Requirement: Sending a service request

The body MUST carry `driverToken`, `desiredStartDate` (today or later), optional `notes` (up to 1000 characters) and `items`: one or more entries, each with `dependentToken` and `days`, a non-empty list where each day has `weekday` (`DayOfWeek`) and the booleans `needsOutbound` and `needsReturn`. Missing fields, an empty list, a past date, the same dependent twice, the same weekday twice in one item, or a day with neither `needsOutbound` nor `needsReturn` MUST return HTTP 400.

The client MUST NOT send the school period, boarding times or route shifts. The school period of each day MUST come from the dependent's weekly school grid; boarding times and route shifts are the driver's answer, in the proposal.

The system MUST refuse, creating nothing:

| Condition | Response |
|---|---|
| driver unknown, inactive or not `APPROVED` | HTTP 404 `service_request.driver.not_found` |
| driver with `is_available = false` | HTTP 422 `service_request.driver.not_available` |
| a dependent that is not the caller's | HTTP 404 `service_request.dependent.not_found` |
| a requested weekday that is not in the dependent's school grid | HTTP 422 `service_request.day.no_school` |
| contract data missing (`contract-readiness`) | HTTP 422 `service_request.not_ready` |
| link `BLOCKED` | HTTP 422 `service_request.link.blocked` |
| link with a `SIGNED`, `ACTIVE` or `SUSPENDED` contract | HTTP 422 `service_request.link.has_contract` |
| the pair already has an open request (`OPEN` or `ANSWERED`), including a concurrent one | HTTP 409 `service_request.open_exists` |
| the client already has 5 open requests | HTTP 422 `service_request.open_limit` |

On success the system returns HTTP 201 with `status = OPEN` and `expiresAt` 72 hours after creation, and creates or reuses the link (`client-driver-origin`).

Each item MUST store a copy of the dependent's name, birth date, school and pickup city and neighborhood (the `neighborhood` of the dependent's address, when it has one) as they are at sending time, and each requested day with the school period the grid has for that weekday at sending time. Both views MUST show the dependent, school, city and neighborhood from that copy. Later edits to the dependent or to its grid MUST NOT change the request, and removing or restoring the dependent, or removing the school, MUST NOT hide the request or any of its items.

#### Scenario: A client asks for a quote for two siblings

- **WHEN** a ready client sends a request to an available driver for two dependents, Monday to Friday, outbound and return
- **THEN** the system returns HTTP 201 with `status = OPEN`, two items and five days each

#### Scenario: Full-time on some days only

- **WHEN** a client sends a request for a dependent whose grid has `FULLTIME` on Tuesday and Thursday and `MORNING` on the other weekdays, asking for no return on Wednesday
- **THEN** the system returns HTTP 201
- **AND** the request stores each day with the period copied from the grid and the legs the client sent

#### Scenario: A day without school

- **WHEN** a client asks for Saturday for a dependent whose grid has no Saturday
- **THEN** the system returns HTTP 422 with the message of `service_request.day.no_school`
- **AND** no service request is created

#### Scenario: Editing the grid does not change the request

- **WHEN** a client changes Thursday from `FULLTIME` to `MORNING` in the grid after sending a request
- **THEN** the request still shows Thursday as `FULLTIME`

#### Scenario: A day that needs no van

- **WHEN** a client sends a day with `needsOutbound = false` and `needsReturn = false`
- **THEN** the system returns HTTP 400
- **AND** no service request is created

#### Scenario: A second open request to the same driver

- **WHEN** a client sends a request to a driver while an earlier request to that driver is `ANSWERED`
- **THEN** the system returns HTTP 409 with the message of `service_request.open_exists`

#### Scenario: The sixth open request

- **WHEN** a client with five open requests to five drivers sends a request to a sixth driver
- **THEN** the system returns HTTP 422 with the message of `service_request.open_limit`

#### Scenario: A pair that already has a contract

- **WHEN** a client sends a request to a driver with whom they have an `ACTIVE` contract
- **THEN** the system returns HTTP 422 with the message of `service_request.link.has_contract`

#### Scenario: Editing the dependent does not change the request

- **WHEN** a client changes a dependent's school after sending a request for that dependent
- **THEN** the request item still shows the school it was sent with

#### Scenario: Removing a sibling after the request closed

- **WHEN** a client removes one of the two dependents of a `WITHDRAWN` request
- **THEN** both parties still read the request with its two items
- **AND** the removed dependent's item shows the name and age it was sent with

### Requirement: The driver sees only what is needed to price

The driver's view of a request MUST include the request token, status, `expiresAt`, `desiredStartDate`, `notes`, the client's first name and, per item, the dependent token, the dependent's first name and age in whole years, school name, pickup city and neighborhood, and the days.

It MUST NOT include the dependent's full name, birth date or document, any pickup street, number, complement, zip code or place id, or the client's document, phone or e-mail. The client's view MUST show each dependent's full name and birth date as sent, and the driver's current rating (`driverRating`, the average the driver ratings keep, `null` before the first one).

#### Scenario: The driver reads a request

- **WHEN** a driver reads a request for a dependent named "Lucas Souza", born ten years ago, living on "Rua A, 10"
- **THEN** the item shows `dependentFirstName = "Lucas"` and `dependentAge = 10`
- **AND** the response contains neither "Souza", the birth date, nor "Rua A"

### Requirement: The lists filter by one or more statuses

The `status` filter of both lists MUST accept one or more statuses, comma-separated (`?status=OPEN,ANSWERED`) or with the parameter repeated (`?status=OPEN&status=ANSWERED`), and return only the requests in any of them. Without the filter, or with an empty one, the list MUST return every status. A status that does not exist MUST return HTTP 400.

#### Scenario: The client's open tab

- **WHEN** a client with one `OPEN`, one `ANSWERED` and one `DECLINED` request lists `GET /api/clients/me/service-requests?status=OPEN,ANSWERED`
- **THEN** the page holds the `OPEN` and the `ANSWERED` requests only

#### Scenario: A list with an unknown status

- **WHEN** a client lists `?status=OPEN,PENDING`
- **THEN** the system returns HTTP 400

### Requirement: Each request carries its current proposal

Every request in both views, in the lists and in the detail, MUST carry `currentProposal`: the `SENT` proposal while the request is `ANSWERED`, and the `ACCEPTED` one once it is `CONVERTED`. For any other status it MUST be `null`, including an `OPEN` request that a withdrawn or expired proposal reopened.

The summary MUST hold the proposal `token`, `version`, `status`, `totalAmount`, `installments`, `installmentAmount` (as the proposal computes it), `validUntil`, `startsOn` (the start the driver proposed, which may differ from the request's `desiredStartDate`) and `hasPendingAdjustment`, which is `true` when the `SENT` proposal has at least one adjustment request: a new version answers them, and an accepted proposal has none pending. It MUST carry no dependent data, so the driver's view keeps its limits. A page of requests MUST read the summaries of all its requests at once, not one query per request.

#### Scenario: The client's list shows the proposal waiting for her

- **WHEN** a client lists her requests and one of them is `ANSWERED` by version 2, worth 8,000.00 in 10 installments
- **THEN** that request carries `currentProposal` with `version = 2`, `installmentAmount = 800.00` and `hasPendingAdjustment = false`
- **AND** her `OPEN` requests carry `currentProposal = null`

#### Scenario: The client asked for an adjustment

- **WHEN** the client asks to adjust the `SENT` proposal
- **THEN** both parties' lists show `hasPendingAdjustment = true` for that request
- **AND** after the driver sends a new version, the summary is the new version with `hasPendingAdjustment = false`

#### Scenario: A converted request

- **WHEN** the client accepts the proposal
- **THEN** the `CONVERTED` request carries the `ACCEPTED` proposal as `currentProposal`

### Requirement: Each request carries the pair's conversation

Every request in both views, in the lists and in the detail, MUST carry `conversationToken`: the token of the conversation of the request's client and driver (`conversation-opening`). A new request of the same pair MUST carry the same conversation, and a closed request MUST keep carrying it. It MUST be `null` only when the pair has no conversation. A page of requests MUST read the conversation tokens of all its requests at once, not one query per request.

#### Scenario: Both parties get the same conversation

- **WHEN** a client sends a request to a driver
- **THEN** the client's response and the driver's view of that request carry the same `conversationToken`

#### Scenario: A second request of the pair

- **WHEN** the client withdraws a request and sends a new one to the same driver
- **THEN** both requests carry the same `conversationToken`

### Requirement: The lists filter by conversation

`GET /api/clients/me/service-requests` and `GET /api/drivers/me/service-requests` MUST accept an optional `conversationToken`, combinable with `status`, that narrows the list to the requests of that conversation's pair, newest first. A token of a conversation the caller is not part of, or of no conversation at all, MUST return an empty page, never another pair's requests nor an error that reveals whether the conversation exists.

#### Scenario: From the conversation to its negotiations

- **WHEN** a driver opens a conversation from the inbox and lists the requests with its `conversationToken`
- **THEN** the page holds only the requests of that client to that driver, newest first

#### Scenario: Another pair's conversation

- **WHEN** a client lists the requests with the `conversationToken` of a conversation between another client and a driver
- **THEN** the system returns HTTP 200 with an empty page

### Requirement: Request states and deadline

`ServiceRequestStatus` MUST have `OPEN` (waiting for the driver), `ANSWERED` (a proposal is waiting for the client), `CONVERTED`, `DECLINED`, `EXPIRED` and `WITHDRAWN`. `OPEN` and `ANSWERED` are the open states; the others are terminal.

| From | To | Trigger |
|---|---|---|
| `OPEN` | `ANSWERED` | the driver sends a proposal |
| `ANSWERED` | `OPEN` | the current proposal is withdrawn by the driver or expires; `expiresAt` becomes 72 hours later |
| `ANSWERED` | `CONVERTED` | the client accepts the proposal |
| `OPEN`, `ANSWERED` | `DECLINED` | the driver declines the request, or blocks the link |
| `OPEN`, `ANSWERED` | `WITHDRAWN` | the client withdraws the request, declines the proposal, or blocks the link |
| `OPEN` | `EXPIRED` | `expiresAt` has passed |

Declining or withdrawing a request that is not open MUST return HTTP 409 `service_request.not_open`. Declining or withdrawing an `ANSWERED` request MUST close its `SENT` proposal in the same transaction (`WITHDRAWN` when the driver declines, `DECLINED` when the client withdraws).

The system MUST expire overdue `OPEN` requests by a scheduled sweep whose interval comes from the environment (one minute by default) and that can be turned off by configuration. Each round MUST process at most a batch size per step, also from the environment (200 by default), oldest deadline first; the overdue rows left over MUST be processed by the next rounds. Any write that depends on the request being open MUST also check `expiresAt` itself, so an overdue request is never answered before the sweep runs.

#### Scenario: No answer in 72 hours

- **WHEN** an `OPEN` request reaches `expiresAt` with no proposal
- **THEN** after the next sweep its status is `EXPIRED`
- **AND** the pair can send a new request

#### Scenario: More overdue requests than the batch size

- **WHEN** the batch size is 200 and 250 `OPEN` requests are past `expiresAt`
- **THEN** one round expires the 200 with the oldest `expiresAt`
- **AND** the next round expires the other 50

#### Scenario: The client withdraws while a proposal is waiting

- **WHEN** a client withdraws an `ANSWERED` request
- **THEN** the request becomes `WITHDRAWN` and its `SENT` proposal `DECLINED`

#### Scenario: Declining a converted request

- **WHEN** a driver declines a `CONVERTED` request
- **THEN** the system returns HTTP 409 with the message of `service_request.not_open`
