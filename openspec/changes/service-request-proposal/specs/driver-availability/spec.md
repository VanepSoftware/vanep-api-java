## Purpose

`driver.is_available` means "accepting new students": a driver who is not accepting does not show up in search and cannot receive service requests, while existing contracts are untouched.

## ADDED Requirements

### Requirement: Search shows only drivers accepting new students

`GET /api/drivers/search` and `GET /api/drivers/recommended` MUST return only drivers with `is_available = true`, in addition to the existing filters (active and `APPROVED`). The page totals MUST count only those drivers.

The public profile `GET /api/drivers/{token}/profile` MUST stay reachable for an unavailable driver and MUST keep reporting `available`.

#### Scenario: An unavailable driver is hidden from search

- **WHEN** an approved driver covering the searched place has `is_available = false`
- **THEN** the search response does not include that driver
- **AND** the total does not count them

#### Scenario: An unavailable driver is hidden from the recommended list

- **WHEN** a client requests the recommended drivers
- **THEN** no driver with `is_available = false` is returned

#### Scenario: The profile of an unavailable driver stays reachable

- **WHEN** a client opens the profile of an approved driver with `is_available = false`
- **THEN** the system returns HTTP 200 with `available = false`

### Requirement: Approval makes the driver available

Approving a driver (`UNDER_REVIEW` → `APPROVED`) MUST set `is_available = true` in the same write. A new driver, a driver under review and a rejected driver MUST stay with `is_available = false`. After approval, only the driver turns availability off or on again.

#### Scenario: A driver is approved

- **WHEN** an admin approves a driver under review
- **THEN** the response has `available = true`
- **AND** the driver appears in the recommended list

#### Scenario: A driver is rejected

- **WHEN** an admin rejects a driver under review
- **THEN** the driver keeps `is_available = false`

### Requirement: An unavailable driver receives no service requests

Creating a service request for a driver with `is_available = false` MUST be refused with HTTP 422 `service_request.driver.not_available`. Turning availability off MUST NOT change open service requests, proposals or contracts.

#### Scenario: Request to an unavailable driver

- **WHEN** a client sends a service request to a driver with `is_available = false`
- **THEN** the system returns HTTP 422 with the message of `service_request.driver.not_available`
- **AND** no service request and no link are created

#### Scenario: Turning availability off keeps the negotiation

- **WHEN** a driver with an open service request turns `is_available` off
- **THEN** the open service request keeps its status
- **AND** the driver can still send a proposal for it
