# QA — contract-core (constitution rule 55)

Latest run: API built from `feat/contract-core-route-passengers`, which contains phases 1 to 9. Run date: 2026-10-13. Result: **269/269 passed**.

Phase 9 adds `RoutePassengerQueryService` without an HTTP endpoint (the spec forbids one in this change), so this branch runs the whole script as a regression. The query is covered by its repository and service tests (task 9.2).

## How to run

The script writes rows directly, flips seeded drivers and links, and re-applies migrations, so it must only point at a throwaway database seeded on an empty schema. It refuses to start unless the database holds only the seeded link and the two seeded dependents.

```bash
# 1. Throwaway Postgres (never the shared dev database)
docker run -d --rm --name vanep-contract-qa-db -e POSTGRES_USER=vanep -e POSTGRES_PASSWORD=vanep \
  -e POSTGRES_DB=vanep -p 127.0.0.1:5433:5432 postgres:17-alpine

# 2. The API from the branch, on 8081, with the seed on (clients, approved drivers, password "password")
./mvnw -DskipTests package
set -a; . ./.env; set +a
SPRING_PROFILES_ACTIVE=local SERVER_PORT=8081 POSTGRES_PORT=5433 POSTGRES_USER=vanep \
  POSTGRES_PASSWORD=vanep POSTGRES_DB=vanep VANEP_MAIL_ENABLED=false VANEP_SEED_ENABLED=true \
  VANEP_OAUTH_ISSUER= java -jar target/vanep-0.0.1-SNAPSHOT.jar

# 3. The QA (needs curl, jq and docker), once the seed log shows both the cities and the trips
QA_DB_CONTAINER=vanep-contract-qa-db BASE_URL=http://localhost:8081 scripts/qa/contract-core/qa.sh

# 4. Clean up
docker stop vanep-contract-qa-db
```

Tokens come from the mobile password grant (`urn:vanep:params:oauth:grant-type:password` on `/oauth2/token`) for the admin, the clients and the drivers, so every request carries the real `uid` and the real permission bundles from `role_permissions`. The script stops at the first mismatch with a non-zero exit.

## Startup and migration

Flyway applied `V56` to `V60` on an empty database at boot, after the `main` migrations up to `V55`. The seed creates the link between Ana Souza and Fabio Teixeira as `PENDING`, and the `ContractSeeder` activates it with an `ACTIVE` contract for Lucas Souza (Monday to Friday, outbound and return), so the run starts with one contract and one `ACTIVE` link (B1).

## Results

### Operation shift on trips and school shift on dependents

| id | scenario | expected | result |
|---|---|---|---|
| S1 | a driver starts the morning route | 200 | PASS |
| S2 | the same driver starts the afternoon route | 200 | PASS |
| S3 | FULLTIME is not an operation shift | 400 | PASS |
| S4 | unknown shift | 400 | PASS |
| S5 | missing shift | 400 | PASS |
| S6 | null shift | 400 | PASS |
| S7 | shift with the wrong JSON type | 400 | PASS |
| S8 | malformed JSON | 400 | PASS |
| S9 | no token | 401 | PASS |
| S10 | invalid token | 401 | PASS |
| S11 | a client cannot start a route | 403 | PASS |
| S12 | the rejected starts created no trip | 2 | PASS |
| S13 | the admin cannot create a FULLTIME trip | 400 | PASS |
| S14 | the admin creates a NIGHT trip | 201 | PASS |
| S15 | the admin cannot patch a trip to FULLTIME | 400 | PASS |
| S16 | the owner cannot patch a trip to FULLTIME | 400 | PASS |
| S17 | the rejected patches left the shift unchanged | 200 | PASS |
| S18 | the admin moves the trip to another operation shift | 200 | PASS |
| S19 | a client creates a FULLTIME dependent | 201 | PASS |
| S20 | the dependent reads back as FULLTIME | 200 | PASS |
| S21 | unknown school shift on a dependent | 400 | PASS |
| S22 | re-applying V56 turns a stored FULLTIME trip into MORNING | MORNING | PASS |
| S23 | the converted trip is readable through the API | 200 | PASS |
| S24 | V56 left the other shifts alone | AFTERNOON | PASS |
| S25 | trip.shift and dependent.shift are documented | yes | PASS |

