# QA — service-request-proposal (constitution rule 55)

Latest run: API built from `VAN-30/12-conversation-on-request` (PR 12), with the list filter by conversation. Run date: 2026-10-30. Result: the conversation script **48/48 passed**, and as a regression on the same build `qa.sh` **550/550 passed**.

Previous run: the same branch before the list filter, 2026-10-26: the conversation script **36/36 passed**, and as a regression `qa.sh` **550/550 passed**, the e-mail script **26/26 passed** and the `client-driver-chat` script **56/56 passed** (its 13 scenarios of the direct open left with the `POST`; the open is now covered by the conversation script). The filter touches neither the e-mails nor the chat, so these two were not run again.

Previous run: API built from `VAN-30/11-email` (PR 11), 2026-10-25: **550/550 passed** (66 from PR 1, 42 from PR 2, 105 from PR 3, 15 from PR 4, 27 from PR 5, 24 from PR 6, 89 from PR 7, 9 from PR 8, 137 from PR 9, 36 from PR 10), the sweep script **9/9 passed** (PR 10) and the e-mail script **26/26 passed** (PR 11), plus the `contract-core` script as a regression on the same build, with the sweep on: **267/267 passed**, and the `client-driver-chat` script, since the sweep brings its own scheduler next to the STOMP broker's: **69/69 passed**.

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

# 5. The sweep, on a new throwaway database (steps 1 to 3 again), with the API started with
#    EXPIRY_SWEEP_BATCH_SIZE=2 instead of EXPIRY_SWEEP_ENABLED=false; it takes about three minutes
QA_DB_CONTAINER=vanep-srp-qa-db BASE_URL=http://localhost:8081 scripts/qa/service-request-proposal/sweep.sh

# 6. The e-mails, on a new throwaway database (steps 1 to 3 again), with the API started without
#    EXPIRY_SWEEP_ENABLED=false and its output sent to a file (java -jar ... > app.log 2>&1)
APP_LOG=app.log QA_DB_CONTAINER=vanep-srp-qa-db BASE_URL=http://localhost:8081 scripts/qa/service-request-proposal/email.sh

# 7. The conversation opened by the request, on a new throwaway database (steps 1 to 3 again)
QA_DB_CONTAINER=vanep-srp-qa-db BASE_URL=http://localhost:8081 scripts/qa/service-request-proposal/conversation.sh

