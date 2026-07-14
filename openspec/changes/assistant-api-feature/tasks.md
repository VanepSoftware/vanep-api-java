## 0. Preparation

- [x] 0.1 Create branch from `main` for this change (e.g. `feat/assistant-api`)
- [x] 0.2 Review `proposal.md`, `design.md`, and specs under `specs/`

## 1. Phase 1 — Schema + domain (PR1)

> Depends on: nothing. Deliver migration + enums + models + repositories + clean.sql.
> Order per phase: test → migration → model → repository.

- [x] 1.1 Write failing repository/integration stubs or schema smoke for `assistant` and `assistant_invite`
- [x] 1.2 Create Flyway `V11__create_assistant_tables.sql` (`assistant`; `assistant_invite` with public `token`, `link_token_hash`, TTL 72h semantics, statuses PENDING|ACCEPTED|REJECTED|EXPIRED|CANCELLED; soft delete; **no** `driver_link_code`)
- [x] 1.3 Add enums: `AssistantStatus` (`UNLINKED`, `PENDING`, `ACTIVE`, `INACTIVE`), `VerificationStatus`, `AssistantInviteStatus`
- [x] 1.4 Add `AssistantInviteModel` + repository under `br.com.vanep.assistant` (find by `link_token_hash`, by public `token`, cooldown query REJECTED same pair within 7d)
- [x] 1.5 Add `AssistantModel` + `AssistantRepository` (`@SoftDelete`; token `@PrePersist`)
- [x] 1.6 Update `src/test/resources/db/clean.sql`
- [x] 1.7 `./mvnw verify`; open PR1 → `main`

## 2. Phase 2 — Auth + signup (PR2)

> Depends on: Phase 1. Capability: `assistant-auth-signup`.

- [x] 2.1 Tests first: signup → always `UNLINKED` (no invite field); OAuth → `UNLINKED`; JWT claim `assistant_status`
- [x] 2.2 Extend `UserType`, `RoleName`, `PermissionEnum` (invite create/cancel for DRIVER; no link-code perms)
- [x] 2.3 Extend `DataSeeder` + `JwtTokenCustomizer` (`assistant_status`)
- [x] 2.4 Add `AssistantSignupForm` **identical** to client/driver pattern — **no** `linkCode` / invite field; `registerAssistant` → `UNLINKED`
- [x] 2.5 Thymeleaf signup template without invite fields; routes; SecurityConfig (public signup)
- [x] 2.6 OAuth `/signup/complete` ASSISTANT → `UNLINKED` only
- [x] 2.7 MessageSource keys for auth/signup; keep general public-route rate limit if already shared (not link-code guessing)
- [x] 2.8 `./mvnw spotless:check` + `./mvnw verify`; open PR2 → `main`

## 3. Phase 3 — Invite API + link lifecycle (PR3)

> Depends on: Phase 2. Capability: `assistant-linking` (API + mail; web accept in Phase 4).

- [x] 3.1 Tests first: invite by email (happy path); not found; wrong UserType 409; already PENDING/ACTIVE/INACTIVE 409; resend cancels previous PENDING same driver + new invite; cooldown 7d after REJECTED same pair; cancel by driver; pause/resume/revoke; lazy expiry treated as free slot on new invite
- [x] 3.2 `AssistantInviteService`: eligibility, create (+ Decision 5 resend), cancel, lazy expire helper, MailService send with template `email/assistant-invite`
- [x] 3.3 `AssistantLinkService` (or same service): pause, resume, revoke (ACTIVE/INACTIVE; revoke bilateral)
- [x] 3.4 DTOs + controllers: `POST /api/assistants/invites`, `DELETE /api/assistants/invites/{token}`, list + pause/resume/revoke
- [x] 3.5 `AssistantSecurityService` + `@PreAuthorize` / SecurityConfig
- [x] 3.6 Email template (motorista nome, link `{baseUrl}/assistant-invite/{raw}`, prazo 72h); MessageSource keys
- [x] 3.7 `./mvnw spotless:check` + `./mvnw verify`; open PR3 → `main`

## 4. Phase 4 — Web accept/reject + profile (PR4)

> Depends on: Phase 3. Capabilities: `assistant-linking` (web) + `assistant-profile`.

- [x] 4.1 Tests first: GET pending invite; accept/reject via REST autenticado; accept → ACTIVE; reject → UNLINKED + REJECTED
- [x] 4.2 REST: `GET /api/assistants/me/invite`, `POST .../accept`, `POST .../reject` (lazy expiry on read/action)
- [x] 4.3 Profile: `GET|PUT /api/assistants/me`, ownership; driver list DTO complete
- [x] 4.4 E2E: signup UNLINKED → invite → Mailpit/link → accept; cancel; cooldown smoke where practical
- [x] 4.5 `./mvnw spotless:check` + `./mvnw verify` (JaCoCo); open PR4 → `main`

## 5. Wrap-up

- [x] 5.1 Confirm specs scenarios covered
- [ ] 5.2 Archive after merge
- [x] 5.3 Future (out of this change): scheduled expiry job; REST accept/reject for native app; SMS/push
