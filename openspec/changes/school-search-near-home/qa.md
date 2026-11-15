# QA — school-search-near-home

Latest run: API built from `feat/van-76-city-coordinates`. Run date: 2026-11-03; migrations renumbered from V61/V62 to V72/V73 on 2026-11-15 after rebasing onto `main`, see the re-run section at the end. Database: throwaway PostgreSQL 17 container on loopback. No request called Google.

## Source integrity

The catalog was generated from the official IBGE 2022 `Localidades_do_Brasil` GeoPackage. It contains 5,571 unique codes: 5,570 city codes plus the official Fernando de Noronha state-district point. CSV SHA-256: `F43D5DAD6320FD8B223AB78B385FBC59B6D0ADCC576B7979CA9118E14CC323B0`.

## Upgrade path

Flyway first migrated an empty database through V71. A Brasília city row with `ibge_code = 5300108` was inserted without coordinates, then V72 and V73 were applied.

| Scenario | Expected | Result |
|---|---|---|
| V73 updates an existing Brasília row | `-15.784300,-47.908100` | PASS |
| latitude without longitude | check constraint rejects | PASS |
| latitude `91` | range constraint rejects | PASS |

## Fresh database and runtime response

The packaged application migrated a fresh database through V73 and ran the real geographic seed. The seed logged `5571 cities created from IBGE dump`.

| Scenario | Expected | Result |
|---|---|---|
| seeded cities | 5,571 | PASS |
| seeded cities with latitude | 5,571 | PASS |
| seeded cities with longitude | 5,571 | PASS |
| seeded cities with a partial/null coordinate | 0 | PASS |
| authenticated `GET /api/cities?uf=DF&search=brasilia` | Brasília with both coordinates | PASS |

Observed JSON fields:

```json
{"name":"Brasília","stateUf":"DF","cityLatitude":-15.784300,"cityLongitude":-47.908100}
```

The throwaway container was removed after the run.

## Re-run after renumbering to V72/V73 (2026-11-15)

Branch fast-forwarded to `main` (`cfd4a56`); migrations renamed from V61/V62 to V72/V73 with unchanged SQL. Database: fresh throwaway PostgreSQL 17 container on loopback, removed after the run. No request called Google.

| Check | Expected | Result |
|---|---|---|
| `make lint` (spotless) | clean | PASS |
| `./mvnw verify -B` | all tests pass | PASS (2145 tests, 0 failures) |
| Flyway on an empty database | 72 migrations applied, schema at v73 | PASS |
| real seed | `5571 cities created from IBGE dump` | PASS |
| seeded cities / with latitude / with a partial coordinate | 5,571 / 5,571 / 0 | PASS |
| Brasília (`5300108`) | `-15.784300,-47.908100` | PASS |
| São Paulo (`3550308`) | `-23.554800,-46.578999` | PASS |
| address responses carry the city point | mobile app received the points above from dependent and personal address responses | PASS (see the mobile change `qa.md`) |

Not repeated in this pass: the upgrade of a pre-existing city row and the check-constraint rejections in the tables above. Those results are from 2026-11-03 under the old version numbers.

Note for anyone who built this branch before the rename: stale `target/classes/db/migration/V61__add_city_coordinates.sql` and `V62__load_ibge_city_coordinates.sql` make Flyway fail with `Found more than one migration with version 61`. Run `./mvnw clean` (or delete the two files) once.
