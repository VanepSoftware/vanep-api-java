# Constitution

Rules that MUST be followed in this codebase. Stack: **Java 25, Spring Boot 4, Maven, JPA/Flyway over PostgreSQL** (H2 in tests), **Spring Security + OAuth2 Authorization Server**, **Spotless (Google Java Format)**. See `docs/project-overview.md` for the broader philosophy.

## Configuration & secrets

1. **Never hardcode URLs or secrets in source code.** All URLs, hosts, ports, keys, and credentials must come from environment variables or `application.properties` referencing env vars. No magic strings for connection endpoints.
2. **Never edit a Flyway migration that has already been applied.** This includes whitespace and comment-only changes — any edit changes the file's checksum and makes Flyway refuse to start ("Migration checksum mismatch"). To change applied schema, add a new versioned migration. Comment-stripping or formatting tools MUST exclude `src/main/resources/db/migration/`. (Broke startup once via commit `8875157`, which removed comments from V1–V5.)
3. **All environment-specific config must come from the environment (`.env`), never hardcoded.** This covers hosts, ports, credentials, and feature flags — e.g. the mail server. In particular, do **not** pin such values in `docker-compose.yml`'s `environment:` block: it overrides `env_file`, so the same compose file silently diverges between local and prod (a hardcoded `MAIL_HOST: mailpit` once sent all production e-mail to the local Mailpit). Use per-profile defaults in `application-*.properties` (`${VAR:default}`) for dev convenience, and let real values flow from `.env`. Note: an *empty* var (`VAR=`) resolves to an empty string, not the default — leave it absent/commented to fall back to the default.
4. **Never commit secrets** (`.env`, RSA keys, cloud credentials). Document placeholders in `.env.example` only.

## Architecture & code organization

5. **Organize business code by feature** (`br.com.vanep.<feature>` with `controller`, `dto`, `enums`, `mapper`, `model`, `repository`, `service` subpackages). Code shared by two or more features moves to a shared area (`config`, shared model bases, generic utils) instead of being duplicated. The codebase is **feature-based** (packages by functionality), not type-based at the package root — and not to be confused with runtime *feature flags*. **Name every new file for exactly what it is**, with the architectural-role suffix that matches its subpackage — e.g. `ClientController`, `ClientService`, `ClientRepository`, `ClientDTO` (request/response DTOs), `ClientMapper`, and `ClientModel` (a `*Repository` lives in `repository`, a `*DTO` in `dto`, a `*Model` in `model`, …). No generic names like `Handler`, `Manager`, `Util`, or `Data` for these roles. Per feature, each subpackage holds the file named for its role:

   ```
   br.com.vanep.client            (feature: client)
   ├── controller
   │   └── ClientController
   ├── dto
   │   └── ClientDTO
   ├── repository
   │   └── ClientRepository
   ├── service
   │   └── ClientService
   ├── mapper
   │   └── ClientMapper
   └── model
       └── ClientModel
   ```
6. **Before adding anything new, search for existing code** (class, package, migration, property) that can be reused or extended. Reuse or refactor before duplicating (DRY). This applies to code, tests, config patterns, and Flyway migrations.
7. **Keep controllers thin** — orchestration only (parse request, delegate, return response). No business logic in controllers.
8. **Put business logic in `@Service` classes**, not in controllers, models, or filters. Prefer extracting pure rules (validations, policies) into classes testable without a servlet or JPA model when it reduces coupling without over-engineering.
9. **Keep framework details out of the core business rules.** Web annotations, `HttpServletRequest`, JPA specifics, etc. must not leak into domain logic.

## API design

10. **Validate request input with Bean Validation on dedicated request DTOs**, applied via `@Valid` in the controller — do not validate ad hoc inside business logic. Exception for PATCH: do **not** put `@NotNull` / `@NotBlank` on `JsonNullable` fields (omitted JSON would fail). Nested `@Valid` still applies when a present field is an object. Present-null vs present-blank vs uniqueness belong in the `@Service`.
11. **Never bind a request body directly to a JPA model.** Accept a request DTO, map explicitly to the model. This is our equivalent of guarding against mass assignment.
12. **Shape responses with explicit response DTOs** — never return raw JPA model graphs to clients (avoids lazy-loading leaks and over-exposure).
13. **Expose and accept public resource identifiers as opaque `token` strings** (see `SecureTokens`), never internal numeric/sequential `id`s.
14. **Represent fixed sets of values as backed Java `enum`s**, not loose strings or ints.
15. **Prefix REST controllers with the global `/api`** (see `ApiWebConfig`) and keep them in `*.controller` packages.
16. **Partial update is PATCH + `JsonNullable` on every mutable field** of that DTO (see `UserProfileUpdateRequestDTO`: compact canonical `undefined()` in the constructor). Omitted JSON → stored value unchanged. Present non-null → persist. Present JSON `null` → clear if the column is nullable; HTTP 400 if the column is NOT NULL (or the field must not be cleared). Do **not** wrap only a nested field and leave sibling `String`s — omit and `"x": null` collapse to the same Java `null`. Do **not** treat `if (getX() != null)` as omit (that cannot clear). Do **not** reuse the create `applyRequest` on PATCH (that is replace and copies nulls). Merge with `isPresent()` in the `@Service`. POST create remains a full body without `JsonNullable`. PUT remains full replace of a resource **or** of a dedicated sub-resource (e.g. `PUT /api/clients/me/address` with a complete `@Valid` address body). New partial-update endpoints MUST follow this rule. Existing PUT replace MAY stay until that resource is converted; when converting, convert the **whole** update DTO. Each PATCH MUST include a named test that a single-field body leaves every other stored field unchanged.

