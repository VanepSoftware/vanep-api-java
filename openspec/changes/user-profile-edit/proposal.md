## Why

O app já lê a conta autenticada via `GET /api/user/me`, mas não há como o usuário editar dados pessoais (nome, telefone, gênero, e-mail). Os PUTs de client/driver/assistant só alteram campos de papel (foto, bio, etc.). Sem edição de conta, a tela de perfil no Flutter fica incompleta. Prioridade: **Alta** para o MVP do app.

## What Changes

- `PATCH /api/user/me` — atualização parcial de `name`, `phone`, `gender` (partial update com `JsonNullable` para distinguir ausente vs null explícito).
- `POST /api/user/me/email-change` — inicia troca de e-mail com `pending_email` + re-verificação; confirmação aplica o e-mail e marca cooldown.
- Cooldown de **30 dias** (configurável) em `name`, `phone` e `email`, contado a partir da **mudança efetiva** (para e-mail: no confirm, não no POST).
- `gender` editável sem cooldown; `document` e `birthDate` **imutáveis** nesta change; `password` e `username` fora de escopo.
- Resposta `409 Conflict` da feature usa **um único body JSON** (`message`, `code`, `field`, `retryAfter` opcional) tanto para cooldown quanto para e-mail duplicado — o Flutter distingue pela `code`, não pela forma do payload.
- Colunas novas em `users`: `pending_email`, `last_phone_change_at`, `last_email_change_at` (reusa `last_name_change_at`); **sem** unique em `pending_email`.
- Extensão do fluxo de verificação de e-mail para consumir `pending_email` no confirm; **invalidar tokens abertos** do user a cada novo challenge (evita link antigo confirmar pending novo).
- Estender `UserMeResponseDTO` com `pendingEmail` **ativo** (coluna + token válido; mascara pending fantasma pós-TTL) e metadados de cooldown para o Flutter.

**Fora de escopo:**
- Troca/alteração de senha (já existe forgot/reset)
- Edição de `document` (CPF) e `birthDate`
- Fluxo de `username`
- Unique constraint em `pending_email`
- Upload de foto (continua nos endpoints de role)

## Capabilities

### New Capabilities

- `user-profile-edit`: edição autenticada da conta (`PATCH /api/user/me`, troca de e-mail com pending + verify, cooldowns por campo, erros 409 estruturados).

### Modified Capabilities

- _(nenhuma spec main pré-existente de user profile)_

## Impact

- **Código:** `ProfileController`; `UserProfileService` / `UserService`; `UserModel`; `UserMeResponseDTO`; request DTOs `UserProfileUpdateRequestDTO` e `UserEmailChangeRequestDTO`; `UserProfileChangePolicy`; exceção tipada de conflito de perfil + `@RestControllerAdvice` para body 409 unificado; `EmailVerificationService` + `EmailVerificationTokenRepository` (consumir tokens abertos); fluxo web `/verify-email` (`EmailVerificationController` / template — erro de duplicata no confirm).
- **Schema:** nova migration Flyway em `users`.
- **Deps:** `jackson-databind-nullable` (`JsonNullable`) se ainda não estiver no `pom.xml`.
- **Mensagens:** MessageSource keys novas para cooldown/validação; duplicata de e-mail reusa `auth.signup.email.duplicate` (mesmo key, body 409 estruturado da feature).
- **Config:** `vanep.profile.change-cooldown-days` (default 30) via env / `application.properties`.
- **Auth:** `@PreAuthorize("isAuthenticated()")` — qualquer tipo de usuário edita a própria conta via `uid`.
- **Testes:** unit (policy + service) + slice MockMvc no `ProfileController` / verify (API e web).
- **Delivery:** fases empilhadas (migration → policy → PATCH → email-change → response DTO); PRs por camada (constituição 35–43).
