## ADDED Requirements

### Requirement: Global Bean Validation failures expose every resolved error

For controllers without a specialized validation envelope, the API MUST return HTTP 400 as an RFC
9457 `ProblemDetail`. The response MUST keep `status`, `title`, `detail`, and `instance`, MUST add
the selected violation path as `field` when the violation belongs to a field, and MUST add every
violation to `errors` as an object containing `message` and an optional `field`.

The API MUST select errors deterministically: `NotBlank`, `NotEmpty`, and `NotNull` violations
first, followed by the field path in ascending order and then the resolved message. The selected
error MUST provide the top-level `detail` and `field`.

#### Scenario: Multiple field failures

- **WHEN** a request violates required and non-required field constraints
- **THEN** the API returns HTTP 400
- **AND** `detail` and `field` identify the first required violation in deterministic order
- **AND** `errors` contains every violation in deterministic order

#### Scenario: Interpolated validation message

- **WHEN** a constraint message contains an interpolated argument such as `{max}`
- **THEN** `detail` and the matching `errors` entry contain the resolved message

### Requirement: Nested and object validation paths remain meaningful

The API MUST preserve the complete Spring field path for nested DTO violations, including fields
inside `JsonNullable` patch values. Object-level violations MUST be returned without a fabricated
field.

#### Scenario: Nested patch field is invalid

- **WHEN** a dependent patch violates the nested `address.zipCode` constraint
- **THEN** the API returns HTTP 400 with `address.zipCode` in the matching error entry

#### Scenario: Object-level constraint fails

- **WHEN** an object-level constraint fails without a field
- **THEN** the response contains its message in `detail` and `errors`
- **AND** neither the top-level response nor that entry contains `field`

### Requirement: Specialized auth and profile envelopes remain stable

Authentication controller validation MUST continue returning `AuthErrorResponseDTO` with
`code = validation_error`, `message`, and `errors`. Profile e-mail-change validation MUST continue
returning `ProfileErrorResponseDTO` with its specific e-mail code and field.

When both authentication and profile advice include `EmailCodeApiController`, authentication
validation MUST win through explicit advice order rather than bean-registration order.

#### Scenario: Invalid authentication request

- **WHEN** an authentication API request fails Bean Validation
- **THEN** it returns HTTP 400 with `code = validation_error` and its field errors
- **AND** it does not use the global `ProblemDetail` envelope

#### Scenario: Invalid profile e-mail change

- **WHEN** an authenticated user submits a blank e-mail change
- **THEN** it returns HTTP 400 with `code = email_required` and `field = email`

#### Scenario: Shared e-mail-code controller validation

- **WHEN** `EmailCodeApiController` rejects an invalid request
- **THEN** `AuthErrorAdvice` handles the failure before `ProfileErrorAdvice`
