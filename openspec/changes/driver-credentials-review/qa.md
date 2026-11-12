# QA — driver-credentials-review (constitution rule 55)

Latest run: API built from `VAN-86/2-review-and-http` (PR 2), 2026-11-21, after the file-only submissions, on a fresh database with the seed. Result: `qa.sh` **114/114 passed**, twice in a row on the same database. The driver sends each document with only its type and its file, and the admin fills the number and the expiry on approval (D2): the life of a document (L), what the send and the approval must carry (B, P) and the driver's documents (DD).

Before the file-only submissions: API built from `VAN-86/2-review-and-http` (PR 2), 2026-11-20, after the mobile requests, on a fresh database with the seed. Result: `qa.sh` **105/105 passed**, twice in a row on the same database. L2b checks the `file` that the document response now carries (D22).

Before the mobile requests: API built from `VAN-86/2-review-and-http` (PR 2), 2026-11-11. Result: `qa.sh` **104/104 passed**, twice in a row on the same database (the script can be repeated).

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

**Part 3** (PR 2) uses the same tokens plus another seed driver. DD10 proves the caller is resolved by `uid`: it renames the driver's e-mail while their token, whose `sub` is still the old e-mail, keeps working, and restores the e-mail afterwards.

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
| D3 | the specialized course is accepted with its file and no data | 201 | PASS |
| D4 | only the accepted one was written | 3 | PASS |

== Onboarding reading the van's documents
| id | scenario | expected | result |
|---|---|---|---|
| O1 | the driver reads the documents step per van | 200 | PASS |
| O2 | each active van is listed with plate and the two van documents of today's set | 200 | PASS |
| O3 | a client is refused | 403 | PASS |
| O4 | no token | 401 | PASS |

## PR 2 — Review and routes of the documents

== The life of a van document
| id | scenario | expected | result |
|---|---|---|---|
| L1 | the owner sends the CRLV of the van with its file, without typing its data | 201 | PASS |
| L2 | the owner replaces the CRLV's file while it is pending | 201 | PASS |
| L2b | the document describes its file | 200 | PASS |
| L3 | the owner reads the file back as PDF | 200 application/pdf | PASS |
| L4 | the owner tries to approve their own document | 403 | PASS |
| L5 | the status did not change | PENDING | PASS |
| L6 | the admin approves it with the number and the expiry read from the file | 200 | PASS |
| L7 | the owner swaps the file | 201 | PASS |
| L8 | the swapped document is pending with no reviewer and without the approved data | 200 | PASS |
| L9 | the admin approves it again | 200 | PASS |
| L10 | the owner cannot edit the data: there is no PUT of a document | 405 | PASS |
| L11 | the admin rejects with a reason, stored trimmed | 200 | PASS |
| L12 | the van keeps both inspections, the newer first | 200 | PASS |
| L13 | the admin filters the van's documents by status | 200 | PASS |
| L14 | the onboarding shows the van document with its file URL | 200 | PASS |

== Who may touch a van's documents
| id | scenario | expected | result |
|---|---|---|---|
| A1 | another driver lists the van's documents | 403 | PASS |
| A2 | another driver adds a document to the van | 403 | PASS |
| A3 | another driver reads the document | 403 | PASS |
| A4 | another driver reads the file and gets no content | 403 0 | PASS |
| A5 | another driver replaces the file | 403 | PASS |
| A6 | another driver tries to edit the document: there is no PUT | 405 | PASS |
| A7 | another driver removes the document | 403 | PASS |
| A8 | a client lists the van's documents | 403 | PASS |
| A9 | a client reads the document | 403 | PASS |
| A10 | no token | 401 | PASS |
| A11 | an invalid token | 401 | PASS |
| A12 | an unknown van | 404 | PASS |
| A13 | an unknown document | 404 | PASS |
| A14 | nothing refused was written | 3 | PASS |
| A15 | the admin lists the van's documents | 200 | PASS |
| A16 | the admin sends a document for the van | 201 | PASS |

== What a van document must have
| id | scenario | expected | result |
|---|---|---|---|
| B1 | a person's document type | 400 | PASS |
| B2 | a document without file | 400 | PASS |
| B4 | a document without type | 400 | PASS |
| B6 | a type in lower case | 400 | PASS |
| B7 | a type that is not one of the van's | 400 | PASS |
| B9 | nothing refused was written | 4 | PASS |
| B10 | the number and the expiry sent with the file are not read | PENDING null null | PASS |

== What the approval must carry
| id | scenario | expected | result |
|---|---|---|---|
| P1 | approving an authorization without number | 400 | PASS |
| P2 | approving without the expiry | 400 | PASS |
| P3 | a number of 65 characters | 400 | PASS |
| P4 | an expiry in the wrong format | 400 | PASS |
| P5 | the refused approvals changed nothing | PENDING:: | PASS |
| P6 | a number of exactly 64 characters, stored without the outer spaces | 200 | PASS |
| P7 | a rejection does not store the data of its body | 200 | PASS |

== The review of a van document
| id | scenario | expected | result |
|---|---|---|---|
| R1 | approving a document left without file before this change | 422 | PASS |
| R2 | rejecting without reason | 400 | PASS |
| R3 | a review without status | 400 | PASS |
| R4 | an unknown status | 400 | PASS |
| R5 | the refused reviews changed nothing | PENDING: | PASS |

== Removing and restoring
| id | scenario | expected | result |
|---|---|---|---|
| X1 | the owner removes a document | 204 | PASS |
| X2 | the removed document is gone | 404 | PASS |
| X3 | the owner cannot restore it | 403 | PASS |
| X4 | the admin restores it | 200 | PASS |
| X5 | restoring a live document | 409 | PASS |
| X6 | restoring an unknown document | 404 | PASS |
| X7 | the owner adds a second van | 201 | PASS |
| X8 | the owner removes the second van | 204 | PASS |
| X9 | the removed van's documents cannot be listed | 404 | PASS |
| X10 | the removed van's document is unreachable to the owner | 404 | PASS |
| X11 | and to the admin | 404 | PASS |
| X12 | the owner restores the van | 200 | PASS |
| X13 | the restored van brings its documents back | 200 | PASS |
| X14 | the second van is removed again, so the run can be repeated | 204 | PASS |

== Driver documents with number, clean review and the caller by uid
| id | scenario | expected | result |
|---|---|---|---|
| DD2 | a van document type | 400 | PASS |
| DD2b | a driver document without file | 400 | PASS |
| DD3 | nothing refused was written | 3 | PASS |
| DD1 | approving a municipal authorization without number | 400 | PASS |
| DD4 | the specialized course is sent with its file and no number | 201 | PASS |
| DD5 | the owner replaces the course's file while it is pending | 201 | PASS |
| DD6 | the admin approves it with the number read from the file, resolved by uid | 200 | PASS |
| DD7 | swapping the file returns it to pending without the number | 200 | PASS |
| DD8 | rejecting without reason | 400 | PASS |
| DD9 | the owner cannot review | 403 | PASS |
| DD10 | the token's subject no longer matches an e-mail and the driver still lists their documents | 200 | PASS |
| DD11 | another driver reads the document | 403 | PASS |

== Regression
| id | scenario | expected | result |
|---|---|---|---|
| G1 | the side photo still uploads | 201 | PASS |
| G2 | the admin lists pending driver documents | 200 | PASS |

TOTAL: 114 passed
