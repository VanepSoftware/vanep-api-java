## Purpose

The neighborhood people see on an address, a contract, a route and a service request comes from the address itself, and the district tree serves only the driver search and the service areas.

## ADDED Requirements

### Requirement: The shown neighborhood is the address neighborhood

Every response that shows the neighborhood of an address MUST read it from `address.neighborhood`, or from the copy taken of it: the dependent and school addresses, the personal address, the contract item (`pickupNeighborhood`), the route passengers (`pickupNeighborhood`) and the service request items. No response MUST expose the district of an address or of a school: `district` leaves the address JSON, `districtName` and `districtToken` leave the personal address and school JSON, and `pickupDistrictName` leaves the contract item and the route passengers.

The district tree, the driver search and the driver service areas MUST keep working as before.

#### Scenario: A contract item shows the neighborhood of the postal form

- **WHEN** a client whose dependent's address was saved through the postal form with `neighborhood = "Centro"` has a contract for that dependent
- **THEN** the contract item shows `pickupNeighborhood = "Centro"`
- **AND** it has no `pickupDistrictName`

#### Scenario: The personal address has no district

- **WHEN** a user reads `GET /api/user/me/address`
- **THEN** the response has `neighborhood`
- **AND** it has neither `districtName` nor `districtToken`

#### Scenario: The school has no district

- **WHEN** an authenticated user resolves a school place in the Federal District whose deepest subdivision is "QI 21"
- **THEN** the school's `address` has `neighborhood = "Taguatinga Norte"`
- **AND** the school has neither `districtName` nor `districtToken`

### Requirement: An address from a place gets its neighborhood

An address written from a Google place MUST store as `neighborhood` the shallowest `sublocality` component of the place (`sublocality_level_1`, else `sublocality_level_2`, else `sublocality_level_3`). A place with no `sublocality` component MUST store a null neighborhood.

#### Scenario: A school in São Paulo

- **WHEN** an address is written from a place whose components include `sublocality_level_1 = "Vila Mariana"`
- **THEN** the address has `neighborhood = "Vila Mariana"`

#### Scenario: A school in the Federal District

- **WHEN** an address is written from a place with `administrative_area_level_4 = "Taguatinga"`, `sublocality_level_2 = "Taguatinga Norte"` and `sublocality_level_3 = "QI 21"`
- **THEN** the address has `neighborhood = "Taguatinga Norte"`

### Requirement: Stored districts become neighborhoods before the columns go

The migration that removes `address.district_id`, `contract_item.pickup_district_id` and `school.district_id` MUST first copy the name of the linked district into `address.neighborhood` and `contract_item.pickup_neighborhood` where those are null, and the name of the school's district into the neighborhood of the school's address where that is null, and MUST keep any neighborhood already stored.

#### Scenario: An address from a place before the change

- **WHEN** an address has `district_id` pointing to "Setor L Norte" and no neighborhood, and the migration runs
- **THEN** the address has `neighborhood = "Setor L Norte"`
- **AND** the `district_id` column no longer exists

#### Scenario: A school that pointed to a district

- **WHEN** a school has `district_id` pointing to "Setor L Norte", its address has no neighborhood and no district, and the migration runs
- **THEN** the school's address has `neighborhood = "Setor L Norte"`
- **AND** the `school.district_id` column no longer exists
