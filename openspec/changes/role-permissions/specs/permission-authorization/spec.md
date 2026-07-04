## ADDED Requirements

### Requirement: Access token carries the caller's permissions

The OAuth2 access token SHALL carry a `permissions` claim listing the permission strings granted to the authenticated user, resolved from the user's role and its `role_permissions` bundle (`user.role_id → role → role_permissions.permissions`). When the user has no role or bundle, the claim SHALL be an empty list. The existing `roles` claim (`ROLE_<user_type>`) SHALL be preserved.

#### Scenario: Permissions resolved from the user's bundle

- **WHEN** an access token is issued for a user whose role links to a bundle with `["list_roles","delete_role"]`
- **THEN** the token SHALL include a `permissions` claim equal to that list, in addition to the `roles` claim

#### Scenario: User without a role bundle

- **WHEN** an access token is issued for a user with no `role_id` (or a role without a bundle)
- **THEN** the `permissions` claim SHALL be an empty list and the `roles` claim SHALL still be present

### Requirement: Permissions become granted authorities

The resource-server JWT converter SHALL map every entry of the `permissions` claim to a `GrantedAuthority` whose name is the permission string verbatim (no `ROLE_` prefix), alongside the authorities derived from the `roles` claim.

#### Scenario: Permission claim yields matching authority

- **WHEN** a request presents a token whose `permissions` claim contains `list_roles`
- **THEN** the authenticated principal SHALL hold a `GrantedAuthority` named exactly `list_roles`

### Requirement: Routes are authorized by fine-grained permission

Endpoints that previously required `hasRole('ADMIN')` SHALL instead require the specific permission via `@PreAuthorize("hasAuthority('<permission>')")`. Ownership-based checks (e.g. `@clientSecurity.isOwner`) SHALL remain unchanged; where a route combined `hasRole('ADMIN')` with ownership, the role check SHALL be replaced by the corresponding `hasAuthority('<permission>')` while the ownership branch is preserved.

#### Scenario: Caller with the required permission is allowed

- **WHEN** a caller whose token grants `list_clients` requests `GET /api/clients`
- **THEN** the system SHALL allow the request

#### Scenario: Caller lacking the permission is forbidden

- **WHEN** a caller whose token does not grant `delete_client` requests `DELETE /api/clients/{token}`
- **THEN** the system SHALL respond `403 Forbidden`

#### Scenario: Ownership branch preserved

- **WHEN** a caller who is the owner of a client (but lacks `show_client`) requests `GET /api/clients/{token}`
- **THEN** the system SHALL allow the request via the ownership check

#### Scenario: role-permissions endpoints are permission-gated

- **WHEN** a caller without `create_role_permission` requests `POST /api/role-permissions`
- **THEN** the system SHALL respond `403 Forbidden`

### Requirement: Seeded ADMIN bundle grants full access

The system SHALL seed a `role_permissions` bundle named for the administrator that contains every permission in the `PermissionRegistry`, and link it to an ADMIN `role`, so that administrators retain full access after routes migrate from `hasRole('ADMIN')` to `hasAuthority`.

#### Scenario: ADMIN seed contains all permissions

- **WHEN** the seeder runs on an empty database
- **THEN** it SHALL create an ADMIN bundle whose `permissions` equals the full registry and link it to an ADMIN role

#### Scenario: Seeding is idempotent

- **WHEN** the seeder runs again against a database that already has the ADMIN bundle
- **THEN** it SHALL not create a duplicate bundle or role
