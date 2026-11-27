# QA — remove-app-mocks (VAN-95, constitution rule 55)

Latest run: API built from `fix/remove-mocks`, merged with `main`. Run date: 2026-11-27. Result: **57/57 passed**.

## How to run

The script signs up two assistants and changes invites, vehicles, documents, schedules and contracts, so it must only point at a throwaway database seeded on an empty schema. Run it once per fresh environment. It needs Docker, curl and jq (no local Java).

```bash
# 1. Throwaway Postgres + the API image built from the branch, seeded, on 8082
scripts/qa/remove-app-mocks/local-env.sh start

# 2. The QA
scripts/qa/remove-app-mocks/qa.sh

# 3. Clean up (all data is lost)
scripts/qa/remove-app-mocks/local-env.sh stop
```

Tokens come from the mobile password grant (`/oauth2/token`) for the drivers, the clients and the assistants. The assistants use the public sign-up endpoint; only `users.verified` is set in the database, because the environment sends no mail. Rate limiting is disabled in this environment.

## Scope

All new reads are `GET`. Covered: happy path per actor, no token, invalid token, wrong role, another user's resource, missing, blank and unknown parameters, inactive van, expired invite, paused and ended links, parallel reads. Request-body misuse does not apply (no new body).

## Results

### A6/A7 assistant without a driver

| id | scenario | expected | result |
|---|---|---|---|
| U1 | profile before any invite | 200 | PASS |
| U2 | no vans while unlinked | 200 | PASS |
| U3 | students refused while unlinked | 404 | PASS |

### A5 pending invites on the driver list

| id | scenario | expected | result |
|---|---|---|---|
| P1 | empty list before inviting | 200 | PASS |
| P2 | driver invites the assistant | 201 | PASS |
| P3 | pending invite listed with the typed e-mail only | 200 | PASS |
| P4 | another driver does not see the invite | 200 | PASS |
| P5 | expired invite is not listed | 200 | PASS |
| P6 | no vans while the invite is pending | 200 | PASS |
| P7 | students refused while pending | 404 | PASS |
| P8 | list without token | 401 | PASS |
| P9 | list with invalid token | 401 | PASS |
| P10 | list as a client | 403 | PASS |
| P11 | assistant accepts | 204 | PASS |
| P12 | after accepting: one ACTIVE item with the account name | 200 | PASS |

### A6 my vans

| id | scenario | expected | result |
|---|---|---|---|
| V1 | active profile | 200 | PASS |
| V2 | one item per shift, on the driver's main van | 200 | PASS |
| V3 | the morning runs today with a departure time | 200 | PASS |
| V4 | another assistant sees nothing | 200 | PASS |
| V5 | without token | 401 | PASS |
| V6 | invalid token | 401 | PASS |
| V7 | a driver is refused | 403 | PASS |
| V8 | a client is refused | 403 | PASS |
| V9 | parallel reads | 200 200 200 | PASS |

### A7 students of my van

| id | scenario | expected | result |
|---|---|---|---|
| S1 | morning students, numbered from 1 | 200 | PASS |
| S2 | shift without passengers | 200 | PASS |
| S3 | missing shift | 400 | PASS |
| S4 | blank shift | 400 | PASS |
| S5 | unknown shift | 400 | PASS |
| S6 | another driver's van | 404 | PASS |
| S7 | unknown van | 404 | PASS |
| S8 | assistant of nobody | 404 | PASS |
| S9 | without token | 401 | PASS |
| S10 | a driver is refused | 403 | PASS |
| S11 | inactive van | 404 | PASS |

### A8/A10 the client's day

| id | scenario | expected | result |
|---|---|---|---|
| C1 | today's van with the child, vehicle and times | 200 | PASS |
| C2 | dependent without contract is listed | 200 | PASS |
| C3 | valid inspection is not reported | 200 | PASS |
| C4 | another client sees none of it | 200 | PASS |
| C5 | without token | 401 | PASS |
| C6 | invalid token | 401 | PASS |
| C7 | a driver is refused | 403 | PASS |
| C8 | an assistant is refused | 403 | PASS |
| C9 | parallel reads | 200 200 200 | PASS |

