## 1. Database & Configuration

- [x] 1.1 Modify `V15__create_country_table.sql` to create the `country` table, seed default country, alter `state` to add `country_id`, link existing records, set NOT NULL constraint, and add foreign key/unique index.
- [x] 1.2 Update `messages.properties` and `messages_pt_BR.properties` with validation/error translation keys for countries.
- [x] 1.3 Update `PermissionEnum.java` to include country-related authorization permissions (`list_countries`, `show_country`, `create_country`, `update_country`, `delete_country`, `restore_country`).

## 2. Models & Repositories

- [x] 2.1 Implement `CountryModel.java` JPA entity with soft delete support and token auto-generation in `@PrePersist`.
- [x] 2.2 Implement `CountryRepository.java` with JpaRepository, custom queries to restore soft-deleted records, and check deleted state.
- [x] 2.3 Update `StateModel.java` to add a `@ManyToOne` mapping for `CountryModel`.

## 3. DTOs & Mappers

- [x] 3.1 Implement `CountryRequestDTO.java` record with validation rules.
- [x] 3.2 Implement `CountryResponseDTO.java` record.
- [x] 3.3 Implement `CountryMapper.java` manual mapping utility.

## 4. Service & Controller

- [x] 4.1 Implement `CountryService.java` containing transaction-aware create, read, update, delete, and restore methods.
- [x] 4.2 Implement `CountryController.java` exposing API endpoints and securing them with `@PreAuthorize` annotations.

## 5. Seeding & Tests

- [x] 5.1 Implement `CountrySeeder.java` to automatically seed default countries if enabled.
- [x] 5.2 Update `StateSeeder.java` to map seeded states to the seeded country.
- [x] 5.3 Wire up `CountrySeeder` into `DataSeeder.java`.
- [x] 5.4 Run Maven clean test/verify to ensure country CRUD operates correctly and tests pass.
