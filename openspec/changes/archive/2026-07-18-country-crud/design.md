## Context

The application needs a country entity to serve as the root for geographical data (`Country -> State -> City -> Address`). Currently, `state` exists, but lacks a foreign key link to a `country` table. Additionally, `UserModel` and the user table already contain references to `country_id` which are currently unconstrained.

An untracked draft migration `V15__create_country_table.sql` exists locally but has errors (it attempts to recreate the `state` table instead of creating the `country` table, and references a non-existent `country` table).

## Goals / Non-Goals

**Goals:**
- Correctly define the `country` table in database migrations.
- Alter the existing `state` table to link it to `country` via a mandatory foreign key constraint.
- Handle data migration for existing states by linking them to a default country (Brasil).
- Create a complete REST CRUD API for countries under `/api/countries` (GET, POST, PUT, DELETE, POST restore).
- Protect the endpoints using Spring Security with appropriate permissions.
- Seed default country data on application startup.

**Non-Goals:**
- Modifying other components of the application to query/filter by country (except linking state and updating users).
- Supporting multi-tenancy or complex country-specific rule engines in this phase.

## Decisions

### 1. Database Migration Strategy (`V15__create_country_table.sql`)
- **Decision:** Modify `V15__create_country_table.sql` to perform the following:
  1. Create the `country` table with soft delete and tracking columns (`id`, `token`, `name`, `is_active`, `created_at`, `updated_at`, `deleted_at`).
  2. Insert a default country (`Brasil`, with token `'brasil'`) to support data integrity.
  3. Alter the `state` table to add a nullable `country_id` column, update all existing state records to reference the default country (`Brasil`), alter the `country_id` column to `NOT NULL`, and add a foreign key constraint pointing to `country(id)`.
  4. Create unique partial indexes on the `country` table for `token` and `name` where `deleted_at IS NULL`.
- **Alternatives Considered:** Create `country` but don't link it to `state` (rejected because it leaves the geographical hierarchy disconnected).

### 2. Entity Design & Soft Delete
- **Decision:** Map `CountryModel` to the `country` table using Hibernate `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)`.
- Use a `@PrePersist` hook to auto-generate a 32-character hexadecimal token (using `UUID`) if one is not provided.
- **Alternatives Considered:** Hard delete (rejected to align with the soft delete pattern used across `state`, `city`, `address`, etc.).

### 3. Service Layer & Translation Messages
- **Decision:** Validation errors and not found messages will be defined in `messages.properties` and `messages_pt_BR.properties`.
- Keys to add:
  - `country.name.duplicate`: "There is already a country with this name." / "Já existe um país com este nome."
  - `country.already_active`: "The country is already active." / "O país já está ativo."
  - `country.not_found`: "Country not found." / "País não encontrado."

### 4. Route Security & Permissions
- **Decision:** Register permissions in `PermissionEnum`:
  - `list_countries`, `show_country`, `create_country`, `update_country`, `delete_country`, `restore_country`.
  - Admin users will have full CRUD permissions. Clients and drivers will be granted `list_countries` and `show_country` (if they need it, or we can restrict it to admin-only based on default access). Based on state/city pattern, we will allow read access to authenticated users.

## Risks / Trade-offs

- **[Risk] Migration failure on existing state data** → **Mitigation:** Ensure the migration script updates all existing states to a valid default `country_id` (Brasil) before making the column `NOT NULL` and adding the foreign key.
- **[Risk] State Seeder failures** → **Mitigation:** Update `StateSeeder.java` to fetch the seeded `country` and associate it with states during the seed phase.