### A12 expired inspection

| id | scenario | expected | result |
|---|---|---|---|
| E1 | expired inspection reported with driver and date | 200 | PASS |

### A9 no route today

| id | scenario | expected | result |
|---|---|---|---|
| N1 | no van today, next five days returned | 200 | PASS |
| N3 | contract starting soon: no van today, covered child not listed as without van | 200 | PASS |
| N4 | contract starting beyond the window: child still without van | 200 | PASS |
| N2 | no ACTIVE contract: nothing today, both children without van | 200 | PASS |

### A6 paused and former links

| id | scenario | expected | result |
|---|---|---|---|
| L1 | driver pauses | 204 | PASS |
| L2 | no vans while paused | 200 | PASS |
| L3 | students refused while paused | 404 | PASS |
| L4 | driver resumes | 204 | PASS |
| L5 | assistant leaves | 204 | PASS |
| L6 | one former link counted | 200 | PASS |
| L7 | no vans after leaving | 200 | PASS |
| L8 | students refused after leaving | 404 | PASS |

## Key responses

**V2**

```json
[{"token":"bc116a86fd3f4dc5921c8923b","driverToken":"77e2015e390b4a13bfac8285a","driverName":"Fabio Teixeira","plate":"VAN1A23","model":"Sprinter 415","shift":"MORNING","departureTime":"06:40:00","studentCount":1,"hasRouteToday":true},{"token":"bc116a86fd3f4dc5921c8923b","driverToken":"77e2015e390b4a13bfac8285a","driverName":"Fabio Teixeira","plate":"VAN1A23","model":"Sprinter 415","shift":"AFTERNOON","departureTime":"12:10:00","studentCount":1,"hasRouteToday":true}]
```

**S1**

```json
[{"token":"c7a24a9c160247eeb4ea49353","name":"Lucas Souza","stopNumber":1,"placeName":"Centro","hasFamilyInApp":true}]
```

**C1**

```json
{"date":"2026-11-26","vans":[{"driverToken":"77e2015e390b4a13bfac8285a","driverName":"Fabio Teixeira","vehicleModel":"Sprinter 415","plate":"VAN1A23","shift":"MORNING","departureTime":"06:40:00","riders":[{"dependentToken":"7dbc1f72af2f4357a2a1b7ecb","name":"Lucas Souza"}],"boardingStart":"06:40:00","boardingEnd":"06:40:00","schoolDeparture":null},{"driverToken":"77e2015e390b4a13bfac8285a","driverName":"Fabio Teixeira","vehicleModel":"Sprinter 415","plate":"VAN1A23","shift":"AFTERNOON","departureTime":"12:10:00","riders":[{"dependentToken":"7dbc1f72af2f4357a2a1b7ecb","name":"Lucas Souza"}],"boardingStart":null,"boardingEnd":null,"schoolDeparture":"12:10:00"}],"upcoming":[],"childrenWithoutVan":[{"dependentToken":"4c7bc880c870486cb3d8f1f47","name":"Marina Souza"}],"expiredInspections":[]}
```

**N1**

```json
{"date":"2026-11-26","vans":[],"upcoming":[{"date":"2026-11-27","going":true,"returning":true,"firstPickup":"06:40:00"},{"date":"2026-11-30","going":true,"returning":true,"firstPickup":"06:40:00"},{"date":"2026-12-01","going":true,"returning":true,"firstPickup":"06:40:00"},{"date":"2026-12-02","going":true,"returning":true,"firstPickup":"06:40:00"},{"date":"2026-12-03","going":false,"returning":false,"firstPickup":null}],"childrenWithoutVan":[{"dependentToken":"4c7bc880c870486cb3d8f1f47","name":"Marina Souza"}],"expiredInspections":[]}
```
