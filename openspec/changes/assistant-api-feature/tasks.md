## 0. Preparation

- [ ] 0.1 Create branch from `main` for this change (e.g. `feat/assistant-api`)
- [ ] 0.2 Review `proposal.md`, `design.md`, and specs under `specs/`

## 1. Phase 1 — Schema + domain (PR1)

> Depends on: nothing. Deliver migration + enums + models + repositories + clean.sql.
> Order per phase: test → migration → model → repository.

- [ ] 1.1 Write failing repository/integration stubs or schema smoke for `assistant` and `driver_link_code`
- [ ] 1.2 Create Flyway `V11__create_assistant_tables.sql` (`assistant`; `driver_link_code` with 48h TTL semantics; **no** `assistant_invite`)
- [ ] 1.3 Add enums: `AssistantStatus` (`PENDING` reserved unused OK), `VerificationStatus`, `DriverLinkCodeStatus`
- [ ] 1.4 Add `DriverLinkCodeModel` + repository under `br.com.vanep.driver` (atomic consume update)
- [ ] 1.5 Add `AssistantModel` + `AssistantRepository` (`@SoftDelete`; token `@PrePersist`)
- [ ] 1.6 Update `src/test/resources/db/clean.sql`
- [ ] 1.7 `./mvnw verify`; open PR1 → `main`

## 2. Phase 2 — Auth + signup (PR2)

> Depends on: Phase 1. Capability: `assistant-auth-signup`.

- [ ] 2.1 Tests first: signup without code → `UNLINKED`; with valid code → `ACTIVE`; invalid code rejects; OAuth → `UNLINKED`; JWT claim
- [ ] 2.2 Extend `UserType`, `RoleName`, `PermissionEnum` (no invite perms)
- [ ] 2.3 Extend `DataSeeder` + `JwtTokenCustomizer` (`assistant_status`)
- [ ] 2.4 Add `AssistantSignupForm` with optional visible `linkCode`; `registerAssistant` (+ shared atomic consume helper)
- [ ] 2.5 Thymeleaf template with visible optional link-code field; routes; SecurityConfig
- [ ] 2.6 OAuth `/signup/complete` ASSISTANT → `UNLINKED` only (no code)
- [ ] 2.7 Strong rate limit on `POST /signup/assistant`; MessageSource keys
- [ ] 2.8 `./mvnw spotless:check` + `./mvnw verify`; open PR2 → `main`

## 3. Phase 3 — Linking API (PR3)

> Depends on: Phase 2. Capability: `assistant-linking`.

- [ ] 3.1 Tests first: generate (48h expiry), cancel, consume, duplicate across signup/API, pause/resume/revoke
- [ ] 3.2 `AssistantLinkService`: generate/cancel/consume (reuse same atomic path as signup), pause, resume, revoke
- [ ] 3.3 DTOs + controllers: list + pause/resume/revoke; `driver-link-codes` generate/cancel/consume
- [ ] 3.4 `AssistantSecurityService` + `@PreAuthorize` / SecurityConfig
- [ ] 3.5 Strong rate limit on `POST /api/driver-link-codes/consume`
- [ ] 3.6 MessageSource keys for linking errors
- [ ] 3.7 `./mvnw spotless:check` + `./mvnw verify`; open PR3 → `main`

## 4. Phase 4 — Profile + hardening (PR4)

> Depends on: Phase 3. Capability: `assistant-profile`.

- [ ] 4.1 Tests: `GET/PUT /api/assistants/me`, ownership, driver list
- [ ] 4.2 Profile DTOs/mapper/service; complete list endpoint if needed
- [ ] 4.3 E2E: signup+linkCode; OAuth then `/consume`; rate-limit smoke where practical
- [ ] 4.4 `./mvnw spotless:check` + `./mvnw verify` (JaCoCo); open PR4 → `main`

## 5. Wrap-up

- [ ] 5.1 Confirm specs scenarios covered
- [ ] 5.2 Archive after merge
- [ ] 5.3 Future (out of this change): deep link/hidden, MailService, Porta A / PENDING / email-addressed invite
