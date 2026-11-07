## Phase 1 (branch name: fix/van-55-validation-errors)

### Dependency graph and layer assignment

```text
Existing advice and client compatibility audit
  -> HTTP error-contract tests
  -> global Spring MVC validation advice
  -> profile advice scoping
  -> endpoint regression tests and contract-core QA
  -> lint and verification
```

| Phase | Contents | Layer | Depends on | Parallel with |
|---|---|---|---|---|
| 1 | Global Bean Validation response, deterministic error ordering, profile advice isolation, tests, and QA | Web/API error handling | None | None |

- [x] 1.1 Audit the existing global, auth, profile, location, and CEP advice behavior and the mobile error contract.
- [x] 1.2 Add failing tests for the global `ProblemDetail`: selected detail, top-level field, complete errors list, required-first ordering, field-name tie-break, nested paths, object errors, and interpolated messages.
- [x] 1.3 Add failing endpoint tests proving contract validation messages are returned in the HTTP body instead of only existing in `FieldError`.
- [x] 1.4 Add failing profile/auth regression tests that preserve the profile email envelope, profile business-error envelope, and auth validation envelope while non-email profile validation uses the global format.
- [x] 1.5 Implement `ApiErrorAdvice` as the global `ResponseEntityExceptionHandler` for Bean Validation failures.
- [x] 1.6 Restrict `ProfileErrorAdvice` to its controllers, remove its global Spring MVC inheritance, and preserve its existing email and business-error formats.
- [x] 1.7 Add a dedicated running-API QA for the global, nested, profile, and auth envelopes.
- [x] 1.8 Extract validation `ProblemDetail` construction into a factory shared by the global and profile advices.
- [x] 1.9 Make auth/profile advice precedence explicit on their shared controller.
- [x] 1.10 Complete and strictly validate the OpenSpec artifacts.
- [x] 1.11 Run focused tests, `make lint`, `./mvnw verify -B`, and the dedicated Docker QA.
- [ ] 1.12 Receive developer review before push.

### Scope decision

Malformed JSON, HTTP 405, HTTP 415, and query-parameter type errors remain unchanged because their translation is optional in VAN-55 and independent from Bean Validation.
