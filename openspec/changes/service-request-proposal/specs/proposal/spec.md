## Purpose

The driver answers a service request with a structured, immutable and versioned proposal; the client accepts the current version, declines it or asks for an adjustment. Accepting is the explicit click that closes the agreement on one specific version.

## ADDED Requirements

### Requirement: Proposal surfaces

Proposals MUST be reached only through the `/me` surfaces below, with the caller resolved from the `Authentication`. A caller of the wrong `UserType` MUST get HTTP 403, and a token of another user's request or proposal MUST return HTTP 404.

| Method | Path | Who | Effect |
|---|---|---|---|
| `POST` | `/api/drivers/me/service-requests/{token}/proposals` | driver | send a new version |
| `GET` | `/api/drivers/me/service-requests/{token}/proposals` | driver | the version chain, oldest first |
| `GET` | `/api/drivers/me/proposals/{token}` | driver | read |
| `POST` | `/api/drivers/me/proposals/{token}/withdraw` | driver | withdraw |
| `GET` | `/api/clients/me/service-requests/{token}/proposals` | client | the version chain, oldest first |
| `GET` | `/api/clients/me/proposals/{token}` | client | read |
| `POST` | `/api/clients/me/proposals/{token}/accept` | client | accept |
| `POST` | `/api/clients/me/proposals/{token}/decline` | client | decline, optional `reason` up to 500 characters |
| `POST` | `/api/clients/me/proposals/{token}/adjustments` | client | ask for an adjustment |

There MUST be no endpoint that edits a proposal.

#### Scenario: A client cannot send a proposal

- **WHEN** an authenticated `CLIENT` calls `POST /api/drivers/me/service-requests/{token}/proposals`
- **THEN** the system returns HTTP 403

#### Scenario: Another client's proposal is not found

- **WHEN** client B calls accept on a proposal sent to client A
- **THEN** the system returns HTTP 404
- **AND** the proposal does not change

### Requirement: Sending a proposal

Only the driver of the request MUST be able to send a proposal, and only while the request is open (`OPEN` or `ANSWERED`) and, if `OPEN`, before its `expiresAt`; otherwise HTTP 409 `proposal.request.not_open`.

The body MUST carry:

| Field | Rule (HTTP 400 when broken) |
|---|---|
| `startsOn`, `endsOn` | required; `startsOn` today or later |
| `totalAmount` | required, greater than zero, two decimals |
| `installments` | required, 1 to 12 |
| `dueDay` | required, 1 to 28 |
| `enrollmentFee` | optional, zero or more |
| `adjustmentPolicy` | required, `ProposalAdjustmentPolicy`: `NONE`, `IPCA_AT_RENEWAL` |
| `cancellationPolicy` | required, `ProposalCancellationPolicy`: `NOTICE_30_DAYS`, `NOTICE_30_DAYS_WITH_FEE` |
| `vehicleToken` | required |
| `validDays` | optional, 1 to 7, default 7 |
| `message` | optional, up to 1000 characters; not part of the agreement |
| `items` | required, one per dependent: `dependentToken`, `monthlyAmount` greater than zero, and `slots` with `weekday`, `leg`, `shift` (`OperationShift`), `windowStart` and optional `windowEnd` |

The system MUST refuse, creating nothing:

| Condition | Response |
|---|---|
| `endsOn` not after `startsOn` | HTTP 422 `proposal.period.invalid` |
| period longer than 12 months | HTTP 422 `proposal.period.too_long` |
| items do not cover exactly the request's dependents | HTTP 422 `proposal.items.mismatch` |
| a dependent of the request no longer exists | HTTP 422 `proposal.item.dependent_unavailable` |
| vehicle unknown, inactive or of another driver | HTTP 404 `proposal.vehicle.not_found` |
| invalid slots (duplicate `(weekday, leg)`, `windowEnd` not after `windowStart`) | HTTP 400, as in the contract schedule |

