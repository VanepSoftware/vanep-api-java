## Error Contract Delivery Plan (`VAN-89`)

### Dependency graph and layer assignment

```text
Phase 0 (Constitution + OpenSpec + Client Inventory)
  -> Phase 1 (ApiProblemException, ApiErrorAdvice 4xx/500, Security 401/403/429, Validation code, Message parity)
    -> Phase 2..N (Area-by-area migration of services, advices, and DTO validation keys)
      -> Phase M1 (Mobile: ApiProblem, constants, mappers by code, dual auth/profile parsing)
      -> Phase A1 (Admin: ApiProblem parser + code-based error handling)
        -> Phase N+1 (API BREAKING: migrate auth & profile to ProblemDetail, drop legacy envelopes)
          -> Phase N+2 (API: Makefile/CI error-contract check script & DTO key enforcement + Rule 55 QA)
          -> Phase M2 (Mobile: remove legacy auth/profile envelope readers)
```

| Phase | Repo | Contents | Depends on | Parallel with |
|---|---|---|---|---|
| 0 | API | `constitution.md` rule 57, `api-error-contract` OpenSpec, admin & mobile inventory | — | — |
| 1 | API | `ApiProblemException`, `ApiErrorAdvice` (Spring `code` + `500`), Security `401`/`403`/`429`, `ValidationProblemDetailsFactory` `code`, message parity test | 0 | — |
| 2–N | API | Migrate domain services, advices, and DTO validation keys by area (<= 600 LOC / 10 files per PR) | 1 | — |
| M1 | Mobile | `ApiProblem`, `api_error_codes.dart`, code-based mappers, dual-format `auth` & `profile` | 1 + migrated areas | A1 |
| A1 | Admin | `ApiProblem` parser and replace status/text checks with `code` | 1 | M1 |
| N+1 | API | Migrate `auth` & `profile` to `ProblemDetail` and drop legacy DTOs/advices (BREAKING) | M1 in staging | — |
| N+2 | API | Add `scripts/check-error-contract.sh` + `Makefile` + CI step, DTO key audit test, Rule 55 QA | All API migrations | M2 |
| M2 | Mobile | Remove legacy `auth`/`profile` DTO parsers | N+1 | N+2 |

### Phase 0
- [x] 0.1 Audit `vanep-api-java`, `vanep-mobile`, and `vanep-frontend` error handling and document the inventory in `design.md`.
- [x] 0.2 Add rule 57 to `constitution.md`.
- [x] 0.3 Create `openspec/changes/api-error-contract/` (`proposal.md`, `design.md`, `specs/api-error-contract/spec.md`, `tasks.md`).

### Phase 1
- [x] 1.1 Write failing unit/slice tests for `ApiProblemException`, `MessagesParityTest`, `ApiErrorAdvice` (`code` on Spring errors, `@PreAuthorize` 403, safe `500`), `ValidationProblemDetailsFactory` (`validation.failed` + item `code`), `SecurityConfig` (`401`/`403` `ProblemDetail`), and `RateLimitingFilter` (`429` `ProblemDetail` on `/api/**`).
- [x] 1.2 Remove duplicate `driver_service_area.areas.too_many` key and add generic error keys to `messages.properties` and `messages_pt_BR.properties`.
- [x] 1.3 Implement `br.com.vanep.shared.error.ApiProblemException` and `ApiProblemResponseWriter`.
- [x] 1.4 Update `ValidationProblemDetailsFactory` to emit `code: validation.failed` and per-item `code`.
- [x] 1.5 Update `ApiErrorAdvice` to guarantee `code` and pt-BR `detail` on Spring MVC exceptions, handle `AccessDeniedException` as `403 auth.forbidden`, and handle `Exception` as `500 internal.error`.
- [x] 1.6 Implement `ApiAuthenticationEntryPoint` (`401`) and `ApiAccessDeniedHandler` (`403`) in `SecurityConfig` and update `RateLimitingFilter` (`429` `ProblemDetail` on `/api/**`).
- [x] 1.7 Run `make lint` and `./mvnw verify -B`.

### Later Phases (tracked for upcoming PRs)
- [ ] 2..N Migrate domain services, advices (`LocationErrorAdvice`, `CepLookupErrorAdvice`), manual `setProperty("code", ...)` sites, and DTO validation `{key}` messages by area.
  - [x] Phase 2: `driver`, `drivercnh`, `driverdocument`, `vehicledocument`, `schooltransportcredential`, `vehicle`.
- [ ] M1 Update `vanep-mobile` mappers to decide strictly by `code` (including dual-format `auth`/`profile`).
- [ ] A1 Update `vanep-frontend` admin pages to use a unified `ApiProblem` parser and `code`.
- [ ] N+1 Migrate `auth` and `profile` exceptions to `ApiProblemException` and remove legacy envelopes/advices.
- [ ] N+2 Add `scripts/check-error-contract.sh` + `Makefile` target + `.github/workflows/ci.yml` check, DTO validation key audit test, and Rule 55 QA script.
- [ ] M2 Remove legacy `auth`/`profile` envelope readers in `vanep-mobile`.
