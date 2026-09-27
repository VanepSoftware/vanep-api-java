## ADDED Requirements

### Requirement: One active link per client and driver pair

The system MUST persist at most one **active** `client_driver` row per `(client_id, driver_id)`. A Flyway migration MUST enforce this with a partial unique index on those two columns, `WHERE deleted_at IS NULL`.

Because the table is soft-deletable, a removed link MUST NOT prevent a new link for the same pair.

#### Scenario: The same pair cannot be linked twice while active

- **WHEN** an active link exists for a given client and driver
- **AND** another link is created for the same pair
- **THEN** the database unique index rejects the second row

#### Scenario: A removed pair can be linked again

- **WHEN** a link for a given client and driver is deleted
- **AND** a new link is created for the same pair
- **THEN** the new link is persisted as a distinct row
- **AND** looking up that pair returns the new link

#### Scenario: One client links to several drivers

- **WHEN** a client is linked to two different drivers
- **THEN** both links are persisted
- **AND** each carries its own opaque token

### Requirement: Link status follows the canonical relationship states

`RelationshipStatus` MUST be a backed Java enum with the values `PENDING`, `ACTIVE`, `INACTIVE` and `BLOCKED`. The first three match `relationship_status` in `vanep-diagram.dbml`; `BLOCKED` MUST be added to that diagram as well, so the canonical model and the code do not diverge. A link created without an explicit status MUST default to `PENDING`.

Ending a relationship MUST be expressed as `status = INACTIVE`, never as removal: an inactive link stays visible in listings and history, while `deleted_at` hides the row from every default query.

`BLOCKED` MUST NOT be treated as a synonym of `INACTIVE`: an inactive relationship may start again, a blocked one may not. Enforcing that — refusing a new proposal for a blocked pair — and recording **which party** blocked the other are out of scope here and belong to the proposal flow, which owns status transitions.

#### Scenario: A link is born pending

- **WHEN** a link is created with no status
- **THEN** its stored status is `PENDING`

#### Scenario: Blocking keeps the link visible

- **WHEN** a link's status is set to `BLOCKED`
- **THEN** the link is still returned by lookups and listings
- **AND** its `deleted_at` remains null

#### Scenario: Deactivating keeps the link visible

- **WHEN** a link's status is set to `INACTIVE`
- **THEN** the link is still returned by lookups and listings
- **AND** its `deleted_at` remains null

#### Scenario: A removed link disappears from default queries

- **WHEN** a link is deleted through the repository
- **THEN** looking it up by token returns empty
- **AND** it is absent from listings

### Requirement: Both parties can read and update their own link

`SecurityEvaluator` MUST gain `isClientDriverLinkParty(String token, Authentication authentication)`, returning true when the caller is **either** the link's client user **or** the link's driver user. It MUST NOT be named as an ownership method: a link has two parties, not one owner. Per-feature security services MUST NOT be created.

Read and update MUST allow the permission holder or either party. Delete and restore MUST require the permission alone — neither party may remove a row the other also uses.

#### Scenario: The client reads their own link

- **WHEN** a client without `show_client_driver` reads a link whose client is that same user
- **THEN** the system returns HTTP 200

#### Scenario: The driver reads the same link

- **WHEN** a driver without `show_client_driver` reads a link whose driver is that same user
- **THEN** the system returns HTTP 200

#### Scenario: An unrelated user is refused

- **WHEN** a user who is neither the client nor the driver of a link reads it without `show_client_driver`
- **THEN** the system returns HTTP 403

#### Scenario: A party cannot delete the shared link

- **WHEN** either party, holding no `delete_client_driver`, deletes their own link
- **THEN** the system returns HTTP 403
- **AND** the link's `deleted_at` remains null

#### Scenario: An unauthenticated call is rejected

- **WHEN** a request reaches any link endpoint without a bearer token
- **THEN** the system returns HTTP 401

### Requirement: Administrative CRUD over links addressed by token

The system MUST expose, under `/api/client-drivers`: `POST` to create, `GET` for a paginated listing, `GET /{token}` for detail, `PATCH /{token}` for partial update, `DELETE /{token}` for soft delete, and `POST /{token}/restore`.

Requests MUST bind to dedicated request DTOs with Bean Validation, never to the JPA model. Responses MUST expose the opaque `token` of the link, the client and the driver — never a numeric `id`.

#### Scenario: Admin creates a link

- **WHEN** a caller holding `create_client_driver` posts a client token and a driver token
- **THEN** the system returns HTTP 201
- **AND** the link is persisted with `status = PENDING`

#### Scenario: Creating a duplicate active pair is rejected

- **WHEN** an active link already exists for a client and driver
- **AND** a caller holding `create_client_driver` posts the same pair
- **THEN** the system returns HTTP 409
- **AND** the message is resolved from MessageSource key `client_driver.duplicate_pair`

#### Scenario: An unknown client or driver is rejected

- **WHEN** a caller holding `create_client_driver` posts a token that matches no client
- **THEN** the system returns HTTP 404

#### Scenario: Listing excludes removed links

- **WHEN** a caller holding `list_client_drivers` lists links
- **AND** one link has been soft-deleted
- **THEN** the response is a page
- **AND** the removed link is absent from it

#### Scenario: Admin deletes and restores a link

- **WHEN** a caller holding `delete_client_driver` deletes a link
- **THEN** the link is absent from listing and detail
- **AND** a subsequent restore by a caller holding `restore_client_driver` returns HTTP 200
- **AND** the link is present again

#### Scenario: The response exposes no numeric identifier

- **WHEN** a link is returned
- **THEN** the JSON body contains no `id` field for the link, the client or the driver
- **AND** each is identified by an opaque token

### Requirement: Only the status is mutable

`PATCH /api/client-drivers/{token}` MUST follow the partial-update rule: `JsonNullable` on every mutable field. `status` is the only mutable field. Omitting it MUST leave the stored value unchanged; an explicit JSON `null` MUST return HTTP 400.

The client and the driver MUST NOT be mutable. Changing either does not edit a link — it describes a different pair, and would silently break the pair's uniqueness while leaving proposals and contracts pointing at a pair that no longer exists.

#### Scenario: Patching the status is persisted

- **WHEN** a caller holding `update_client_driver` patches a `PENDING` link with `status = ACTIVE`
- **THEN** the system returns HTTP 200
- **AND** the stored status is `ACTIVE`

#### Scenario: An empty patch leaves the link unchanged

- **WHEN** a caller holding `update_client_driver` sends an empty body
- **THEN** the stored status, client and driver are unchanged

#### Scenario: Clearing the status is refused

- **WHEN** a caller holding `update_client_driver` patches a link with `"status": null`
- **THEN** the system returns HTTP 400

### Requirement: Each party lists their own links

The system MUST expose `GET /api/client-drivers/me`, returning the links of the authenticated caller. The side of the table to search MUST be derived from the caller's `UserType`: a `CLIENT` matches on the client side, a `DRIVER` on the driver side.

A caller with no links MUST receive HTTP 200 with an empty list, not HTTP 204.

#### Scenario: A client lists their drivers

- **WHEN** a client with two links reads their own links
- **THEN** the response holds both

#### Scenario: A driver lists their clients

- **WHEN** a driver with one link reads their own links
- **THEN** the response holds that link

#### Scenario: A caller with no links gets an empty list

- **WHEN** a caller with no links reads their own links
- **THEN** the system returns HTTP 200
- **AND** the body is an empty list

#### Scenario: Removed links are not listed

- **WHEN** a caller's only link has been soft-deleted
- **AND** that caller reads their own links
- **THEN** the body is an empty list
