# QA — service-request-proposal (constitution rule 55)

Latest run: on the stack tip, `VAN-30/13-pickup-time-and-price`, which contains this branch: every scenario below passed there (see the `qa.md` of PR 13 for the date and the counts). This file keeps the scenarios of PRs 1 to 2.

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

For the origin scenarios (O1 to O28), the admin creates Diego – Helena as `DRIVER_INVITE`, Diego – Fabio as `CLIENT_SEARCH` and Elena – Gustavo through six concurrent requests. The last block (L1 to L5) rebuilds the schema as it was before `V61`: it drops the three columns and both checks, marks six links `BLOCKED` with no blocker (Diego – Fabio also removed), and runs the `V61` file again.

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

### Every link has an immutable origin
| id | scenario | expected | result |
|---|---|---|---|
| O1 | the seeded link and the links of the setup were created as CLIENT_SEARCH | 0 | PASS |
| O2 | the admin creates a link with origin DRIVER_INVITE | 201 | PASS |
| O3 | the origin is stored | DRIVER_INVITE | PASS |
| O4 | the client of the link reads its origin | 200 | PASS |
| O5 | the driver's own list carries the origin of every link | 200 | PASS |
| O6 | the admin's page carries the origin of every link | 200 | PASS |
| O7 | the admin creates a link with origin CLIENT_SEARCH | 201 | PASS |

### Link origin — misuse
| id | scenario | expected | result |
|---|---|---|---|
| O8 | no origin | 400 | PASS |
| O9 | origin null | 400 | PASS |
| O10 | unknown origin | 400 | PASS |
| O11 | origin in lower case | 400 | PASS |
| O12 | blank origin | 400 | PASS |
| O13 | origin as a boolean | 400 | PASS |
| O14 | origin as an object | 400 | PASS |
| O15 | malformed JSON | 400 | PASS |
| O16 | a client cannot create a link | 403 | PASS |
| O17 | a driver cannot create a link | 403 | PASS |
| O18 | no token | 401 | PASS |
| O19 | invalid token | 401 | PASS |
| O20 | the refused creations wrote nothing | 10 | PASS |
| O21 | the same pair again with the other origin | 409 | PASS |
| O22 | the existing link keeps DRIVER_INVITE | DRIVER_INVITE | PASS |
| O23 | there is no route that writes the origin | 405 | PASS |
| O24 | the origin did not change | DRIVER_INVITE | PASS |
| O25 | the admin removes the link | 204 | PASS |
| O26 | restoring brings it back with its origin, whatever the body says | 200 | PASS |
| O27 | six concurrent creations of a new pair: one wins, the rest get 409 | 201 409 409 409 409 409 | PASS |
| O28 | and the pair has exactly one link | 1 | PASS |

### V61: origin and block columns (database checks)
| id | scenario | expected | result |
|---|---|---|---|
| D1 | BLOCKED without a blocker | rejected by client_driver_blocked_by_check | PASS |
| D2 | BLOCKED without a block time | rejected by client_driver_blocked_at_check | PASS |
| D3 | a blocker on a link that is not BLOCKED | rejected by client_driver_blocked_by_check | PASS |
| D4 | a block time on a link that is not BLOCKED | rejected by client_driver_blocked_at_check | PASS |
| D5 | BLOCKED with a blocker and a block time | accepted | PASS |
| D6 | a blocker that is not a user | rejected by client_driver_blocked_by_fkey | PASS |
| D7 | a link without origin | rejected by "origin" | PASS |
| D8 | the checks above were rolled back | PENDING CLIENT_SEARCH | PASS |
| D9 | origin, blocked_by and blocked_at are documented | 3 | PASS |

### V61 re-applied on links blocked before the blocker existed
| id | scenario | expected | result |
|---|---|---|---|
| L1 | each legacy BLOCKED link gets the status its contracts give | 02bad9e33db04ac8a1176e781=PENDING,376ed1b721694cdbb2038bfe6=PENDING,78711ae19daa4285aca20c590=INACTIVE,ca28d676994346a58b0c331d7=INACTIVE,e66f629d7f4b4200a5729b526=ACTIVE,fb8d83209b084e9fab1e12e6b=PENDING | PASS |
| L2 | the links that were not BLOCKED keep their status | 249c8ab01e7445b894e0d0c5d=PENDING,93725a1b6a2b40f0be09937ef=PENDING,a19be41714774502978be4ad5=PENDING,cfbdafbce82e48d584c1f2813=INACTIVE,ec031310f4724723990706d69=ACTIVE | PASS |
| L3 | every link, removed ones included, gets CLIENT_SEARCH | 0 0 | PASS |
| L4 | the checks are back | rejected by client_driver_blocked_by_check | PASS |
| L5 | the API reads a link the migration brought back | 200 | PASS |

### Regression: contract-core

`scripts/qa/contract-core/qa.sh`, now sourcing `scripts/qa/lib.sh`, passed 269/269 against `main` before this change and 267/267 against this branch. Its B13 now expects the new message (`driver_rating.link.never_contracted`): the scenario is the same (a link without contract cannot rate), and only the text changed in PR 1. Since PR 2, every link it creates sends `origin`, and V39 blocks its link with a blocker and a block time, as the `V61` checks require.

## Behaviors worth knowing

- The client rating keeps no minimum link age (R21): only the driver rating has the five-minute rule, as before.
- The link status does not decide eligibility: an `INACTIVE` link whose contract ended still allows rating (R6 to R8).
- The profile of a driver who is not accepting new students stays reachable and reports `available = false` (A10, A11); only search and recommended hide them.
- Approving a driver turns `is_available` on in the same write, so an approved driver shows up in search and recommended right away (V8 to V10). A rejected driver stays unavailable (V3).
- Two admin creations of the same pair that race get `409 client_driver.duplicate_pair`, not a 500 (O27): the unique pair index decides, and the service turns its violation into the same conflict as the sequential case.
- Jackson is lenient with enums: `"origin": 1` creates a `DRIVER_INVITE` link (the index of the value) and `"DRIVER_INVITE "` with a trailing space is accepted. The scenarios above stick to what the API refuses; tightening enum parsing is left for the global change on validation errors.
- `V61` cannot run twice as it stands (`add column`), so L1 to L5 rebuild the schema before it rather than re-running it on top of itself. Rebuilding drops the stored origins, which is why every link comes back `CLIENT_SEARCH` (L3).
