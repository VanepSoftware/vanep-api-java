## Purpose

Until push notifications exist, the counterpart of each negotiation step that needs their action is told by e-mail, so a request or a proposal does not sit unseen.

## ADDED Requirements

### Requirement: The counterpart is e-mailed when it is their turn

The system MUST send one e-mail, in pt-BR, to the counterpart's account address for each of these events:

| Event | Recipient |
|---|---|
| service request sent | driver |
| service request declined by the driver | client |
| proposal sent (any version) | client |
| adjustment asked | driver |
| proposal accepted | driver |
| proposal declined | driver |

No e-mail is sent for expirations, withdrawals or blocks.

#### Scenario: The driver is told about a new request

- **WHEN** a client sends a service request
- **THEN** the driver receives one e-mail about it

#### Scenario: The client is told about a new version

- **WHEN** the driver sends version 2 of a proposal
- **THEN** the client receives one e-mail about it

### Requirement: E-mail follows the committed state only

The e-mail MUST be sent only after the transaction that produced the event commits. A request that fails or rolls back MUST send nothing. A failure to send MUST NOT fail or roll back the request that produced the event.

#### Scenario: A refused request sends nothing

- **WHEN** a client's service request is refused with HTTP 409
- **THEN** no e-mail is sent

#### Scenario: The mail server is down

- **WHEN** the mail server fails while the driver sends a proposal
- **THEN** the system still returns HTTP 201 and the proposal is stored

### Requirement: E-mails to the driver protect the child's data

An e-mail to the driver MUST identify each dependent by first name only and MUST NOT contain the dependent's full name, birth date or any pickup address detail.

#### Scenario: Request e-mail to the driver

- **WHEN** the driver is e-mailed about a request for "Lucas Souza"
- **THEN** the e-mail contains "Lucas" and does not contain "Souza"
