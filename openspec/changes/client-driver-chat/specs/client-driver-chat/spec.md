## ADDED Requirements

### Requirement: A conversation is unique per client and driver

The system MUST keep at most one non-deleted conversation per (client, driver) pair. A conversation MUST NOT reference the `client_driver` link, and creating, changing or removing a link MUST NOT create, change or remove a conversation. Conversations and messages MUST be exposed only through opaque `token` identifiers.

#### Scenario: The conversation survives the link

- **WHEN** a client who already has a conversation with a driver gets a `client_driver` link to that driver
- **THEN** the existing conversation and all its messages remain available to both parties

#### Scenario: The database rejects a second conversation for the same pair

- **WHEN** a second non-deleted conversation is inserted for a pair that already has one
- **THEN** the database rejects it through a unique index

### Requirement: Only the client opens a conversation

`POST /api/conversations` MUST require the `create_conversation` permission, which the `CLIENT` bundle holds and the `DRIVER` bundle does not. The body MUST carry the driver's token. The caller's client profile is the client side of the conversation. The target driver MUST be searchable (approved and active, the same rule as `@sec.isSearchableDriver`).

When the pair already has a conversation, the system MUST return it with HTTP 200 instead of creating another. When the pair has none, the system MUST create it and return HTTP 201.

#### Scenario: A client opens a new conversation

- **WHEN** a client with no conversation with a searchable driver calls `POST /api/conversations` with that driver's token
- **THEN** the system returns HTTP 201 with the new conversation's token

#### Scenario: A client reopens an existing conversation

- **WHEN** a client who already has a conversation with the driver calls `POST /api/conversations` with that driver's token
- **THEN** the system returns HTTP 200 with the existing conversation's token
- **AND** no new conversation is stored

#### Scenario: A driver cannot open a conversation

- **WHEN** a user with only the `DRIVER` bundle calls `POST /api/conversations`
- **THEN** the system returns HTTP 403

#### Scenario: A driver that is not searchable cannot be contacted

- **WHEN** a client calls `POST /api/conversations` with the token of a driver that is not approved, not active or does not exist
- **THEN** the system returns HTTP 404 `conversation.driver.not_found`

### Requirement: The driver lists their conversations

`GET /api/conversations/me` MUST require the `list_driver_conversations` permission, which the `DRIVER` bundle holds. It MUST return only the conversations of the caller's driver profile, ordered by the time of the last message, most recent first, with conversations without messages ordered by creation time. Each item MUST carry the conversation token, the client's name and the time of the last message. The query MUST NOT issue one query per conversation.

#### Scenario: The driver sees only their own conversations

- **WHEN** a driver with conversations from two clients calls `GET /api/conversations/me`
- **THEN** the response holds exactly those two conversations
- **AND** no conversation of another driver appears

#### Scenario: The most recent conversation comes first

- **WHEN** client A sent a message after client B to the same driver
- **THEN** the conversation with client A comes before the one with client B

#### Scenario: A client cannot list driver conversations

- **WHEN** a user with only the `CLIENT` bundle calls `GET /api/conversations/me`
- **THEN** the system returns HTTP 403

### Requirement: Only the two parties read and send messages

`GET /api/conversations/{token}/messages` and `POST /api/conversations/{token}/messages` MUST be allowed only to the conversation's client and driver, checked by `@sec.isConversationParty`. Any other caller MUST get HTTP 403.

#### Scenario: An outsider cannot read the conversation

- **WHEN** a user who is neither the client nor the driver of a conversation calls `GET /api/conversations/{token}/messages`
- **THEN** the system returns HTTP 403

#### Scenario: An outsider cannot send a message

- **WHEN** a user who is neither the client nor the driver of a conversation calls `POST /api/conversations/{token}/messages`
- **THEN** the system returns HTTP 403
- **AND** no message is stored

### Requirement: Both parties send text messages

`POST /api/conversations/{token}/messages` MUST store a message with the text, the sender side (`CLIENT` or `DRIVER`, a backed enum resolved from the caller) and the send time, and return HTTP 201 with the stored message. The text MUST NOT be blank and MUST NOT exceed 2000 characters; otherwise the system MUST return HTTP 400 without storing anything. Sending a message MUST update the conversation's last message time.

