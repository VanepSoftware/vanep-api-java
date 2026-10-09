# QA — contract-core (constitution rule 55)

Latest run: API built from `feat/contract-core-service`, which contains phases 1 to 5. Run date: 2026-10-13. Result: **57/57 passed**.

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

Flyway applied `V56` to `V59` on an empty database at boot, after the `main` migrations up to `V55`. The seed creates the link between Ana Souza and Fabio Teixeira as `ACTIVE`, with no contract: the `ContractSeeder` arrives in phase 7. Before phase 7 the demo seed also runs before the city catalog, so it skips the seed school and creates Lucas and Marina Souza without school or address (the runner order is fixed in phase 7). The script creates that school with SQL and completes both dependents through `PATCH /api/dependent/{token}`, as the client would.

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
| K15 | the rolled-back checks left no contract behind | 0 | PASS |

### V59: contract permissions of the ADMIN bundle

| id | scenario | expected | result |
|---|---|---|---|
| P1 | a real admin token carries the six contract permissions | 6 | PASS |
| P2 | no other bundle has a contract permission | 0 | PASS |
| P3 | re-applying V59 on a bundle without them adds back exactly the six | 98 | PASS |
| P4 | re-applying V59 again changes nothing | 98 | PASS |
| P5 | a fresh admin token still carries the six | 6 | PASS |

## Behaviors worth knowing

- **A JSON number is accepted where an enum is expected.** `{"shift": 1}` starts the `AFTERNOON` route: Jackson reads the number as the enum position. This is the API-wide default, not something this change introduced. S7 checks a wrong type that is refused (an array).
- **Re-applying a migration (S22, P3, M1) runs its SQL again through `psql`** against rows written by the script. The data migrations of this change are idempotent, which is what makes this a fair test of what they do to rows that already exist.
- **The schema checks (D, K) run each statement in a transaction that is rolled back**, on the same database the API migrated at boot, so they prove the indexes and `CHECK`s exactly as Flyway applied them and leave nothing behind (D12, K15).
