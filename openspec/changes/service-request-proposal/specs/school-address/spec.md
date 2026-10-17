## Purpose

A school chosen through the place search carries its address, taken from the same place, so the app can show where the school is without the admin filling it in.

## ADDED Requirements

### Requirement: Resolving a new school stores its address

When `POST /api/schools/resolve` creates a school, the system MUST also store the school's address from the same place details, in the same transaction and without another call to Google Places: street, number, zip code, city and neighborhood (`address-neighborhood`). When the place has no street, the school MUST be created without an address and the request MUST still succeed. The response MUST include the address.

#### Scenario: A school with a street

- **WHEN** an authenticated user resolves a school place with `route = "Rua Estela"`, `street_number = "268"` and `postal_code = "04011-001"`
- **THEN** the system returns HTTP 201 with the school
- **AND** the school's `address` has `street = "Rua Estela"`, `number = "268"`, `zipCode = "04011001"` and `neighborhood = "Vila Mariana"`

#### Scenario: A school place without a street

- **WHEN** an authenticated user resolves a school place with no street and no subdivision deep enough to stand for one
- **THEN** the system returns HTTP 201 with the school
- **AND** the school has no `address`

#### Scenario: Resolving the same school again

- **WHEN** an authenticated user resolves a school that already has an address
- **THEN** the system returns HTTP 200 with the same school and the same address
- **AND** no new address is stored

### Requirement: A known school without an address is completed on resolve

When `POST /api/schools/resolve` finds a school that already exists and has no address, the system MUST fetch the place details, under the same per-user limit as a new school, and store the address as for a new school. When the limit is reached, the system MUST return the school as it is, without an error.

#### Scenario: An old school gets its address

- **WHEN** an authenticated user resolves a school that exists without an address
- **THEN** the system returns HTTP 200 with the school and its new `address`

#### Scenario: The per-user limit is reached

- **WHEN** an authenticated user past the per-user limit resolves a school that exists without an address
- **THEN** the system returns HTTP 200 with the school and no `address`

### Requirement: The school's city follows its address

A school MUST keep its own city, taken from the resolve, for when it has no address. When an admin stores a place in the school's address, the system MUST also set the school's city to the address's city, so the school's `cityToken` and its address never point to different cities.

#### Scenario: The admin moves the school to a place in another city

- **WHEN** an admin saves a place in Campinas as the address of a school whose city is Sorocaba
- **THEN** the school's address is in Campinas
- **AND** the school's `cityToken` is Campinas's
