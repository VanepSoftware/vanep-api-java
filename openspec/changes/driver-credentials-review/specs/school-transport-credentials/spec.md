## Purpose

One answer to "can this driver, with this van, provide school transport on this date, and with which documents": the requirements of CTB arts. 136, 138 and 329 checked against the reviewed documents, used by the proposal and the contract and shown to the driver before they try to offer a van.

## ADDED Requirements

### Requirement: The credentials of a driver and a van on a date

For a driver, one of their vehicles and a date, the system MUST evaluate these requirements, each satisfied only by a submission that is not removed, not superseded, `APPROVED`, and does not expire before that date. A driver document without expiry counts as not expiring:

| Requirement | Satisfied by |
|---|---|
| `DRIVER_LICENSE` | an approved, active CNH of the driver with category containing `D` or `E` and `validUntil` not before the date |
| `SCHOOL_DRIVER_COURSE` | a driver document `SCHOOL_DRIVER_COURSE` |
| `CRIMINAL_RECORD` | a driver document `CRIMINAL_RECORD` |
| `VEHICLE_REGISTRATION` | a vehicle document `CRLV` of that vehicle |
| `VEHICLE_INSPECTION` | a vehicle document `VEHICLE_INSPECTION` of that vehicle |
| `SCHOOL_VEHICLE_AUTHORIZATION` | a vehicle document `SCHOOL_VEHICLE_AUTHORIZATION` of that vehicle |

The result MUST list, for each satisfied requirement, the document number and expiry of the document with the latest expiry, and MUST list the unsatisfied requirements. A `MUNICIPAL_AUTHORIZATION` driver document that is approved and valid MUST be listed as an optional credential and MUST never make the result fail. The vehicle MUST be active and belong to the driver; otherwise every vehicle requirement fails.

#### Scenario: Everything approved and valid

- **WHEN** the driver has an active CNH category `AD` valid until 2030, an approved course and criminal record, and the van has an approved CRLV, inspection and authorization, all valid on the date
- **THEN** no requirement is unsatisfied and each credential has its number and expiry

#### Scenario: Documents of another van do not count

- **WHEN** the driver's van A has an approved inspection and van B has none
- **THEN** for van B, `VEHICLE_INSPECTION` is unsatisfied

#### Scenario: Expired on the date

- **WHEN** the van's inspection expires the day before the date
- **THEN** `VEHICLE_INSPECTION` is unsatisfied

#### Scenario: A CNH under review does not count yet

- **WHEN** the driver's only CNH is `PENDING`, sent with its file and without data yet
- **THEN** `DRIVER_LICENSE` is unsatisfied

#### Scenario: Category B is not enough

- **WHEN** the driver's only active CNH has category `B`
- **THEN** `DRIVER_LICENSE` is unsatisfied

### Requirement: The driver sees what each van is missing

`GET /api/vehicles/{token}/school-transport-credentials` MUST return, for the van's owner and for callers with `show_vehicle`, the evaluation for that van on the current date in `America/Sao_Paulo`: each requirement with `satisfied`, and the number and expiry of the document that satisfies it. Anyone else MUST get HTTP 403, without authentication HTTP 401, an unknown or removed van HTTP 404, and an inactive van HTTP 422 (`vehicle.inactive`).

#### Scenario: A new van without documents

- **WHEN** an approved driver adds a van and reads its credentials
- **THEN** the response is HTTP 200 with the driver requirements satisfied and the three vehicle requirements unsatisfied
