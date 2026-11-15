# QA — driver-students-family (constitution rule 55)

Run from `feat/VAN-57-driver-students-family`. Run date: 2026-11-08. Result: **70/70 passed**.

## How to run

The script writes rows (passengers, dependents, links, a contract) and must only point at a throwaway database seeded on an empty schema.

```bash
# 1. Throwaway Postgres (5435) + the API from the branch (8082), seed on
scripts/qa/driver-students-family/local-env.sh start

# 2. The QA (needs curl, jq and docker)
scripts/qa/driver-students-family/qa.sh

# 3. Clean up
scripts/qa/driver-students-family/local-env.sh stop
```

Tokens come from the mobile password grant on `/oauth2/token` for Fabio and Gustavo (drivers), Bruno, Carla and Diego (clients) and the admin, so every request carries the real `uid` and the real permission bundles.

`local-env.sh demo` leaves Fabio with one student in each family state (not invited, invited, family joined), for testing the app by hand.

## Findings fixed during the run

- **Concurrent merges all answered 200** (E8). Merge and dismiss now move the match with `UPDATE ... WHERE status = 'PENDING'`; only the request that changes the row goes on, the others get 409.
- **Concurrent first invites and concurrent acceptances** would hit the partial unique indexes and answer 500. They now answer 409 `family_invite.concurrent_request` (B13, D10); a retry reads what the winner wrote.

## Results

### Migration
| id | scenario | expected | result |
|---|---|---|---|
| M1 | V69 and V70 applied by Flyway | 69 70 | PASS |

### Van of the student
| id | scenario | expected | result |
|---|---|---|---|
| A1 | create in own van | 201 | PASS |
| A2 | another driver's van refused | 422 | PASS |
| A2b | nothing created | 1 | PASS |
| A3 | unknown van refused | 422 | PASS |
| A4 | null vehicleToken clears the van, keeps the rest | 200 | PASS |
| A5 | vehicle-only patch sets the van | 200 | PASS |
| A6 | wrong JSON type refused | 400 | PASS |
| A7 | occupancy with another driver's van | 404 | PASS |
| A8 | occupancy filtered by own van | 200 | PASS |

### Invite
| id | scenario | expected | result |
|---|---|---|---|
| B1 | first invite | 200 | PASS |
| B2 | resend keeps the code, refreshes sentAt | 200 | PASS |
| B3 | invitedAt on the passenger | 200 | PASS |
| B4 | another driver | 404 | PASS |
| B5 | a client | 403 | PASS |
| B6 | no token | 401 | PASS |
| B7 | invalid token | 401 | PASS |
| B8 | unknown passenger | 404 | PASS |
| B9 | one invite row for the passenger | 1 | PASS |
| B10 | group link | 200 | PASS |
| B11 | group link is reused | 200 | PASS |
| B12 | group link as a client | 403 | PASS |
| B13 | concurrent first invites never answer 500 | ok | PASS |
| B14 | concurrent first invites leave one invite | 1 | PASS |

### Preview
| id | scenario | expected | result |
|---|---|---|---|
| C1 | anonymous preview | 200 | PASS |
| C2 | group preview has no student | 200 | PASS |
| C3 | unknown code | 404 | PASS |

### Acceptance
| id | scenario | expected | result |
|---|---|---|---|
| D1 | client accepts for own dependent | 200 | PASS |
| D1b | link is PENDING from DRIVER_INVITE | PENDING|DRIVER_INVITE | PASS |
| D2 | repeated acceptance is idempotent | 200 | PASS |
| D2b | one match for the passenger | 1 | PASS |
| D3 | another client's dependent | 404 | PASS |
| D3b | no link created | 3 | PASS |
| D4 | a driver cannot accept | 403 | PASS |
| D5 | missing dependentToken | 400 | PASS |
| D6 | wrong JSON type | 400 | PASS |
| D7 | malformed JSON | 400 | PASS |
| D8 | unknown code | 404 | PASS |
| D9 | accept without token | 401 | PASS |
| D10 | concurrent acceptances never answer 500 | ok | PASS |
| D11 | concurrent acceptances leave one link and one match | 1|1 | PASS |

### Match
| id | scenario | expected | result |
|---|---|---|---|
| E1 | student shows FAMILY_JOINED | FAMILY_JOINED | PASS |
| E2 | match summary on the passenger | 200 | PASS |
| E3 | another driver cannot merge | 404 | PASS |
| E4 | dismiss | 200 | PASS |
| E4b | back to INVITED | INVITED | PASS |
| E5 | merge without pending match | 409 | PASS |
| E6 | dismiss without pending match | 409 | PASS |
| E7 | a new acceptance suggests again | 200 | PASS |
| E8 | concurrent merges: one wins | 200 409 409 | PASS |
| E9 | merged keeps token, takes the dependent's name | 200 | PASS |
| E10 | occupancy counts the merged student with family | 200 | PASS |
| E11 | student is WITH_FAMILY | WITH_FAMILY | PASS |
| E12 | invite after merge | 409 | PASS |
| E13 | accept after merge | 409 | PASS |

### Group link
| id | scenario | expected | result |
|---|---|---|---|
| G1 | group link matches the only same first name at the school | 200 | PASS |
| G1b | match is on that passenger | FAMILY_JOINED | PASS |
| G2 | no passenger with that first name: no match, link kept | 200 | PASS |

### Students list
| id | scenario | expected | result |
|---|---|---|---|
| F1 | both sources, ordered by name | 200 | PASS |
| F2 | filter by van keeps the van and the unassigned | 200 | PASS |
| F3 | another driver's van | 404 | PASS |
| F4 | a client | 403 | PASS |
| F5 | no token | 401 | PASS |
| F6 | another driver sees none of Fabio's | 200 | PASS |

### Contract replaces the merged passenger
| id | scenario | expected | result |
|---|---|---|---|
| H1 | admin activates a contract for the merged dependent | 201 | PASS |
| H2 | student listed once, from the contract | 200 | PASS |
| H3 | merged passenger soft deleted | 1 | PASS |

### Database rules
| id | scenario | expected | result |
|---|---|---|---|
| I1 | one live invite per passenger | rejected by family_invite_passenger_active_key | PASS |
| I2 | one live group link per driver | rejected by family_invite_driver_group_active_key | PASS |
| I3 | one pending match per passenger | rejected by family_match_pending_passenger_key | PASS |

All 70 scenarios passed.
