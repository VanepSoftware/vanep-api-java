## Why

The country domain tests and integration tests specifically scoped to the `country` feature require dedicated test suites and setups. To ensure full compliance with the project guidelines without altering non-country feature tests, we need to add and adjust tests exclusively for the country feature.

## What Changes

- Add comprehensive unit and integration tests specifically for `country` domain features (`CountryControllerTest`, `CountrySeederTest`, `CountryServiceTest`).
- Ensure all country-related test fixtures properly instantiate and validate country entities (token generation, active state, CRUD operations).
- Do NOT alter tests belonging to non-country features (such as address, city, state, driver, client, etc.).

## Capabilities

### New Capabilities
- `country-tests`: Unit and integration testing for country domain endpoints, service methods, and seeder logic.

### Modified Capabilities
<!-- None -->

## Impact

- `src/test/java/br/com/vanep/country/**`: Test classes dedicated exclusively to the country domain.
