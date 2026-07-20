## Context

The user specified that only country-related test files should be modified. Non-country feature test files (address, city, state, etc.) must not be edited.

## Goals / Non-Goals

**Goals:**
- Ensure all tests in `br.com.vanep.country.**` pass cleanly.
- Keep tests strictly focused on the country domain without modifying non-country test files.

**Non-Goals:**
- Modifying test files of other features (`address`, `city`, `state`, etc.).

## Decisions

### Decision 1: Scope Test Modifications Strictly to Country Package
- **Rationale**: User explicitly requested not to touch test files outside of the `country` feature.

## Risks / Trade-offs

- **[Risk]**: Non-country integration tests might fail if run in isolation against DB schema constraints without data.
  - **Mitigation**: Focus testing and validation specifically on the country package tests.
