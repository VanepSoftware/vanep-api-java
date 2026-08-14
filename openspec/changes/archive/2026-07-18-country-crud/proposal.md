## Why

Currently, the application has geographic entities like `state`, `city`, and `address`, but lacks the root `country` entity. Users and profiles (such as `UserModel`) already reference `country_id` in their models/database schema. Introducing a complete CRUD for Country allows the application to manage countries, enabling users to be correctly registered under their respective countries and allowing the geographic hierarchy (Country -> State -> City -> Address) to be complete and fully validated.

## What Changes

- **Database migration**: Create `country` table and alter `state` table to add a foreign key constraint linking it to `country`.
- **Backend Components**:
  - `CountryModel` (JPA Entity with soft delete support)
  - `CountryRepository` (JpaRepository with token-based lookups and custom queries for restoring)
  - `CountryService` (CRUD business logic, validation, and error messages)
  - `CountryController` (Endpoints mapping GET, POST, PUT, DELETE, and POST restore, secured with Spring Security `@PreAuthorize` permissions)
  - `CountryRequestDTO` and `CountryResponseDTO` (Java records for request validation and response mapping)
  - `CountryMapper` (Manual mapper component)
- **Security & Authorization**: Add country permissions to `PermissionEnum` and auto-seed them for administrative roles.
- **Seeding**: Initialize country seed data (e.g., "Brasil") so existing states can be correctly associated.

## Capabilities

### New Capabilities
- `country-crud`: Defines endpoints and backend structures to manage (create, read, update, delete, restore) country records in the system.

### Modified Capabilities
<!-- None -->

## Impact

- **New REST API Endpoints**: `/api/countries` (CRUD operations).
- **Database Schema**:
  - New table `country`.
  - Altered table `state` (adds `country_id` column).
- **Spring Security**: Adds new permissions (`create_country`, `list_countries`, `show_country`, `update_country`, `delete_country`, `restore_country`) to the authorization system.
