## ADDED Requirements

### Requirement: Every `/api/**` error returns RFC 9457 `ProblemDetail` with a stable `code`

All `4xx` and `5xx` responses emitted under `/api/**` MUST have `Content-Type: application/problem+json` and an RFC 9457 `ProblemDetail` body containing `type` (`about:blank`), `status`, `title`, `detail` (pt-BR), and `code`.

`code` MUST equal the message key in `<area>.<subject>.<reason>` lowercase `snake_case` format. Renaming or removing a published `code` is a breaking change.

#### Scenario: Domain conflict returns `ProblemDetail` with stable `code`

- **WHEN** a service throws `ApiProblemException.conflict("service_request.open_exists")`
- **THEN** the HTTP response has status `409` and `Content-Type: application/problem+json`
- **AND** the JSON body contains `code = "service_request.open_exists"` and the pt-BR `detail`

### Requirement: Generic Spring MVC, Security, Rate Limit, and unexpected errors include `code`

Framework-generated HTTP errors under `/api/**` MUST carry deterministic generic codes without leaking stack traces, class names, or SQL statements:

| Condition | Status | `code` |
|---|---|---|
| Bean Validation | `400` | `validation.failed` |
| Unreadable request body | `400` | `request.body_unreadable` |
| Invalid or missing request parameter | `400` | `request.parameter_invalid` |
| Missing or invalid bearer token | `401` | `auth.unauthenticated` |
| Forbidden / `@PreAuthorize` denial | `403` | `auth.forbidden` |
| Unknown route | `404` | `request.route_not_found` |
| Unsupported HTTP method | `405` | `request.method_not_allowed` |
| Unsupported media type | `415` | `request.media_type_unsupported` |
| Rate limit exceeded | `429` | `request.rate_limited` |
| Unhandled server exception | `500` | `internal.error` |

#### Scenario: Missing or invalid bearer token on `/api/**`

- **WHEN** a client calls an authenticated `/api/**` route without a valid bearer token
- **THEN** the response has status `401`, `WWW-Authenticate` header, and `ProblemDetail` with `code = "auth.unauthenticated"`

#### Scenario: `@PreAuthorize` denial on `/api/**`

- **WHEN** an authenticated caller lacks permission for an `/api/**` route
- **THEN** the response has status `403` and `ProblemDetail` with `code = "auth.forbidden"`

#### Scenario: Rate limit exceeded on `/api/auth/**`

- **WHEN** `RateLimitingFilter` rejects a request under `/api/**`
- **THEN** the response has status `429` and `ProblemDetail` with `code = "request.rate_limited"`

#### Scenario: Unhandled exception returns safe 500

- **WHEN** a controller or service throws an unhandled `RuntimeException`
- **THEN** the response has status `500` and `ProblemDetail` with `code = "internal.error"`
- **AND** `detail` is a generic pt-BR message with no exception class, stack trace, or SQL

### Requirement: Bean Validation errors expose `code` globally and per item

`ValidationProblemDetailsFactory` MUST set `code = "validation.failed"` on the `ProblemDetail` root and include `code` on every entry of `errors` (`[{field, code, message}]`), derived from `ConstraintViolation.getMessageTemplate()` without braces.

#### Scenario: Validation error includes root and per-item codes

- **WHEN** a request DTO fails a constraint declaring `message = "{proposal.starts_on.past}"`
- **THEN** the HTTP `400` body has `code = "validation.failed"`
- **AND** the matching item in `errors` has `field`, `code = "proposal.starts_on.past"`, and resolved `message`

### Requirement: Message bundles maintain 1:1 parity without duplicate keys

Every message key MUST exist in both `messages.properties` and `messages_pt_BR.properties`, with no duplicate keys in either file.

#### Scenario: Message bundle parity test

- **WHEN** the test suite validates `messages.properties` and `messages_pt_BR.properties`
- **THEN** both files contain the exact same key set and zero duplicate keys
