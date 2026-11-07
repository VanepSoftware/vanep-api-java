# QA — service-request-proposal (constitution rule 55)

Latest run: on the stack tip, `VAN-30/13-pickup-time-and-price`, which contains this branch: every scenario below passed there (see the `qa.md` of PR 13 for the date and the counts). This file keeps the scenarios of PRs 1 to 1.

## How to run

The script inserts contracts directly, backdates links and flips driver availability, so it must only point at a throwaway database seeded on an empty schema. It refuses to start unless the database holds only the seeded link and no service area.

Google Places is replaced by `places-stub.py`, which serves the recorded fixtures under `src/test/resources/fixtures/places`. The API gets a fake key and the stub's address, so the run never spends Places quota (rule 50).

```bash
# 1. Throwaway Postgres (never the shared dev database)
docker run -d --rm --name vanep-srp-qa-db -e POSTGRES_USER=vanep -e POSTGRES_PASSWORD=vanep \
  -e POSTGRES_DB=vanep -p 127.0.0.1:5433:5432 postgres:17-alpine

# 2. The Places stub
python3 -I scripts/qa/service-request-proposal/places-stub.py 18090 &

# 3. The API from the branch, on 8081, with the seed on (clients, approved drivers, password "password")
./mvnw -DskipTests package
set -a; . ./.env; set +a
SPRING_PROFILES_ACTIVE=local SERVER_PORT=8081 POSTGRES_PORT=5433 POSTGRES_USER=vanep \
  POSTGRES_PASSWORD=vanep POSTGRES_DB=vanep VANEP_MAIL_ENABLED=false VANEP_SEED_ENABLED=true \
  EXPIRY_SWEEP_ENABLED=false VANEP_OAUTH_ISSUER= GOOGLE_PLACES_BASE_URL=http://127.0.0.1:18090 GOOGLE_PLACES_API_KEY=qa-fake-key \
  java -jar target/vanep-0.0.1-SNAPSHOT.jar

# 4. The QA (needs curl, jq and docker), once the seed log shows both the cities and the trips
QA_DB_CONTAINER=vanep-srp-qa-db BASE_URL=http://localhost:8081 scripts/qa/service-request-proposal/qa.sh

# 8. Clean up
docker stop vanep-srp-qa-db; kill %1
```

Tokens come from the mobile password grant (`urn:vanep:params:oauth:grant-type:password` on `/oauth2/token`) for the admin, the clients and the drivers. The script stops at the first mismatch with a non-zero exit. The generic helpers (`request`, `expect`, `check`, `sql`, `login`, `in_parallel`…) live in `scripts/qa/lib.sh`, shared with the `contract-core` script.

## Setup

| Link | Contract | Link status |
|---|---|---|
| Bruno – Gustavo | `ACTIVE` | `ACTIVE` |
| Bruno – Helena | `ENDED` | `INACTIVE` |
| Carla – Gustavo | `CANCELLED` | `INACTIVE` |
| Carla – Helena | `ACTIVE`, deleted by the admin during the run (R12) | `ACTIVE` |
| Diego – Gustavo | none | `PENDING` |
| Elena – Helena | `ACTIVE`, link just created | `ACTIVE` |

For the approval scenarios (V1 to V12), Fabio is put back `UNDER_REVIEW` with `is_available = false` by SQL, as a driver who just finished the onboarding. The cleanup restores every seeded driver to `APPROVED` and available.

## Results

### Ratings require a contract that reached ACTIVE
| id | scenario | expected | result |
|---|---|---|---|
| R1 | status with an ACTIVE contract | 200 | PASS |
| R2 | the client rates the driver of an ACTIVE contract, rating at the maximum | 201 | PASS |
| R3 | after rating, the button goes away | 200 | PASS |
| R4 | rating the same link again | 409 | PASS |
| R5 | the driver rates the client of an ACTIVE contract, rating at the minimum | 201 | PASS |
| R6 | status of an INACTIVE link whose contract ENDED | 200 | PASS |
| R7 | the client rates the driver after the contract ENDED | 201 | PASS |
| R8 | the driver rates the client after the contract ENDED | 201 | PASS |
| R9 | a CANCELLED contract does not let the client rate | 422 | PASS |
| R10 | a CANCELLED contract does not let the driver rate | 422 | PASS |
| R11 | status with only a CANCELLED contract | 200 | PASS |
| R12 | the admin deletes the only ACTIVE contract of a link | 204 | PASS |
| R13 | a deleted contract does not let the client rate | 422 | PASS |
| R14 | a deleted contract does not let the driver rate | 422 | PASS |
| R15 | status of a PENDING link with no contract | 200 | PASS |
| R16 | no contract: the client cannot rate | 422 | PASS |
| R17 | no contract: the driver cannot rate | 422 | PASS |
| R18 | a third client with no link to the driver | 404 | PASS |
| R19 | a third driver with no link to the client | 404 | PASS |
| R20 | an ACTIVE contract on a link younger than five minutes | 422 | PASS |
| R21 | the client rating has no minimum link age | 201 | PASS |
| R22 | refused ratings stored nothing (driver, client) | 3 4 | PASS |

