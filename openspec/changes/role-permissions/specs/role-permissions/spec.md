## ADDED Requirements

### Requirement: Role identity enum

The system SHALL define a `RoleName` enum with exactly three values — `ADMIN`, `CLIENT`, `DRIVER` — and add a nullable, unique `role_name` column to `role`, populated only for the three system-seeded rows. This tag is independent from the existing free-text, admin-editable `name` column (unaffected by this change): renaming a role's `name` SHALL NOT change its `role_name` tag or break lookups by it.

#### Scenario: Lookup by role name is stable across renames

- **WHEN** the system looks up the role tagged `RoleName.CLIENT`
- **THEN** it SHALL return that role regardless of the current value of its free-text `name`

#### Scenario: Existing role CRUD is unaffected

- **WHEN** an admin creates, updates, or deletes a role through the existing `RoleController` endpoints
- **THEN** the operation SHALL behave exactly as before this change; `role_name` is not exposed or settable through that API

### Requirement: New Client and Driver users are auto-assigned their system role

When a `Client` is created (via signup or seed), the system SHALL set the owning `User`'s `role_id` to the role tagged `RoleName.CLIENT`. When a `Driver` is created (via signup or seed), the system SHALL set the owning `User`'s `role_id` to the role tagged `RoleName.DRIVER`.

#### Scenario: Client signup assigns the CLIENT role

- **WHEN** a new user completes client signup (`RegistrationService.registerClient`)
- **THEN** the created `User`'s `role_id` SHALL reference the role tagged `RoleName.CLIENT`

#### Scenario: Driver signup assigns the DRIVER role

- **WHEN** a new user completes driver signup (`RegistrationService.registerDriver`)
- **THEN** the created `User`'s `role_id` SHALL reference the role tagged `RoleName.DRIVER`

#### Scenario: Seeded clients and drivers are also assigned

- **WHEN** `DataSeeder` creates client or driver seed users
- **THEN** each seeded `User`'s `role_id` SHALL be set the same way as the signup path

### Requirement: Driver seed data

The system SHALL seed at least one `Driver` record (`DataSeeder.seedDrivers()`), created with `approval_status = APPROVED` and active, so a usable driver test account exists without going through the (not-yet-built) admin approval flow. Seeding SHALL be idempotent.

#### Scenario: Seeding creates an approved driver

- **WHEN** the seeder runs on an empty database
- **THEN** it SHALL create a `Driver` whose `approval_status` is `APPROVED` and whose `User.role_id` references the role tagged `RoleName.DRIVER`

#### Scenario: Seeding is idempotent

- **WHEN** the seeder runs again against a database that already has the seeded driver(s)
- **THEN** it SHALL not create duplicates

### Requirement: Permission catalog

The system SHALL define the complete set of valid permission strings as a backed Java `enum` (`PermissionEnum`) using the `verb_resource` convention (e.g. `list_roles`, `create_client`), and SHALL expose them through a single `PermissionRegistry` used as the source of truth for validation. No permission string outside the enum SHALL be accepted or persisted.

#### Scenario: Registry lists every enum value

- **WHEN** the registry is asked for all permissions
- **THEN** it SHALL return exactly the string value of every `PermissionEnum` case, with no duplicates and no extra entries

#### Scenario: Permission strings follow verb_resource

- **WHEN** any `PermissionEnum` case is read
- **THEN** its value SHALL be a lowercase `verb_resource` string in English (e.g. `delete_role`), never a colon-namespaced or pt-BR form

### Requirement: Role permission bundle persistence

The system SHALL persist a `role_permissions` bundle with an opaque `token`, a unique `name`, and a JSON list of permission strings, using soft delete (`deleted_at`). Each `role` SHALL reference exactly one bundle via `role_permissions_id`.

#### Scenario: Bundle is created with valid permissions

- **WHEN** a bundle is persisted with a name and a list of permission strings all present in the registry
- **THEN** the row SHALL be stored with a generated `token`, the given `name`, and the `permissions` JSON array, and `deleted_at` NULL

#### Scenario: Bundle stores permissions as a JSON list

- **WHEN** a bundle with permissions `["list_roles","show_role"]` is read back
- **THEN** the persisted `permissions` column SHALL round-trip to that same list of strings

### Requirement: Create role permission bundle

The system SHALL expose `POST /api/role-permissions` to create a bundle. The request SHALL be a dedicated request DTO validated with Bean Validation: `name` required and unique; `permissions` a non-empty list where every element exists in the `PermissionRegistry`. The response SHALL be an explicit response DTO exposing `token` (never the numeric id).

#### Scenario: Successful creation

- **WHEN** an authorized caller POSTs a valid unique `name` and a non-empty list of registry permissions
- **THEN** the system SHALL persist the bundle and respond `201 Created` with the bundle's `token`, `name`, and `permissions`

#### Scenario: Unknown permission rejected

- **WHEN** the caller POSTs a `permissions` list containing a string not present in the registry
- **THEN** the system SHALL respond `400 Bad Request` with a pt-BR validation message and persist nothing

#### Scenario: Duplicate name rejected

- **WHEN** the caller POSTs a `name` that already belongs to another bundle
- **THEN** the system SHALL respond `400 Bad Request` and persist nothing

### Requirement: List and read role permission bundles

The system SHALL expose `GET /api/role-permissions` (paginated) and `GET /api/role-permissions/{token}` returning response DTOs. Lookups SHALL be by opaque `token`, never by numeric id.

#### Scenario: List returns a page of bundles

- **WHEN** an authorized caller GETs `/api/role-permissions`
- **THEN** the system SHALL respond `200 OK` with a page of bundle response DTOs

#### Scenario: Read by token

- **WHEN** an authorized caller GETs `/api/role-permissions/{token}` for an existing bundle
- **THEN** the system SHALL respond `200 OK` with that bundle's response DTO

#### Scenario: Unknown token

- **WHEN** the caller GETs a token that matches no bundle
- **THEN** the system SHALL respond `404 Not Found`

### Requirement: Update role permission bundle

The system SHALL expose `PUT /api/role-permissions/{token}` to update `name` and/or `permissions`, applying the same validation rules as creation (unique name ignoring the bundle itself; every permission in the registry).

#### Scenario: Successful update replaces permissions

- **WHEN** an authorized caller PUTs a valid `name` and a new registry-valid `permissions` list for an existing bundle
- **THEN** the system SHALL replace the stored values and respond `200 OK` with the updated response DTO

#### Scenario: Update with unknown permission rejected

- **WHEN** the caller PUTs a `permissions` list containing a string absent from the registry
- **THEN** the system SHALL respond `400 Bad Request` and leave the bundle unchanged

### Requirement: Delete role permission bundle

The system SHALL expose `DELETE /api/role-permissions/{token}` performing a soft delete (`deleted_at`).

#### Scenario: Soft delete

- **WHEN** an authorized caller DELETEs an existing bundle
- **THEN** the system SHALL set `deleted_at` and respond `204 No Content`, and the bundle SHALL no longer appear in the default list
