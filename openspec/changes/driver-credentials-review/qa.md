# QA — driver-credentials-review (constitution rule 55)

PR 8 and PR 9 were QA'd apart, each on top of PR 7: the runs below are the PR 9 ones, and the PR 8 results come from its own run. They were not run again together after PR 9 was moved on top of PR 8.

Latest run: API built from `VAN-86/9-admin-queue` (PR 9, on top of PR 7), 2026-11-21, after the file-only submissions, on a fresh database with the seed and the JVM in `Pacific/Kiritimati`. Result: `qa.sh` **331/331 passed**, twice in a row on the same database. The regression of PRs 1 to 7 with the file-only submissions.

Before the file-only submissions: API built from `VAN-86/9-admin-queue` (PR 9, on top of PR 7), 2026-11-21, after the CNH category rule, on a fresh database with the seed and the JVM in `Pacific/Kiritimati`. Result: `qa.sh` **304/304 passed**, twice in a row on the same database, with the regression of PRs 1 to 7 under the date limits (D21). N4 to N6b check that the API only takes the CNH categories D, E, AD and AE, and that a license saved before with category B is still read and reported as `cnh_category`.

Before the CNH category rule: API built from `VAN-86/9-admin-queue` (PR 9, on top of PR 7), 2026-11-20, after the mobile requests, on a fresh database with the seed and the JVM in `Pacific/Kiritimati`. Result: `qa.sh` **300/300 passed**, twice in a row on the same database, with the regression of PRs 1 to 7 under the date limits (D21).

Before the mobile requests: API built from `VAN-86/9-admin-queue` (PR 9, on top of PR 7), 2026-11-20, after the code review fixes, on a fresh database with the seed and the JVM in `Pacific/Kiritimati`. Result: `qa.sh` **284/284 passed**, twice in a row on the same database. N8 now expects `cnh_file` while the new driver's CNH has no file, and N8b checks the step complete after the upload.

Before the code review fixes: API built from `VAN-86/9-admin-queue` (PR 9, on top of PR 7), 2026-11-15, on a fresh database with the seed and the JVM in `Pacific/Kiritimati`. Result: `qa.sh` **283/283 passed**, twice in a row on the same database: the four tabs and the counts, the search by name, e-mail and plate with or without hyphen, the queue current at every read, who reads it, R10 with 1,000 more drivers, and the regression of PRs 1 to 7 (PR 8 is a parallel branch).

Previous runs: API built from `VAN-86/7-compliance-policy` (PR 7), 2026-11-15, on a fresh database with the seed, with the JVM in `Pacific/Kiritimati` (already the next day, while São Paulo is still on the 15th). `qa.sh` **259/259 passed**, twice in a row on the same database: the blockers of each refused approval of the new driver, an approval and a rejection at once, São Paulo's date against the JVM's, and the regression of PRs 1 to 6, including the CNH file as a PDF. Before that: API built from `VAN-86/6-cnh-review` (PR 6), 2026-11-15, on a fresh database with the seed. `qa.sh` **253/253 passed**, twice in a row on the same database: the CNH review, the renewal with the same number, the number of one driver (two drivers sending the same new number at once, 8 times in a row: a later run found that PostgreSQL can abort one of the two with a deadlock instead of the violation, and the service now answers that one with the same 409; this run logged 3 such deadlocks and no 500), the CNH file as a PDF (the digital CNH) on the `/file` route, read back as `application/pdf`, with the old `/photo` route gone and the column renamed by V77, the onboarding with a rejected CNH and the seed, and the regression of PRs 1 to 5. The new driver of part 4 now has their CNH approved by the admin before DRIVER_LICENSE counts. As a regression, `scripts/qa/service-request-proposal/qa.sh` on its own fresh database with `places-stub.py` and the JVM in `Etc/GMT+12`: **563/563 passed**. Before that: API built from `VAN-86/5-review-and-submissions` (PR 5), 2026-11-15, on a fresh database with the seed. `qa.sh` **224/224 passed**, twice in a row on the same database: V76 on legacy data, the decisions, the final submissions, the renewals and the file caching, and the regression of PRs 1 to 4, whose PR 2 and PR 3 flows now follow the PR 5 rules (the decision carries the version, a rejection carries a code, a renewal is a new submission). Before that: API built from `VAN-86/4-review-schema` (PR 4), 2026-11-15, rebased on the current `main`, on a fresh database with the seed. `qa.sh` **159/159 passed**, twice in a row on the same database: the 141 scenarios of PRs 1 to 3 as a regression, and the 18 of V75. Before that: API built from `VAN-86/3-credentials-and-onboarding` (PR 3), 2026-11-12: `qa.sh` **141/141 passed**, twice in a row, and `scripts/qa/service-request-proposal/qa.sh` as a regression on its own fresh database with `places-stub.py`: **563/563 passed**. API built from `VAN-86/2-review-and-http` (PR 2), 2026-11-11: `qa.sh` **104/104 passed**, twice in a row.

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

