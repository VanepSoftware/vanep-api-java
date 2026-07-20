## ADDED Requirements

### Requirement: Create Country
The system MUST allow administrators to create a country by providing a unique name. The system SHALL automatically generate a unique 32-character token and set the active status to true.

#### Scenario: Create country successfully
- **WHEN** an administrator posts a valid country creation request with a unique name
- **THEN** the system SHALL persist the country and return it in a CountryResponseDTO with HTTP Status 201 Created

#### Scenario: Create country with duplicate name
- **WHEN** an administrator posts a country creation request with a name that already exists in the system
- **THEN** the system SHALL reject the request with HTTP Status 409 Conflict

#### Scenario: Create country without permission
- **WHEN** a non-administrator user posts a country creation request
- **THEN** the system SHALL deny the request with HTTP Status 403 Forbidden

### Requirement: List Countries
The system MUST allow users with proper permissions to retrieve all active countries.

#### Scenario: List active countries
- **WHEN** an authorized user requests a list of all countries
- **THEN** the system SHALL return a list of all active countries with HTTP Status 200 OK

### Requirement: Show Country
The system MUST allow users with proper permissions to retrieve a country by its token.

#### Scenario: Show country successfully
- **WHEN** an authorized user requests a country by its token
- **THEN** the system SHALL return the country details with HTTP Status 200 OK

#### Scenario: Show country not found
- **WHEN** an authorized user requests a country by a non-existent token
- **THEN** the system SHALL return HTTP Status 404 Not Found

### Requirement: Update Country
The system MUST allow administrators to update a country's name by its token.

#### Scenario: Update country name successfully
- **WHEN** an administrator updates a country's name with a unique value
- **THEN** the system SHALL update the record and return HTTP Status 200 OK

#### Scenario: Update country with duplicate name
- **WHEN** an administrator updates a country's name with a name already used by another country
- **THEN** the system SHALL return HTTP Status 409 Conflict

### Requirement: Delete Country (Soft Delete)
The system MUST allow administrators to soft-delete a country by its token.

#### Scenario: Soft-delete country successfully
- **WHEN** an administrator deletes a country by its token
- **THEN** the system SHALL mark the country as deleted, set its deleted_at timestamp, and return HTTP Status 204 No Content

### Requirement: Restore Country
The system MUST allow administrators to restore a previously soft-deleted country by its token.

#### Scenario: Restore soft-deleted country successfully
- **WHEN** an administrator requests to restore a soft-deleted country by its token
- **THEN** the system SHALL remove the deleted_at timestamp, activate the country, and return the restored country with HTTP Status 200 OK
