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

10. **Validate request input with Bean Validation on dedicated request DTOs**, applied via `@Valid` in the controller — do not validate ad hoc inside business logic.
11. **Never bind a request body directly to a JPA model.** Accept a request DTO, map explicitly to the model. This is our equivalent of guarding against mass assignment.
12. **Shape responses with explicit response DTOs** — never return raw JPA model graphs to clients (avoids lazy-loading leaks and over-exposure).
13. **Expose and accept public resource identifiers as opaque `token` strings** (see `SecureTokens`), never internal numeric/sequential `id`s.
14. **Represent fixed sets of values as backed Java `enum`s**, not loose strings or ints.
15. **Prefix REST controllers with the global `/api`** (see `ApiWebConfig`) and keep them in `*.controller` packages.

## Persistence

16. **Avoid N+1 queries** when returning related data: use fetch joins, `@EntityGraph`, or batch fetching — do not lazily iterate associations in a loop.
17. **Apply all schema changes through Flyway migrations** in `src/main/resources/db/migration`; never alter the database manually and never recreate existing tables — extend with new versioned revisions (see rule 2).
18. **Use soft delete for all removable domain models.** Annotate models with Hibernate `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)` and add a nullable `deleted_at` column in Flyway migrations. Call `repository.delete(model)` — Hibernate translates it to an `UPDATE`, not a physical `DELETE`. Do not map `deleted_at` as a Java field unless you need to read it explicitly; the annotation manages the column. Never issue native `DELETE FROM` or `@Query` deletes on soft-deletable tables in application code; reserve physical deletes for test cleanup scripts (e.g. `clean.sql`). For unique constraints on soft-deletable columns, use partial indexes with `WHERE deleted_at IS NULL` (see V6/V10 migrations).

## Security

19. **Protect routes through Spring Security / the OAuth2 Authorization Server.** New endpoints must declare their authorization rules in `SecurityConfig` (or method security); do not ship a publicly reachable endpoint by omission.
20. **When adding a client-facing resource, define its authorization rule explicitly** alongside the endpoint — authorization is part of the feature, not a follow-up.
21. **Centralize ownership (resource-owner) authorization in the global `SecurityEvaluator` bean (`@sec`), never in per-feature security services.** When an endpoint must also allow the resource's owner (e.g. `@PreAuthorize("hasAuthority('update_vehicle') or @sec.isVehicleOwner(#token, authentication)")`), add an `is<Entity>Owner(String token, Authentication authentication)` method to `SecurityEvaluator` (`br.com.vanep.auth.security`) that resolves the caller with `SecurityHelper.getCallerUid(authentication)` and compares it to the resource owner's user token. Do **not** create a `*SecurityService` per feature (e.g. `ClientSecurityService`, `VehicleSecurityService`) — that duplicates the same pattern across packages; those were consolidated into `@sec` in the ownership refactor.

## Testing

22. **Every new feature (or relevant change) ships with tests** covering its main behavior: unit tests (Mockito) for services/validators/policies, slice tests (`MockMvc` + security) for HTTP endpoints.
23. **The build enforces a minimum line coverage (JaCoCo) on `verify`.** Run `./mvnw verify` (or `make test-coverage`) locally before opening a PR — a green local build prevents CI rework.
24. **Tests use H2 in memory**; reuse the existing test profiles/properties in `src/test/resources` instead of inventing parallel config files.

## Code quality (Clean Code)

25. **Write tests before the code** they cover (test-first).
26. **Small functions, single purpose.** If you describe it with "and then… and after that…", split it.
27. **Function names start with a verb and say exactly what they do.** Prefer `validateCpf()` over `handleData()`, `driverIndex` over `i`. Avoid generic names like `process()`, `handle()`, `calculate()`.
28. **Use consistent vocabulary** across the codebase — pick `find` *or* `get` for the same idea and stick to it.
29. **Comments explain the "why", not the "what".** Improve the code first; comment only what is non-obvious (business rules, workarounds for external bugs).
30. **Explicit, clean error handling.** Throw meaningful exceptions; never swallow errors in empty `catch` blocks; don't mix business logic with error-handling noise.
31. **Remove duplication and keep cohesion** (DRY + single responsibility per class).
32. **Avoid `private` methods where a small, named, testable method would do; minimize unnecessary privacy.**
33. **Delete dead code.** Leave code cleaner than you found it (boy scout rule); treat refactoring as first-class work.
34. **When refactoring, prioritize clarity over conciseness.**

## Phased delivery

35. **Split feature work into phases**; ship each phase on its own branch with one PR. Phases must be explicit and numbered in a generated `tasks.md`.
36. **Before code generation, produce a dependency graph, layer assignment, and a PR plan table** (`| Phase | Contents | Depends on | Parallel with |`); do not implement until the plan is approved.
37. **Dependency first:** artifacts with zero internal dependencies form the first PR; never generate a later layer before its dependency.
38. **One dependency layer per PR**; never mix artifacts from different layers, and never ship an interface and its implementation in the same PR. If an upper layer ships first, use stubs/mocks until the dependency PR merges.
39. **PRs in the same layer may be reviewed in parallel** only when they do not depend on each other.
40. **Cap each PR at ~600 productive lines and 10 new files**; subdivide before implementing if exceeded.
41. **Per phase, implement in order:** test → migration → model → repository → security/authorization → request DTO → service → controller → response DTO. Run migrations before tasks that depend on the new schema.
42. **Every phase includes its own automated tests** (unit + slice) covering only the code delivered in that phase; do not defer testing to a later phase. CI must pass after each phase.
43. **Run `./mvnw spotless:check` (`make lint`) and `./mvnw verify` (`make test-coverage`) before opening each phase PR.**

## Conventions

44. **The build requires Spotless (Google Java Format).** Auto-fix with `make lint-fix` / `./mvnw spotless:apply`; verify with `make lint` / `./mvnw spotless:check`. Unformatted code fails CI. Never exclude `db/migration/` from migration-checksum protection while formatting (see rule 2).
45. **Write user-facing validation and business error messages in Portuguese (pt-BR)**, consistent with existing controllers. Never hardcode the pt-BR string at the throw site — use an English message *key* (e.g. `role_permission.name.duplicate`) resolved through Spring's `MessageSource` (`src/main/resources/messages.properties` for the English default, `messages_pt_BR.properties` for the pt-BR translation actually served; `spring.mvc.locale=pt_BR` is fixed in `application.properties`). Inject `MessageSource` into the `@Service` and resolve with `messages.getMessage(key, args, LocaleContextHolder.getLocale())` before throwing.
46. **Write commit messages and PR descriptions in pt-BR.** Include test and lint status in the PR description, and link the PR to its GitHub issue (`Closes #N` / via the GitHub Project) — we track work through native GitHub Issues/Projects, not ticket-prefixed titles.
47. **Name files, classes, packages, and code identifiers in English — never pt-BR.** The codebase is English (`client`, `driver`, `user`, `ClientController`, `ClientRepository`); a new file must follow the same. This is the deliberate inverse of rules 45–46: *only* user-facing messages (rule 45) and commit/PR descriptions (rule 46) are pt-BR; everything in source — file names, identifiers, types — stays English.
48. **Keep all non-user-facing string literals in source code in English** — MessageSource keys, logs, comments, and internal constants.
