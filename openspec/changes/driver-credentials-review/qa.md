# QA — driver-credentials-review (constitution rule 55)

Latest run: API built from `VAN-86/1-schema` (PR 1), 2026-11-21, after the file-only submissions, on a fresh database with the seed. Result: `qa.sh` **36/36 passed**, twice in a row on the same database. The V72 checks bind only approved documents (C4 to C6d), and the approved municipal authorization without number goes back to review (M4, M4b).

Before the file-only submissions: API built from `VAN-86/1-schema` (PR 1), 2026-11-11. Result: `qa.sh` **32/32 passed**.

## How to run

The script creates and drops a database (`v72check`) inside the Postgres container and writes rows directly, so it must only point at a throwaway container.

```bash
# 1. Throwaway Postgres (never the shared dev database)
docker run -d --rm --name vanep-vd-qa-db -e POSTGRES_USER=vanep -e POSTGRES_PASSWORD=vanep \
  -e POSTGRES_DB=vanep -p 127.0.0.1:5435:5432 postgres:17-alpine

# 2. The API from the branch, on 8081, with the seed on (password "password")
./mvnw -DskipTests package
set -a; . ./.env; set +a
SPRING_PROFILES_ACTIVE=local SERVER_PORT=8081 POSTGRES_PORT=5435 POSTGRES_USER=vanep \
  POSTGRES_PASSWORD=vanep POSTGRES_DB=vanep VANEP_MAIL_ENABLED=false VANEP_SEED_ENABLED=true \
  EXPIRY_SWEEP_ENABLED=false VANEP_OAUTH_ISSUER= GOOGLE_PLACES_BASE_URL=http://127.0.0.1:1 \
  GOOGLE_PLACES_API_KEY=qa-fake-key MEDIA_STORAGE_ROOT=/tmp/vanep-vd-media \
  java -jar target/vanep-0.0.1-SNAPSHOT.jar

# 3. The script, only after the log shows "Seed: trips created" (the seed runs after startup)
QA_DB_CONTAINER=vanep-vd-qa-db BASE_URL=http://localhost:8081 scripts/qa/driver-credentials-review/qa.sh
```

**Part 1 (V72 on legacy data)** does not use the API: it applies V1 to V71 to `v72check` through `psql`, inserts the van documents and photos in `driver_document`, documents of the person's types and a van with a document photo, applies V72 and checks:
- that the wrong types were removed logically, without becoming van documents;
- that the person's documents and the files stayed;
- that the photo column is gone.

It then checks the constraints, the van document's expiry included, and the permission grants.

**Part 2** runs against the live API, which migrated the main database with Flyway. It uses real tokens from the mobile password grant, for the seed's admin, driver (Fábio) and client (Ana).

## PR 1 — schema, shared review and the van without its document photo

== V72 on legacy data
| id | scenario | expected | result |
|---|---|---|---|
| M1 | the van, photo, residence proof and free types are removed from driver_document | 0 | PASS |
| M2 | they are removed logically, not deleted | 6 | PASS |
| M3 | nothing becomes a van document, the document photo included | 0 | PASS |
| M4 | the person's documents stay, the approved municipal authorization without number back in review | CRIMINAL_RECORD:PENDING,MUNICIPAL_AUTHORIZATION:PENDING | PASS |
| M4b | the municipal authorization back in review has no reviewer | 0 | PASS |
| M5 | the vehicle has no document photo column | 0 | PASS |
| M6 | the files stay in media_file | 6 | PASS |
| M7 | the van itself is untouched | VAN1A23:true | PASS |

== V72 constraints
| id | scenario | expected | result |
|---|---|---|---|
| C1 | a van document type in driver_document | rejected by driver_document_type_check | PASS |
| C2 | a removed legacy row keeps its old type | accepted | PASS |
| C3 | a person's document type in vehicle_document | rejected by vehicle_document_type_check | PASS |
| C4 | an approved school vehicle authorization without number | rejected by vehicle_document_authorization_number_check | PASS |
| C5 | an approved school vehicle authorization with number | accepted | PASS |
| C6 | an approved van document without expiry | rejected by vehicle_document_approved_expiry_check | PASS |
| C6b | a pending van document without number nor expiry, as the driver sends it | accepted | PASS |
| C6c | an approved municipal authorization without number | rejected by driver_document_authorization_number_check | PASS |
| C6d | a pending municipal authorization without number | accepted | PASS |
| C7 | a vehicle document without vehicle | rejected by vehicle_id | PASS |
| C8 | two live documents with the same token | rejected by vehicle_document_token_active_key | PASS |

== V72 permissions
| id | scenario | expected | result |
|---|---|---|---|
| P1 | the ADMIN bundle gets the five vehicle document permissions and keeps the old ones | delete_vehicle_document,list_contracts,list_vehicle_documents,restore_vehicle_document,show_vehicle_document,update_vehicle_document | PASS |
| P2 | no other bundle gets them | 0 | PASS |

== Flyway on the live database
| id | scenario | expected | result |
|---|---|---|---|
| F1 | V72 is applied by Flyway | 1 | PASS |
| F2 | the seed's documents have only the person's types | 0 | PASS |

== The van without its document photo
| id | scenario | expected | result |
|---|---|---|---|
| V1 | the owner reads the van: no photoDocumentUrl | 200 | PASS |
| V2 | uploading a document photo: the route is gone | 404 | PASS |
| V3 | reading the document photo: the route is gone | 404 | PASS |
| V4 | the front photo still uploads | 201 | PASS |
| V5 | the admin reads the van with the front photo URL | 200 | PASS |

== Driver documents with the person's types only
| id | scenario | expected | result |
|---|---|---|---|
| D1 | a CRLV as a driver document is refused | 400 | PASS |
| D2 | a profile photo as a driver document is refused | 400 | PASS |
| D3 | the specialized course is accepted | 201 | PASS |
| D4 | only the accepted one was written | 3 | PASS |

== Onboarding reading the van's documents
| id | scenario | expected | result |
|---|---|---|---|
| O1 | the driver reads the documents step per van | 200 | PASS |
| O2 | each active van is listed with plate and the two van documents of today's set | 200 | PASS |
| O3 | a client is refused | 403 | PASS |
| O4 | no token | 401 | PASS |

TOTAL: 36 passed
