## Phase 1 (branch name: fix/van-94-strict-json-types)

### Dependency graph and layer assignment

```text
Client compatibility audit
  -> HTTP deserialization tests
  -> Jackson configuration
  -> contract-core QA expectations
  -> lint and verification
```

| Phase | Contents | Depends on | Parallel with |
|---|---|---|---|
| 1 | Strict JSON enum and textual deserialization, endpoint tests, and QA updates | — | — |

- [x] 1.1 Audit `vanep-mobile` and `vanep-frontend` request payloads for numeric enums and numeric textual fields.
- [x] 1.2 Add failing HTTP tests for a numeric enum, a numeric enum inside `JsonNullable`, numeric/decimal/boolean textual values, and enum whitespace.
- [x] 1.3 Configure Jackson 3 to reject numeric enums and scalar-to-text coercion.
- [x] 1.4 Reject leading or trailing whitespace in enum values.
- [x] 1.5 Update the `contract-core` QA scenarios and remove the documented coercion tolerance.
- [x] 1.6 Run the `make lint` equivalent, `./mvnw verify -B`, and the 269-scenario `contract-core` QA.
- [x] 1.7 Receive developer review before commit and push.
