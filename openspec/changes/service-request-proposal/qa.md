# QA — service-request-proposal (constitution rule 55)

Latest run: on the stack tip, `VAN-30/13-pickup-time-and-price`, which contains this branch: every scenario below passed there (see the `qa.md` of PR 13 for the date and the counts). This file keeps the scenarios of PRs 1 to 4.

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

For the school days scenarios (G1 to G24, W1 to W24, K1 to K8), Ana's two seeded dependents come complete from the seed (valid CPF, birth date, gender, school, pickup address and grid): Lucas is full time on Tuesday and Thursday and in the morning on the other weekdays, Marina in the morning from Monday to Friday. Bruno is the other client and Gustavo the driver of the misuse block.

For the `V64` scenarios (Q1 to Q15), every insert runs inside a transaction that is rolled back. The requests use Diego – Gustavo and Bruno – Helena, and the items take Lucas's school and the city of his pickup address, with a snapshot built from his name and birth date. The checks are SQL against the tables.

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

### Regression: contract-core

`scripts/qa/contract-core/qa.sh`, now sourcing `scripts/qa/lib.sh`, passed 269/269 against `main` before this change and 267/267 against this branch. Its B13 now expects the new message (`driver_rating.link.never_contracted`): the scenario is the same (a link without contract cannot rate), and only the text changed in PR 1. Since PR 2, every link it creates sends `origin`, and V39 blocks its link with a blocker and a block time, as the `V61` checks require. Since PR 3 the seeded dependents carry a school grid, which no `contract-core` scenario reads, and `V62` drops `dependent.shift`: S19 to S21, which tested that field, are gone, S25 checks only `trip.shift`, and S22 re-applies `V56` with the column added back for the duration, because `V56` comments it. `V63` drops `address.district_id`, `contract_item.pickup_district_id` and `school.district_id`, and no `contract-core` scenario reads these columns, `pickupDistrictName` or the school's `districtName`.

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
- A request body that fails Bean Validation on the dependent routes gets the generic `Invalid request content.` detail, not the field message (F1 to F3, F7): the `dependent.document.invalid` key is in place for the global change on validation errors. Since `api-validation-errors` (VAN-55) is in `main`, the same bodies return the field message in pt-BR in `detail`, with `field` and `errors[]`; the script only checks the status there, so it is unchanged. The duplicate CPF is a service error and carries its pt-BR message (F6).
- The school types the app searches by (`primary_school`, `secondary_school`) are covered by `SchoolResolveServiceTest` only: the Places stub serves recorded fixtures, and none of them is typed only as a primary or secondary school (rule 50 forbids inventing one).
- Before PR 3, removing a dependent removed its pickup address but left `address_id` pointing to it, because Hibernate drops the pending update of an entity it deletes. A restored dependent then passed the readiness with an address that no longer existed. C1 checks the fix: Marina, removed and restored in G14 to G18, misses her address.
- The seed school used to have no address, so every route that returns a school showed `address: null` for it. The seed now stores one (N14), and a database seeded before gets it on the next start.