### V57: schedule, schedule_slot and unlinked_passenger (database checks)

| id | scenario | expected | result |
|---|---|---|---|
| D1 | the same weekday and leg twice in one schedule | rejected by schedule_slot_weekday_leg_active_key | PASS |
| D2 | outbound and return on the same weekday | accepted | PASS |
| D3 | a soft-deleted slot frees its weekday and leg | accepted | PASS |
| D4 | window end before its start | rejected by schedule_slot_window_check | PASS |
| D5 | window end equal to its start | rejected by schedule_slot_window_check | PASS |
| D6 | window without an end | accepted | PASS |
| D7 | a slot that points to no schedule | rejected by schedule_slot_schedule_id_fkey | PASS |
| D8 | two unlinked passengers sharing one schedule | rejected by unlinked_passenger_schedule_id_active_key | PASS |
| D9 | two unlinked passengers sharing one address | rejected by unlinked_passenger_address_id_active_key | PASS |
| D10 | a soft-deleted passenger frees its schedule and address | accepted | PASS |
| D11 | two live passengers with the same token | rejected by unlinked_passenger_token_active_key | PASS |
| D12 | the rolled-back checks left no passenger behind | 0 | PASS |

### V58: contract and contract_item (database checks)

| id | scenario | expected | result |
|---|---|---|---|
| K1 | a second ACTIVE contract on the same link | rejected by contract_client_driver_active_key | PASS |
| K2 | an ENDED contract next to the ACTIVE one | accepted | PASS |
| K3 | a new ACTIVE contract once the old one is soft-deleted | accepted | PASS |
| K4 | end date on the start date | rejected by contract_period_check | PASS |
| K5 | total amount of zero | rejected by contract_total_amount_check | PASS |
| K6 | thirteen installments | rejected by contract_installments_check | PASS |
| K7 | zero installments | rejected by contract_installments_check | PASS |
| K8 | due day 29 | rejected by contract_due_day_check | PASS |
| K9 | due day 28 and one installment | accepted | PASS |
| K10 | two live contracts with the same token | rejected by contract_token_active_key | PASS |
| K11 | two items sharing one schedule | rejected by contract_item_schedule_id_active_key | PASS |
| K12 | an item priced at zero | rejected by contract_item_monthly_amount_check | PASS |
| K13 | an item without a pickup street | rejected by pickup_street | PASS |
| K14 | a valid item copying the dependent's school and address | accepted | PASS |
| K15 | the rolled-back checks left no contract behind | 1 | PASS |

### V59: contract permissions of the ADMIN bundle

| id | scenario | expected | result |
|---|---|---|---|
| P1 | a real admin token carries the six contract permissions | 6 | PASS |
| P2 | no other bundle has a contract permission | 0 | PASS |
| P3 | re-applying V59 on a bundle without them adds back exactly the six | 97 | PASS |
| P4 | re-applying V59 again changes nothing | 97 | PASS |
| P5 | a fresh admin token still carries the six | 6 | PASS |

### GET /api/contracts and GET /api/contracts/{token}

| id | scenario | expected | result |
|---|---|---|---|
| C1 | the admin lists the seeded contract | 200 | PASS |
| C2 | the client of the link reads it | 200 | PASS |
| C3 | the driver of the link reads it | 200 | PASS |
| C4 | the response carries tokens and no id field | 200 | PASS |
| C5 | another client cannot read it | 403 | PASS |
| C6 | another driver cannot read it | 403 | PASS |
| C7 | no token | 401 | PASS |
| C8 | invalid token | 401 | PASS |
| C9 | a party asking for an unknown contract | 403 | PASS |
| C10 | the admin asking for an unknown contract, pt-BR message | 404 | PASS |
| C11 | a client cannot list every contract | 403 | PASS |
| C12 | the client lists the contracts of their link | 200 | PASS |
| C13 | the driver lists the contracts of their link | 200 | PASS |
| C14 | another client cannot list them | 403 | PASS |
| C15 | the admin lists them | 200 | PASS |

### POST /api/contracts — the admin creates active contracts

