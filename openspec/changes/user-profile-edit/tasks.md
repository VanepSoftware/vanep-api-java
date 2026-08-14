## 0. Preparation

- [x] 0.1 Create branch `feat/user-profile-edit` from `main`
- [x] 0.2 Review artifacts (`proposal.md`, `design.md`, `specs/user-profile-edit/spec.md`)
- [x] 0.3 Confirm next Flyway version number (after latest applied migration)

## 1. Phase 1 — Schema + JsonNullable (PR 1)

> Goal: columns + model + Jackson support + cooldown property. No HTTP yet.
> Depends on: — | Parallel with: —
> Order: test (model/repo smoke if any) → migration → model → config

- [x] 1.1 Add `org.openapitools:jackson-databind-nullable` to `pom.xml` and register `JsonNullableModule` in Jackson config
- [x] 1.2 Add Flyway migration: `pending_email varchar(255)`, `last_phone_change_at`, `last_email_change_at` on `users` (no unique on `pending_email`)
- [x] 1.3 Map new columns on `UserModel`
- [x] 1.4 Add `vanep.profile.change-cooldown-days=${PROFILE_CHANGE_COOLDOWN_DAYS:30}` to `application.properties` / `.env.example`
- [x] 1.5 Run `make lint-fix` / `./mvnw spotless:check` and `./mvnw verify`
- [x] 1.6 Open PR phase 1 (pt-BR, lint/test status)

## 2. Phase 2 — Policy + structured profile errors (PR 2)

> Goal: pure cooldown policy; **unified** error envelope (`message`, `code`, `field`, `retryAfter?`) for **409 and 400** of this feature; all `code` values lowercase snake_case; MessageSource keys.
> Depends on: Phase 1 | Parallel with: —
> Order: test → policy → exception/advice → messages

- [x] 2.1 Unit tests for `UserProfileChangePolicy` (within window → retryAfter; elapsed → allow; null last → allow)
- [x] 2.2 Implement `UserProfileChangePolicy` reading cooldown days from config (no servlet/JPA)
- [x] 2.3 Add `ProfileErrorResponseDTO` + `ProfileErrorCode` (lowercase snake_case) + typed exceptions: 409 `cooldown` / `email_duplicate`; 400 `field_null` / `phone_blank` / `email_same` / `email_invalid` / `email_required`
- [x] 2.4 Add `@RestControllerAdvice` (`ProfileErrorAdvice` in `br.com.vanep.user.exception`) mapping `ProfileErrorException` → HTTP status from the exception + shared DTO (decidido — não opcional)
- [x] 2.5 Add MessageSource keys (EN + `messages_pt_BR.properties`): cooldown per field, `user.profile.phone.blank`, `user.profile.email.same`, `user.profile.email.required`, `user.profile.email.invalid`, `user.profile.field.null`; reuse `auth.signup.email.duplicate` for duplicate **message** text
- [x] 2.6 Slice/unit tests: 409 cooldown (`code=cooldown` + ISO `retryAfter`); 409 duplicate (`code=email_duplicate`); each 400 code above with same shape and null/omitted `retryAfter`
- [x] 2.7 `make lint` + `./mvnw verify`; open PR phase 2

## 3. Phase 3 — PATCH /api/user/me (PR 3)

> Goal: partial update name/phone/gender with JsonNullable and cooldowns.
> Depends on: Phase 2 | Parallel with: Phase 4
> Order: test → request DTO → service → controller

- [x] 3.1 Failing unit tests for `UserProfileService.patchMe` (absent no-op, null→400 `field_null`, blank phone→400 `phone_blank`, name cooldown→409, gender always ok, same value no cooldown bump)
- [x] 3.2 Create `UserProfileUpdateRequestDTO` with `JsonNullable` fields
- [x] 3.3 Implement `UserProfileService.patchMe` (load via `UserService.requireByToken`, apply policy, persist `last_name_change_at` / `last_phone_change_at` only when value changes; throw `ProfileBadRequestException` / `ProfileCooldownException` — never bare `ResponseStatusException` for these)
- [x] 3.4 Add `PATCH /api/user/me` on `ProfileController` with `@Valid` + `isAuthenticated()`
- [x] 3.5 MockMvc slice tests: 401, 200 happy path, 400 blank phone (`code=phone_blank`), 409 name cooldown (`code=cooldown`, `retryAfter` ISO)
- [x] 3.6 Ensure `document` / `birthDate` untouched in assertions
- [x] 3.7 `make lint` + `./mvnw verify`; open PR phase 3

## 4. Phase 4 — Email change + verify (PR 4)

> Goal: pending_email flow; **invalidate open tokens** on each new challenge; cooldown on confirm only; mail to new address.
> Depends on: Phase 2 | Parallel with: Phase 3
> Order: test → DTO → service → verification evolve → controller

- [x] 4.1 Unit tests: start email-change sets pending, does not touch `email`/`last_email_change_at`; duplicate primary → 409 body `code=email_duplicate` + key `auth.signup.email.duplicate`; same email → 400 `code=email_same`; cooldown on last_email → 409 `code=cooldown`; replace pending **without** “already in progress” block
- [x] 4.2 Unit tests: issuing a new challenge consumes all prior open tokens; submitting old token_A after replace to B fails; token_B confirms B
- [x] 4.3 Unit tests: verify with pending promotes email + sets `last_email_change_at` + clears pending; race/duplicate on confirm; classic verify without pending unchanged
- [x] 4.4 Create `UserEmailChangeRequestDTO` (`@Email`, `@NotBlank`) — map validation failures to structured `email_invalid` / `email_required` (same envelope)
- [x] 4.5 Add repository method to consume open tokens by `user_id` (`consumed_at IS NULL`)
- [x] 4.6 Evolve `EmailVerificationService.startVerification`: invalidate open tokens, then create new token; send link to `pending_email` when set
- [x] 4.7 Implement `requestEmailChange` in `UserProfileService` (early `existsByEmail`, cooldown check, set pending, call verification — **does not** use `activePendingEmail` to reject; use `ProfileBadRequestException.emailSame` / conflict exceptions)
- [x] 4.8 Evolve `verify`: promote pending with unique handling (by token hash only)
- [x] 4.9 Add `POST /api/user/me/email-change` on `ProfileController`
- [x] 4.10 Adjust web verify error path for duplicate-on-confirm (query/flash) so deep links are not silent failures
- [x] 4.11 MockMvc / integration tests for POST + verify + A→B old-link scenarios
- [x] 4.12 `make lint` + `./mvnw verify`; open PR phase 4

## 5. Phase 5 — Enrich GET /me (PR 5)

> Goal: `pendingEmail` via `activePendingEmail` (GET-only) + `*ChangeAvailableAt` on `UserMeResponseDTO`.
> Depends on: Phases 3 and 4 | Parallel with: —
> Order: test → DTO → mapper/service → controller assertions

- [x] 5.1 Extend `UserMeResponseDTO` with `pendingEmail`, `nameChangeAvailableAt`, `phoneChangeAvailableAt`, `emailChangeAvailableAt`
- [x] 5.2 Implement `activePendingEmail` / `resolvePendingEmailForMe` (coluna + token aberto não expirado); document that **only** GET `/me` uses it
- [x] 5.3 Update `UserService.toMeResponse` / `getMe` to use the helper + compute available-at from last_* + cooldown config
- [x] 5.4 Unit + MockMvc tests: active pending shown; ghost pending (expired/no open token) → `pendingEmail` null; cooldown hints
- [x] 5.5 Update README snippet for `/api/user/me` if it documents the response shape
- [x] 5.6 `make lint` + `./mvnw verify`; open PR phase 5