# 8. Clean up
docker stop vanep-srp-qa-db; kill %1
```

`qa.sh` runs with the expiry sweep off: several scenarios move a deadline into the past by SQL and check what the parties get before the sweep (E29, X14, Y36), and a round in between would close those rows. OFF1 checks that, off, nothing expires on its own for more than an interval. `sweep.sh` runs with the sweep on, at the default one-minute interval, and a batch of 2 so that one round cannot expire three rows. `email.sh` reads the e-mails from the API log: with `VANEP_MAIL_ENABLED=false`, `MailService` logs each one as `[mail desabilitado] para=… assunto="…"` followed by its HTML, and the script counts these lines per recipient and subject and searches the HTML of the last one.

Tokens come from the mobile password grant (`urn:vanep:params:oauth:grant-type:password` on `/oauth2/token`) for the admin, the clients and the drivers. The script stops at the first mismatch with a non-zero exit. The generic helpers (`request`, `expect`, `check`, `sql`, `login`, `in_parallel`…) live in `scripts/qa/lib.sh`, shared with the `contract-core` script.

## Setup

The admin creates six links through `POST /api/client-drivers`. Their contracts are inserted by SQL, because the admin API only creates `ACTIVE` contracts and cannot reach `CANCELLED`. Each link status is set to match its contracts, and every link except Elena–Helena is backdated one day to clear the five-minute minimum age of the driver rating. Before that, the admin ends the seeded contract (Ana – Fabio, for Lucas): while it is in progress, D17 locks Lucas, and the grid and readiness scenarios edit him. The seed also brings Ana's open service request to Gustavo (PR 6), with the link it created.

| Link | Contract | Link status |
|---|---|---|
| Bruno – Gustavo | `ACTIVE` | `ACTIVE` |
| Bruno – Helena | `ENDED` | `INACTIVE` |
| Carla – Gustavo | `CANCELLED` | `INACTIVE` |
| Carla – Helena | `ACTIVE`, deleted by the admin during the run (R12) | `ACTIVE` |
| Diego – Gustavo | none | `PENDING` |
| Elena – Helena | `ACTIVE`, link just created | `ACTIVE` |

For the origin scenarios (O1 to O28), the admin creates Diego – Helena as `DRIVER_INVITE`, Diego – Fabio as `CLIENT_SEARCH` and Elena – Gustavo through six concurrent requests. The last block (L1 to L5) rebuilds the schema as it was before `V61`: it drops the three columns and both checks, marks six links `BLOCKED` with no blocker (Diego – Fabio also removed), and runs the `V61` file again.

For the school days scenarios (G1 to G24, W1 to W24, K1 to K8), Ana's two seeded dependents come complete from the seed (valid CPF, birth date, gender, school, pickup address and grid): Lucas is full time on Tuesday and Thursday and in the morning on the other weekdays, Marina in the morning from Monday to Friday. Bruno is the other client and Gustavo the driver of the misuse block.

The seed gives Ana a phone and a personal address (PR 6), so that her service request goes out; the readiness scenarios (C1 to C18) clear both by SQL first, so Ana starts with neither, and her dependents complete; Marina lost her pickup address in G14 (removing a dependent removes its address). C7 gives Ana a phone and an address by SQL. The CPF scenarios (F1 to F11) create and remove their own dependents. For the school address scenarios (N1 to N9), Ana resolves the two school fixtures through the Places stub: Colégio Bandeirantes, in São Paulo, has a route, and the Objetivo of Taguatinga has none, so its deepest subdivision stands for the street. N5 removes the Bandeirantes address by SQL and points the school to the fixture name, so the next resolve finds a known school without an address and completes it. The neighborhood scenarios (N10 to N14) read Lucas, Ana's personal address, the seeded contract and the seeded school, which the seed gives an address of its own (Rua Vergueiro, 1000, Liberdade, São Paulo), apart from the dependents' home. The last block (H1 to H6) adds the three district columns back, points two new addresses, a new school and the seeded contract item to a district node, runs the `V63` file again, and restores the item.

For the `V64` scenarios (Q1 to Q15), every insert runs inside a transaction that is rolled back. The requests use Diego – Gustavo and Bruno – Helena, and the items take Lucas's school and the city of his pickup address, with a snapshot built from his name and birth date. The checks are SQL against the tables.

For the `V65` scenarios (P1 to P27), each check runs in a rolled-back transaction that first inserts the requests it needs (Diego – Gustavo `ANSWERED`, Bruno – Helena) and a `SENT` version 1 on Diego – Gustavo; the vehicle is the first seeded one, with its plate and capacity in the snapshot and the resulting contract the seeded one. P10 walks every numeric range to both of its limits on the same proposal; P11 to P19 go one step beyond each. P23 sets the pair index aside inside its transaction: with an empty table the planner has no reason to prefer one `SENT` index over the other.

For the seeded request (S1 to S14), SQL reads the request the seed sent at startup through `ServiceRequestService` (Lucas, Monday to Friday, round trip, to Gustavo) and the proposal Gustavo answered it with through `ProposalService` (PR 8): his van, one item for Lucas with a slot per requested leg, the way back at 12:10 on mornings and 17:20 on full-time days. The request is therefore `ANSWERED` from startup. S3 compares its days with the grid Lucas had at startup, which the G scenarios changed since. S13 and S14 read the same proposal through the request routes, as the request's current proposal: Ana's list filtered by `ANSWERED` and Gustavo's detail.

For the dependent locks (T1 to T18), Ana creates Rafa Souza; SQL gives him Lucas's school and a pickup address of his own, and the API his grid. T2 to T4 use a request inserted by SQL on Ana – Fabio, `OPEN` and then `ANSWERED`, set `WITHDRAWN` before the contract. T5 is a real `POST /api/contracts` by the admin on the same link, whose seeded contract already ended.

For the service request routes (U1 to U35, E1 to E37, J1 to J17), the script adds four approved and available drivers by SQL (Igor, Julia, Kaio and Lara, who never log in), so that Ana can reach five open requests and race on a pair without a link. Ana is the only ready client; Bruno, without phone and address, gets a dependent of his own (Davi) through the API for the readiness refusal. U1 to U5 call the seven routes in a row with each kind of caller. The block, the `ACTIVE` contract, the `ANSWERED` and `CONVERTED` statuses, the overdue deadline and the `SENT` proposals are set by SQL, because the routes that reach them come in later PRs (proposal, block, sweep); each is undone right after its scenario. Marina gets an address and a Monday-to-Friday grid through the API (E2, E3) before the request for the two siblings, and is removed after it is withdrawn (E24).

For the proposal routes (X1 to X15, B1 to B61, Y1 to Y47, Z1 to Z14), Ana first withdraws her open requests and then sends new ones through the API to the three seeded drivers, who answer with their seeded vans (Gustavo `VAN2B34`, Fabio `VAN1A23`, Helena `VAN3C45`). B1 to B7 call the nine routes in a row with each kind of caller, on Gustavo's chain. Ana creates Nina Souza through the API (school, pickup address and grid) for the request for two siblings to Fabio. SQL sets only what no route reaches yet: an `OPEN` request and a `SENT` proposal past their deadlines (X14, Y36, before the sweep of PR 10), Gustavo's van inactive (B54, there is no route for it) and Nina removed during the negotiation (B59, the API refuses to remove a dependent in an open request); each is undone right after its scenario. Helena changes her plate through `PUT /api/vehicles/{token}` and Ana moves Lucas through `PATCH /api/dependent/{token}`, and both are put back afterwards, as are Lucas's name, phone and birth date after Z8. Z13 and Z14 send the accept and the new version as two concurrent calls with each party's token. Y39 to Y47 follow the current proposal in both request lists through the adjustment, the accept, the decline and the withdrawal; for Y44, Helena sends one more version (1,000.00 in three installments, starting a day after the desired date) on the request her withdrawal reopened, and withdraws it at Y47, so the races block starts from the same `OPEN` request.

For the block (BL1 to BL35), every link is one the run already has: Ana – Helena with no contract, Ana – Fabio with only ended contracts (BL24 comes back `INACTIVE`), Ana – Kaio for a request still `OPEN`, and Bruno – Gustavo with its `ACTIVE` contract for the refusal. Each negotiation that a block closes is opened through the API right before it (a request by Ana and a proposal by the driver). BL31 sends three blocks of Ana at once; BL32 and BL33 race Ana's block against Helena's with each party's token. BL35 runs ten rounds of Ana's request to Helena sent at the same time as Helena's block, with Helena unblocking after each round. Every block is lifted by its owner before the next scenario.

For the sweep, `qa.sh` ends with OFF1: an `OPEN` request to Kaio and a `SENT` proposal of Helena past their deadlines by SQL, then 70 seconds of waiting. `sweep.sh` starts from its own throwaway database: the seeded request to Gustavo, `ANSWERED` with its `SENT` proposal, plus requests by Ana to Fabio and Helena; it ends the seeded contract by SQL so that Ana can ask Fabio. Each set of deadlines goes into the past in one SQL statement, so a round sees the whole set or none of it, and the script waits for the round (at most 75 seconds) before checking. SW7 and SW8 pull three deadlines back by three, two and one minutes.

For the e-mails (EM1 to EM26), `email.sh` starts from its own throwaway database, with the sweep on. The seed sends its request and its proposal through the services, so the log already holds one e-mail to Gustavo and one to Ana (EM1). The script ends the seeded contract by SQL so that Ana can ask Fabio, and every step after that goes through the API: Ana's requests to Fabio for Lucas, Fabio's proposals with his van, the adjustment, the declines, the accept, the withdrawals and the block. Only EM21 uses SQL, to move the reopened request's deadline into the past, and then waits for the sweep (at most 75 seconds). The leak check looks for Lucas's surname, his birth date, and the street and zip code of his pickup address.

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

### Weekly school days of a dependent
| id | scenario | expected | result |
|---|---|---|---|
| G1 | the seeded dependent is full time on Tuesday and Thursday, Monday first | 200 | PASS |
| G2 | the other seeded dependent has mornings from Monday to Friday | 200 | PASS |
| G3 | the client replaces the grid out of order and gets it back Monday first | 200 | PASS |
| G4 | reading the grid returns the replaced days | 200 | PASS |
| G5 | the database holds only the replaced days as active | MONDAY:AFTERNOON,THURSDAY:FULLTIME,FRIDAY:AFTERNOON | PASS |
| G6 | the five replaced days were soft-deleted, not removed | 5 | PASS |
| G7 | replacing the grid changed no other field of the dependent | {"token":"403d2a811a8a4336a2e3b17ed","client":{"token":"438845e1aa614c1fbcd065c0d"},"name":"Marina Souza","birthDate":"2019-09-15","gender":"FEMALE","document":"45317828791","phone":null,"email":null,"isSelf":false,"isDefault":false,"school":{"token":"cd45c837648a40e588c3bc001","name":"Escola Municipal Seed","address":null},"address":{"token":"9981eb44adf047c5825ffdd0e","zipCode":"01001000","street":"Rua das Acácias","number":"250","complement":null,"neighborhood":"Centro","cityToken":"bfbf0dd051a040a893ca814ec","cityName":"São Paulo","stateUf":"SP","active":true,"createdAt":"2026-10-24T23:10:46.613208Z"},"createdAt":"2026-10-24T23:10:46.614521Z","updatedAt":"2026-10-24T23:10:46.614521Z"} | PASS |
| G8 | all seven weekdays are accepted | 200 | PASS |
| G9 | the admin reads the grid of any dependent | 200 | PASS |
| G10 | the admin replaces the grid of any dependent | 200 | PASS |
| G11 | an empty list clears the grid | 200 | PASS |
| G12 | the cleared grid reads empty | 200 | PASS |
| G13 | the client sets a grid on the dependent it is about to remove | 200 | PASS |
| G14 | the client removes the dependent | 204 | PASS |
| G15 | the grid of a removed dependent is not found | 404 | PASS |
| G16 | nor can it be replaced | 404 | PASS |
| G17 | the rows of the removed dependent stay as they were | TUESDAY:NIGHT | PASS |
| G18 | the client restores the dependent | 200 | PASS |
| G19 | the restored dependent comes back with its grid | 200 | PASS |
| G20 | six concurrent replacements of the same grid all succeed | 200 200 200 200 200 200 | PASS |
| G21 | and leave exactly one active grid | MONDAY:MORNING,TUESDAY:FULLTIME,WEDNESDAY:MORNING,THURSDAY:FULLTIME,FRIDAY:MORNING | PASS |
| G22 | the dependent has no single shift anymore | 200 | PASS |
| G23 | a shift sent on creation is ignored | 201 | PASS |
| G24 | the client removes the dependent created to check the shift | 204 | PASS |

### Weekly school days — misuse
| id | scenario | expected | result |
|---|---|---|---|
| W1 | reading without a token | 401 | PASS |
| W2 | reading with an invalid token | 401 | PASS |
| W3 | replacing without a token | 401 | PASS |
| W4 | another client cannot read the grid | 403 | PASS |
| W5 | another client cannot replace the grid | 403 | PASS |
| W6 | a driver cannot read the grid | 403 | PASS |
| W7 | a driver cannot replace the grid | 403 | PASS |
| W8 | the grid of an unknown dependent | 404 | PASS |
| W9 | replacing the grid of an unknown dependent | 404 | PASS |
| W10 | the same weekday twice, pt-BR message | 400 | PASS |
| W11 | eight entries, one past the seven weekdays | 400 | PASS |
| W12 | a day without weekday | 400 | PASS |
| W13 | a day without schoolShift | 400 | PASS |
| W14 | an unknown weekday | 400 | PASS |
| W15 | an unknown schoolShift | 400 | PASS |
| W16 | a weekday in lower case | 400 | PASS |
| W17 | a null day | 400 | PASS |
| W18 | days null | 400 | PASS |
| W19 | no days field | 400 | PASS |
| W20 | days as a string | 400 | PASS |
| W21 | days as an object | 400 | PASS |
| W22 | schoolShift as a boolean | 400 | PASS |
| W23 | malformed JSON | 400 | PASS |
| W24 | the refused requests left the grid unchanged | MONDAY:MORNING,TUESDAY:FULLTIME,WEDNESDAY:MORNING,THURSDAY:FULLTIME,FRIDAY:MORNING | PASS |

### V62: dependent_school_day (database checks)
| id | scenario | expected | result |
|---|---|---|---|
| K1 | a second active row for the same weekday | rejected by dependent_school_day_weekday_active_key | PASS |
| K2 | the same weekday once the active row is soft-deleted | accepted | PASS |
| K3 | the same weekday on another dependent | accepted | PASS |
| K4 | a row without school_shift | rejected by "school_shift" | PASS |
| K5 | a row for a dependent that does not exist | rejected by dependent_school_day_dependent_id_fkey | PASS |
| K6 | the table, weekday and school_shift are documented | 3 | PASS |
| K7 | the checks above were rolled back | MONDAY:MORNING,TUESDAY:FULLTIME,WEDNESDAY:MORNING,THURSDAY:FULLTIME,FRIDAY:MORNING | PASS |
| K8 | V62 dropped dependent.shift | 0 | PASS |

### V64: service_request tables (database checks)
| id | scenario | expected | result |
|---|---|---|---|
| Q1 | a second OPEN request of the same pair | rejected by service_request_client_driver_open_key | PASS |
| Q2 | an OPEN request while the pair has an ANSWERED one | rejected by service_request_client_driver_open_key | PASS |
| Q3 | an OPEN request after an EXPIRED one of the same pair | accepted | PASS |
| Q4 | OPEN requests of two different pairs | accepted | PASS |
| Q5 | a repeated token | rejected by service_request_token_key | PASS |
| Q6 | the same weekday twice in one item | rejected by service_request_day_weekday_key | PASS |
| Q7 | a day with neither outbound nor return | rejected by service_request_day_leg_check | PASS |
| Q8 | a day with only the outbound and a day with only the return | accepted | PASS |
| Q9 | the item keeps no district id | 0 | PASS |
| Q10 | an item for a dependent that does not exist | rejected by service_request_item_dependent_id_fkey | PASS |
| Q11 | the overdue sweep reads the partial index | yes | PASS |
| Q12 | the tables, the status and the copied and per-day columns are documented | 8 | PASS |
| Q13 | the checks above were rolled back | 1 | PASS |
| Q14 | the negotiation tables have no deleted_at | 0 | PASS |
| Q15 | a snapshot without the dependent | rejected by service_request_item_snapshot_check | PASS |

### V65: proposal tables (database checks)
| id | scenario | expected | result |
|---|---|---|---|
| P1 | a second SENT proposal of the same pair | rejected by proposal_client_driver_sent_key | PASS |
| P2 | a SENT version after a SUPERSEDED one of the same request | accepted | PASS |
| P3 | SENT proposals of two different pairs | accepted | PASS |
| P4 | a repeated version in the same request | rejected by proposal_request_version_key | PASS |
| P5 | ACCEPTED without accepted_at | rejected by proposal_accepted_at_check | PASS |
| P6 | accepted_at on a SENT proposal | rejected by proposal_accepted_at_check | PASS |
| P7 | ACCEPTED with accepted_at | accepted | PASS |
| P8 | a resulting contract on a proposal that is not ACCEPTED | rejected by proposal_resulting_contract_check | PASS |
| P9 | the same resulting contract on two proposals | rejected by proposal_resulting_contract_key | PASS |
| P10 | every numeric range at its limits | accepted | PASS |
| P11 | version 0 | rejected by proposal_version_check | PASS |
| P12 | installments 0 | rejected by proposal_installments_check | PASS |
| P13 | installments 13 | rejected by proposal_installments_check | PASS |
| P14 | due day 0 | rejected by proposal_due_day_check | PASS |
| P15 | due day 29 | rejected by proposal_due_day_check | PASS |
| P16 | total amount 0 | rejected by proposal_total_amount_check | PASS |
| P17 | enrollment fee -0.01 | rejected by proposal_enrollment_fee_check | PASS |
| P18 | ends_on equal to starts_on | rejected by proposal_period_check | PASS |
| P19 | monthly amount 0 | rejected by proposal_item_monthly_amount_check | PASS |
| P20 | two items on the same schedule | rejected by proposal_item_schedule_id_key | PASS |
| P21 | an adjustment with neither value nor note | rejected by proposal_adjustment_content_check | PASS |
| P22 | an adjustment with only a value and one with only a note | accepted | PASS |
| P23 | the overdue sweep can read the partial index on valid_until | yes | PASS |
| P24 | the tables, the status, the snapshot, both contract links and both menus are documented | 10 | PASS |
| P25 | the checks above were rolled back | 1 | PASS |
| P26 | the proposal tables have no deleted_at | 0 | PASS |
| P27 | a snapshot without the vehicle | rejected by proposal_snapshot_check | PASS |

### Contract readiness
| id | scenario | expected | result |
|---|---|---|---|
| C1 | the seeded client misses phone and address; the restored dependent misses its address | 200 | PASS |
| C2 | client entries carry no dependentToken | yes | PASS |
| C3 | the tokens separated by a comma give the same answer | 200 | PASS |
| C4 | a token sent twice is checked once | 200 | PASS |
| C5 | without dependent tokens only the client is checked | 200 | PASS |
| C6 | clearing the birth date makes the dependent miss it | 200 | PASS |
| C7 | with phone, address and a complete dependent the client is ready | 200 | PASS |
| C8 | clearing the grid makes the dependent miss the school days | 200 | PASS |
| C9 | the client sets the grid back | 200 | PASS |
| C10 | and the dependent is ready again | 200 | PASS |

### Contract readiness — misuse
| id | scenario | expected | result |
|---|---|---|---|
| C11 | no token | 401 | PASS |
| C12 | invalid token | 401 | PASS |
| C13 | a driver cannot ask | 403 | PASS |
| C14 | the admin is not a client | 403 | PASS |
| C15 | another client's dependent | 404 | PASS |
| C16 | an unknown token among known ones | 404 | PASS |
| C17 | the client removes a dependent | 204 | PASS |
| C18 | a removed dependent | 404 | PASS |

### CPF of a dependent
| id | scenario | expected | result |
|---|---|---|---|
| F1 | a CPF with wrong check digits on creation | 400 | PASS |
| F2 | a CPF of repeated digits on creation | 400 | PASS |
| F3 | a CPF one digit short | 400 | PASS |
| F4 | the refused creations stored nothing | 4 | PASS |
| F5 | a masked CPF is stored as digits | 201 | PASS |
| F6 | the same CPF without the mask is a duplicate | 409 | PASS |
| F7 | an invalid CPF on update | 400 | PASS |
| F8 | the refused update kept the stored CPF | 52998224725 | PASS |
| F9 | document null clears the CPF | 200 | PASS |
| F10 | an empty document is stored as no document | 201 | PASS |
| F11 | the client removes the dependents created to check the CPF | 204 | PASS |

### School address on resolve (D21)
| id | scenario | expected | result |
|---|---|---|---|
| N1 | a new school gets the address of its place | 201 | PASS |
| N2 | a place with no route takes its deepest subdivision as the street and its shallowest sublocality as the neighborhood, and the school has no districtName nor districtToken | 201 | PASS |
| N3 | resolving the school again keeps its address | 200 | PASS |
| N4 | no second address was stored for the school | 1 | PASS |
| N5 | a known school without an address is completed on resolve | 200 | PASS |
| N6 | the school now points to the new address | d38950d86f614c71813d7b325 | PASS |
| N7 | the dependent's school comes with its address | 201 | PASS |
| N8 | the dependent list carries the school address | 200 | PASS |
| N9 | the client removes the dependent created for the school | 204 | PASS |

### Neighborhood instead of district (D20)
| id | scenario | expected | result |
|---|---|---|---|
| N10 | the dependent address has a neighborhood and no district | 200 | PASS |
| N11 | the personal address has no districtName nor districtToken | 200 | PASS |
| N12 | the contract item shows the pickup neighborhood and no pickupDistrictName | 200 | PASS |
| N13 | V63 removed address.district_id, contract_item.pickup_district_id and school.district_id | 0 | PASS |
| N14 | the seeded school comes with an address of its own, in its city | 200 | PASS |

### V63 re-applied on rows that pointed to a district
| id | scenario | expected | result |
|---|---|---|---|
| H1 | an address without a neighborhood takes the name of its district | Setor L Norte | PASS |
| H2 | an address with a neighborhood keeps it | Centro | PASS |
| H3 | a contract item without a pickup neighborhood takes the name of its district | Setor L Norte | PASS |
| H4 | a school address without a neighborhood takes the name of the school's district | Setor L Norte | PASS |
| H5 | the three columns are gone again | 0 | PASS |
| H6 | the API reads the contract after the migration ran again | 200 | PASS |

### Seeded service request
| id | scenario | expected | result |
|---|---|---|---|
| S1 | the seed sent one request from Ana to Gustavo, answered by the seeded proposal, on a PENDING link from the search | ANSWERED PENDING CLIENT_SEARCH | PASS |
| S2 | its item copies Lucas, his school, his city and his neighborhood | Lucas Souza|2016-04-02|Escola Municipal Seed|São Paulo|SP|Centro | PASS |
| S3 | the days keep the grid of the sending time, although the grid changed since | MONDAY:MORNING,TUESDAY:FULLTIME,WEDNESDAY:MORNING,THURSDAY:FULLTIME,FRIDAY:MORNING | PASS |
| S4 | every seeded day asks for the way to school and back | t | PASS |
| S5 | it expires 72 hours after it was sent | 72 | PASS |
| S6 | Lucas is not removed while the seeded request is open | 422 | PASS |
| S7 | the seed answered it with one SENT version 1, without a parent, on the same link | 1 SENT 1 true true | PASS |
| S8 | it offers Gustavo's van, copied with its plate and capacity | VAN2B34 true true | PASS |
| S9 | one item for Lucas, with the schedule built from the requested grid | 1 10 MONDAY:OUTBOUND:06:40,MONDAY:RETURN:12:10,TUESDAY:OUTBOUND:06:40,TUESDAY:RETURN:17:20 | PASS |
| S10 | valid for seven days, ten installments of the item's monthly amount | 7 10 4200.00 420.00 | PASS |
| S11 | the period starts on the desired start and stays within twelve months | true true | PASS |
| S12 | no contract came from it, and the link did not change | 0 PENDING | PASS |
| S13 | Ana's list carries the seeded proposal as the request's current one: ten installments of 420.00, no adjustment pending | 200 | PASS |
| S14 | Gustavo reads the same summary in the detail | 200 | PASS |

### Dependent locks (D17)
| id | scenario | expected | result |
|---|---|---|---|
| T1 | a new dependent gets school, address and grid | 200 | PASS |
| T2 | a dependent in an OPEN request is not removed | 422 | PASS |
| T3 | during the negotiation the grid still changes (the accept re-checks it) | 200 | PASS |
| T4 | a dependent in an ANSWERED request is not removed | 422 | PASS |
| T5 | after the request is withdrawn, the admin contracts the dependent | 201 | PASS |
| T6 | a dependent with an ACTIVE contract is not removed | 422 | PASS |
| T7 | not even by the admin | 422 | PASS |
| T8 | the school is locked, even resent with its current value | 422 | PASS |
| T9 | the address is locked | 422 | PASS |
| T10 | clearing the address is locked too | 422 | PASS |
| T11 | the grid is locked | 422 | PASS |
| T12 | the name still changes | 200 | PASS |
| T13 | the dependent kept its school, address and grid, and was not removed | t t t MONDAY:MORNING,TUESDAY:FULLTIME | PASS |
| T14 | the contract still lists the dependent's item | 200 | PASS |
| T15 | the contract ends | 200 | PASS |
| T16 | two removals at once after the contract ended: one 204, one 404 | 204 404 | PASS |
| T17 | the ended contract stays readable, without the removed dependent's item until phase 3 (R14) | 200 | PASS |
| T18 | another client still cannot remove the dependent | 403 | PASS |

### Service requests over HTTP — misuse
| id | scenario | expected | result |
|---|---|---|---|
| U1 | the seven routes without a token | 401 401 401 401 401 401 401 | PASS |
| U2 | the seven routes with an invalid token | 401 401 401 401 401 401 401 | PASS |
| U3 | the admin on both surfaces | 403 403 403 403 403 403 403 | PASS |
| U4 | a driver: the client routes are forbidden, another driver's request is not found | 403 403 403 403 200 404 404 | PASS |
| U5 | another client: Lucas and Ana's request are not found, the driver routes are forbidden | 404 200 404 404 403 403 403 | PASS |
| U6 | without driverToken | 400 | PASS |
| U7 | a blank driverToken | 400 | PASS |
| U8 | without desiredStartDate | 400 | PASS |
| U9 | desiredStartDate yesterday | 400 | PASS |
| U10 | desiredStartDate that is not a date | 400 | PASS |
| U11 | notes with 1001 characters | 400 | PASS |
| U12 | an empty item list | 400 | PASS |
| U13 | without items | 400 | PASS |
| U14 | items that are not a list | 400 | PASS |
| U15 | an item with no days | 400 | PASS |
| U16 | a blank dependentToken | 400 | PASS |
| U17 | a day without needsReturn | 400 | PASS |
| U18 | needsOutbound that is not a boolean | 400 | PASS |
| U19 | a weekday that does not exist | 400 | PASS |
| U20 | malformed JSON | 400 | PASS |
| U21 | the same dependent twice | 400 | PASS |
| U22 | the same weekday twice in one item | 400 | PASS |
| U23 | a day that needs no van | 400 | PASS |
| U24 | nothing above created a request or a link, and the seeded request is still ANSWERED | 2 11 ANSWERED | PASS |
| U25 | an unknown driver | 404 | PASS |
| U26 | a driver under review | 404 | PASS |
| U27 | a driver not taking new students | 422 | PASS |
| U28 | an unknown dependent | 404 | PASS |
| U29 | another client's dependent | 404 | PASS |
| U30 | a client without phone and address | 422 | PASS |
| U31 | a dependent without address | 422 | PASS |
| U32 | a day without school | 422 | PASS |
| U33 | a blocked link | 422 | PASS |
| U34 | a pair with an ACTIVE contract | 422 | PASS |
| U35 | no refusal created a request or a link | 2 11 | PASS |

### Service requests over HTTP — client
| id | scenario | expected | result |
|---|---|---|---|
| E1 | Lucas is full time on Tuesday and Thursday | 200 | PASS |
| E2 | Marina gets an address | 200 | PASS |
| E3 | and mornings from Monday to Friday | 200 | PASS |
| E4 | two siblings, Monday to Friday, both ways | 201 | PASS |
| E5 | it expires 72 hours after it was sent, on a new PENDING link from the search | 72 PENDING CLIENT_SEARCH 12 | PASS |
| E6 | full time on some days only, no return on Wednesday | 201 | PASS |
| E7 | the ended pair's link is reused, with its origin | cfbdafbce82e48d584c1f2813 CLIENT_SEARCH 12 | PASS |
| E8 | editing the grid does not change the request: Thursday is still full time | 200 | PASS |
| E9 | editing the dependent does not change the request: the school it was sent with | 200 | PASS |
| E10 | notes with exactly 1000 characters | 201 | PASS |
| E11 | a second request to a driver whose request is ANSWERED (the seeded one) | 409 | PASS |
| E12 | nothing was created | 5 13 | PASS |
| E13 | the fifth open request | 201 | PASS |
| E14 | the sixth open request | 422 | PASS |
| E15 | the refused request left no link | 0 | PASS |
| E16 | the list shows every request of Ana, newest first | 200 | PASS |
| E17 | filtered by OPEN (the seeded one is ANSWERED) | 200 | PASS |
| E18 | paginated | 200 | PASS |
| E19 | a status that does not exist | 400 | PASS |
| E20 | reads one of her requests | 200 | PASS |
| E21 | an unknown token | 404 | PASS |
| E22 | withdraws the request for the two siblings | 200 | PASS |
| E23 | withdrawing it again | 409 | PASS |
| E24 | Marina is removed after the request closed | 204 | PASS |
| E25 | Ana still reads both items, Marina with the name and birth date she was sent with | 200 | PASS |
| E26 | and so does Helena | 200 | PASS |
| E27 | withdraws a request while a proposal is waiting | 200 | PASS |
| E28 | the proposal is DECLINED | DECLINED | PASS |
| E29 | an OPEN request past its deadline, before the sweep | 409 | PASS |
| E30 | it was not withdrawn | OPEN | PASS |
| E31 | two requests to the same new driver at once: one 201, one 409 | 201 409 | PASS |
| E32 | one link and one request for the pair | 1 1 | PASS |
| E33 | filtered by two statuses, comma-separated | 200 | PASS |
| E34 | and with the parameter repeated | 200 | PASS |
| E35 | a list with a status that does not exist | 400 | PASS |
| E36 | every request in Ana's list carries its driver's rating, as stored | 200 | PASS |
| E37 | and so does the detail | 200 | PASS |

### Service requests over HTTP — driver
| id | scenario | expected | result |
|---|---|---|---|
| J1 | Fabio lists his OPEN requests | 200 | PASS |
| J2 | Fabio reads the client's first name and the dependent's first name and age | 200 | PASS |
| J3 | the response has no surname, birth date, street, zip code, document, phone or e-mail |  | PASS |
| J4 | another driver's request | 404 | PASS |
| J5 | a reason with 501 characters | 400 | PASS |
| J6 | a reason that is not text | 400 | PASS |
| J7 | malformed JSON | 400 | PASS |
| J8 | the request is still OPEN | OPEN | PASS |
| J9 | Fabio declines with a reason | 200 | PASS |
| J10 | Ana reads it declined, with the reason | 200 | PASS |
| J11 | declining it again | 409 | PASS |
| J12 | a reason with exactly 500 characters, on the seeded request | 200 | PASS |
| J13 | the seeded proposal is WITHDRAWN with it | WITHDRAWN | PASS |
| J14 | Helena declines without a body | 200 | PASS |
| J15 | Helena declines while her proposal is waiting | 200 | PASS |
| J16 | her proposal is WITHDRAWN | WITHDRAWN | PASS |
| J17 | declining a CONVERTED request | 409 | PASS |
| J18 | it stays CONVERTED | CONVERTED | PASS |

### Proposals over HTTP — driver
| id | scenario | expected | result |
|---|---|---|---|
| X1 | Gustavo sends the first version | 201 | PASS |
| X2 | the driver's view has no surname |  | PASS |
| X3 | valid for 7 days by default, and the request is ANSWERED | 7 ANSWERED | PASS |
| X4 | a second version for exactly twelve months, at the lower limits (1 installment, due day 1, 1 day) | 201 | PASS |
| X5 | version 2 points to version 1, now SUPERSEDED, and is valid for one day | 896e04d6e3ac4f1ebfdd61658 SUPERSEDED 1 | PASS |
| X6 | Gustavo lists the chain, oldest first | 200 | PASS |
| X7 | Gustavo reads version 2 | 200 | PASS |
| X8 | Gustavo withdraws version 2 to correct the offer | 200 | PASS |
| X9 | the request is OPEN again, for 72 hours | OPEN 72 | PASS |
| X10 | withdrawing it again | 409 | PASS |
| X11 | the next version after the withdrawal, with a zero enrollment fee and no message | 201 | PASS |
| X12 | version 3 points to the withdrawn version 2, which stays WITHDRAWN | 890eab1a990948619129448f3 WITHDRAWN ANSWERED | PASS |
| X13 | a proposal for a request the driver declined | 409 | PASS |
| X14 | a proposal for an OPEN request past its deadline, before the sweep | 409 | PASS |
| X15 | nothing was sent and the request is still OPEN | 0 OPEN | PASS |

### Proposals over HTTP — misuse
| id | scenario | expected | result |
|---|---|---|---|
| B1 | the nine routes without a token | 401 401 401 401 401 401 401 401 401 | PASS |
| B2 | the nine routes with an invalid token | 401 401 401 401 401 401 401 401 401 | PASS |
| B3 | the admin on both surfaces | 403 403 403 403 403 403 403 403 403 | PASS |
| B4 | the client on the driver routes | 403 403 403 403 | PASS |
| B5 | the driver on the client routes | 403 403 403 403 403 | PASS |
| B6 | another driver: the request and the proposal are not found | 404 404 404 404 | PASS |
| B7 | another client: the request and the proposal are not found | 404 404 404 404 404 | PASS |
| B8 | another client's proposal, with its message | 404 | PASS |
| B9 | an unknown proposal | 404 | PASS |
| B10 | a proposal for an unknown request | 404 | PASS |
| B11 | no route changed the chain | 6 0 SUPERSEDED,WITHDRAWN,SENT ANSWERED | PASS |
| B12 | without startsOn | 400 | PASS |
| B13 | startsOn yesterday | 400 | PASS |
| B14 | startsOn that is not a date | 400 | PASS |
| B15 | without endsOn | 400 | PASS |
| B16 | totalAmount zero | 400 | PASS |
| B17 | a negative totalAmount | 400 | PASS |
| B18 | totalAmount with three decimals | 400 | PASS |
| B19 | totalAmount as text | 400 | PASS |
| B20 | zero installments | 400 | PASS |
| B21 | 13 installments | 400 | PASS |
| B22 | installments as text | 400 | PASS |
| B23 | due day 0 | 400 | PASS |
| B24 | due day 29 | 400 | PASS |
| B25 | a negative enrollment fee | 400 | PASS |
| B26 | an adjustment policy that does not exist | 400 | PASS |
| B27 | without cancellationPolicy | 400 | PASS |
| B28 | without vehicleToken | 400 | PASS |
| B29 | a blank vehicleToken | 400 | PASS |
| B30 | validDays 0 | 400 | PASS |
| B31 | validDays 8 | 400 | PASS |
| B32 | a message with 1001 characters | 400 | PASS |
| B33 | an empty item list | 400 | PASS |
| B34 | without items | 400 | PASS |
| B35 | items that are not a list | 400 | PASS |
| B36 | a monthly amount of zero | 400 | PASS |
| B37 | an item without dependentToken | 400 | PASS |
| B38 | an item with no slots | 400 | PASS |
| B39 | a slot without windowStart | 400 | PASS |
| B40 | a shift that does not exist | 400 | PASS |
| B41 | a weekday that does not exist | 400 | PASS |
| B42 | a time that is not a time | 400 | PASS |
| B43 | the same weekday and leg twice | 400 | PASS |
| B44 | a window that ends before it starts | 400 | PASS |
| B45 | malformed JSON | 400 | PASS |
| B46 | no body | 400 | PASS |
| B47 | endsOn on startsOn | 422 | PASS |
| B48 | endsOn before startsOn | 422 | PASS |
| B49 | twelve months and one day | 422 | PASS |
| B50 | one item too many: Lucas twice | 422 | PASS |
| B51 | an item for an unknown dependent instead of Lucas | 422 | PASS |
| B52 | the vehicle of another driver | 404 | PASS |
| B53 | an unknown vehicle | 404 | PASS |
| B54 | an inactive vehicle of his own | 404 | PASS |
| B55 | no refusal sent a version or touched the chain | 6 0 SUPERSEDED,WITHDRAWN,SENT ANSWERED | PASS |
| B56 | every field at its upper limit (12 installments, due day 28, 7 days, 1000 characters) | 201 | PASS |
| B57 | one item too few: only Lucas for Lucas and Nina | 422 | PASS |
| B58 | an item for Nina, who is not in Gustavo's request | 422 | PASS |
| B59 | a dependent of the request removed since (set by SQL: the API refuses it during a negotiation) | 422 | PASS |
| B60 | Fabio's request has no proposal and is still OPEN | 0 OPEN | PASS |
| B61 | a proposal cannot be edited or removed on either surface | 405 405 405 405 405 405 | PASS |

### Proposals over HTTP — client
| id | scenario | expected | result |
|---|---|---|---|
| Y1 | Ana lists the four versions, oldest first, with Lucas's full name | 200 | PASS |
| Y2 | Ana reads the current version, with the vehicle it was sent with | 200 | PASS |
| Y3 | Ana asks to lower the price | 201 | PASS |
| Y4 | and for another time, with a note only | 201 | PASS |
| Y39 | Ana's list carries version 4 as the request's current proposal, with an adjustment pending | 200 | PASS |
| Y40 | and so does Gustavo's | 200 | PASS |
| Y5 | without field | 400 | PASS |
| Y6 | a field that does not exist | 400 | PASS |
| Y7 | a field that is an object | 400 | PASS |
| Y8 | a suggested value with 101 characters | 400 | PASS |
| Y9 | a note with 501 characters | 400 | PASS |
| Y10 | malformed JSON | 400 | PASS |
| Y11 | no body | 400 | PASS |
| Y12 | neither a value nor a note | 422 | PASS |
| Y13 | a blank value and a blank note | 422 | PASS |
| Y14 | the refusals saved no adjustment | 2 | PASS |
| Y15 | a suggested value with exactly 100 characters | 201 | PASS |
| Y16 | a note with exactly 500 characters | 201 | PASS |
| Y17 | Gustavo sees the four adjustments, in order | 200 | PASS |
| Y18 | accepting version 1, superseded | 409 | PASS |
| Y19 | declining version 2, withdrawn | 409 | PASS |
| Y20 | asking to adjust version 3, superseded | 409 | PASS |
| Y21 | a decline reason with 501 characters | 400 | PASS |
| Y22 | a decline reason that is not text | 400 | PASS |
| Y23 | the old versions and the current one did not change | SUPERSEDED,WITHDRAWN,SUPERSEDED,SENT ANSWERED 4 | PASS |
| Y24 | Ana accepts version 4 | 200 | PASS |
| Y25 | the request is CONVERTED, no contract was created and the link status did not change | CONVERTED 0 PENDING | PASS |
| Y41 | the CONVERTED request carries the accepted version, with nothing pending | 200 | PASS |
| Y26 | Ana reads the accepted proposal exactly as it was sent | {
| Y27 | and so does Gustavo | {
| Y28 | accept, decline, adjustment and withdrawal after the accept | 409 409 409 409 | PASS |
| Y29 | a new version for the converted request | 409 | PASS |
| Y30 | Fabio's proposal for both siblings | 200 | PASS |
| Y31 | Fabio sees them by first name only | 200 | PASS |
| Y32 | Ana declines it with a reason | 200 | PASS |
| Y33 | the request is WITHDRAWN | WITHDRAWN | PASS |
| Y42 | and has no current proposal | 200 | PASS |
| Y34 | Fabio reads it declined, with the reason | 200 | PASS |
| Y35 | a new version after the decline | 409 | PASS |
| Y36 | accepting a proposal past its validity, before the sweep | 409 | PASS |
| Y37 | it is still SENT and the request ANSWERED | SENT ANSWERED | PASS |
| Y38 | Helena withdraws it | 200 | PASS |
| Y43 | the request the withdrawal reopened has no current proposal either | 200 | PASS |
| Y44 | Helena sends 1,000.00 in three installments, starting a day after the desired date: each installment is 333.33, the cents truncated | 201 | PASS |
| Y45 | and Ana's list shows the same installment and the start Helena proposed, not the one Ana asked for | 200 | PASS |
| Y46 | the open tab (OPEN and ANSWERED) in one call: a summary on every ANSWERED request, none on the OPEN ones | 200 | PASS |
| Y47 | Helena withdraws it, and the request is OPEN again | 200 | PASS |

### Proposals over HTTP — races and the re-check on accept
| id | scenario | expected | result |
|---|---|---|---|
| Z1 | Helena changes the plate of the offered van | 200 | PASS |
| Z2 | Ana accepts after the plate changed | 409 | PASS |
| Z3 | the proposal is still SENT and shows the plate it was sent with | 200 | PASS |
| Z4 | Ana declines it with a reason of exactly 500 characters | 200 | PASS |
| Z5 | the family moves Lucas to another neighborhood after the proposal | 200 | PASS |
| Z6 | Ana accepts after the move | 409 | PASS |
| Z7 | the proposal stays SENT and the request ANSWERED | SENT ANSWERED | PASS |
| Z8 | Ana corrects Lucas's name, phone and birth date | 200 | PASS |
| Z9 | two accepts at once after the correction: one 200, one 409 | 200 409 | PASS |
| Z10 | the corrected name did not block it: ACCEPTED, the request CONVERTED | ACCEPTED CONVERTED | PASS |
| Z11 | two new versions at once: one 201, one 409 | 201 409 | PASS |
| Z12 | the pair has a single SENT proposal, version 2 | 1 2 | PASS |
| Z13 | Ana accepts while Gustavo sends a new version: exactly one wins, the other gets 409 | ok | PASS |
| Z14 | never an ACCEPTED and a SENT proposal for the request | 1 SENT ANSWERED | PASS |

### Block with an owner (client-driver-block)
| id | scenario | expected | result |
|---|---|---|---|
| BL1 | block and unblock without a token | 401 401 | PASS |
| BL2 | with an invalid token | 401 401 | PASS |
| BL3 | the admin is not a party | 403 403 | PASS |
| BL4 | a client outside the link | 403 403 | PASS |
| BL5 | a driver outside the link | 403 403 | PASS |
| BL6 | an unknown link | 403 403 | PASS |
| BL7 | the link did not change | PENDING - f | PASS |
| BL8 | Ana blocks Helena during a negotiation | 200 | PASS |
| BL9 | the blocker and the time are stored, the request is WITHDRAWN and the proposal DECLINED | BLOCKED CLIENT t WITHDRAWN DECLINED | PASS |
| BL10 | Helena reads that the client blocked | 200 | PASS |
| BL11 | Ana asks Helena for a quote while she blocks her | 422 | PASS |
| BL12 | no request was created | 16 15 | PASS |
| BL13 | Helena, who was blocked, tries to unblock | 403 | PASS |
| BL14 | Helena blocks the blocked link | 409 | PASS |
| BL15 | Ana blocks it again | 409 | PASS |
| BL16 | the link is still blocked by Ana | BLOCKED CLIENT t | PASS |
| BL17 | Ana unblocks a link with no contract | 200 | PASS |
| BL18 | the blocker and the time are cleared | PENDING - f | PASS |
| BL19 | unblocking it again | 409 | PASS |
| BL20 | Fabio blocks Ana during a negotiation | 200 | PASS |
| BL21 | the request is DECLINED, the proposal WITHDRAWN and the accepted one untouched | BLOCKED DRIVER t DECLINED WITHDRAWN ACCEPTED | PASS |
| BL22 | Ana asks Fabio, who blocked her, for a quote: the same message | 422 | PASS |
| BL23 | Ana cannot lift Fabio's block | 403 | PASS |
| BL24 | Fabio unblocks; the link had only ended contracts | 200 | PASS |
| BL25 | Ana blocks a driver whose request is still OPEN | 200 | PASS |
| BL26 | the OPEN request is WITHDRAWN | WITHDRAWN | PASS |
| BL27 | and unblocks it | 200 | PASS |
| BL28 | Bruno blocks a link with an ACTIVE contract | 422 | PASS |
| BL29 | and so does Gustavo | 422 | PASS |
| BL30 | the link stays ACTIVE with no blocker | ACTIVE - f | PASS |
| BL31 | three blocks at once by the same party: one 200, two 409 | 200 409 409 | PASS |
| BL32 | Ana and Helena block each other at once: exactly one wins | ok | PASS |
| BL33 | the negotiation was closed once, with the statuses of the side that won | CLIENT WITHDRAWN DECLINED | PASS |
| BL34 | the winner unblocks | 200 | PASS |
| BL35 | ten rounds of Ana asking Helena for a quote while Helena blocks her: every block gets 200, and the request is refused or closed by it | (none) | PASS |

### Expiry sweep turned off (EXPIRY_SWEEP_ENABLED=false)
| id | scenario | expected | result |
|---|---|---|---|
| OFF1 | 70 seconds later, past more than one interval, nothing expired on its own | OPEN SENT ANSWERED | PASS |

### Expiry sweep (one-minute interval, batch of 2)
| id | scenario | expected | result |
|---|---|---|---|
| SW1 | within one interval the overdue proposal is EXPIRED | EXPIRED | PASS |
| SW2 | its request is OPEN again for 72 hours instead of expiring in the same round | OPEN 72 | PASS |
| SW3 | the overdue OPEN request is EXPIRED in the same round | EXPIRED | PASS |
| SW4 | the request still on time is untouched | OPEN | PASS |
| SW5 | accepting the expired proposal | 409 | PASS |
| SW6 | Ana asks Fabio again: the expired request no longer counts as open | 201 | PASS |
| SW7 | three overdue requests: a round expires only the two oldest | EXPIRED EXPIRED OPEN | PASS |
| SW8 | the next round expires the third | EXPIRED | PASS |
| SW9 | the two rounds ran about one interval apart (55 to 65 seconds) | t | PASS |

### Negotiation e-mails (VANEP_MAIL_ENABLED=false, read from the log)
| id | scenario | expected | result |
|---|---|---|---|
| EM1 | the seed went through the services: Gustavo was told of the request, Ana of the proposal | 1 1 | PASS |
| EM2 | Ana asks Fabio for a quote: one e-mail to Fabio | 1 | PASS |
| EM3 | it names Lucas and Ana by first name | t t | PASS |
| EM4 | and has no surname, birth date, street or zip code | (none) | PASS |
| EM5 | a second request to Fabio while the first is open | 409 | PASS |
| EM6 | the refused request sent nothing (e-mails in the log) | 3 | PASS |
| EM7 | Fabio sends version 1: one more e-mail to Ana | 2 | PASS |
| EM8 | and version 2: another one | 3 | PASS |
| EM9 | Ana's e-mail is about version 2, with Lucas's full name and the total | t t t | PASS |
| EM10 | Ana asks for a lower price | 201 | PASS |
| EM11 | one e-mail to Fabio, with the field and the value | 1 t t | PASS |
| EM12 | and no surname | (none) | PASS |
| EM13 | Ana declines version 2 | 200 | PASS |
| EM14 | one e-mail to Fabio, with the reason | 1 t | PASS |
| EM15 | Fabio declines the next request | 200 | PASS |
| EM16 | one e-mail to Ana, with Fabio's name and the reason | 1 t t | PASS |
| EM17 | Ana accepts a proposal | 200 | PASS |
| EM18 | one e-mail to Fabio, naming Lucas | 1 t | PASS |
| EM19 | and no surname | (none) | PASS |
| EM20 | Fabio withdraws his proposal | 200 | PASS |
| EM21 | the sweep expires the reopened request | EXPIRED | PASS |
| EM22 | neither the withdrawal nor the expiration sent an e-mail (e-mails in the log) | 14 | PASS |
| EM23 | Ana withdraws a request | 200 | PASS |
| EM24 | the withdrawal sent nothing (e-mails in the log) | 15 | PASS |
| EM25 | Ana blocks Fabio during a negotiation | 200 | PASS |
| EM26 | the block sent nothing (e-mails in the log) | 16 | PASS |

### Conversation opened by the request (PR 12)

`conversation.sh` needs a database seeded on an empty schema, like `email.sh`. It ends the seeded contract (Ana – Fabio), wipes the conversations and runs the `V66` file again by hand to check the backfill and its idempotence.

| id | scenario | expected | result |
|---|---|---|---|
| CV1 | no bundle holds `create_conversation` after boot | 0 | PASS |
| CV2 | a client token does not carry it (it carries `list_client_conversations`) | f | PASS |
| CV3 | the seeded request opened Ana's conversation with Gustavo | 1 | PASS |
| CV4 | `V66` opens the conversation of a pair whose request had none | 1 | PASS |
| CV5 | `V66` run again adds nothing | 1 | PASS |
| CV6 | `V66` removes the permission from a bundle that still has it | 0 | PASS |
| CV7 | the seeded request carries that conversation for Ana | 200 | PASS |
| CV8 | and for Gustavo | 200 | PASS |
| CV9 | the client can no longer open one directly | 404 | PASS |
| CV10 | no token | 401 | PASS |
| CV11 | nor can a driver | 404 | PASS |
| CV12 | nothing was created | 1 | PASS |
| CV13 | an invalid request to Helena | 400 | PASS |
| CV14 | a request to an unknown driver | 404 | PASS |
| CV15 | the refused requests opened no conversation | 1 | PASS |
| CV16 | Ana asks Fabio for a quote, with the conversation token | 201 | PASS |
| CV17 | it is the pair's only conversation | 1 | PASS |
| CV18 | Ana's inbox lists it | 200 | PASS |
| CV19 | Fabio's inbox lists it | 200 | PASS |
| CV20 | Fabio reads the request with the same conversation | 200 | PASS |
| CV21 | and finds it in his list with `?conversationToken=` | 200 | PASS |
| CV22 | every request in Ana's list carries its conversation | 200 | PASS |
| CV23 | Fabio writes first, from the request | 201 | PASS |
| CV24 | Ana reads it | 200 | PASS |
| CV25 | Bruno, outside the pair, cannot | 403 | PASS |
| CV26 | Ana withdraws, and the request still carries the conversation | 200 | PASS |
| CV27 | both still talk after the withdrawal | 201 | PASS |
| CV28 | a new request to Fabio keeps the same conversation | 201 | PASS |
| CV29 | still one conversation for the pair | 1 | PASS |
| CV30 | Fabio declines it, with the conversation | 200 | PASS |
| CV31 | and still writes after declining | 201 | PASS |
| CV32 | the history keeps every message | 200 | PASS |
| CV33 | six concurrent first requests to Helena | one 201, five 409 | PASS |
| CV34 | open one conversation | 1 | PASS |
| CV35 | another request while that one is open | 409 | PASS |
| CV36 | opens no other | 1 | PASS |
| CV37 | Ana lists her requests to Fabio from the conversation, newest first | 200 | PASS |
| CV38 | combined with the status | 200 | PASS |
| CV39 | paged | 200 | PASS |
| CV40 | Fabio gets the same requests | 200 | PASS |
| CV41 | Gustavo, with a conversation he is not part of, gets nothing | 200 | PASS |
| CV42 | nor does Bruno | 200 | PASS |
| CV43 | Ana's other conversation lists only the seeded request | 200 | PASS |
| CV44 | an unknown conversation | 200 | PASS |
| CV45 | a blank conversation token | 200 | PASS |
| CV46 | with an invalid status | 400 | PASS |
| CV47 | no token | 401 | PASS |
| CV48 | the driver on the client surface | 403 | PASS |

### Regression: contract-core

`scripts/qa/contract-core/qa.sh`, now sourcing `scripts/qa/lib.sh`, passed 269/269 against `main` before this change and 267/267 against this branch. Its B13 now expects the new message (`driver_rating.link.never_contracted`): the scenario is the same (a link without contract cannot rate), and only the text changed in PR 1. Since PR 2, every link it creates sends `origin`, and V39 blocks its link with a blocker and a block time, as the `V61` checks require. Since PR 3 the seeded dependents carry a school grid, which no `contract-core` scenario reads, and `V62` drops `dependent.shift`: S19 to S21, which tested that field, are gone, S25 checks only `trip.shift`, and S22 re-applies `V56` with the column added back for the duration, because `V56` comments it. `V63` drops `address.district_id`, `contract_item.pickup_district_id` and `school.district_id`, and no `contract-core` scenario reads these columns, `pickupDistrictName` or the school's `districtName`. Since PR 6 the seed sends a service request from Ana to Gustavo, which creates that link: the setup accepts the second seeded link and reuses it as Ana – Gustavo instead of creating it. A1 used to move Marina while she had contracts in progress, which D17 now refuses: A1 checks the refusal, the script ends her `ACTIVE` contracts through the admin, and A2 and A3 move her and check that the contracts still pick up at the old address.

## Behaviors worth knowing

- The client rating keeps no minimum link age (R21): only the driver rating has the five-minute rule, as before.
- The link status does not decide eligibility: an `INACTIVE` link whose contract ended still allows rating (R6 to R8).
- The profile of a driver who is not accepting new students stays reachable and reports `available = false` (A10, A11); only search and recommended hide them.
- Approving a driver turns `is_available` on in the same write, so an approved driver shows up in search and recommended right away (V8 to V10). A rejected driver stays unavailable (V3).
- Two admin creations of the same pair that race get `409 client_driver.duplicate_pair`, not a 500 (O27): the unique pair index decides, and the service turns its violation into the same conflict as the sequential case.
- Jackson is lenient with enums: `"origin": 1` creates a `DRIVER_INVITE` link (the index of the value) and `"DRIVER_INVITE "` with a trailing space is accepted. The scenarios above stick to what the API refuses; tightening enum parsing is left for the global change on validation errors.
- `V61` cannot run twice as it stands (`add column`), so L1 to L5 rebuild the schema before it rather than re-running it on top of itself. Rebuilding drops the stored origins, which is why every link comes back `CLIENT_SEARCH` (L3).
- Six concurrent `PUT`s of the same grid all return 200 and leave one active grid (G20, G21): the replace locks the dependent row, so each one waits for the previous and replaces it. Without it, two of them could insert the same weekdays at once and run into the partial unique index.
- Removing a dependent leaves its grid rows untouched (G17), hidden with the dependent, so the restore brings the grid back (G19).
- A request body that fails Bean Validation on the dependent routes gets the generic `Invalid request content.` detail, not the field message (F1 to F3, F7): the `dependent.document.invalid` key is in place for the global change on validation errors. The duplicate CPF is a service error and carries its pt-BR message (F6).
- The school types the app searches by (`primary_school`, `secondary_school`) are covered by `SchoolResolveServiceTest` only: the Places stub serves recorded fixtures, and none of them is typed only as a primary or secondary school (rule 50 forbids inventing one).
- Before PR 3, removing a dependent removed its pickup address but left `address_id` pointing to it, because Hibernate drops the pending update of an entity it deletes. A restored dependent then passed the readiness with an address that no longer existed. C1 checks the fix: Marina, removed and restored in G14 to G18, misses her address.
- The seed school used to have no address, so every route that returns a school showed `address: null` for it. The seed now stores one (N14), and a database seeded before gets it on the next start.
- The lock on the school, the address and the grid looks at the field, not at its value: resending the current school is refused too (T8). The app sends only the fields the person changed.
- Removing a dependent locks its row, as the service request and the contract item do: two removals at once give one 204 and one 404 (T16).
- Once the contract ended and the dependent left, the contract is still read, but without the dependent's item (T17): `contract_item` points to the dependent by association, and Hibernate hides the removed row. The plain ids come in phase 3 (R14).
- A request body that fails Bean Validation on the service request routes gets the generic `Invalid request content.` detail (U6 to U19, J5): the field keys (`service_request.items.required`, `service_request.notes.too_long`…) are in place for the global change on validation errors. The same dependent twice, the same weekday twice and a day without legs are service errors and carry their pt-BR message (U21 to U23).
- An `OPEN` request past `expiresAt` is already closed for both parties before the sweep runs: withdrawing it gives `409 service_request.not_open` and leaves it `OPEN` for the sweep to expire (E29, E30).
- A `sort` on a field that does not exist (`?sort=bogus`) gives HTTP 500 on the two lists, as on every paginated list of the API (`/api/contracts` included): Spring Data appends the field to the query and Hibernate rejects it. Not covered by the script; it is a change for all the lists at once.
- Sending a new version reads the current `SENT` proposal and the latest version before the conditional write on the request locks it; a version sent by someone else in between makes the supersede or the insert miss, which is `409 proposal.sent_conflict`. Two versions sent one after the other both go through, each superseding the previous one (X4, X11); two sent at once give one 201 and one 409, and the pair keeps a single `SENT` (Z11, Z12).
- A request body that fails Bean Validation on the proposal routes gets the generic `Invalid request content.` detail (B12 to B42, Y5 to Y9, Y21): the field keys (`proposal.starts_on.past`, `proposal.valid_days.invalid`…) are in place for the global change on validation errors. The duplicate slot and the window that ends before it starts come from the schedule service and carry their pt-BR message (B43, B44), as do every 422, 404 and 409 of the service.
- As with the link origin, `"field": 1` in an adjustment is read as the enum index and saved as `SCHEDULE`; Y7 sends an object instead, which the API refuses.
- When the accept and a new version race, either side may win, and the run records only that exactly one did (Z13) and that the request never holds an `ACCEPTED` and a `SENT` proposal at once (Z14). The accept locks the dependents and the vehicle before its conditional writes, so a version sent meanwhile makes the accept miss with `409 proposal.not_current`, and an accept that commits first makes the version miss with `409 proposal.request.not_open`.
- Each request carries `currentProposal` (D22): the `SENT` proposal while `ANSWERED`, the `ACCEPTED` one once `CONVERTED`, and `null` otherwise, also on the `OPEN` request a withdrawal reopened (Y43). A page reads every summary in one query, a projection with the adjustment's `exists` in the `select`, and only for its `ANSWERED` and `CONVERTED` requests (`ServiceRequestServiceTest`).
- `installmentAmount` is `totalAmount / installments` with the cents truncated (Y44: 1,000.00 in three is 333.33); nothing reconciles the total with the items' `monthlyAmount` (R6 of the `contract-core`).
- The summary carries the start the driver proposed (`startsOn`), which can differ from the request's `desiredStartDate` (Y45), and the client's view carries the driver's rating as `driver.rating` stores it (E36, E37).
- The `status` filter of both lists takes several statuses, comma-separated or with the parameter repeated (E33, E34, Y46); one unknown status in the list gives 400 (E35).
- An expired proposal that the sweep has not closed yet can still be withdrawn by the driver (Y38), which reopens the request; the sweep would have done the same.
- The block and the unblock are native conditional `UPDATE`s. In HQL, assigning `blockedBy`, an association to the soft-deletable `UserModel`, makes Hibernate 7 join `users` and write the parameter into the link's id; the native statement writes the `blocked_by` column and keeps `deleted_at is null` in its condition.
- Without a scheduler of its own, `@Scheduled` took the STOMP broker's scheduler, the only `TaskScheduler` in the context (the first run logged the rounds on `MessageBroker-*` threads). `SchedulingConfig` gives the sweep a one-thread `scheduling-1`, and the chat script still passes with the sweep on.
- A block closes the negotiation through the same writes as the withdrawal and the decline, after the conditional write on the link: two blocks at once leave one blocker and one set of closing statuses (BL31 to BL33).
- Sending a request locks the link row (`select ... for no key update of` the link only) before it checks the block, so a request and a block of the same pair run one after the other (BL35). Before the lock, the request read the link without waiting for the block, and a request created at the same moment as a block stayed `OPEN` on the blocked link: a 40-round run on the PR 11 build without the lock (Ana's request to Fabio against Fabio's block) left 2 such requests; the same run with the lock left none, every block won and every request got 422.
- The sweep's first round runs at startup, and the next ones one interval after the previous ended (`fixedDelay`): SW9 measures the gap between two rounds.
- Each e-mail leaves after the commit of the action that caused it (`@TransactionalEventListener(phase = AFTER_COMMIT)`), in a read-only transaction of its own that reads the request and the proposal the same way the recipient's routes do. A failing mail server is logged and leaves the action committed (`NegotiationEmailServiceTest`); an action refused by the service publishes nothing (EM5, EM6).
- The seed goes through `ServiceRequestService` and `ProposalService`, so a local start with the seed on logs two e-mails (EM1).
- The service request and proposal dates are compared with today in São Paulo (`TripService.SERVICE_ZONE`). U9 and B13 build "yesterday" in the same zone: from `current_date`, which Postgres gives in UTC, the first run of PR 11, at 21:10 in São Paulo, sent today's date as yesterday and U9 got 201.
