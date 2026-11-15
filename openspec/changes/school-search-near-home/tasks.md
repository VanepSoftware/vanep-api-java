## 1. Specification and tests

- [x] 1.1 Document the source, coordinate semantics, and API contract.
- [x] 1.2 Add mapper and controller assertions for city coordinates.

## 2. Database and seed

- [x] 2.1 Add paired, range-checked city coordinate columns.
- [x] 2.2 Generate the 5,571-row catalog from the official IBGE 2022 GeoPackage.
- [x] 2.3 Load existing rows in Flyway and new rows in `CitySeeder`.

## 3. API

- [x] 3.1 Map coordinates in city responses.
- [x] 3.2 Map city coordinates in generic and personal address responses.

## 4. Verification

- [x] 4.1 Run focused tests, `make lint`, and `./mvnw verify -B`.
- [x] 4.2 Run real QA against a throwaway PostgreSQL database and record evidence in `qa.md`.