**Part 1b (V75 on the data V72 left)** (PR 4) adds a rejected document, a rejected driver and a CNH, applies V75 and checks the reason code `OTHER` on the rejected rows, the CNH `PENDING` at version 0, `btree_gist`, the dropped CNH indexes, the `CHECK`s of the reason codes and of the CNH status, the exclusion constraint of the CNH number (two drivers refused, the same driver and a removed CNH accepted) and `review_documents` in the `ADMIN` bundle only.

The first run of part 3 on PR 4 found that the `CHECK` "a rejected row has a reason code" in V75 made every rejection answer 500, because the PR 4 service does not send a code yet. That `CHECK`, and the unique index of one `PENDING` per owner and type (a second `PENDING` would also answer 500), moved to the migration of PR 5, together with the service that enforces them.

**Part 1c (V76)** (PR 5) adds two `PENDING` submissions of the same owner and type and a rejection without code, applies V76 and checks that the newest stays pending and the older is superseded, the code `OTHER` on the old rejection, the three one-pending indexes and the `CHECK`s of the code on a rejection.

**Part 5** (PR 5) uses the seed driver: a rejection with each reason code (each resend supersedes the previous rejection, which keeps its decision and loses its file), `OTHER` with and without a message, two decisions with the same version, a file swapped while the admin looked, a real token without `review_documents` (the permission is removed from the `ADMIN` bundle by SQL, the admin logs in again and the permission is put back), the renewal of an inspection with the old file deleted from the disk after the commit, every `409` of a decided submission, two simultaneous submissions of the same type and the `Cache-Control` of a document file and of a van photo. Every run ends with no pending submission left, so the script can be repeated.

**Part 6** (PR 6) reviews the CNH of the new driver of part 4: the owner cannot decide, edit or remove it once approved; a renewal with the same number lives next to it, takes a PDF as its file (the digital CNH) on the `/file` route, is refused without file and on an old version, and replaces it when approved (the old file leaves the disk); another driver cannot use the number, also when two drivers send the same new number at once; a driver whose only CNH was rejected sees `cnh_rejected`; and every seed CNH is approved.

