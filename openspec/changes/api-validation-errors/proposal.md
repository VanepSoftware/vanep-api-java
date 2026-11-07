## Why

Bean Validation failures outside the authentication and profile APIs used Spring's generic
`ProblemDetail`, which hid the resolved validation messages and field paths. Clients could not
reliably associate a failure with the invalid field, especially for nested DTOs and
`JsonNullable` patches.

## What Changes

- Return a deterministic global validation envelope with the selected message in `detail`, its
  field in `field`, and every violation in `errors`.
- Preserve complete nested field paths and object-level validation errors.
- Keep the existing authentication and profile/e-mail envelopes backward compatible.
- Scope the profile advice to its controllers and define advice precedence explicitly.
- Share validation `ProblemDetail` construction through a dedicated factory rather than coupling
  one advice to another.
- Add focused automated tests and executable HTTP QA for every affected envelope.

## Capabilities

### New Capabilities

- `api-validation-errors`: Stable response contracts for Bean Validation failures across the API.

### Modified Capabilities

- None.

## Impact

- Global Spring MVC error handling and the profile advice.
- Additive fields in global HTTP 400 responses; existing `status`, `title`, `detail`, and
  `instance` fields remain available.
- No database, migration, infrastructure, or successful-response changes.