## Persistence

17. **Avoid N+1 queries** when returning related data: use fetch joins, `@EntityGraph`, or batch fetching — do not lazily iterate associations in a loop.
18. **Apply all schema changes through Flyway migrations** in `src/main/resources/db/migration`; never alter the database manually and never recreate existing tables — extend with new versioned revisions (see rule 2).
19. **Use soft delete for all removable domain models.** Annotate models with Hibernate `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)` and add a nullable `deleted_at` column in Flyway migrations. Call `repository.delete(model)` — Hibernate translates it to an `UPDATE`, not a physical `DELETE`. Do not map `deleted_at` as a Java field unless you need to read it explicitly; the annotation manages the column. Never issue native `DELETE FROM` or `@Query` deletes on soft-deletable tables in application code; reserve physical deletes for test cleanup scripts (e.g. `clean.sql`). For unique constraints on soft-deletable columns, use partial indexes with `WHERE deleted_at IS NULL` (see V6/V10 migrations).

## Security

20. **Protect routes through Spring Security / the OAuth2 Authorization Server.** New endpoints must declare their authorization rules in `SecurityConfig` (or method security); do not ship a publicly reachable endpoint by omission.
21. **When adding a client-facing resource, define its authorization rule explicitly** alongside the endpoint — authorization is part of the feature, not a follow-up.
22. **Centralize ownership (resource-owner) authorization in the global `SecurityEvaluator` bean (`@sec`), never in per-feature security services.** When an endpoint must also allow the resource's owner (e.g. `@PreAuthorize("hasAuthority('update_vehicle') or @sec.isVehicleOwner(#token, authentication)")`), add an `is<Entity>Owner(String token, Authentication authentication)` method to `SecurityEvaluator` (`br.com.vanep.auth.security`) that resolves the caller with `SecurityHelper.getCallerUid(authentication)` and compares it to the resource owner's user token. Do **not** create a `*SecurityService` per feature (e.g. `ClientSecurityService`, `VehicleSecurityService`) — that duplicates the same pattern across packages; those were consolidated into `@sec` in the ownership refactor.

## Testing

23. **Every new feature (or relevant change) ships with tests** covering its main behavior: unit tests (Mockito) for services/validators/policies, slice tests (`MockMvc` + security) for HTTP endpoints.
24. **The build enforces a minimum line coverage (JaCoCo) on `verify`.** Run `./mvnw verify` (or `make test-coverage`) locally before opening a PR — a green local build prevents CI rework.
25. **Tests use H2 in memory**; reuse the existing test profiles/properties in `src/test/resources` instead of inventing parallel config files.

50. **No test may call a real external API — ever.** The whole suite must pass with no network and no credentials. Unit, slice, and repository tests all stub the outbound client (`@MockitoBean`, a stubbed `RestClient`, or a local `MockWebServer`) and feed it **recorded fixtures** committed under `src/test/resources`. No test reaches Google Places, an SMTP server, or any third-party host — not even "just once", not even a test tagged to be skipped in CI. Three reasons, worst first: a real call **spends paid quota on every run** (Places bills per `Place Details`, and CI runs on every push); the suite becomes non-deterministic, failing on network or credential problems that have nothing to do with the code under test; and a green build starts depending on a secret that CI has no business holding. **Enforcement is configuration, not discipline** — `application-test.properties` pins every outbound base URL to an unroutable local address (`http://localhost:1/...`) and every key to an obviously fake value, so an accidental real call dies with a connection error instead of silently succeeding and billing. When new provider fixtures are needed, capture them **once**, in a documented manual spike, and commit the raw JSON; never re-capture from the suite.

    > Rule 50 is numbered out of sequence on purpose: it belongs to Testing, but inserting it as 26 would renumber rules 26–49 and invalidate every `regra N` reference already written across `openspec/`.

## Code quality (Clean Code)

