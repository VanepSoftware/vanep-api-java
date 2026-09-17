## ADDED Requirements

### Requirement: Pending onboarding steps exposed on the profile

The system SHALL extend `GET /api/user/me` with an `onboarding` object containing `pendingSteps`, a list of backed enum values. The enum MUST include `PERSONAL_ADDRESS` and `SERVICE_AREA`. The system MUST NOT expose these as separate booleans, so that new steps can be added without breaking the mobile client.

The system MUST only include `SERVICE_AREA` for users that have a driver profile, so the client needs no role logic of its own.

#### Scenario: Driver missing both steps

- **WHEN** an authenticated driver with no personal address and no service areas reads `GET /api/user/me`
- **THEN** the system returns `pendingSteps` containing `PERSONAL_ADDRESS` and `SERVICE_AREA`

#### Scenario: Driver missing only service areas

- **WHEN** an authenticated driver has a personal address but no service areas
- **THEN** the system returns `pendingSteps` containing only `SERVICE_AREA`

#### Scenario: Client never receives the driver step

- **WHEN** an authenticated client with no personal address reads `GET /api/user/me`
- **THEN** the system returns `pendingSteps` containing only `PERSONAL_ADDRESS`
- **AND** never includes `SERVICE_AREA`

#### Scenario: Fully onboarded user

- **WHEN** an authenticated driver has both a personal address and at least one service area
- **THEN** the system returns an empty `pendingSteps` list

#### Scenario: Unauthenticated access

- **WHEN** a request without a valid Bearer token calls `GET /api/user/me`
- **THEN** the system returns `401 Unauthorized`