| id | scenario | expected | result |
|---|---|---|---|
| H1 | outbound Monday to Friday with one driver | 201 | PASS |
| H2 | creating the contract activated the pending link | ACTIVE | PASS |
| H3 | the same student, return only, with another driver | 201 | PASS |
| H4 | siblings share one contract with their own prices | 201 | PASS |
| H5 | the siblings contract stored two items and twenty slots | 2 20 | PASS |

### POST /api/contracts — terms, items and schedule rejected

| id | scenario | expected | result |
|---|---|---|---|
| V1 | missing endsOn | 400 | PASS |
| V2 | missing startsOn | 400 | PASS |
| V3 | end date on the start date, pt-BR message | 422 | PASS |
| V4 | end date before the start date | 422 | PASS |
| V5 | one day past twelve months, pt-BR message | 422 | PASS |
| V6 | due day 31 | 400 | PASS |
| V7 | due day 29 | 400 | PASS |
| V8 | due day 0 | 400 | PASS |
| V9 | thirteen installments | 400 | PASS |
| V10 | zero installments | 400 | PASS |
| V11 | total amount of zero | 400 | PASS |
| V12 | negative total amount | 400 | PASS |
| V13 | total amount with the wrong JSON type | 400 | PASS |
| V14 | no items | 400 | PASS |
| V15 | missing items | 400 | PASS |
| V16 | item priced at zero | 400 | PASS |
| V17 | the same dependent twice, pt-BR message | 400 | PASS |
| V18 | a dependent of another client, pt-BR message | 422 | PASS |
| V19 | unknown dependent | 404 | PASS |
| V20 | a dependent without an address, pt-BR message | 422 | PASS |
| V21 | a dependent without a school, pt-BR message | 422 | PASS |
| V22 | unknown link | 404 | PASS |
| V23 | blank link token | 400 | PASS |
| V24 | empty schedule, pt-BR message | 400 | PASS |
| V25 | missing schedule | 400 | PASS |
| V26 | the same weekday and leg twice, pt-BR message | 400 | PASS |
| V27 | window end before its start, pt-BR message | 400 | PASS |
| V28 | window end equal to its start | 400 | PASS |
| V29 | a slot without shift (never derived from the time) | 400 | PASS |
| V30 | a slot with the FULLTIME shift | 400 | PASS |
| V31 | unknown weekday | 400 | PASS |
| V32 | impossible time | 400 | PASS |
| V33 | malformed JSON | 400 | PASS |
| V34 | wrong content type | 415 | PASS |
| V35 | no token | 401 | PASS |
| V36 | the client of the link cannot create | 403 | PASS |
| V37 | the driver of the link cannot create | 403 | PASS |
| V38 | a driver pending approval, pt-BR message | 422 | PASS |
| V39 | a blocked link, pt-BR message | 422 | PASS |
| V40 | a second active contract on the same link, pt-BR message | 409 | PASS |
| V41 | the rejected creates stored nothing | 4 5 5 40 | PASS |
| V42 | and left the link pending | PENDING | PASS |

### POST /api/contracts — limits accepted

| id | scenario | expected | result |
|---|---|---|---|
| L1 | exactly twelve months | 201 | PASS |
| L2 | a school year that crosses the calendar year | 201 | PASS |
| L3 | due day 28 and a single installment | 201 | PASS |
| L4 | one-minute window and one-cent amounts | 201 | PASS |
| L5 | unknown and read-only fields in the body are ignored | 201 | PASS |

### Weekday and leg collisions

| id | scenario | expected | result |
|---|---|---|---|
| X1 | the dependent already rides that weekday and leg, pt-BR message | 409 | PASS |
| X2 | the admin suspends the siblings contract | 200 | PASS |
| X3 | a suspended contract keeps the link active | ACTIVE | PASS |
| X4 | a suspended contract still reserves its slots | 409 | PASS |
| X5 | reactivating the suspended contract never collides | 200 | PASS |
| X6 | a weekday the dependent does not ride yet | 201 | PASS |

### PATCH /api/contracts/{token} — status only, through the state machine

