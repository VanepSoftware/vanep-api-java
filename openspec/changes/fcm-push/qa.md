# QA — fcm-push, VAN-18 (constitution rule 55)

Latest run: API built from `feat/VAN-18-fcm-push`, 2026-11-22, on a throwaway Postgres 17 with Flyway applying `V78__create_device_token.sql` for real. Real tokens from the mobile password grant for a client (Ana), a driver (Fabio) and the admin.

- **Push disabled** (no `FIREBASE_CREDENTIALS_PATH`): **41/41 passed**.
- **Push enabled, FCM unreachable** (a generated service account whose `token_uri` is `http://localhost:1`, so no request leaves the host): **42/42 passed**.

Real delivery to a device needs the project's service account and is not part of this run (see the PR).

History: the first run failed C1. Five simultaneous registrations of a new token returned one 500 (`duplicate key … device_token_fcm_token_active_key`). Registration now retries once in a new transaction, and the retry becomes an update. The first unreachable run failed P3: `sendEach` reports per-device failures in the batch instead of throwing, so a broken credential failed every push without a log line. Any failed device now logs a warning.

## How to run

The script wipes `device_token`, `conversation` and `conversation_message`, so it must only point at a throwaway database. `local-env.sh` creates one.

```bash
# Push disabled
scripts/qa/fcm-push/local-env.sh start disabled
BASE_URL=http://localhost:8083 QA_DB_CONTAINER=vanep-van18-qa-db \
  QA_API_LOG=$TMPDIR/vanep-van18/api.log QA_FCM_MODE=disabled scripts/qa/fcm-push/qa.sh
scripts/qa/fcm-push/local-env.sh stop

# Push enabled, FCM unreachable
scripts/qa/fcm-push/local-env.sh start unreachable
BASE_URL=http://localhost:8083 QA_DB_CONTAINER=vanep-van18-qa-db \
  QA_API_LOG=$TMPDIR/vanep-van18/api.log QA_FCM_MODE=unreachable scripts/qa/fcm-push/qa.sh
scripts/qa/fcm-push/local-env.sh stop
```

`local-env.sh start` prints the exact `qa.sh` line. It needs curl, jq, docker and openssl.

## Scenarios

Observed column: disabled run / unreachable run.

| id | scenario | request | expected | observed |
|---|---|---|---|---|
| **Authentication** | | | | |
| A1 | register without a token | POST/DELETE /api/devices | 401 | PASS / PASS |
| A2 | register with an invalid token | POST/DELETE /api/devices | 401 | PASS / PASS |
| A3 | unregister without a token | POST/DELETE /api/devices | 401 | PASS / PASS |
| A4 | rejected calls stored nothing | POST/DELETE /api/devices | 0 | PASS / PASS |
| **Register (happy path)** | | | | |
| R1 | client registers an Android device | POST /api/devices | 204 | PASS / PASS |
| R2 | the device belongs to the client | POST /api/devices | ana.souza@seed.vanep.com.br | PASS / PASS |
| R3 | registering again is idempotent | POST /api/devices | 204 | PASS / PASS |
| R4 | still one live row | POST /api/devices | 1 | PASS / PASS |
| R5 | platform follows the latest call | POST /api/devices | IOS | PASS / PASS |
| R6 | last_seen_at moved forward | POST /api/devices | t | PASS / PASS |
| R7 | driver registers a device | POST /api/devices | 204 | PASS / PASS |
| R8 | admin registers a web device | POST /api/devices | 204 | PASS / PASS |
| **Device changes hands** | | | | |
| H1 | driver signs in on the client's device | POST /api/devices | 204 | PASS / PASS |
| H2 | the device moved to the driver | POST /api/devices | fabio.teixeira@seed.vanep.com.br | PASS / PASS |
| H3 | no duplicate row | POST /api/devices | 1 | PASS / PASS |
| **Validation** | | | | |
| V1 | blank fcmToken | POST /api/devices | 400 | PASS / PASS |
| V2 | missing fcmToken | POST /api/devices | 400 | PASS / PASS |
| V3 | missing platform | POST /api/devices | 400 | PASS / PASS |
| V4 | unknown platform | POST /api/devices | 400 | PASS / PASS |
| V5 | platform of the wrong JSON type | POST /api/devices | 400 | PASS / PASS |
| V6 | fcmToken of the wrong JSON type | POST /api/devices | 400 | PASS / PASS |
| V7 | malformed JSON | POST /api/devices | 400 | PASS / PASS |
| V8 | fcmToken one past the 1024 limit | POST /api/devices | 400 | PASS / PASS |
| V9 | rejected bodies stored nothing | POST /api/devices | 0 | PASS / PASS |
| V10 | fcmToken exactly at the limit | POST /api/devices | 204 | PASS / PASS |
| **Concurrency** | | | | |
| C1 | five simultaneous registrations never fail with 5xx | 5× parallel POST /api/devices | no 5xx | PASS / PASS |
| C2 | and leave exactly one live row | 5× parallel POST /api/devices | 1 | PASS / PASS |
| **Unregister** | | | | |
| U1 | client unregisters a device that is no longer theirs | DELETE /api/devices/{fcmToken} | 204 | PASS / PASS |
| U2 | the driver's device is untouched | DELETE /api/devices/{fcmToken} | 1 | PASS / PASS |
| U3 | driver unregisters their device | DELETE /api/devices/{fcmToken} | 204 | PASS / PASS |
| U4 | no live row left | DELETE /api/devices/{fcmToken} | 0 | PASS / PASS |
| U5 | the row is soft deleted, not erased | DELETE /api/devices/{fcmToken} | 1 | PASS / PASS |
| U6 | unregistering twice is harmless | DELETE /api/devices/{fcmToken} | 204 | PASS / PASS |
| U7 | unregistering an unknown token is harmless | DELETE /api/devices/{fcmToken} | 204 | PASS / PASS |
| U8 | a removed token can be registered again | DELETE /api/devices/{fcmToken} | 204 | PASS / PASS |
| U9 | with a fresh live row | DELETE /api/devices/{fcmToken} | 1 | PASS / PASS |
| **Push on a business event** | | | | |
| P1 | the chat message is sent | POST /api/conversations/{token}/messages | 201 | PASS / PASS |
| P2 | disabled: startup warned that push is disabled · unreachable: startup did not warn: push is on | POST /api/conversations/{token}/messages | 1 / 0 | PASS / PASS |
| P3 | disabled: no push was attempted · unreachable: the FCM failure was logged after the response | POST /api/conversations/{token}/messages | 0 / 1 | PASS / PASS |
| P4 | unreachable only: the failure is logged on a push- pool thread | POST /api/conversations/{token}/messages | t | — / PASS |
| P5 | the driver's devices survive a failed or skipped push | POST /api/conversations/{token}/messages | 2 | PASS / PASS |
| P6 | the message was committed regardless of push | POST /api/conversations/{token}/messages | 200 | PASS / PASS |

Evidence from the unreachable run, after P1 returned 201:

```
WARN … [         push-1] b.c.v.n.service.PushNotificationService  : Push notification to user 7 failed on 2 of 2 devices.
```
