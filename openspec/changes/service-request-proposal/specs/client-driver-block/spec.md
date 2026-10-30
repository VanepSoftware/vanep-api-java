## Purpose

Either party of a link can block the other, the block records who did it, and only that party can lift it, so a block always has an owner and a way out.

## ADDED Requirements

### Requirement: A party blocks the link

`POST /api/client-drivers/{token}/block` MUST be allowed only to the client or the driver of the link. The admin and any other user MUST get HTTP 403. On success the link becomes `BLOCKED`, `blocked_by` stores the caller's user and `blocked_at` the time, and the response says which party blocked (`blockedBy`: `CLIENT` or `DRIVER`).

Blocking MUST be refused with HTTP 422 `client_driver.block.has_contract` while the link has a contract that is `SIGNED`, `ACTIVE` or `SUSPENDED`; that relationship ends through the contract, not through a block. Blocking a link that is already `BLOCKED` MUST return HTTP 409 `client_driver.already_blocked`.

Blocking MUST close the pair's open negotiation in the same transaction:

| Who blocks | Open service request | `SENT` proposal |
|---|---|---|
| client | `WITHDRAWN` | `DECLINED` |
| driver | `DECLINED` | `WITHDRAWN` |

#### Scenario: A client blocks a driver during a negotiation

- **WHEN** the client of a link with an `ANSWERED` service request and a `SENT` proposal blocks the link
- **THEN** the system returns HTTP 200 with `status = BLOCKED` and `blockedBy = CLIENT`
- **AND** the service request becomes `WITHDRAWN` and the proposal `DECLINED`

#### Scenario: Blocking with a running contract

- **WHEN** a party blocks a link that has an `ACTIVE` contract
- **THEN** the system returns HTTP 422 with the message of `client_driver.block.has_contract`
- **AND** the link status does not change

#### Scenario: Someone outside the link cannot block it

- **WHEN** a client who is not part of the link calls block on it
- **THEN** the system returns HTTP 403

### Requirement: Only the blocker unblocks

`POST /api/client-drivers/{token}/unblock` MUST be allowed only to the party who blocked. The other party MUST get HTTP 403 `client_driver.unblock.not_blocker`; users outside the link MUST get HTTP 403. Unblocking a link that is not `BLOCKED` MUST return HTTP 409 `client_driver.not_blocked`.

On success `blocked_by` and `blocked_at` are cleared and the status becomes the one derived from the link's contracts (`PENDING` with no contract, `INACTIVE` with contracts none of which is signed and not ended).

#### Scenario: The blocker unblocks

- **WHEN** the driver who blocked a link with no contract unblocks it
- **THEN** the system returns HTTP 200 with `status = PENDING`

#### Scenario: The blocked party cannot unblock

- **WHEN** the client of a link blocked by its driver calls unblock
- **THEN** the system returns HTTP 403 with the message of `client_driver.unblock.not_blocker`
- **AND** the link stays `BLOCKED`

### Requirement: A blocked pair cannot negotiate

Creating a service request for a pair whose link is `BLOCKED` MUST return HTTP 422 `service_request.link.blocked`, with the same message whichever party blocked.

#### Scenario: Request to a driver who blocked the client

- **WHEN** a client sends a service request to a driver who blocked their link
- **THEN** the system returns HTTP 422 with the message of `service_request.link.blocked`
- **AND** no service request is created
