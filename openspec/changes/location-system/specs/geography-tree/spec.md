## ADDED Requirements

### Requirement: Geographic tree with variable depth

The system SHALL model geography as a single shared tree `country → state → city → district`, where `district` is self-referencing via a nullable `parent_id`. The system MUST NOT assume a fixed number of levels below `city`. A `district` with `parent_id = NULL` is a direct child of a `city`; deeper nodes reference their parent district.

The `district` table MUST use soft delete (`@SoftDelete` with `deleted_at`) and MUST declare a partial unique index on (`parent_id`, `city_id`, `normalized_name`) `WHERE deleted_at IS NULL`. Public identifiers MUST be opaque `token` strings.

#### Scenario: Direct child of a city

- **WHEN** a district node is created for "Taguatinga" under the city "Brasília"
- **THEN** the system persists it with `city_id` set to Brasília and `parent_id` null

#### Scenario: Nested district

- **WHEN** a district node is created for "QNL 5" whose parent component is "Taguatinga"
- **THEN** the system persists it with `parent_id` referencing the Taguatinga node
- **AND** the same `city_id` as its parent

#### Scenario: Depth beyond two levels

- **WHEN** a place resolves to components "Conjunto J" under "QNL 5" under "Taguatinga"
- **THEN** the system persists all three nodes in a single chain without schema change

### Requirement: Lazy tree construction from address components

The system SHALL build the geographic tree on demand from the `addressComponents` returned by Google Place Details. The system MUST NOT seed `state`, `city`, or `district` from static datasets. Creation MUST be idempotent: resolving the same chain twice MUST NOT create duplicate nodes.

The system MUST persist the node derived from the place's `addressComponents`, and MUST NOT persist the place selected by the user as the node itself.

#### Scenario: First resolution creates the chain

- **WHEN** the first place resolving to `[BR, DF, Brasília, Taguatinga]` is processed and none of those nodes exist
- **THEN** the system creates the state, city, and district nodes in order
- **AND** links each to its parent

#### Scenario: Second resolution reuses the chain

- **WHEN** a second place resolving to the same chain is processed
- **THEN** the system reuses the existing nodes
- **AND** creates no duplicates

#### Scenario: Selected place is normalized before persisting

- **WHEN** a user selects the place "Taguatinga Norte" and its `addressComponents` carry the sublocality "Taguatinga"
- **THEN** the system anchors the node at "Taguatinga"
- **AND** does not create a node named "Taguatinga Norte"

### Requirement: Country remains curated and matched by ISO code

The system SHALL keep `country` as the only curated level of the tree, because it carries business attributes (currency, phone code, locale) that Google does not provide. When resolving a chain, the system MUST match the Google `country` component by its `shortText` (ISO 3166-1 alpha-2) against `country.iso_code`, not by name.

#### Scenario: Country matched by ISO code

- **WHEN** a chain resolves with a country component whose `shortText` is `BR` and whose `longText` is `Brazil`
- **THEN** the system matches the existing `country` row with `iso_code = 'BR'`
- **AND** does not create a country named "Brazil"

#### Scenario: Unsupported country rejected

- **WHEN** a chain resolves to a country whose `iso_code` has no active `country` row
- **THEN** the system rejects the resolution with a business error resolved through MessageSource

### Requirement: Name normalization for node matching

The system SHALL store a `normalized_name` for every tree node, derived by removing accents and lowercasing the canonical name returned by Google. Node lookup during resolution MUST use `normalized_name` scoped to the parent, never the raw display name.

#### Scenario: Accent and case variation match the same node

- **WHEN** a chain resolves a component named "Brasília" and another resolves "BRASILIA" under the same state
- **THEN** the system matches both to the same city node

### Requirement: Read-only anchor resolution

The system SHALL resolve a search location to the deepest existing node in its ancestor chain, without creating any node. Anchor resolution MUST be read-only.

#### Scenario: Anchor stops at the deepest existing node

- **WHEN** a search place resolves to `[BR, DF, Brasília, Taguatinga, QNL 5, Conjunto J]` and only nodes up to "Taguatinga" exist
- **THEN** the system returns "Taguatinga" as the anchor
- **AND** creates neither "QNL 5" nor "Conjunto J"

#### Scenario: Search creates no nodes

- **WHEN** any anchor resolution runs
- **THEN** the system performs no insert on `state`, `city`, or `district`
