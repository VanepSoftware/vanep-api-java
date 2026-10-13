# QA — client-driver-chat (constitution rule 55)

Run against the API built from `feat/161-chat-realtime`, which contains phases B1–B3, so this run covers the whole spec. Run date: 2026-10-12. Result: **59/59 passed** (48 HTTP, 11 realtime), and a second run passed again.

## How to run

The script wipes the chat tables and temporarily flips a seeded driver to `UNDER_REVIEW`, so it must only point at a throwaway database.

```bash
# 1. Throwaway Postgres (never the shared dev database)
docker run -d --rm --name vanep-chat-qa-db -e POSTGRES_USER=vanep -e POSTGRES_PASSWORD=vanep \
  -e POSTGRES_DB=vanep -p 127.0.0.1:5433:5432 postgres:17-alpine

# 2. The API from the branch, on 8081, with the seed on (clients, approved drivers, password "password")
./mvnw -DskipTests package
set -a; . ./.env; set +a
SPRING_PROFILES_ACTIVE=local SERVER_PORT=8081 POSTGRES_PORT=5433 POSTGRES_USER=vanep \
  POSTGRES_PASSWORD=vanep POSTGRES_DB=vanep VANEP_MAIL_ENABLED=false VANEP_SEED_ENABLED=true \
  VANEP_OAUTH_ISSUER= java -jar target/vanep-0.0.1-SNAPSHOT.jar

# 3. The QA (needs curl, jq, docker and Node 22+)
QA_DB_CONTAINER=vanep-chat-qa-db BASE_URL=http://localhost:8081 scripts/qa/client-driver-chat/qa.sh

# 4. Clean up
docker stop vanep-chat-qa-db
```

Tokens come from the mobile password grant (`urn:vanep:params:oauth:grant-type:password` on `/oauth2/token`), so every request carries the real `uid` and the real permission bundles from `role_permissions`. Driver tokens come from `GET /api/drivers/recommended`, as in the app. Actors: clients Ana, Bruno (outsider to Ana's conversation) and Carla; drivers Fabio, Gustavo and Helena.

The script stops at the first mismatch with a non-zero exit. That was checked by running a copy that expected `200` on M33: it failed on M33 with exit code 1.

## Migration

Flyway applied `V53` on an empty database at boot. `role_permissions` after boot: `CLIENT` has `create_conversation` only, `DRIVER` has `list_driver_conversations` only, `ADMIN` has both, `ASSISTANT` has neither. A decoded driver token carries `list_driver_conversations` and not `create_conversation`.

## Results

### `POST /api/conversations` — open or resume

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

### `GET /api/conversations/me` — driver inbox

| id | scenario | expected | result |
|---|---|---|---|
| H13 | the driver sees both clients, latest message first | 200 | PASS |
| H14 | another driver sees only their own (no messages yet, `lastMessageAt` null) | 200 | PASS |
| M33 | a client cannot list driver conversations | 403 | PASS |
| M34 | no token | 401 | PASS |

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
- **The sender gets their own message on the topic (W4).** The app deduplicates by message token (mobile spec, D5), so the message shows once.