26. **Write tests before the code** they cover (test-first).
27. **Small functions, single purpose.** If you describe it with "and then… and after that…", split it.
28. **Function names start with a verb and say exactly what they do.** Prefer `validateCpf()` over `handleData()`, `driverIndex` over `i`. Avoid generic names like `process()`, `handle()`, `calculate()`.
29. **Use consistent vocabulary** across the codebase — pick `find` *or* `get` for the same idea and stick to it.
30. **Comments explain the "why", not the "what".** Improve the code first; comment only what is non-obvious (business rules, workarounds for external bugs).
31. **Explicit, clean error handling.** Throw meaningful exceptions; never swallow errors in empty `catch` blocks; don't mix business logic with error-handling noise.
32. **Remove duplication and keep cohesion** (DRY + single responsibility per class).
33. **Avoid `private` methods where a small, named, testable method would do; minimize unnecessary privacy.**
34. **Delete dead code.** Leave code cleaner than you found it (boy scout rule); treat refactoring as first-class work.
35. **When refactoring, prioritize clarity over conciseness.**

## Phased delivery

36. **Split feature work into phases**; ship each phase on its own branch with one PR. Phases must be explicit and numbered in a generated `tasks.md`.
37. **Before code generation, produce a dependency graph, layer assignment, and a PR plan table** (`| Phase | Contents | Depends on | Parallel with |`); do not implement until the plan is approved.
38. **Dependency first:** artifacts with zero internal dependencies form the first PR; never generate a later layer before its dependency.
39. **One dependency layer per PR**; never mix artifacts from different layers, and never ship an interface and its implementation in the same PR. If an upper layer ships first, use stubs/mocks until the dependency PR merges.
40. **PRs in the same layer may be reviewed in parallel** only when they do not depend on each other.
41. **Cap each PR at ~600 productive lines and 10 new files**; subdivide before implementing if exceeded.
42. **Per phase, implement in order:** test → migration → model → repository → security/authorization → request DTO → service → controller → response DTO. Run migrations before tasks that depend on the new schema.
43. **Every phase includes its own automated tests** (unit + slice) covering only the code delivered in that phase; do not defer testing to a later phase. CI must pass after each phase.
44. **Run `./mvnw spotless:check` (`make lint`) and `./mvnw verify` (`make test-coverage`) before opening each phase PR.**

## Conventions

45. **The build requires Spotless (Google Java Format).** Auto-fix with `make lint-fix` / `./mvnw spotless:apply`; verify with `make lint` / `./mvnw spotless:check`. Unformatted code fails CI. Never exclude `db/migration/` from migration-checksum protection while formatting (see rule 2).
46. **Write user-facing validation and business error messages in Portuguese (pt-BR)**, consistent with existing controllers. Never hardcode the pt-BR string at the throw site — use an English message *key* (e.g. `role_permission.name.duplicate`) resolved through Spring's `MessageSource` (`src/main/resources/messages.properties` for the English default, `messages_pt_BR.properties` for the pt-BR translation actually served; `spring.mvc.locale=pt_BR` is fixed in `application.properties`). Inject `MessageSource` into the `@Service` and resolve with `messages.getMessage(key, args, LocaleContextHolder.getLocale())` before throwing.
47. **Write commit messages and PR descriptions in pt-BR.** Include test and lint status in the PR description, and link the PR to its GitHub issue (`Closes #N` / via the GitHub Project) — we track work through native GitHub Issues/Projects, not ticket-prefixed titles.
48. **Name files, classes, packages, and code identifiers in English — never pt-BR.** The codebase is English (`client`, `driver`, `user`, `ClientController`, `ClientRepository`); a new file must follow the same. This is the deliberate inverse of rules 46–47: *only* user-facing messages (rule 46) and commit/PR descriptions (rule 47) are pt-BR; everything in source — file names, identifiers, types — stays English.
49. **Keep everything in source code in English, with no exceptions beyond translation files.** This includes Javadoc and inline comments, log messages, exception messages, internal constants, test names, and assertion/`.as()` descriptions — not just string literals. The only place pt-BR text is allowed is a translation resource resolved through `MessageSource` (rule 46) or an explicit i18n file in another Vanep repo (mobile/frontend). If you find yourself writing a Portuguese sentence anywhere else in source, it belongs in a message key instead, or it shouldn't be written at all.
51. **Comment only when it is strictly necessary.** The default is no comment. Code says *what* it does; a name that needs a sentence to be understood is a naming problem, not a documentation problem — rename it. Do not write Javadoc that restates the signature, section banners, `// getters and setters`, commented-out code, or narration of the obvious. A comment is justified only when the code cannot carry the information itself: a non-obvious external constraint (a provider quirk, a billing SKU, a legal rule), or a deliberate choice whose alternative looks correct and is not. When one is genuinely needed, keep it to a line or two, in English (rule 49), and explain the **why** — never the how. Rationale that is longer than that belongs in `openspec/` or the PR description, which is where design decisions are reviewed, not in the source file.