#### Scenario: The client sends a message

- **WHEN** the conversation's client sends "Tem vaga para a manhã?"
- **THEN** the system returns HTTP 201 with the message, sender `CLIENT`

#### Scenario: The driver replies

- **WHEN** the conversation's driver sends a message to that conversation
- **THEN** the system returns HTTP 201 with the message, sender `DRIVER`

#### Scenario: A blank message is rejected

- **WHEN** a party sends a message whose text is empty or only whitespace
- **THEN** the system returns HTTP 400
- **AND** no message is stored

#### Scenario: A message over the limit is rejected

- **WHEN** a party sends a message with 2001 characters
- **THEN** the system returns HTTP 400
- **AND** no message is stored

### Requirement: The history is paginated, newest first

`GET /api/conversations/{token}/messages` MUST return the conversation's messages paginated and ordered by send time, most recent first. Each message MUST carry its token, text, sender side and send time.

#### Scenario: The first page holds the latest messages

- **WHEN** a conversation has 45 messages and a party requests page 0 with size 30
- **THEN** the response holds the 30 most recent messages, newest first
- **AND** it reports 45 messages in total

### Requirement: Stored messages are delivered in real time

A STOMP endpoint MUST be exposed at `/api/ws`. Its handshake MUST require a valid bearer JWT, the same as any `/api/**` route. After a message is stored, the system MUST publish it, with the same shape the REST response uses, to `/topic/conversations/{token}`. A subscription to `/topic/conversations/{token}` MUST be accepted only when the subscriber is a party of that conversation. Any other subscription destination MUST be rejected.

#### Scenario: The other party receives the message live

- **WHEN** the driver is subscribed to `/topic/conversations/{token}` and the client sends a message to that conversation
- **THEN** the driver receives the message on that topic

#### Scenario: The handshake requires a token

- **WHEN** a client opens `/api/ws` without a bearer token
- **THEN** the handshake is refused with HTTP 401

#### Scenario: An outsider cannot subscribe

- **WHEN** an authenticated user who is not a party of the conversation subscribes to `/topic/conversations/{token}`
- **THEN** the subscription is rejected and no message of that conversation reaches them

#### Scenario: A message is not published when storing fails

- **WHEN** sending a message fails validation or authorization
- **THEN** nothing is published to the conversation's topic

### Requirement: Message bodies are encrypted at rest

The system MUST store every message body encrypted with AES-256-GCM, with a random 96-bit IV per message, in the format `v1:<base64(iv || ciphertext || tag)>`. The plaintext MUST NOT appear in the database. The API MUST return the decrypted text to the parties, so the REST and WebSocket contracts do not change. The key MUST come from `VANEP_CHAT_ENCRYPTION_KEY` (64 hex characters). The application MUST refuse to start without a valid key, and under the `prod` profile it MUST also refuse the development key. A body stored before encryption shipped, without the `v1:` prefix, MUST be returned as stored.

#### Scenario: The database only holds ciphertext

- **WHEN** a party sends "Tem vaga para a manhã?"
- **THEN** the stored body starts with `v1:` and does not contain the plaintext
- **AND** reading the history returns "Tem vaga para a manhã?"

#### Scenario: The same text never produces the same ciphertext

- **WHEN** the same text is sent twice
- **THEN** the two stored bodies differ

#### Scenario: A tampered body is not returned as text

- **WHEN** a stored ciphertext is altered or was written with another key
- **THEN** decrypting it fails instead of returning altered text

#### Scenario: The application does not start without a key

- **WHEN** the application starts with `VANEP_CHAT_ENCRYPTION_KEY` missing or not 64 hex characters
- **THEN** startup fails with a message naming the property

#### Scenario: Production refuses the development key

- **WHEN** the application starts under the `prod` profile with the development key
- **THEN** startup fails

#### Scenario: A message stored before encryption is still readable

- **WHEN** a body without the `v1:` prefix is read
- **THEN** it is returned as stored

