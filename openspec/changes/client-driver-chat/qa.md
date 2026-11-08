# QA — client-driver-chat (constitution rule 55)

Latest run: API built from `VAN-30/12-conversation-on-request` (service-request-proposal PR 12), 2026-10-26: **56/56 passed** (36 HTTP, 9 encryption at rest, 11 realtime). That PR removed `POST /api/conversations`: a conversation now opens with the pair's first service request (`conversation-opening`, D23 of `service-request-proposal`). The script inserts its three conversations by SQL, and the open section keeps M1 (no token, 401) and gains M35 (the client gets 404 and nothing is created). H1–H3 and M2–M12 left with the endpoint; the open, its races and its refusals are in `scripts/qa/service-request-proposal/conversation.sh`.

Previous run: API built from `feat/161-chat-client-inbox`, which contains phases B1–B5, so it covers the whole spec. Run date: 2026-10-12. Result: **69/69 passed** (49 HTTP, 9 encryption at rest, 11 realtime) on three consecutive runs.

Earlier runs: B4 passed 68/68 on five consecutive runs.

History: B3 (`feat/161-chat-realtime`) passed 59/59 twice, before the encryption checks existed. During the B4 run, the first two attempts stopped during setup without a message. After the setup steps were changed to fail with an explicit reason, five consecutive runs passed. The most likely cause is an interrupted run that left the seeded driver Helena under review, so the next setup could not find her; the script now reports that case directly.

## How to run

The script wipes the chat tables, so it must only point at a throwaway database. (Before PR 12 it also flipped a seeded driver to `UNDER_REVIEW` for M10.)

```bash
# 1. Throwaway Postgres (never the shared dev database)
docker run -d --rm --name vanep-chat-qa-db -e POSTGRES_USER=vanep -e POSTGRES_PASSWORD=vanep \
  -e POSTGRES_DB=vanep -p 127.0.0.1:5433:5432 postgres:17-alpine

# 2. The API from the branch, on 8081, with the seed on (clients, approved drivers, password "password")
./mvnw -DskipTests package
set -a; . ./.env; set +a
SPRING_PROFILES_ACTIVE=local SERVER_PORT=8081 POSTGRES_PORT=5433 POSTGRES_USER=vanep \
  POSTGRES_PASSWORD=vanep POSTGRES_DB=vanep VANEP_MAIL_ENABLED=false VANEP_SEED_ENABLED=true \
  VANEP_OAUTH_ISSUER= VANEP_CHAT_ENCRYPTION_KEY=$(openssl rand -hex 32) \
  java -jar target/vanep-0.0.1-SNAPSHOT.jar

# 3. The QA (needs curl, jq, docker and Node 22+)
QA_DB_CONTAINER=vanep-chat-qa-db BASE_URL=http://localhost:8081 scripts/qa/client-driver-chat/qa.sh

# 4. Clean up
docker stop vanep-chat-qa-db
```