| id | scenario | expected | result |
|---|---|---|---|
| T0 | a new contract on a link whose contracts all ended | 201 | PASS |
| T1 | the link went from INACTIVE back to ACTIVE, same link | ACTIVE same link | PASS |
| T2 | an empty PATCH | 200 | PASS |
| T3 | the empty PATCH changed no stored field | unchanged | PASS |
| T4 | dates and amounts are not patchable | 200 | PASS |
| T5 | null status, pt-BR message | 400 | PASS |
| T6 | unknown status | 400 | PASS |
| T7 | malformed JSON | 400 | PASS |
| T8 | superseding by PATCH, pt-BR message | 422 | PASS |
| T9 | ACTIVE back to AWAITING_SIGNATURES, pt-BR message | 409 | PASS |
| T10 | the driver of the link cannot end it | 403 | PASS |
| T11 | the client of the link cannot end it | 403 | PASS |
| T12 | no token | 401 | PASS |
| T13 | unknown contract | 404 | PASS |
| T14 | the rejected patches left it ACTIVE | 200 | PASS |
| T15 | the admin ends the contract | 200 | PASS |
| T16 | ending the only live contract deactivates the link | INACTIVE | PASS |
| T17 | reopening an ended contract | 409 | PASS |
| T18 | the ended contract stays ENDED | 200 | PASS |

### DELETE and POST /restore

| id | scenario | expected | result |
|---|---|---|---|
| R1 | the driver of the link cannot delete it | 403 | PASS |
| R2 | the admin deletes the only contract of a link | 204 | PASS |
| R3 | deleting the only contract returns the link to PENDING | PENDING | PASS |
| R4 | the deleted contract left the listing | 200 | PASS |
| R5 | the row, its items, schedules and slots keep deleted_at | true true true true | PASS |
| R6 | the admin no longer finds it | 404 | PASS |
| R7 | neither does the client of the link | 403 | PASS |
| R8 | deleting it again | 404 | PASS |
| R9 | the client cannot restore it | 403 | PASS |
| R10 | the admin restores it with items and schedule | 200 | PASS |
| R11 | restoring it activates the link again | ACTIVE | PASS |
| R12 | restoring a live contract | 404 | PASS |
| R13 | restoring an unknown contract | 404 | PASS |
| R14 | the admin deletes it again | 204 | PASS |
| R15 | a newer active contract on the same link | 201 | PASS |
| R16 | restoring over the newer active contract | 409 | PASS |
| R17 | the newer one is deleted | 204 | PASS |
| R18 | the return-only contract ends, freeing that link | 200 | PASS |
| R19 | meanwhile another driver takes Monday outbound | 201 | PASS |
| R20 | restoring a contract whose slots were taken meanwhile | 409 | PASS |
| R21 | the contract stays deleted | 1 | PASS |

### Pickup address is a copy

| id | scenario | expected | result |
|---|---|---|---|
| A1 | the client moves the dependent to Rua B, 20 | 200 | PASS |
| A2 | the contracts still pick up at Rua das Acácias, 250 | 200 | PASS |

### Concurrent creates on one link

| id | scenario | expected | result |
|---|---|---|---|
| Z1 | six concurrent creates for one link: one 201, five 409 | 201x1 409x5 | PASS |
| Z2 | and a single active contract was stored | 1 | PASS |
| Z3a | the winner is suspended | 200 | PASS |
| Z3b | a second contract on the link, return only | 201 | PASS |
| Z3c | and suspended too | 200 | PASS |
| Z3 | two concurrent reactivations on one link: one 200, one 409 | 200 409 | PASS |
| Z4 | a single contract of the link is ACTIVE | 1 | PASS |
| Z5a | an active contract on another link | 201 | PASS |
| Z5b | deleted | 204 | PASS |
| Z5c | a second active contract on that link | 201 | PASS |
| Z5d | deleted too | 204 | PASS |
| Z5 | two concurrent restores of active contracts on one link: one 200, one 409 | 200 409 | PASS |
| Z6 | a single one came back | 1 | PASS |

### The link status has no direct write path

| id | scenario | expected | result |
|---|---|---|---|
| B1 | the seeded link is ACTIVE through its seeded ACTIVE contract | 200 | PASS |
| B2 | a status sent on creation is ignored | 201 | PASS |
| B3 | BLOCKED sent on creation is ignored too | 201 | PASS |
| B4 | the same pair twice | 409 | PASS |
| B5 | missing clientToken | 400 | PASS |
| B6 | a client cannot create a link | 403 | PASS |
| B7 | the client of the link cannot activate it | 405 | PASS |
| B8 | the driver of the link cannot activate it | 405 | PASS |
| B9 | the admin cannot either | 405 | PASS |
| B10 | the link stayed PENDING | PENDING | PASS |
| B11 | update_client_driver is in no bundle | 0 | PASS |
| B12 | nor in a real admin token | 0 | PASS |
| B13 | accepted effect: a client cannot rate a driver through a link without contract | 422 | PASS |

