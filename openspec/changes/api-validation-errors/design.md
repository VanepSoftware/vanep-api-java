## Context

The API has three validation response families that existing clients consume:

- general controllers use RFC 9457 `ProblemDetail`;
- authentication controllers use `AuthErrorResponseDTO`;
- profile e-mail changes use `ProfileErrorResponseDTO`.

Before this change, `ProfileErrorAdvice` inherited Spring's global exception handler and affected
controllers outside its domain. A global fix must expose useful validation data without replacing
the two established specialized envelopes.

## Decisions

### Deterministic global selection

`ValidationProblemDetailsFactory` converts every `ObjectError` into an entry, sorts required
constraints (`NotBlank`, `NotEmpty`, `NotNull`) first, then by field and message, and selects the
first entry for the top-level `detail` and optional `field`. The complete sorted collection is
returned in `errors`.

Field errors keep Spring's complete field path, including nested paths such as
`address.zipCode`. Object errors omit `field` instead of inventing one.

### Advice ownership

`ApiErrorAdvice` is the only global `ResponseEntityExceptionHandler`. `ProfileErrorAdvice` is
scoped to `ProfileController` and `EmailCodeApiController` and no longer inherits the global
handler.

Both advices use `ValidationProblemDetailsFactory`; neither advice injects or invokes another
advice. `AuthErrorAdvice` keeps `Ordered.HIGHEST_PRECEDENCE`, while `ProfileErrorAdvice` uses
`Ordered.HIGHEST_PRECEDENCE + 1`, making the authentication envelope win deterministically on the
shared `EmailCodeApiController`.

### Compatibility

The global response only adds `field` and `errors` and replaces the generic validation detail with
the already resolved message. Authentication validation keeps `code`, `message`, and `errors`.
Profile e-mail validation keeps `message`, `code`, `field`, and `retryAfter`.

Malformed JSON, unsupported methods/media types, and query-parameter conversion errors remain out
of scope.

## Verification

- Standalone advice tests cover ordering, nested paths, object errors, and interpolation.
- Controller tests cover `JsonNullable`, dependent nesting, contracts, auth, and profile behavior.
- A dedicated running-API script checks the global contract, nested path, profile/e-mail envelope,
  and auth envelope through real HTTP requests.