Tokens come from the mobile password grant (`urn:vanep:params:oauth:grant-type:password` on `/oauth2/token`), so every request carries the real `uid` and the real permission bundles from `role_permissions`. Driver tokens come from `GET /api/drivers/recommended`, as in the app. Actors: clients Ana, Bruno (outsider to Ana's conversation) and Carla; drivers Fabio, Gustavo and Helena.

The script stops at the first mismatch with a non-zero exit. That was checked by running a copy that expected `200` on M33: it failed on M33 with exit code 1.

## Startup and migration

With `VANEP_CHAT_ENCRYPTION_KEY=abc` and with an empty `VANEP_CHAT_ENCRYPTION_KEY=`, the application refused to start (exit 1) with "vanep.chat.encryption-key must be 64 hex characters (openssl rand -hex 32)." The `prod` check against the development key is covered by `ProductionSecretsValidatorTest`, since starting the `prod` profile also needs the production JWK and secrets.

Flyway applied `V53`, `V54` and `V55` on an empty database at boot. `role_permissions` after boot: `CLIENT` has `create_conversation` only, `DRIVER` has `list_driver_conversations` only, `ADMIN` has both, `ASSISTANT` has neither. A decoded driver token carries `list_driver_conversations` and not `create_conversation`.

## Results

### `POST /api/conversations` — open or resume

Run of 2026-10-12. Since PR 12 of `service-request-proposal`, only M1 (no token, 401) remains in the script, next to the new M35 (the client gets 404 and no conversation is created).

| id | scenario | expected | result |
|---|---|---|---|
| H1 | client opens a new conversation (names filled, `lastMessageAt` null) | 201 | PASS |
| H2 | same client and driver resume it, same token | 200 | PASS |
| H3 | another client gets their own conversation | 201 | PASS |
| M1 | no token | 401 | PASS |
| M2 | invalid token | 401 | PASS |
| M3 | a driver cannot open a conversation | 403 | PASS |
| M4 | missing `driverToken` | 400 | PASS |
| M5 | blank `driverToken` | 400 | PASS |
| M6 | null `driverToken` | 400 | PASS |
| M7 | malformed JSON | 400 | PASS |
| M8 | wrong content type (`text/plain`) | 415 | PASS |
| M9 | unknown driver, with the pt-BR message "Motorista não encontrado." | 404 | PASS |
| M10 | driver not searchable (under review) | 404 | PASS |
| M11 | `driverToken` with the wrong JSON type (array) | 400 | PASS |
| M12 | six concurrent opens for the same pair | one 201, five 200, one token | PASS |

### `POST /api/conversations/{token}/messages`

| id | scenario | expected | result |
|---|---|---|---|
| H4 | the client sends (`sender` CLIENT, ISO `sentAt` in UTC) | 201 | PASS |
| H5 | the driver replies (`sender` DRIVER) | 201 | PASS |
| H6 | exactly 2000 characters | 201 | PASS |
| H7 | emoji and accents round-trip unchanged | 201 | PASS |
| H8 | markup is stored as plain text, verbatim | 201 | PASS |
| M13 | 2001 characters | 400 | PASS |
| M14 | only whitespace | 400 | PASS |
| M15 | empty string | 400 | PASS |
| M16 | missing `body` | 400 | PASS |
| M17 | null `body` | 400 | PASS |
| M18 | `body` as an object | 400 | PASS |
| M19 | malformed JSON | 400 | PASS |
| M20 | another client cannot write here | 403 | PASS |
| M21 | another driver cannot write here | 403 | PASS |
| M22 | unknown conversation | 403 | PASS |
| M23 | no token | 401 | PASS |
| M24 | the rejected sends above stored nothing | same count before and after | PASS |
| H9 | a later message in another client's conversation | 201 | PASS |

### `GET /api/conversations/{token}/messages`

| id | scenario | expected | result |
|---|---|---|---|
| H10 | the client reads the history, newest first | 200 | PASS |
| H11 | the driver reads the same history, both senders present | 200 | PASS |
| H12 | explicit `page=1&size=2` | 200 | PASS |
| M25 | a client `sort` is ignored, order stays newest first | 200 | PASS |
| M26 | `page=-1` falls back to the first page | 200 | PASS |
| M27 | `page=abc&size=xyz` falls back to page 0, size 30 | 200 | PASS |
| M28 | `size=100000` is capped | 200 | PASS |
| M29 | another client cannot read it | 403 | PASS |
| M30 | another driver cannot read it | 403 | PASS |
| M31 | unknown conversation | 403 | PASS |
| M32 | no token | 401 | PASS |

### `GET /api/conversations/me` — each party's conversations

| id | scenario | expected | result |
|---|---|---|---|
| H13 | the driver sees both clients, latest message first | 200 | PASS |
| H14 | another driver sees only their own (no messages yet, `lastMessageAt` null) | 200 | PASS |
| H15 | the client sees only their own conversation, with the driver's name (B5) | 200 | PASS |
| H16 | another client sees only theirs (B5) | 200 | PASS |
| M34 | no token | 401 | PASS |

### Encryption at rest

Checked directly in the database with `psql`. A stored body looks like `v1:EceMXp7abeoyp8iSZa6kJocYpYGZ2YAwgFMe8UKl4zapeLee4mV+NCx93…`.

| id | scenario | expected | result |
|---|---|---|---|
| E1 | the stored body of H4 is ciphertext with the `v1:` prefix, without the plaintext | `v1:...` | PASS |
| E2 | no row in the table holds plaintext (every body has the prefix) | 0 rows | PASS |
| E3a/E3b | the same text ("Oi") sent twice | 201, 201 | PASS |
| E3 | the two identical texts are stored as two different values (random IV) | 2 distinct | PASS |
| E4 | both are read back as "Oi" | 200 | PASS |
| E5 | a row inserted as plaintext (written before encryption) is still readable | 200 | PASS |
| E6 | a tampered ciphertext makes the history fail instead of returning altered text, and the response does not leak plaintext | 500 | PASS |
| E7 | the history is readable again once the tampered row is gone | 200 | PASS |

### WebSocket `/api/ws` (STOMP)

| id | scenario | expected | result |
|---|---|---|---|
| W1 | handshake without a token is refused (HTTP 401) | refused | PASS |
| W2 | handshake with an invalid token is refused | refused | PASS |
| W3 | the driver receives the client message live, same token, sender and `sentAt` as the REST response | MESSAGE | PASS |
| W4 | the sender also receives their own message on the topic | MESSAGE | PASS |
| W5 | a message rejected by REST (blank) is never published | no MESSAGE | PASS |
| W6 | an outsider subscribing to the conversation gets ERROR and no later message | ERROR | PASS |
| W7 | subscribing to a topic outside the conversations is rejected | ERROR | PASS |
| W8 | subscribing to `/topic/conversations/` (no token) is rejected | ERROR | PASS |
| W9 | subscribing to a nested destination under the conversation is rejected | ERROR | PASS |
| W10 | subscribing to a wildcard destination is rejected | ERROR | PASS |
| W11 | a SEND frame from a party is rejected and never reaches the topic | ERROR | PASS |

## Behaviors worth knowing

- **Unknown conversation is 403, not 404 (M22, M31).** `@sec.isConversationParty` runs before the service, and a token that does not exist has no party. The API does not reveal whether a conversation token exists.
- **Paging input is forgiving (M26–M28).** Spring's `Pageable` resolver falls back to the defaults on negative or non-numeric values and caps the page size. Bad paging never errors, and never returns another conversation's data.
- **Markup is data, not HTML (H8).** The body is stored and returned verbatim. The app renders it with `Text`, which does not interpret markup. A future web client must also render it as text.
- **A corrupted ciphertext breaks that conversation's history with a 500 (E6).** GCM refuses altered data on purpose. Only someone with write access to the database, or a changed `VANEP_CHAT_ENCRYPTION_KEY`, can cause it. Losing or changing the key makes every stored message unreadable, so the key must be generated once and kept with the other secrets.
- **The sender gets their own message on the topic (W4).** The app deduplicates by message token (mobile spec, D5), so the message shows once.
