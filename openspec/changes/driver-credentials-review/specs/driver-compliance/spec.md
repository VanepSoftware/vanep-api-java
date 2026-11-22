## Purpose

One evaluation says where a driver stands: what each requirement looks like, whether the driver can be approved, why not, and whose move it is. The approval, the onboarding, the admin queue and the admin's driver page all read the same evaluation, so they never disagree.

## ADDED Requirements

### Requirement: The state of each requirement

For a driver and a date, the system MUST evaluate the driver's requirements (`DRIVER_LICENSE`, `SCHOOL_DRIVER_COURSE`, `CRIMINAL_RECORD`, and the optional `MUNICIPAL_AUTHORIZATION`) and, for each active van, the van's requirements (`VEHICLE_REGISTRATION`, `VEHICLE_INSPECTION`, `SCHOOL_VEHICLE_AUTHORIZATION`), using only live submissions that are not superseded. Each requirement MUST have `satisfied` as in `school-transport-credentials` and a `state`:

| State | When |
|---|---|
| `APPROVED` | a submission satisfies it on the date |
| `PENDING` | otherwise, and the newest submission is `PENDING` |
| `REJECTED` | otherwise, and the newest submission is `REJECTED` |
| `EXPIRED` | otherwise, and the newest submission is `APPROVED` but expired on the date (a `PENDING` submission has no expiry until it is approved) |
| `MISSING` | there is no submission |

Each requirement MUST name the submission that counts and, when a renewal is under review next to an approval, that pending submission too.

#### Scenario: Renewal under review

- **WHEN** the van's inspection is approved and valid and a newer one is `PENDING`
- **THEN** `VEHICLE_INSPECTION` is `APPROVED`, satisfied, and names the pending renewal

#### Scenario: Expired without renewal

- **WHEN** the driver's only criminal record is approved but expired on the date
- **THEN** `CRIMINAL_RECORD` is `EXPIRED` and unsatisfied

### Requirement: The decision for the admin

The evaluation MUST say:
- which active vans are qualified;
- `canApprove`: the driver is `UNDER_REVIEW`, their required requirements are satisfied and at least one van is qualified;
- the `blockers` when it cannot: the driver's unsatisfied requirements and, when no van is qualified, the unsatisfied requirements of the van closest to complete;
- the `nextAction`, which is the first that applies:
  1. `READY_TO_APPROVE` when `canApprove`;
  2. `ADMIN` when any submission of the driver or of an active van can be reviewed: any `PENDING` one, which always has its file;
  3. `DRIVER` when the driver must act: the registration is `PENDING` or `REJECTED`, or it is `UNDER_REVIEW` and cannot be approved, or it is `APPROVED` without its own requirements or without a qualified van;
  4. `NONE` otherwise.

The evaluation MUST also count the submissions that can be reviewed, give the time of the oldest one, count the required requirements that are `REJECTED`, `MISSING` and `EXPIRED`, and give the nearest expiry.

#### Scenario: One complete van is enough

- **WHEN** a driver under review has every driver requirement approved, van A complete and van B with nothing
- **THEN** `canApprove` is true and `nextAction` is `READY_TO_APPROVE`

#### Scenario: Waiting for the driver

- **WHEN** a driver under review has no pending submission and the only van's authorization was rejected
- **THEN** `nextAction` is `DRIVER` and the blockers name `SCHOOL_VEHICLE_AUTHORIZATION` of that van

#### Scenario: An approved driver renewing

- **WHEN** an approved driver with everything in order sends a new inspection
- **THEN** `nextAction` is `ADMIN` and the pending count is 1

### Requirement: The date is the service date

The evaluation MUST use the current date in `America/Sao_Paulo`, whatever the server's time zone.

#### Scenario: Midnight in São Paulo

- **WHEN** a document is valid until `2026-11-12` and it is `2026-11-13T01:00Z` (`2026-11-12` 22:00 in São Paulo)
- **THEN** the document is still valid

### Requirement: Many drivers in constant queries

The system MUST evaluate any number of drivers together in a constant number of queries, whatever the number of drivers, vans and documents. Evaluating one driver MUST follow the same path.

#### Scenario: Evaluating the whole base

- **WHEN** 3 drivers and 300 drivers are evaluated
- **THEN** both take the same number of queries