**Part 7** (PR 7) checks the `blockers` of the two refused approvals of part 4 (the driver's pending documents and the three of the closest van; then only the pending inspection), races an approval and a rejection of the seed driver (put under review by SQL: one decision and one 400 with `code`), and runs the expiry checks with the JVM in a zone whose date differs from São Paulo's (`QA_JVM_ZONE`, given to the API as `-Duser.timezone`): a document valid until yesterday in São Paulo fails and one valid until today passes, whatever day it is for the JVM. Z1x fails if the run's JVM zone gives the same date as São Paulo.

**Part 8** (PR 8) needs the API sending e-mail to a throwaway Mailpit (`SPRING_MAIL_HOST=127.0.0.1 SPRING_MAIL_PORT=1026 VANEP_MAIL_ENABLED=true`, Mailpit started with `docker run -d --rm --name vanep-vd-qa-mail -p 127.0.0.1:1026:1025 -p 127.0.0.1:8026:8025 axllent/mailpit:v1.27`, and `QA_MAILPIT_URL=http://localhost:8026` for the script). It rejects a van inspection, a driver document and a CNH and reads each e-mail in Mailpit (name, plate, reason of the code, message, in pt-BR) and the `notified_at`; then it rejects the seed driver's onboarding without code, with `OTHER` and no reason, with an unknown code and with `DUPLICATE_ACCOUNT`, and checks the single rejection e-mail and the text the driver's onboarding shows.

**Part 9** (PR 9) compares each tab's `totalElements` with `/counts`, checks the order of `AWAITING_DECISION` and the badge, searches by the new driver's plate with a hyphen and in lower case and by e-mail, moves the expiry of the seed driver's criminal record to yesterday by SQL to see `EXPIRING` change on the next read and puts it back, and checks who may read. For **R10** it inserts by SQL 1,000 drivers, each with a CNH, the two documents, a van and its three documents (a fifth of them with a pending one), times every tab, `/counts` and a search, and removes them.

**R10 timings** (the API on the developer's machine against the throwaway PostgreSQL, page of 20):

| run | drivers | `AWAITING_DECISION` | `PENDING_DOCUMENTS` | `EXPIRING` | `ALL` | `/counts` | search by plate |
|---|---|---|---|---|---|---|---|
| 1 | 1,005 | 0.29 s | 0.40 s | 0.44 s | 0.44 s | 0.39 s | 0.01 s |
| 2 | 1,007 | 0.28 s | 0.58 s | 0.67 s | 0.66 s | 0.56 s | 0.01 s |

Below the signal of about 1 s that would bring back the stored table of D16, but in the same order of magnitude: the time grows with the base, as R10 accepts.

**Part 2** runs against the live API, which migrated the main database with Flyway. It uses real tokens from the mobile password grant, for the seed's admin, driver (Fábio) and client (Ana).

**Part 3** (PR 2) uses the same tokens plus another seed driver. DD10 proves the caller is resolved by `uid`: it renames the driver's e-mail while their token, whose `sub` is still the old e-mail, keeps working, and restores the e-mail afterwards.

**Part 4** (PR 3) signs up a new driver through `POST /api/auth/signup/driver` and logs in with the password grant. Two things are set by SQL because this change does not deliver them and the run has no mail or Places: the e-mail verification and the driver's city (a new address per run). The inspection expiry is moved by SQL to yesterday and to today (T1, T2).

The first run of part 4 found a defect from PR 1: with one complete van and another without documents, the documents step was complete and `canSubmit` true, but `submit-onboarding` answered 422 listing the second van (E9). The fix went into the PR 1 commit, with its tests.

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
| F3 | V75 is applied by Flyway | 1 | PASS |
| F5 | V76 is applied by Flyway | 1 | PASS |
| F4 | review_documents is in the live ADMIN bundle only | ADMIN | PASS |
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
| D4 | only the accepted one was written | 10 | PASS |

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
| L7 | the owner cannot swap the file of the approved document | 409 | PASS |
| L8 | the approved document keeps its decision | 200 | PASS |
| L9 | the owner cannot edit the data: there is no PUT of a document | 405 | PASS |
| L10 | a renewed CRLV is a new submission | 201 | PASS |
| L11 | the admin rejects it with a code and a message, stored trimmed | 200 | PASS |
| L12 | a second pending inspection of the van is refused | 409 | PASS |
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
| A14 | nothing refused was written | 12 | PASS |
| A15 | the admin lists the van's documents | 200 | PASS |
| A16 | the admin sends a document for the van | 201 | PASS |
| A17 | the owner removes the pending one, so the run can be repeated | 204 | PASS |

== What a van document must have
| id | scenario | expected | result |
|---|---|---|---|
| B1 | a person's document type | 400 | PASS |
| B2 | a document without file | 400 | PASS |
| B4 | a document without type | 400 | PASS |
| B6 | a type in lower case | 400 | PASS |
| B7 | a type that is not one of the van's | 400 | PASS |
| B9 | nothing refused was written | 13 | PASS |
| B10 | the number and the expiry sent with the file are not read | PENDING null null | PASS |
| B11 | the owner removes the pending one | 204 | PASS |

== What the approval must carry
| id | scenario | expected | result |
|---|---|---|---|
| P1 | approving an authorization without number | 400 | PASS |
| P2 | approving without the expiry | 400 | PASS |
| P3 | a number of 65 characters | 400 | PASS |
| P4 | an expiry in the wrong format | 400 | PASS |
| P5 | the refused approvals changed nothing | PENDING:: | PASS |
| P6 | a number of exactly 64 characters, stored without the outer spaces | 200 | PASS |
| P7 | a rejection as NOT_ELIGIBLE does not store the data of its body | 200 | PASS |

== Correcting the data of an approved document
| id | scenario | expected | result |
|---|---|---|---|
| Q1 | the reviewer corrects a mistyped number and expiry | 200 | PASS |
| Q2 | the correction records who corrected and keeps the file | 570fd2fda38c40ba9335584d0:yes | PASS |
| Q3 | a correction on the version before is stale | 409 | PASS |
| Q4 | a correction without the required number | 400 | PASS |
| Q5 | an expiry before today may be corrected: the document is then expired | 200 | PASS |
| Q6 | the reviewer puts the right expiry back | 200 | PASS |
| Q7 | the owner cannot correct | 403 | PASS |
| Q8 | a rejected document is not corrected | 409 | PASS |
| Q9 | a correction without version | 400 | PASS |

== The review of a van document
| id | scenario | expected | result |
|---|---|---|---|
| R1 | approving a document left without file before this change | 422 | PASS |
| R2 | rejecting without a reason code | 400 | PASS |
| R2b | rejecting for another reason without a message | 400 | PASS |
| R3 | a review without status | 400 | PASS |
| R4 | an unknown status | 400 | PASS |
| R4b | a review without the version it saw | 400 | PASS |
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
| X6b | restoring a pending document next to another pending one | 409 | PASS |
| X6c | the refused restore left it removed | 1 | PASS |
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
| DD3 | nothing refused was written | 10 | PASS |
| DD1 | approving a municipal authorization without number | 400 | PASS |
| DD4 | the specialized course is sent with its file and no number | 201 | PASS |
| DD5 | the owner replaces the course's file while it is pending | 201 | PASS |
| DD6 | the admin approves it with the number read from the file, resolved by uid | 200 | PASS |
| DD7 | the owner cannot swap the file of the approved course | 409 | PASS |
| DD8 | rejecting without a reason code | 400 | PASS |
| DD9 | the owner cannot review | 403 | PASS |
| DD9b | the admin rejects the new course as incomplete | 200 | PASS |
| DD9c | the reviewer corrects the course's number | 200 | PASS |
| DD10 | the token's subject no longer matches an e-mail and the driver still lists their documents | 200 | PASS |
| DD11 | another driver reads the document | 403 | PASS |

== Regression
| id | scenario | expected | result |
|---|---|---|---|
| G1 | the side photo still uploads | 201 | PASS |
| G2 | the admin lists rejected driver documents | 200 | PASS |

## PR 3 — Credentials, the legal onboarding and the approval

== A new driver from scratch
| id | scenario | expected | result |
|---|---|---|---|
| N1 | a new driver signs up | 201 | PASS |
| N2 | the driver adds a van | 201 | PASS |
| N3 | a new van: nothing satisfied but the optional municipal authorization is not required | 200 | PASS |
| N4 | the driver sends a photo of the license, without typing its data | 201 | PASS |
| N4b | nothing of the license was typed | ::: | PASS |
| N5 | the license step counts the CNH sent with its file | 200 | PASS |
| N6 | a pending CNH does not satisfy DRIVER_LICENSE yet | false | PASS |
| N6b | approving the license as category B is refused on the category field | 400 | PASS |
| N6c | approving without the registration number names the field | 400 | PASS |
| N6d | the refused approvals left the CNH pending, without data | PENDING: | PASS |
| N7 | the admin approves the CNH with the data read from it, category AD | 200 | PASS |
| N8 | the license step is complete | 200 | PASS |
| N9 | the approved category AD satisfies DRIVER_LICENSE | true | PASS |
| N10 | a license saved before the rule with category B is read and reported as cnh_category | 200 | PASS |
| N11 | category B does not satisfy DRIVER_LICENSE | false | PASS |

== Documents one at a time
| id | scenario | expected | result |
|---|---|---|---|
| E1 | today the driver only sends the CRLV | 201 | PASS |
| E2 | the documents step is incomplete, lists what is left and keeps the CRLV | 200 | PASS |
| E3 | submitting now is refused, naming the types and the plate | 422 | PASS |
| E4 | the refused submission changed nothing | PENDING 1 | PASS |
| E5 | a week later the driver sends the course and the criminal record | 201 | PASS |
| E6 | still missing the school authorization of any van | 422 | PASS |
| E7 | the driver adds a second van with no documents | 201 | PASS |
| E8 | one complete van is enough; the second shows its three types missing | 200 | PASS |
| E9 | the driver submits | 200 | PASS |
| E10 | a driver under review can still add a document | 201 | PASS |

== Approving the driver
| id | scenario | expected | result |
|---|---|---|---|
| A1x | approving with the documents still pending: the blockers name the driver's and the closest van's | 422 | PASS |
| A2x | the driver is still under review | UNDER_REVIEW | PASS |
| A3x | the admin rejects the driver: the inspection is unreadable | 200 | PASS |
| A4x | the driver uploads a new file and submits again | 200 | PASS |
| A5x | with the inspection still pending only VEHICLE_INSPECTION fails | 200 | PASS |
| A6x | approving with the inspection of the only complete van pending: only that blocker | 422 | PASS |
| A7x | everything approved: the van passes, with each number and expiry | 200 | PASS |
| A8x | the second van shows the three vehicle requirements missing | 200 | PASS |
| A9x | the admin approves the driver | 200 | PASS |

== Credentials over time and access
| id | scenario | expected | result |
|---|---|---|---|
| T1 | an inspection expired yesterday makes VEHICLE_INSPECTION fail | false | PASS |
| T2 | an inspection valid until today passes | true | PASS |
| T3 | the seed driver's van passes | 200 | PASS |
| T4 | the admin reads it with show_vehicle | 200 | PASS |
| T5 | another driver is refused | 403 | PASS |
| T6 | a client is refused | 403 | PASS |
| T7 | no token | 401 | PASS |
| T8 | an unknown van | 404 | PASS |
| T9 | a removed van | 404 | PASS |

TOTAL: 331 passed

## PR 4 — schema of the new review

== V75 on legacy data
| id | scenario | expected | result |
|---|---|---|---|
| R1 | a rejected document gets the reason code OTHER and keeps its message | OTHER:Ilegível | PASS |
| R2 | a rejected driver gets the reason code OTHER | OTHER | PASS |
| R3 | an existing CNH becomes PENDING, at version 0 | PENDING:0 | PASS |
| R4 | the documents start at version 0 and not superseded | 0 | PASS |
| R5 | the other documents get no reason code | 1 | PASS |
| R6 | btree_gist is installed | 1 | PASS |
| R7 | the old one-CNH-per-driver and unique-number indexes are gone | 0 | PASS |
| R8 | the three submissions keep who last corrected their data, and when | 6 | PASS |

== V75 constraints
| id | scenario | expected | result |
|---|---|---|---|
| K1 | an unknown reason code on a document | rejected by driver_document_rejection_reason_code_check | PASS |
| K2 | a van document with a valid reason code | accepted | PASS |
| K2b | NOT_ELIGIBLE is a reason code: a true document that does not allow school transport | accepted | PASS |
| K3 | an unknown CNH status | rejected by driver_cnh_status_check | PASS |
| K4 | an unknown driver rejection code | rejected by driver_rejection_reason_code_check | PASS |
| K5 | the same CNH number on two drivers | rejected by driver_cnh_registration_one_driver_excl | PASS |
| K6 | the same CNH number again on the same driver (a renewal) | accepted | PASS |
| K7 | another driver may reuse a number of a removed CNH | accepted | PASS |

== V75 permissions
| id | scenario | expected | result |
|---|---|---|---|
| P3 | the ADMIN bundle gets review_documents | 1 | PASS |
| P4 | no other bundle gets it | 0 | PASS |

The live database: F3 and F4 in the PR 1 table above. The regression of PRs 1 to 3 is every table of this file.

## PR 5 — decisions with version and reason, final submissions and file caching

== V76 on legacy data
| id | scenario | expected | result |
|---|---|---|---|
| Q1 | of two pending submissions of the same owner and type, the newest stays pending | dd-course-new | PASS |
| Q2 | the older one is superseded | 1 | PASS |
| Q3 | a rejection without code made before V76 gets OTHER | OTHER | PASS |

== V76 constraints
| id | scenario | expected | result |
|---|---|---|---|
| U1 | a second pending submission of the same owner and type | rejected by driver_document_one_pending_key | PASS |
| U2 | a second pending van document of the same type | rejected by vehicle_document_one_pending_key | PASS |
| U3 | a second pending CNH of the same driver | rejected by driver_cnh_one_pending_key | PASS |
| U4 | a new pending submission next to a superseded and a removed one | accepted | PASS |
| U5 | a rejected document without reason code | rejected by driver_document_rejected_reason_check | PASS |
| U6 | a rejected van document without reason code | rejected by vehicle_document_rejected_reason_check | PASS |
| U7 | a rejected CNH without reason code | rejected by driver_cnh_rejected_reason_check | PASS |

== A rejection has a structured reason
| id | scenario | expected | result |
|---|---|---|---|
| S-ILLEGIBLE | rejecting with ILLEGIBLE needs no message | 200 | PASS |
| S-EXPIRED | rejecting with EXPIRED needs no message | 200 | PASS |
| S-DATA_MISMATCH | rejecting with DATA_MISMATCH needs no message | 200 | PASS |
| S-WRONG_TYPE | rejecting with WRONG_TYPE needs no message | 200 | PASS |
| S-INCOMPLETE | rejecting with INCOMPLETE needs no message | 200 | PASS |
| S-NOT_ELIGIBLE | rejecting with NOT_ELIGIBLE needs no message | 200 | PASS |
| S1 | each resend superseded the previous rejection; one rejection stays live | 1 | PASS |
| S2 | the superseded rejection keeps its decision and loses its file | REJECTED:ILLEGIBLE:true: | PASS |
| S3 | the superseded rejections are not listed | 200 | PASS |
| S4 | OTHER without a message | 400 | PASS |
| S5 | OTHER with a message | 200 | PASS |
| S6 | an unknown reason code | 400 | PASS |

== The decision names the version it saw
| id | scenario | expected | result |
|---|---|---|---|
| V1 | the first admin approves with the version they saw | 200 | PASS |
| V2 | the second admin, with the same version, is stale | 409 | PASS |
| V3 | the first decision stands | APPROVED | PASS |
| V4 | the owner swapped the file while the admin looked: approving what they saw is stale | 409 | PASS |
| V5 | the document is still pending | PENDING | PASS |
| V6 | deciding it with the current version | 200 | PASS |
| V7 | a real token with update_driver_document and without review_documents | true false | PASS |
| V8 | that token cannot decide | 403 | PASS |
| V9 | nor decide a van document | 403 | PASS |
| V10 | the reviewer decides it | 200 | PASS |

== Renewing is a new submission
| id | scenario | expected | result |
|---|---|---|---|
| W1 | the inspection is approved | 200 | PASS |
| W2 | the owner sends the renewal before it expires | 201 | PASS |
| W3 | the van keeps VEHICLE_INSPECTION while the renewal waits | true | PASS |
| W4 | the approved file is on disk before the renewal is approved | yes | PASS |
| W5 | the admin approves the renewal | 200 | PASS |
| W6 | the previous approval is superseded, keeps its decision and has no file | APPROVED:true: | PASS |
| W7 | its media row is removed | 0 | PASS |
| W8 | and its object is deleted from the disk after the commit | no | PASS |
| W9 | the van passes with the renewal | 200 | PASS |
| W10 | the listing shows the renewal, not the superseded one | 200 | PASS |
| W11 | the superseded one has nothing to download | 404 | PASS |

== A reviewed submission is final
| id | scenario | expected | result |
|---|---|---|---|
| Z1 | editing the approved inspection: there is no PUT of a document | 405 | PASS |
| Z2 | uploading a file to it | 409 | PASS |
| Z3 | the owner removing it | 409 | PASS |
| Z4 | deciding it again | 409 | PASS |
| Z5 | nothing changed | APPROVED:2027-06-09 | PASS |
| Z6 | the owner cannot remove an approved driver document | 409 | PASS |
| Z7 | the admin with delete_driver_document removes it | 204 | PASS |

== One pending submission per owner and type
| id | scenario | expected | result |
|---|---|---|---|
| Y1 | two simultaneous submissions of the same type: one is created, the other refused | 201 409 | PASS |
| Y2 | a single pending one was stored | 1 | PASS |
| Y3 | the owner removes their pending submission, so the run can be repeated | 204 | PASS |

== Dates within the type's limits
| id | scenario | expected | result |
|---|---|---|---|
| K1 | approving an inspection that expired yesterday | 400 | PASS |
| K2 | approving a CRLV after December 31 of next year | 400 | PASS |
| K3 | both are still pending, without data | PENDING: PENDING: | PASS |
| K4 | approving a CRLV on December 31 of next year, the last day | 200 | PASS |
| K5 | correcting it past the limit | 400 | PASS |
| K6 | the CRLV kept its date | 2027-12-31 | PASS |
| K7 | the owner removes the van of these checks, so the run can be repeated | 204 | PASS |
| K8 | approving a driver course that expired yesterday | 400 | PASS |
| K9 | approving a criminal record ten years ahead, which has no upper limit | 200 | PASS |

== Files are cached while the URL does not change
| id | scenario | expected | result |
|---|---|---|---|
| H1 | a document file with the v of its URL | max-age=86400, private, immutable | PASS |
| H2 | a document file without v | no-cache, private | PASS |
| H3 | a document file with an old v | no-cache, private | PASS |
| H4 | a van photo with the v of its URL | max-age=86400, private, immutable | PASS |
| H5 | a van photo without v | no-cache, private | PASS |

## PR 6 — the CNH reviewed like the documents

== The CNH is reviewed by an admin
| id | scenario | expected | result |
|---|---|---|---|
| C1 | the owner cannot decide their own CNH | 403 | PASS |
| C2 | the owner cannot edit the approved CNH: there is no PUT of a CNH | 405 | PASS |
| C3 | nor remove it | 409 | PASS |

== Renewing the CNH keeps the current one
| id | scenario | expected | result |
|---|---|---|---|
| C4 | a renewal is sent with its file and without data | 201 | PASS |
| C5 | the approved CNH still satisfies DRIVER_LICENSE | true | PASS |
| C6 | a second pending CNH | 409 | PASS |
| C7 | approving the renewal without its data | 400 | PASS |
| C7b | the renewal's file is replaced by the digital CNH, a PDF | 201 | PASS |
| C7c | the admin reads the CNH file back as a PDF | 200 application/pdf | PASS |
| C7d | the old photo route of the CNH is gone | 404 | PASS |
| C7e | the CNH column is file_media_id, not photo_media_id | file_media_id | PASS |
| C8 | approving the version from before the file | 409 | PASS |
| C9 | the admin approves the renewal with the same number | 200 | PASS |
| C10 | the previous CNH is superseded, keeps its decision and has no file | APPROVED:true: | PASS |
| C11 | its file is deleted from the disk | no | PASS |
| C12 | DRIVER_LICENSE comes from the renewal | 200 | PASS |
| C13 | the driver lists only the renewal | 200 | PASS |

== The CNH dates stay within limits
| id | scenario | expected | result |
|---|---|---|---|
| J0 | the driver sends another CNH to check the dates on | 201 | PASS |
| J1 | approving a CNH issued tomorrow | 400 | PASS |
| J2 | approving a CNH that expired yesterday | 400 | PASS |
| J3 | approving a CNH valid for ten years and a day | 400 | PASS |
| J4 | the CNH is still pending, without data | PENDING: | PASS |
| J4b | the owner removes the pending CNH, so the run can be repeated | 204 | PASS |
| J5 | the CNH describes its PDF | 200 | PASS |

== A registration number belongs to one driver
| id | scenario | expected | result |
|---|---|---|---|
| C14 | approving another driver's CNH with the same number | 409 | PASS |
| C14b | that CNH is still pending | PENDING | PASS |
| C15 | two admins approve CNHs of two drivers with the same new number at once, 8 times: one is approved, the other refused, every time | 200 409;200 409;200 409;200 409;200 409;200 409;200 409;200 409; | PASS |
| C16 | each number belongs to a single driver | 1 1 1 1 1 1 1 1  | PASS |

== Correcting the data of the approved CNH
| id | scenario | expected | result |
|---|---|---|---|
| CC1 | the reviewer corrects the category and adds the issuing state | 200 | PASS |
| CC2 | a correction to category B is refused | 400 | PASS |
| CC3 | the owner cannot correct | 403 | PASS |
| CC4 | the reviewer puts category D back | 200 | PASS |

== The onboarding reads the CNH review
| id | scenario | expected | result |
|---|---|---|---|
| C17 | a pending CNH counts as sent | 200 | PASS |
| C18 | the admin rejects the CNH | 200 | PASS |
| C19 | the onboarding reports cnh_rejected | 200 | PASS |
| C20 | a new CNH supersedes the rejected one | 201 | PASS |
| C21 | and the step counts it as sent again | 200 | PASS |

== The seed passes the credentials
| id | scenario | expected | result |
|---|---|---|---|
| C22 | every seed CNH is approved | 0 | PASS |
| C23 | the seed driver's van passes | 200 | PASS |

## PR 7 — one evaluation for the approval

== Approving and rejecting at the same time
| id | scenario | expected | result |
|---|---|---|---|
| K1x | an approval and a rejection of the same driver at once: one decision and one 400 | 200 400 | PASS |
| K2x | the driver holds one of the two decisions | 1 | PASS |
| K3x | approving a driver that is no longer under review | 400 | PASS |

== The service date is São Paulo's
| id | scenario | expected | result |
|---|---|---|---|
| Z1x | this run's JVM date differs from São Paulo's (QA_JVM_ZONE=Pacific/Kiritimati) | differ | PASS |
| Z2x | valid until yesterday in São Paulo fails, though it is still that day for the JVM | false | PASS |
| Z3x | valid until today in São Paulo passes | true | PASS |

## PR 8 — e-mails after the commit and a code on the onboarding rejection

== The driver hears about a rejected submission
| id | scenario | expected | result |
|---|---|---|---|
| M1 | the admin rejects the van's inspection as ILLEGIBLE | 200 | PASS |
| M2 | the e-mail names the inspection, the plate, the reason and the message, in pt-BR | yes | PASS |
| M3 | the inspection records notified_at | t | PASS |
| M4 | a driver document rejected for another reason: the name and the admin's message | yes | PASS |
| M5 | the driver document records notified_at | t | PASS |
| M6 | a rejected CNH is mailed with the reason of its code | yes | PASS |
| M7 | the CNH records notified_at | t | PASS |

== The rejection of the onboarding has a code
| id | scenario | expected | result |
|---|---|---|---|
| O1 | a rejection without a code | 400 | PASS |
| O2 | OTHER without a reason | 400 | PASS |
| O3 | an unknown code | 400 | PASS |
| O4 | the refused rejections changed nothing | UNDER_REVIEW | PASS |
| O5 | a duplicate account needs no reason | 200 | PASS |
| O6 | the code is stored | DUPLICATE_ACCOUNT: | PASS |
| O7 | the rejection e-mail, sent after the commit, carries the reason of the code | yes | PASS |
| O8 | a single rejection e-mail | 1 | PASS |
| O9 | the driver's onboarding shows the code and its text, with no message written | 200 | PASS |

## PR 9 — the admin's queue and counts

== The four tabs and the counts
| id | scenario | expected | result |
|---|---|---|---|
| Q1x | the counts | 200 | PASS |
| Q2x-AWAITING_DECISION | the AWAITING_DECISION tab totals what /counts says | 0 | PASS |
| Q2x-PENDING_DOCUMENTS | the PENDING_DOCUMENTS tab totals what /counts says | 1 | PASS |
| Q2x-EXPIRING | the EXPIRING tab totals what /counts says | 1 | PASS |
| Q2x-ALL | the ALL tab totals what /counts says | 7 | PASS |
| Q3x | a row has the situation, no CPF and no phone | 200 | PASS |
| Q4x | the badge counts the drivers waiting for the admin | 1 | PASS |
| Q5x | awaiting decision: only drivers under review, the driver's turn last | 200 | PASS |
| Q6x | awaitingDriver counts the driver's turn | 0 | PASS |

== The search
| id | scenario | expected | result |
|---|---|---|---|
| S1x | by the plate with a hyphen | 200 | PASS |
| S2x | by the plate in lower case without hyphen | 200 | PASS |
| S3x | by the e-mail, any case | 200 | PASS |
| S4x | a search that matches nobody | 200 | PASS |
| S5x | the same page twice is the same | {"content":[{"token":"79f51985b8cc4faab6fa59e67","name":"Motorista Recusado","email":"qa.recusado.1795316016@vanep.test","city":null,"vehicleCount":0,"approvalStatus":"PENDING","submittedAt":null,"createdAt":"2026-11-22T02:53:47.484878Z","nextAction":"ADMIN","canApprove":false,"pendingReviewCount":1,"oldestPendingAt":"2026-11-22T02:53:48.024565Z","rejectedCount":0,"missingCount":2,"expiredCount":0,"nextExpiryOn":null},{"token":"582f108cd923410aad1806616","name":"Motorista QA","email":"qa.corrida.b.1795316016@vanep.test","city":null,"vehicleCount":0,"approvalStatus":"PENDING","submittedAt":null,"createdAt":"2026-11-22T02:53:43.544144Z","nextAction":"DRIVER","canApprove":false,"pendingReviewCount":0,"oldestPendingAt":null,"rejectedCount":0,"missingCount":3,"expiredCount":0,"nextExpiryOn":null}],"empty":false,"first":true,"last":false,"number":0,"numberOfElements":2,"pageable":{"offset":0,"pageNumber":0,"pageSize":2,"paged":true,"sort":{"empty":true,"sorted":false,"unsorted":true},"unpaged":false},"size":2,"sort":{"empty":true,"sorted":false,"unsorted":true},"totalElements":7,"totalPages":4} | PASS |
| S6x | an unknown tab | 400 | PASS |

== The queue is current at every read
| id | scenario | expected | result |
|---|---|---|---|
| E1x | a document expired yesterday puts the driver in EXPIRING on the next read, with nothing run | 200 | PASS |
| E2x | putting the date back takes it out again | false | PASS |

== Who reads the queue
| id | scenario | expected | result |
|---|---|---|---|
| W1x | a driver | 403 | PASS |
| W2x | a client | 403 | PASS |
| W3x | no token | 401 | PASS |

== R10: the queue with 1,000 more drivers
| id | scenario | expected | result |
|---|---|---|---|
| L1x | 1,000 drivers were added, each with a van and its documents | 1000 | PASS |
| L2x | the counts see the 1,000 new drivers | true | PASS |
| L3x | the search finds a plate among them | 1 | PASS |
| L4x | every tab, the counts and the search answer | 200 200 200 200 200 200  | PASS |