### V60: legacy links brought in line

| id | scenario | expected | result |
|---|---|---|---|
| M1 | an ACTIVE link without contract becomes PENDING | PENDING | PASS |
| M2 | every other link keeps its status (ACTIVE with contract, INACTIVE, PENDING) | 0334e3f4b9244d7b875cde693=ACTIVE,081ee0b3a4104d9f8bff0b2b8=INACTIVE,128d992627164e4b944af4835=ACTIVE,22302cd5329e484ab0fb49bf4=ACTIVE,2f813be0548a4d899db0d7c25=PENDING,4928d4f9072c4d19b40e9e138=ACTIVE,5d765f283e744b8a87a91013a=PENDING,b20cc69da80e460580c8ba21d=ACTIVE | PASS |
| M3 | re-applying V60 changes nothing | 0334e3f4b9244d7b875cde693=ACTIVE,081ee0b3a4104d9f8bff0b2b8=INACTIVE,128d992627164e4b944af4835=ACTIVE,22302cd5329e484ab0fb49bf4=ACTIVE,2f813be0548a4d899db0d7c25=PENDING,4928d4f9072c4d19b40e9e138=ACTIVE,5d765f283e744b8a87a91013a=PENDING,6a4c4467ad2e42b6bed2b9b0b=PENDING,b20cc69da80e460580c8ba21d=ACTIVE | PASS |
| M4 | V60 also strips update_client_driver from a bundle that has it | 0 | PASS |

### /api/drivers/me/unlinked-passengers — the driver's own students

| id | scenario | expected | result |
|---|---|---|---|
| U1 | a driver registers a student they already transport | 201 | PASS |
| U2 | it appears in the driver's list | 200 | PASS |
| U3 | the driver reads it | 200 | PASS |
| U4 | another driver's list does not show it | 200 | PASS |
| U5 | another driver reading it | 404 | PASS |
| U6 | another driver patching it | 404 | PASS |
| U7 | another driver replacing its schedule | 404 | PASS |
| U8 | another driver deleting it | 404 | PASS |
| U9 | a client cannot manage unlinked passengers | 403 | PASS |
| U10 | neither can the admin | 403 | PASS |
| U11 | a client cannot create one | 403 | PASS |
| U12 | no token | 401 | PASS |
| U13 | invalid token | 401 | PASS |
| U14 | unknown token | 404 | PASS |

### POST — validation

| id | scenario | expected | result |
|---|---|---|---|
| N1 | missing name | 400 | PASS |
| N2 | blank name | 400 | PASS |
| N3 | a 256-character name | 400 | PASS |
| N4 | 501-character notes | 400 | PASS |
| N5 | missing school | 400 | PASS |
| N6 | unknown school, pt-BR message | 404 | PASS |
| N7 | missing school shift | 400 | PASS |
| N8 | unknown school shift | 400 | PASS |
| N9 | missing address | 400 | PASS |
| N10 | invalid zip code | 400 | PASS |
| N11 | unknown city | 404 | PASS |
| N12 | empty schedule, pt-BR message | 400 | PASS |
| N13 | missing schedule | 400 | PASS |
| N14 | the same weekday and leg twice | 400 | PASS |
| N15 | window end before its start | 400 | PASS |
| N16 | a slot with the FULLTIME shift | 400 | PASS |
| N17 | name with the wrong JSON type | 400 | PASS |
| N18 | malformed JSON | 400 | PASS |
| N19 | wrong content type | 415 | PASS |
| N20 | the rejected creates stored nothing | 1 71 | PASS |
| N21 | a 255-character name and 500-character notes | 201 | PASS |
| N22 | unknown personal fields are not stored | 201 | PASS |
| N23 | the table has no column for them | 0 | PASS |
| N24 | a driver pending approval registers one too | 201 | PASS |
| N25 | five concurrent creates: no limit, five 201 | 201x5 | PASS |
| N26 | with five distinct tokens | 200 | PASS |

