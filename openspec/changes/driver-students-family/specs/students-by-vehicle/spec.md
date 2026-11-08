## ADDED Requirements

### Requirement: A student can belong to a van

`unlinked_passenger.vehicle_id` MUST be nullable. The unlinked passenger `POST` MUST accept an optional `vehicleToken` and the `PATCH` MUST accept it as `JsonNullable` (rule 16). The vehicle MUST belong to the caller and be active, otherwise 422 `unlinked_passenger.vehicle.not_owned`. Contract items have no van in this change and count as students without a van.

#### Scenario: Assigning a van

- **WHEN** a driver patches an unlinked passenger with only `vehicleToken`
- **THEN** the passenger has that van and every other field is unchanged

#### Scenario: Another driver's van

- **WHEN** the `vehicleToken` belongs to another driver
- **THEN** the system returns HTTP 422

#### Scenario: A contracted student counts for every van

- **WHEN** a driver with an `ACTIVE` contract item filters by any of their vans
- **THEN** the contracted student is in the result

### Requirement: Students and occupancy filter by van

`GET /api/drivers/me/students` and `GET /api/drivers/me/occupancy` MUST accept an optional `vehicleToken` and then count only the students of that van plus the students without a van. A van that is not the caller's MUST return 404 `driver_student.vehicle.not_found`.

#### Scenario: Two vans

- **WHEN** a driver has students in van A, in van B and without a van, and filters by A
- **THEN** the result has the van A students and the students without a van
