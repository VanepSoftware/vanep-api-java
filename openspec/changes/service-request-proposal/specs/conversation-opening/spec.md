## Purpose

A conversation between a client and a driver opens only with the pair's first service request (the spike's "the request is the first message"), so every conversation has a negotiation behind it and both parties reach it from the request.

## ADDED Requirements

### Requirement: The service request opens the conversation

Sending a service request (`service-request`) MUST open the conversation of the client and the driver, in the same transaction, when the pair has none, and MUST reuse it when the pair already has one. A refused request (HTTP 400, 404, 409 or 422) MUST NOT open a conversation. Concurrent first requests of the same pair MUST leave exactly one conversation.

#### Scenario: The first request opens the conversation

- **WHEN** a client with no conversation with a driver sends that driver a service request
- **THEN** the pair has one conversation, listed in `GET /api/conversations/me` for both parties
- **AND** the driver can send the first message in it

#### Scenario: A refused request opens nothing

- **WHEN** a client sends a request that the system refuses
- **THEN** no conversation is created

#### Scenario: Concurrent first requests

- **WHEN** six first requests of the same pair arrive at the same time
- **THEN** one is created, five get HTTP 409, and the pair has one conversation

### Requirement: No conversation is opened directly

`POST /api/conversations` MUST NOT exist, and no permission MUST allow opening a conversation outside a service request. The `create_conversation` permission MUST be removed from every bundle.

#### Scenario: A client tries to open a conversation directly

- **WHEN** an authenticated client calls `POST /api/conversations` with a driver's token
- **THEN** the system returns HTTP 404 and creates no conversation

### Requirement: The conversation outlives the request

Withdrawing, declining, converting or expiring a request MUST NOT close the pair's conversation: both parties MUST keep reading and sending messages in it.

#### Scenario: Talking after a withdrawal

- **WHEN** the client withdraws the request
- **THEN** both parties still send messages in the pair's conversation

### Requirement: Pairs that already had a request get their conversation

The migration that removes `create_conversation` MUST open the conversation of every pair that has a service request and no conversation, and MUST be idempotent. Conversations opened before it, without a request, MUST stay as they are.

#### Scenario: A request sent before the change

- **WHEN** the migration runs on a database where a pair has a request and no conversation
- **THEN** that pair has one conversation, and running it again adds none