### PATCH, PUT /schedule and DELETE

| id | scenario | expected | result |
|---|---|---|---|
| W1 | a single-field PATCH (notes) | 200 | PASS |
| W2 | name, school, shift, address and schedule unchanged | unchanged | PASS |
| W3 | an empty PATCH changes nothing | 200 | PASS |
| W4 | notes can be cleared | 200 | PASS |
| W5 | name cannot be cleared | 400 | PASS |
| W6 | school shift cannot be cleared | 400 | PASS |
| W7 | school cannot be cleared | 400 | PASS |
| W8 | address cannot be cleared | 400 | PASS |
| W9 | a 256-character name | 400 | PASS |
| W10 | unknown school shift | 400 | PASS |
| W11 | unknown school | 404 | PASS |
| W12 | the rejected patches changed nothing | 200 | PASS |
| W13 | name, school shift and address patched together | 200 | PASS |
| W14 | the schedule is replaced by five outbound slots | 200 | PASS |
| W15 | then by three | 200 | PASS |
| W16 | the previous fifteen slots remain soft-deleted, three are live | 15 3 | PASS |
| W17 | an empty schedule | 400 | PASS |
| W18 | a body without slots | 400 | PASS |
| W19 | a window ending before it starts | 400 | PASS |
| W20 | the same weekday and leg twice | 400 | PASS |
| W21 | the rejected replacements kept the three slots | 200 | PASS |
| W22 | the driver deletes it | 204 | PASS |
| W23 | it left the list | 200 | PASS |
| W24 | reading it is not found | 404 | PASS |
| W25 | deleting it again is not found | 404 | PASS |
| W26 | patching it is not found | 404 | PASS |
| W27 | the row, its schedule and slots keep deleted_at | true true true | PASS |

## Behaviors worth knowing

- **A JSON number is accepted where an enum is expected.** `{"shift": 1}` starts the `AFTERNOON` route: Jackson reads the number as the enum position. This is the API-wide default, not something this change introduced. S7 checks a wrong type that is refused (an array).
- **Re-applying a migration (S22, P3, M1) runs its SQL again through `psql`** against rows written by the script. The data migrations of this change are idempotent, which is what makes this a fair test of what they do to rows that already exist.
- **The schema checks (D, K) run each statement in a transaction that is rolled back**, on the same database the API migrated at boot, so they prove the indexes and `CHECK`s exactly as Flyway applied them and leave nothing behind (D12, K15).
- **A Bean Validation failure is a 400 with the generic detail `Invalid request content.`**, as on every endpoint of the API (the dependent endpoint does the same). The key the spec names for those cases (`contract.due_day.invalid`, `contract.installments.invalid`, `contract.amount.invalid`) is resolved into the field error but not shown in the body. Errors raised by the services (422, 409 and the schedule 400s) carry the pt-BR message, and the script asserts it.
- **An unknown contract token is 403 for a party and 404 for the admin (C9, C10).** `@sec.isContractParty` runs before the service, so the API does not reveal whether a token exists to someone without `show_contract`.
- **Concurrent writes on one link keep a single `ACTIVE` contract (Z1 to Z6).** The service checks first and the partial unique index decides the race; the request that loses gets the same `409 contract.active_conflict` as the sequential check. Before the fix in phase 5, one of six concurrent creates returned 500.
- **The weekday-and-leg collision is enforced by the service only** (R5 in `design.md`). Two simultaneous creates for the same dependent on different links are not guarded by the database in this phase, and the script does not race them.
- **Unknown and read-only fields in the create body are ignored (L5).** A contract always starts `ACTIVE`, and the pickup address always comes from the dependent.
- **B13 is the accepted effect of R2:** with the link status derived from contracts, a client cannot rate a driver until the admin registers the contract of the pair.
- **A JSON number is accepted where text is expected.** `{"name": 42}` creates a passenger named "42", the same API-wide Jackson default as above. N17 checks an object, which is refused.
- **The admin also gets 403 on `/api/drivers/me/unlinked-passengers` (U10):** the caller must be a driver, and the token in the URL is resolved among the caller's own passengers (U5 to U8 are 404).
