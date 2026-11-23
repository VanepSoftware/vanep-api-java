## Context

Audit performed on 2026-11-23 across `vanep-api-java`, `vanep-mobile`, and `vanep-frontend`:

- **API (`vanep-api-java`):**
  - 296 `ResponseStatusException` usages across 51 files in `src/main/java` (plus 4 in security/web/media helpers).
  - 5 `@RestControllerAdvice` beans (`ApiErrorAdvice`, `AuthErrorAdvice`, `ProfileErrorAdvice`, `LocationErrorAdvice`, `CepLookupErrorAdvice`), plus manual `setProperty("code", ...)` in `DependentService`, `AddressCatalogResolverService`, `AddressService`, and `CepLookupService`.
  - Bean Validation annotations across DTOs: 210 total (115 with `{key}`, 25 with literal pt-BR strings, 70 without `message`).
  - `messages.properties` and `messages_pt_BR.properties` share 317 keys with 1:1 parity, except both duplicate `driver_service_area.areas.too_many`.
  - `RateLimitingFilter` responds `429` in `text/plain` on `/api/auth/**`; `/api/**` resource-server `401`/`403` responses have no JSON body.
- **Mobile (`vanep-mobile`):**
  - 23 repository files handle `DioException` alongside 5 dedicated failure mappers.
  - Text-matching mappers: `driver_service_area_repository_impl.dart` (`'bairro'`, `'máximo'`) and `assistant_failure_mapper.dart` (`contains(...)`).
  - Status-only/detail mappers: `negotiation_call_guard.dart`, `school_repository_impl.dart`, `driver_student_repository_impl.dart`, and sibling repositories.
  - Legacy envelopes: `auth_api_error_dto.dart` + `account_failure_mapper.dart`, and `profile_error_dto.dart` + `profile_edit_failure_mapper.dart`.
- **Admin (`vanep-frontend`):**
  - `src/lib/server/api.ts` proxies status codes and response bodies transparently under `/api/admin/**`.
  - Existing admin pages do not parse `ProblemDetail.code` yet: `admin/clients/page.tsx` and `admin/permissions/page.tsx` branch on HTTP status (`409`, `403`, `404`, `400`, `413`), while `admin/roles/page.tsx` and `admin/drivers/page.tsx` only check `!res.ok`.

## Decisions

### Unified `ProblemDetail` contract

All `4xx` and `5xx` responses under `/api/**` use RFC 9457 `ProblemDetail` (`application/problem+json`) with `type = about:blank` and:

- `status`, `title`, `detail` (pt-BR via fixed locale), `code` (required on every `/api/**` error).
- Optional `field`, `errors` (`[{field, code, message}]` on validation failures), and endpoint-specific extras (`retryAfter`, `blockers`, etc.).

### Single exception and advice architecture

- `ApiProblemException` (`br.com.vanep.shared.error`) extends Spring's `ErrorResponseException`, populates `code` on its `ProblemDetail`, sets `detailMessageCode` to the message key so `ResponseEntityExceptionHandler` resolves `detail` through `MessageSource`, and supports `.with(key, value)` extras.
- `ApiErrorAdvice` is the sole `@RestControllerAdvice`:
  - `handleExceptionInternal` guarantees `code` (and pt-BR `detail` for generic Spring MVC errors) on every response.
  - `@ExceptionHandler(AccessDeniedException.class)` ensures `@PreAuthorize` denials emit `403` with `code = auth.forbidden` instead of falling into the `500` catch-all.
  - `@ExceptionHandler(Exception.class)` logs the full exception and returns `500` with `code = internal.error` and a generic pt-BR message (no stack trace, class name, or SQL).
- `ApiProblemResponseWriter` writes the same `ProblemDetail` JSON for filter-level security errors (`401` `auth.unauthenticated` keeping `WWW-Authenticate`, `403` `auth.forbidden`, and `429` `request.rate_limited` on `/api/**`). Non-`/api/**` web routes keep HTML redirects and plain-text responses. `/oauth2/token` keeps its RFC 6749 OAuth2 error format.

### Auth & Profile de-para table (Phase N+1, breaking after mobile M1)

| Legacy source | Legacy `code` | HTTP | New `code` (`ProblemDetail`) | Extras |
|---|---|---|---|---|
| `AuthErrorCode` | `validation_error` | 400 | `validation.failed` | `field`, `errors` |
| `AuthErrorCode` | `email_duplicate` | 409 | `auth.signup.email.duplicate` | `field = "email"` |
| `AuthErrorCode` | `document_duplicate` | 409 | `auth.signup.document.duplicate` | `field = "document"` |
| `AuthErrorCode` | `invalid_code` | 400 | `auth.error.invalid_code` | — |
| `AuthErrorCode` | `invalid_signup_ticket` | 400 | `auth.error.invalid_signup_ticket` | — |
| `ProfileErrorCode` | `cooldown` | 409 | `user.profile.name.cooldown` / `user.profile.phone.cooldown` / `user.profile.email.cooldown` | `field`, `retryAfter` |
| `ProfileErrorCode` | `email_duplicate` | 409 | `auth.signup.email.duplicate` | `field = "email"` |
| `ProfileErrorCode` | `field_null` | 400 | `user.profile.field.null` | `field` |
| `ProfileErrorCode` | `phone_blank` | 400 | `user.profile.phone.blank` | `field = "phone"` |
| `ProfileErrorCode` | `email_same` | 400 | `user.profile.email.same` | `field = "email"` |
| `ProfileErrorCode` | `email_invalid` | 400 | `user.profile.email.invalid` | `field = "email"` |
| `ProfileErrorCode` | `email_required` | 400 | `user.profile.email.required` | `field = "email"` |
| `ProfileErrorCode` | `name_too_long` | 400 | `user.profile.name.too_long` | `field = "name"` |
| `ProfileErrorCode` | `phone_too_long` | 400 | `user.profile.phone.too_long` | `field = "phone"` |
| `ProfileErrorCode` | `email_too_long` | 400 | `user.profile.email.too_long` | `field = "email"` |

### Contract enforcement without extra dependencies

Per project owner decision, no ArchUnit dependency is added to `pom.xml`. Enforcement combines:
- JUnit parity tests for `messages*.properties` and DTO validation annotation keys.
- A repository check script in `scripts/check-error-contract.sh` wired into `Makefile` and `.github/workflows/ci.yml` (activated in Phase N+2 once migrations finish) verifying zero `ResponseStatusException` usages in `src/main/java` and a single `@RestControllerAdvice`.