### Ratings — misuse
| id | scenario | expected | result |
|---|---|---|---|
| M1 | no token | 401 | PASS |
| M2 | invalid token | 401 | PASS |
| M3 | a driver cannot rate a driver | 403 | PASS |
| M4 | a client cannot rate a client | 403 | PASS |
| M5 | rating just above the maximum | 400 | PASS |
| M6 | rating just below the minimum | 400 | PASS |
| M7 | rating with the wrong JSON type | 400 | PASS |
| M8 | malformed JSON | 400 | PASS |
| M9 | missing driverToken | 400 | PASS |
| M10 | unknown driver | 404 | PASS |
| M11 | a driver cannot ask the client's rating status | 403 | PASS |
| M12 | misuse stored nothing | 3 4 | PASS |

### Search and recommended show only drivers accepting new students
| id | scenario | expected | result |
|---|---|---|---|
| A1 | Gustavo covers Taguatinga | 200 | PASS |
| A2 | Helena covers Taguatinga | 200 | PASS |
| A3 | both available drivers are found, and counted | 200 | PASS |
| A4 | the three seeded drivers are recommended | 200 | PASS |
| A5 | Helena stops accepting new students | 200 | PASS |
| A6 | is_available is stored false | f | PASS |
| A7 | search leaves Helena out and the total does not count her | 200 | PASS |
| A8 | the second page of size 1 is empty | 200 | PASS |
| A9 | recommended leaves Helena out and counts two | 200 | PASS |
| A10 | Helena's profile stays reachable and says she is not available | 200 | PASS |
| A11 | another driver reads the same profile | 200 | PASS |
| A12 | another user cannot turn her availability back on | 403 | PASS |
| A13 | availability missing | 400 | PASS |
| A14 | availability with the wrong JSON type | 400 | PASS |
| A15 | the refused updates left her unavailable | f | PASS |
| A16 | still hidden after the refused updates | 200 | PASS |
| A17 | Helena accepts new students again | 200 | PASS |
| A18 | and she is back in the search | 200 | PASS |
| A19 | search without token | 401 | PASS |
| A20 | recommended with an invalid token | 401 | PASS |

### Approval makes the driver available
| id | scenario | expected | result |
|---|---|---|---|
| V1 | a driver under review is not recommended | 200 | PASS |
| V2 | the admin rejects the driver | 200 | PASS |
| V3 | a rejected driver stays unavailable | f | PASS |
| V4 | the driver cannot approve themselves | 403 | PASS |
| V5 | a client cannot approve a driver | 403 | PASS |
| V6 | approving without token | 401 | PASS |
| V7 | the refused approvals changed nothing | UNDER_REVIEW false | PASS |
| V8 | the admin approves the driver, who becomes available | 200 | PASS |
| V9 | is_available is stored true | t | PASS |
| V10 | the approved driver is recommended right away | 200 | PASS |
| V11 | approving an approved driver again | 400 | PASS |
| V12 | approving an unknown driver | 404 | PASS |

### Regression: contract-core

`scripts/qa/contract-core/qa.sh`, now sourcing `scripts/qa/lib.sh`, passed 269/269 against `main` before this change and 267/267 against this branch. Its B13 now expects the new message (`driver_rating.link.never_contracted`): the scenario is the same (a link without contract cannot rate), and only the text changed in PR 1.

## Behaviors worth knowing

- The client rating keeps no minimum link age (R21): only the driver rating has the five-minute rule, as before.
- The link status does not decide eligibility: an `INACTIVE` link whose contract ended still allows rating (R6 to R8).
- The profile of a driver who is not accepting new students stays reachable and reports `available = false` (A10, A11); only search and recommended hide them.
- Approving a driver turns `is_available` on in the same write, so an approved driver shows up in search and recommended right away (V8 to V10). A rejected driver stays unavailable (V3).
