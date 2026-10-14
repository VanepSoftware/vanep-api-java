# QA — contract-core (constitution rule 55)

Latest run: API built from `feat/contract-core-shift-split`, which contains phases 1 to 1. Run date: 2026-10-13. Result: **25/25 passed**.

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

Flyway applied `V56` on an empty database at boot, after the `main` migrations up to `V55`. The seed creates the link between Ana Souza and Fabio Teixeira as `ACTIVE`, with no contract: the `ContractSeeder` arrives in phase 7. Before phase 7 the demo seed also runs before the city catalog, so it skips the seed school and creates Lucas and Marina Souza without school or address (the runner order is fixed in phase 7). The script creates that school with SQL and completes both dependents through `PATCH /api/dependent/{token}`, as the client would.

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

## Behaviors worth knowing

- **A JSON number is accepted where an enum is expected.** `{"shift": 1}` starts the `AFTERNOON` route: Jackson reads the number as the enum position. This is the API-wide default, not something this change introduced. S7 checks a wrong type that is refused (an array).
- **Re-applying a migration (S22, P3, M1) runs its SQL again through `psql`** against rows written by the script. The data migrations of this change are idempotent, which is what makes this a fair test of what they do to rows that already exist.