On success the system returns HTTP 201 with `status = SENT` and `validUntil` `validDays` days after creation. The first proposal of a request is `version = 1` with no parent. Each later one is the next version, with the previous proposal of the request as parent; if that previous proposal is `SENT`, it MUST become `SUPERSEDED` in the same transaction. The request becomes `ANSWERED`.

The proposal MUST store a copy of the offered vehicle (plate, brand, model, manufacture year, color and capacity), and both parties MUST see the vehicle from that copy, whatever the driver later does to the vehicle.

#### Scenario: The first proposal

- **WHEN** the driver sends a valid proposal for an `OPEN` request
- **THEN** the system returns HTTP 201 with `version = 1` and `status = SENT`
- **AND** the request becomes `ANSWERED`

#### Scenario: A new version replaces the current one

- **WHEN** the driver sends a proposal for a request whose version 1 is `SENT`
- **THEN** the new proposal has `version = 2` and points to version 1 as parent
- **AND** version 1 becomes `SUPERSEDED`

#### Scenario: Items for a different set of dependents

- **WHEN** the driver sends a proposal with items for only one of the request's two dependents
- **THEN** the system returns HTTP 422 with the message of `proposal.items.mismatch`

#### Scenario: Proposal for an overdue request

- **WHEN** the driver sends a proposal for an `OPEN` request whose `expiresAt` has passed but the sweep has not run
- **THEN** the system returns HTTP 409 with the message of `proposal.request.not_open`

### Requirement: The installment amount comes from the total

Every proposal response, and the summary a request carries, MUST include `installmentAmount`: `totalAmount` divided by `installments`, with the cents truncated. The last installment takes the remainder, so the installments always add up to `totalAmount`. The system MUST NOT reconcile `totalAmount` with the items' `monthlyAmount` (R6 of the `contract-core`): the total is what is billed, and each item's `monthlyAmount` is the list price of that dependent.

#### Scenario: A total that splits evenly

- **WHEN** a proposal is sent with `totalAmount = 8000.00` and `installments = 10`
- **THEN** its `installmentAmount` is `800.00`

#### Scenario: A total that does not split evenly

- **WHEN** a proposal is sent with `totalAmount = 1000.00` and `installments = 3`
- **THEN** its `installmentAmount` is `333.33`, and the last installment would be `333.34`

### Requirement: Only one current version per pair

At most one proposal per link MUST be `SENT` at any time, guaranteed by the database. When two versions are sent concurrently, the one that loses MUST get HTTP 409 `proposal.sent_conflict`. When an accept and a new version race, exactly one MUST win: either the proposal is `ACCEPTED` and the new version is refused with HTTP 409 `proposal.request.not_open`, or the new version is `SENT` and the accept is refused with HTTP 409 `proposal.not_current`.

#### Scenario: Accept races a new version

- **WHEN** the client accepts version 1 at the same moment the driver sends version 2
- **THEN** exactly one of the two requests succeeds and the other returns HTTP 409
- **AND** the pair never has an `ACCEPTED` proposal and a `SENT` proposal for the same request

### Requirement: The client answers the current version

On a `SENT` proposal the client MUST be able to:

- **accept**: only before `validUntil`, otherwise HTTP 409 `proposal.expired`, and only while what was asked and offered still holds (table below). The proposal becomes `ACCEPTED` with `acceptedAt`, and the request `CONVERTED`. No contract is created in this phase and the link status does not change
- **decline**: the proposal becomes `DECLINED`, with the optional reason, and the request `WITHDRAWN`
- **ask for an adjustment**: `field` (`ProposalAdjustmentField`: `PRICE`, `SCHEDULE`, `PERIOD`, `INSTALLMENTS`, `OTHER`), optional `suggestedValue` up to 100 characters and optional `note` up to 500 characters, at least one of the two, otherwise HTTP 422 `proposal.adjustment.empty`. The proposal stays `SENT` and the adjustment appears in the proposal for both parties

