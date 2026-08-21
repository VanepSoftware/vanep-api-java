## 0. Preparation

- [ ] 0.1 Create branch `feat/owned-address-model` from `main`
- [ ] 0.2 Review `proposal.md`, `design.md`, and specs (`owned-address`, `client-home-address`, `dependent`, `school-owned-address`)
- [ ] 0.3 Confirm next Flyway version is **V20** (after `V19__add_user_profile_edit_columns.sql`)
- [ ] 0.4 Recreate the local Postgres database (drop Docker volume / schema) so V20 applies on empty data — no backfill

## 1. Phase 1 — Schema + AddressService (PR 1)

> Goal: V20 (FK + unique) and upsert/clear/exclusivity in `AddressService`. No owner HTTP yet.
> Depends on: — | Parallel with: —
> Order: test → migration → repository → messages → service

- [ ] 1.1 Persistence tests: two active clients cannot share the same `address_id` after the unique index
- [ ] 1.2 Add `V20__owned_address_foreign_keys.sql`: FKs `address_id → address(id)` and partial unique indexes `WHERE address_id IS NOT NULL AND deleted_at IS NULL` on each owner table — no clone SQL
- [ ] 1.3 Update table/column comments: address belongs to one owner, not a platform catalog; adjust `clean.sql` only if FK order requires it
- [ ] 1.4 Unit tests for `upsertForClient` / `upsertForDependent` / `upsertForSchool`: first save creates+links; second save updates same id; clear calls `delete` and nulls pointer; unknown `cityToken` → 404 `city.not_found`; already owned → 409 `address.already_owned`; count exclusivity uses only active owners
- [ ] 1.5 MessageSource keys EN + `messages_pt_BR.properties` (`address.already_owned`; reuse `address.not_found` / `city.not_found`)
- [ ] 1.6 Implement upsert/clear and cross-table **active** ownership count on `AddressService`; repository queries without N+1
- [ ] 1.7 `make lint` + `./mvnw verify`; open PR phase 1 (pt-BR, lint/test status)

## 2. Phase 2 — Client `/me` address (PR 2)

> Goal: client home address on `/me` and dedicated write endpoints. Dependent and school unchanged. Catalog `/api/addresses` still exists until phase 5.
> Depends on: Phase 1 | Parallel with: Phases 3, 4
> Order: test → DTO → service → controller

- [ ] 2.1 Failing unit + MockMvc for `GET /api/clients/me` nested address/null; `PUT/DELETE /api/clients/me/address` (200/401/204 idempotent); `PUT /api/clients/{token}` without `addressToken`
- [ ] 2.2 Nested `AddressResponseDTO` on me/list/get; remove `addressToken` from update DTO; `ClientService`/`ClientMapper`/`ClientController` (`isAuthenticated()` + CLIENT like `getMyProfile`); `ClientService.delete` calls `clearForClient` before deleting the client
- [ ] 2.3 `make lint` + `./mvnw verify`; open PR phase 2

## 3. Phase 3 — Dependent PATCH JsonNullable + owned address (PR 3)

> Goal: rewrite dependent PATCH merge (`JsonNullable` on all mutable fields); nested owned pickup address; D10 on delete/restore.
> Depends on: Phase 1 | Parallel with: Phases 2, 4
> Order: test → DTO → mapper/service → controller

- [ ] 3.1 Failing unit + MockMvc — create/patch nested address distinct from client home; omit no-op; `"address": null` clears; `"phone": null` clears; `schoolToken` present (value or null) still 400; no `addressToken` on DTO; delete dependent clears address; restore dependent has `address` null
- [ ] 3.2 `DependentUpdateDTO` with `JsonNullable` on **name, birthDate, gender, document, phone, email, isSelf, isDefault, shift, address**; compact `undefined()` like `UserProfileUpdateRequestDTO`; nested `AddressRequestDTO` / `AddressResponseDTO`; drop `addressToken` and treat `schoolToken` as present → 400
- [ ] 3.3 Replace mapper `applyUpdate` (`if (getX() != null)`) with `isPresent()` merge in `DependentService`: name present blank/null → 400; isSelf/isDefault/shift present null → 400; birthDate/gender/document/phone/email explicit null → clear; document duplicate 409 excluding this token; same document resent → 200; `isDefault` present keeps existing RN12 (`isPresent()`, not `Boolean.equals` on a raw Boolean); `delete`/`restore` follow D10
- [ ] 3.4 **Regression (named):** PATCH body only `{ "name": "Novo" }` on a dependent that has phone, email, birthDate, and address → those remain unchanged (proves omit ≠ null)
- [ ] 3.5 `make lint` + `./mvnw verify`; open PR phase 3

## 4. Phase 4 — School PATCH + owned address (PR 4)

> Goal: rewrite school update as PATCH (PUT removed); nested owned address; full JsonNullable merge.
> Depends on: Phase 1 | Parallel with: Phases 2, 3
> Order: test → DTO → service → controller

- [ ] 4.1 School address HTTP: failing unit + MockMvc — POST create with nested address; PATCH nested address updates; PATCH `"address": null` clears; PUT `/api/schools/{token}` is not mapped; no `addressId` in JSON; delete school clears address; restore school has `address` null
- [ ] 4.2 School PATCH merge DTO: `SchoolUpdateRequestDTO` with `JsonNullable` on **name, cnpj, phone, email, address**; compact `undefined()` like `UserProfileUpdateRequestDTO`; `PATCH` + `update_school`; remove `PUT`; do not reuse create `applyRequest`
- [ ] 4.3 School PATCH merge rules in `SchoolService`: name present blank/null → 400; name omitted → keep; cnpj/phone/email explicit null → clear; cnpj duplicate 409 excluding this school; same cnpj resent → 200; `delete` calls `clearForSchool` before deleting the school
- [ ] 4.4 **Regression (named):** PATCH body only `{ "name": "Novo" }` on a school that has cnpj, phone, email, and address → those four remain unchanged (proves omit ≠ null)
- [ ] 4.5 `make lint` + `./mvnw verify`; open PR phase 4

## 5. Phase 5 — Remove catalog + seeder 1:1 (PR 5)

> Goal: no `/api/addresses`; no catalog permissions; seed always linked.
> Depends on: Phases 2, 3, and 4 | Parallel with: —
> Order: test (404 no mapping) → delete controller → permissions → seeder

- [ ] 5.1 MockMvc (or slice) asserting `GET/POST /api/addresses` is not mapped (404), including with admin JWT
- [ ] 5.2 Delete `AddressController` and `AddressControllerTest`
- [ ] 5.3 Remove `LIST_ADDRESSES`, `SHOW_ADDRESS`, `CREATE_ADDRESS`, `UPDATE_ADDRESS`, `DELETE_ADDRESS` from `PermissionEnum` and ADMIN (and any) bundles; drop dead strings from stored permission lists in seed
- [ ] 5.4 Update `AddressSeeder` / entity seeders so each seed client/dependent/school gets its own **linked** address row (same CEP allowed); no unlinked seed rows
- [ ] 5.5 Remove `existsByZipCodeAndNumber` if unused (dead code)
- [ ] 5.6 `make lint` + `./mvnw verify`; open PR phase 5
