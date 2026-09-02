# Client Dependent Management Specification (S14)

## Purpose

Expose the client user interface and interaction flows for managing dependents on Screen 14 (S14), consuming the backend REST API (`/api/dependent`) while adhering to business rules RN01 (one dependent per contract) and RN12 (default dependent handling).

## Requirements

### Requirement: Expose Dependent Listing (Screen S14)

The client application MUST display the list of active dependents belonging to the logged-in client. Each dependent item MUST be rendered in a card displaying their avatar, name, birth date formatted as `DD/MM/AAAA`, and a distinct "Padrão" badge if `isDefault` is true.

#### Scenario: Listing client dependents with default badge
- **GIVEN** an authenticated client user with dependents "Pedro Silva" (default) and "Luiza Silva"
- **WHEN** the user opens Screen S14 ("Dependentes")
- **THEN** the system displays header title "Dependentes" and subtitle "Cada contrato é vinculado a um dependente."
- **AND** "Pedro Silva" is rendered with a "Padrão" badge
- **AND** "Luiza Silva" is rendered without the badge
- **AND** a button "+ Adicionar dependente" is visible at the bottom

### Requirement: Add New Dependent

The client application MUST provide a form to create a new dependent by sending a `POST /api/dependent` request with fields: `name`, `birthDate`, `gender`, `document`, `phone`, `email`, `isSelf`, `shift`, `isDefault`, and `address`.

#### Scenario: Successful dependent creation
- **GIVEN** an authenticated client on Screen S14
- **WHEN** the user taps "+ Adicionar dependente", fills in name "Luiza Silva", birth date "08/11/2018", and submits
- **THEN** the client sends a `POST /api/dependent` request
- **AND** upon HTTP 201 response, the new dependent card appears in the listing

### Requirement: Automatic and Manual Default Selection (RN12)

The system MUST enforce RN12 default dependent logic:
- The first dependent created for a client MUST automatically become the default dependent (`isDefault = true`).
- When a user has 2+ dependents, they MAY manually toggle a dependent to be default. The backend MUST clear the default flag on all other dependents.

#### Scenario: First created dependent becomes default automatically
- **GIVEN** a client with zero dependents
- **WHEN** the client creates their first dependent
- **THEN** the API returns `isDefault: true`
- **AND** the UI renders the "Padrão" badge on this dependent card

#### Scenario: Switch default dependent manually
- **GIVEN** a client with dependents "Pedro Silva" (default) and "Luiza Silva"
- **WHEN** the user selects "Luiza Silva" as default
- **THEN** the client sends `PATCH /api/dependent/{token}` with `{ "isDefault": true }`
- **AND** the UI updates so only "Luiza Silva" displays the "Padrão" badge

### Requirement: Edit Dependent Details

The client application MUST allow updating any existing dependent's information via `PATCH /api/dependent/{token}`.

#### Scenario: Edit dependent name and birth date
- **GIVEN** an existing dependent "Pedro Silva"
- **WHEN** the user opens the edit modal, changes the birth date, and saves
- **THEN** the client sends a `PATCH /api/dependent/{token}` request with the updated fields
- **AND** upon HTTP 200 response, the updated info is rendered on Screen S14

### Requirement: Remove Dependent with Default Re-promotion

The client application MUST allow soft-deleting a dependent via `DELETE /api/dependent/{token}`. If the deleted dependent was the default and exactly one active dependent remains, that remaining dependent MUST automatically be promoted to default.

#### Scenario: Delete default dependent when two exist
- **GIVEN** a client with "Pedro Silva" (default) and "Luiza Silva" (non-default)
- **WHEN** the user deletes "Pedro Silva"
- **THEN** the client sends `DELETE /api/dependent/{token}`
- **AND** the API automatically promotes "Luiza Silva" to `isDefault: true`
- **AND** the UI updates to show "Luiza Silva" with the "Padrão" badge