Any of the three on a proposal that is not `SENT` MUST return HTTP 409 `proposal.not_current` and change nothing.

Accepting MUST re-check the current records against the copies of the request and the proposal, with the dependents and the vehicle locked, and MUST refuse with HTTP 409 `proposal.accept.stale`, changing nothing, when:

| Changed since the copy | Example |
|---|---|
| a dependent of the request was removed | the client removed one of the siblings |
| a dependent's school, pickup city or pickup neighborhood differs from the request copy | the family moved to another neighborhood after the proposal |
| a requested weekday is no longer in the dependent's school grid, or has another period | Thursday is no longer full time |
| the vehicle was removed, belongs to another driver, or its plate or capacity differs from the proposal copy | the driver changed the van |
| the driver is no longer active and `APPROVED` | the driver was suspended |

Correcting a dependent's name, phone or birth date MUST NOT block the accept.

#### Scenario: The client accepts

- **WHEN** the client accepts a `SENT` proposal before `validUntil`
- **THEN** the system returns HTTP 200 with `status = ACCEPTED` and `acceptedAt`
- **AND** the request becomes `CONVERTED`
- **AND** no contract exists for the link

#### Scenario: Accepting a superseded version

- **WHEN** the client accepts version 1 after version 2 was sent
- **THEN** the system returns HTTP 409 with the message of `proposal.not_current`

#### Scenario: Accepting after the validity

- **WHEN** the client accepts a `SENT` proposal whose `validUntil` has passed but the sweep has not run
- **THEN** the system returns HTTP 409 with the message of `proposal.expired`
- **AND** the proposal does not become `ACCEPTED`

#### Scenario: The client asks to lower the price

- **WHEN** the client asks for an adjustment with `field = PRICE` and `suggestedValue = "320,00"`
- **THEN** the system returns HTTP 201
- **AND** the proposal stays `SENT` and lists the adjustment

#### Scenario: The family moved after the proposal

- **WHEN** the client moves the dependent's address to another neighborhood after the proposal was sent and then accepts it
- **THEN** the system returns HTTP 409 with the message of `proposal.accept.stale`
- **AND** the proposal stays `SENT`

#### Scenario: The driver changed the van after the proposal

- **WHEN** the driver changes the plate of the offered vehicle and the client accepts the proposal
- **THEN** the system returns HTTP 409 with the message of `proposal.accept.stale`
- **AND** the proposal still shows the plate it was sent with

#### Scenario: A corrected name does not block the accept

- **WHEN** the client corrects the dependent's name after the proposal was sent and accepts it
- **THEN** the system returns HTTP 200 with `status = ACCEPTED`

### Requirement: The driver withdraws, and proposals expire

The driver MUST be able to withdraw a `SENT` proposal (HTTP 409 `proposal.not_current` otherwise). A `SENT` proposal past `validUntil` MUST become `EXPIRED` by the scheduled sweep, under the same batch size per round as the requests, oldest `validUntil` first. In both cases the request returns to `OPEN` with `expiresAt` 72 hours later, so the driver can send a new version or let the request expire.

#### Scenario: The driver withdraws to correct the offer

- **WHEN** the driver withdraws a `SENT` proposal
- **THEN** the proposal becomes `WITHDRAWN` and the request `OPEN` with a new `expiresAt`
- **AND** the driver can send the next version

### Requirement: Proposals never change

After creation, a proposal's terms, items, slots, vehicle copy and message MUST never change. Only `status`, `acceptedAt`, the decline reason and, in a later phase, the resulting contract are written afterwards. Each item's schedule MUST be stored in the same `schedule` and `schedule_slot` model the contract item uses.

Both parties MUST see the same proposal, except that the driver sees each dependent by first name only.

#### Scenario: Reading an accepted proposal

- **WHEN** either party reads a proposal after it was accepted
- **THEN** its terms, items and slots are exactly those it was sent with
