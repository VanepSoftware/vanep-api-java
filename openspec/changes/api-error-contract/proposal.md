## Why

API endpoints currently emit four incompatible error shapes (`ProblemDetail` without `code`, `ProblemDetail` with manually set `code`, `AuthErrorResponseDTO`, and `ProfileErrorResponseDTO`), while security filters and unexpected server errors emit empty or plain-text bodies. Mobile and admin clients fall back to matching Portuguese strings in `detail` or guessing by HTTP status alone, making copy edits silent breaking changes.

## What Changes

- Establish a single RFC 9457 `ProblemDetail` (`application/problem+json`) contract for all `4xx` and `5xx` responses under `/api/**`, carrying a stable `code` equal to the message key.
- Introduce `ApiProblemException` in `br.com.vanep.shared.error` as the single domain exception type translated through Spring's `MessageSource`.
- Centralize all Spring MVC, Bean Validation, Security (`401`, `403`, `429`), and unexpected `500` error responses into the same contract without leaking internals.
- Migrate all services, domain advices, and DTO validation annotations to stable message keys, then retire the legacy `auth` and `profile` envelopes after mobile M1 ships.
- Enforce message parity, DTO validation keys, and contract compliance via tests and a `Makefile`/CI check script (without adding new dependencies to `pom.xml`).
- Add a constitution rule (`57`) and the `api-error-contract` capability specification.

## Capabilities

### New Capabilities

- `api-error-contract`: Unified RFC 9457 `ProblemDetail` error contract with stable `code` across `/api/**`, generic Spring/Security error codes, and `auth`/`profile` migration mapping.

### Modified Capabilities

- None.

## Impact

- Global error handling (`ApiErrorAdvice`, `ValidationProblemDetailsFactory`), security chain (`SecurityConfig`, `RateLimitingFilter`), and all feature services/DTOs in `vanep-api-java`.
- Coordinating client updates in `vanep-mobile` and `vanep-frontend` (documented in `design.md` and `tasks.md`).
- Breaking change for `/api/auth/**` and `/api/users/me` error envelopes, sequenced after mobile dual-format support (`M1 -> N+1 -> M2`).
- No database schema or Flyway migration changes.
