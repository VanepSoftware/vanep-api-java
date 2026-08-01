## Context

Hoje `GET /api/user/me` (`ProfileController` + `UserService`) devolve a conta autenticada (`UserMeResponseDTO`), mas não há mutação de campos de `users`. Os endpoints de role (`PUT /api/clients/{token}`, drivers, assistants) alteram só dados de papel (foto, bio, endereço…). A coluna `last_name_change_at` já existe sem regra. `EmailVerificationService` só marca `verified=true` — não conhece troca de e-mail.

Stakeholders: app Flutter (tela de perfil), API Vanep. Constraints: constituição (feature packages, DTOs, MessageSource pt-BR, Flyway only, phased PRs), reuso de `auth.signup.email.duplicate`.

---

## Goals / Non-Goals

**Goals:**

- Permitir edição autenticada de `name`, `phone`, `gender` via `PATCH /api/user/me`.
- Permitir troca de e-mail via `POST /api/user/me/email-change` + confirmação no fluxo de verify existente (E2: `pending_email`).
- Enforçar cooldown de 30 dias (configurável) em name/phone/email, contado na mudança efetiva.
- Expor `409` com **um body JSON único** da feature (`code`, `message`, `field`, `retryAfter` opcional) para cooldown e e-mail duplicado.
- Manter `document` e `birthDate` imutáveis; password/username fora.

**Non-Goals:**

- Alterar senha, username, CPF, birthDate.
- Unique em `pending_email`.
- Espelhar e-mail do provider OAuth (conta local é fonte de login/contato).
- PUT `/me` tipado por role nesta change.
- API mobile dedicada de verify (reusa `/verify-email`; deep link do app aponta para o mesmo).

---

## Decisions

### D1 — Superfície HTTP (A2)

| Método | Path | Body | Efeito |
|--------|------|------|--------|
| `PATCH` | `/api/user/me` | `name?`, `phone?`, `gender?` | Atualiza campos presentes; aplica cooldown se valor mudou |
| `POST` | `/api/user/me/email-change` | `{ "email": "..." }` | Seta `pending_email`, dispara e-mail de verificação; **não** marca cooldown |
| (existente) | `GET /verify-email?token=` | — | Se há `pending_email`, promove a `email`, limpa pending, `verified=true`, seta `last_email_change_at` |

Auth: `@PreAuthorize("isAuthenticated()")` + `SecurityHelper.requireCallerUid` → `UserService.requireByToken`. Sem permissão admin especial — só o próprio uid.

**Alternativa rejeitada (A1):** um PATCH único incluindo email mistura fluxo assíncrono com sync e complica testes/UX.

### D2 — Partial update com `JsonNullable`

Request DTO (`UserProfileUpdateRequestDTO`) usa `JsonNullable<String>` / `JsonNullable<Gender>`:

| Estado JSON | Semântica |
|-------------|-----------|
| campo ausente | no-op |
| `"field": null` | **400** (não limpamos name/phone/gender) |
| `"phone": ""` | **400** (`user.profile.phone.blank`) |
| valor igual ao atual | no-op (não bumpa cooldown) |
| valor novo válido | aplica + seta `last_*_change_at` |

Dep: adicionar `org.openapitools:jackson-databind-nullable` + registrar `JsonNullableModule` na config Jackson (se não auto-configurado).

**Alternativa rejeitada:** `Optional` puro não distingue ausente de null no Jackson sem custom deserializer.

### D3 — Cooldown storage (C1)

Migration nova (próxima Vx):

```sql
alter table users
  add column pending_email varchar(255),
  add column last_phone_change_at timestamptz,
  add column last_email_change_at timestamptz;
-- last_name_change_at já existe
-- SEM unique em pending_email
```

Config: `vanep.profile.change-cooldown-days=${PROFILE_CHANGE_COOLDOWN_DAYS:30}`.

Policy pura `UserProfileChangePolicy` (sem JPA/servlet): `assertCanChange(Instant lastChangeAt, Instant now) → Instant retryAfter` ou lança exceção de domínio. Service aplica MessageSource + HTTP.

Cooldown de e-mail: **somente no confirm bem-sucedido**, não no POST.

### D4 — Email pending (E2) sem unique em pending

```
POST email-change
  ├─ email inválido / blank → 400
  ├─ email == users.email atual → 400 (noop senseless)
  ├─ existsByEmail(novo) (conta ativa) → 409 estruturado code=email_duplicate, field=email
  │     message via auth.signup.email.duplicate
  ├─ cooldown ativo (last_email_change_at) → 409 estruturado code=cooldown, field=email + retryAfter
  ├─ pending_email = novo (sempre substitui; NÃO bloqueia se já há pending)
  ├─ invalidateOpenVerificationTokens(user)   // ver D10 — obrigatório
  ├─ startVerification(user)  // e-mail enviado ao ENDEREÇO NOVO
  └─ NÃO altera users.email nem verified nem last_email_change_at

GET /me → pendingEmail via activePendingEmail(user) somente (ver D9)

verify(token)  // por hash; não usa activePendingEmail
  ├─ token inválido/expirado/consumido → falha atual
  ├─ se pending_email != null:
  │     ├─ existsByEmail(pending) → conflito (409 / página de erro)
  │     │     key auth.signup.email.duplicate
  │     ├─ email = pending_email; pending_email = null
  │     ├─ last_email_change_at = now
  │     └─ verified = true
  └─ senão: verified = true (signup clássico)
```

Envio do mail de verificação na troca: o link deve ir para o **e-mail novo** (`pending_email`), não para `users.email`. Ajustar `EmailVerificationService.startVerification` (overload ou ler pending se setado).

OAuth-only: permitido; `users.email` é login/contato da Vanep, desacoplado do provider.

Duplicata de e-mail: **reusar** `auth.signup.email.duplicate` — textos EN/pt-BR já são genéricos (“Já existe uma conta com este e-mail.”), sem menção a cadastro.

### D9 — `activePendingEmail` só para o GET `/me`

Helper (nome sugerido: `activePendingEmail(user) → Optional<String>` / `resolvePendingEmailForMe`), **não** um boolean de gate:

- Retorna `pending_email` **somente se** a coluna está preenchida **e** existe ao menos um `email_verification_token` do user com `consumed_at IS NULL` e `expires_at > now`.
- Caso contrário retorna vazio → o DTO expõe `pendingEmail: null` (máscara de “pending fantasma” após TTL; MVP sem CTA “solicitar de novo”; o user pede de novo via POST).

**Escopo de uso nesta change (explícito):**

| Caller | Usa o helper? | Comportamento |
|--------|---------------|---------------|
| `GET /api/user/me` (montagem do DTO) | **Sim — único consumidor** | Decide o valor de `pendingEmail` |
| `POST /email-change` | **Não** | Sempre sobrescreve pending; não rejeita “já tem troca em andamento” |
| `verify` | **Não** | Resolve pelo hash do token (fluxo existente) |

Não tratar o helper como abstração genérica de política — evita alguém plugar um `409` indevido no POST.

Lazy cleanup da coluna no GET (**não** nesta change): máscara na leitura basta para o MVP.

### D10 — Invalidar tokens abertos ao novo pending (obrigatório, Fase 4)

Problema: token não carrega o e-mail alvo. Sequência A→B sem invalidar deixa `token_A` válido promovendo o `pending_email` atual (B) — janela de confirmação sem consentimento do endereço final (confused deputy).

**Requisito:** ao iniciar (ou substituir) um `pending_email` / ao emitir novo challenge de verificação nesse fluxo, o sistema MUST marcar como consumidos **todos** os tokens abertos do user (`consumed_at = now()` where `user_id = ? AND consumed_at IS NULL`) **antes** de persistir o token novo. Preferir centralizar em `EmailVerificationService.startVerification` (ou path único chamado pelo email-change) para não haver caminho paralelo que esqueça o UPDATE.

Binding token↔email pretendido = follow-up estrutural; invalidação é mitigação suficiente nesta change.

### D5 — 409 estruturado (contrato único da feature)

Hoje o projeto usa `ResponseStatusException` (só `reason` string). Na feature de perfil, **todos** os `409` de API usam o mesmo envelope — o Flutter **não** distingue causas pela ausência de campos.

DTO de resposta (nome sugerido: `ProfileConflictResponseDTO`):

```json
{
  "message": "…pt-BR…",
  "code": "cooldown",
  "field": "email",
  "retryAfter": "2026-08-31T15:00:00Z"
}
```

| `code` | Quando | `field` | `retryAfter` |
|--------|--------|---------|--------------|
| `cooldown` | mudança bloqueada pelo cooldown | `name` \| `phone` \| `email` | ISO-8601 obrigatório |
| `email_duplicate` | e-mail já é `users.email` de outra conta (POST email-change ou confirm via API) | `email` | omitido ou `null` |

- Exceções tipadas (ex.: `ProfileCooldownException`, `ProfileEmailDuplicateException`) ou uma `ProfileConflictException(code, field, retryAfter, message)`.
- `@RestControllerAdvice` **decidido** (não opcional) mapeia essas exceções → HTTP 409 + DTO acima.
- Mensagem de duplicata continua resolvida por `auth.signup.email.duplicate` (copy genérico); só o **envelope HTTP** muda vs signup Thymeleaf.
- Fluxo **web** `/verify-email` (redirect/template): não precisa do JSON; trata duplicata no confirm com erro explícito na UI (query/flash) — ver risco na tabela.

**Alternativa rejeitada (A):** dois shapes de 409 no mesmo endpoint (cooldown estruturado vs `ResponseStatusException` string) — frágil para o Flutter.

### D6 — Pacotes e camadas

- HTTP: estender `ProfileController` (`br.com.vanep.auth.api`) — já é `/api/user`.
- Domínio: `br.com.vanep.user` — `UserProfileUpdateRequestDTO`, `UserEmailChangeRequestDTO`, `ProfileConflictResponseDTO`, `UserProfileChangePolicy`, `UserProfileService` (+ `UserService` para require/getMe).
- Verify: estender `EmailVerificationService` + `EmailVerificationTokenRepository`; ajustar `EmailVerificationController` / template web para erro de duplicata no confirm.
- Mensagens: keys novas + reuso `auth.signup.email.duplicate` (message only; envelope = D5).

### D7 — GET `/me` enriquecido

`UserMeResponseDTO` ganha:

- `pendingEmail` (`String`, nullable) — valor de **D9** (`activePendingEmail`), nunca a coluna crua sozinha
- `nameChangeAvailableAt`, `phoneChangeAvailableAt`, `emailChangeAvailableAt` (`Instant?`) — `last_* + cooldown` se ainda no futuro; senão null

Decisão: **incluir** os três `*ChangeAvailableAt` + `pendingEmail` mascarado (baixo custo, alto valor UX).

### D8 — phone “obrigatório se enviado”

Phone pode ser `null` no banco (signup opcional). PATCH:

- ausente → no-op  
- null JSON / `""` → 400  
- valor non-blank → set + cooldown se mudou  

Não há operação de limpar phone.

---

## Dependency graph / PR plan

```
                    ┌─────────────────────────┐
                    │ 1 Migration + Model     │
                    │   + Jackson JsonNullable│
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │ 2 Policy + Exceptions   │
                    │   + MessageSource keys  │
                    │   + Advice 409          │
                    └───────────┬─────────────┘
                                │
              ┌─────────────────┴─────────────────┐
              │                                   │
  ┌───────────▼───────────┐         ┌─────────────▼────────────┐
  │ 3 PATCH /api/user/me  │         │ 4 Email-change + verify  │
  │   + DTOs + service    │         │   + invalidate tokens    │
  └───────────┬───────────┘         └─────────────┬────────────┘
              │                                   │
              └─────────────────┬─────────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │ 5 UserMeResponseDTO     │
                    │   activePendingEmail    │
                    │   + availableAt         │
                    └─────────────────────────┘
```

| Phase | Contents | Depends on | Parallel with |
|-------|----------|------------|---------------|
| 1 | Flyway (`pending_email`, `last_phone_change_at`, `last_email_change_at`); mapear em `UserModel`; dep `jackson-databind-nullable` + module; property cooldown | — | — |
| 2 | `UserProfileChangePolicy`; exceções de conflito + advice + `ProfileConflictResponseDTO` (`code`, `message`, `field`, `retryAfter?`); MessageSource keys + testes | 1 | — |
| 3 | `UserProfileUpdateRequestDTO`; `UserProfileService.patchMe`; `PATCH /api/user/me`; testes unit + MockMvc | 2 | 4 |
| 4 | `UserEmailChangeRequestDTO`; `POST .../email-change`; invalidate open tokens; evolve verify (mail → pending; confirm promove); testes incl. A→B + link antigo morto | 2 | 3 |
| 5 | Estender `UserMeResponseDTO` + `activePendingEmail` no GET + `*ChangeAvailableAt`; testes (pending fantasma / token expirado → null) | 3, 4 | — |

Cada fase: test-first, `make lint` + `./mvnw verify`, PR próprio (~600 LOC / 10 files).

Ordem canônica por fase (constituição 41): test → migration (só fase 1) → model → … → service → controller → response DTO.

---

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Dois users com mesmo `pending_email`; segundo no confirm perde | Unique em `email` no confirm → 409 estruturado `code=email_duplicate` (API) ou erro web explícito; message key `auth.signup.email.duplicate` |
| Link antigo confirma pending novo (A→B) | **D10:** consumir todos os tokens abertos do user antes do novo token |
| Pending fantasma após TTL (coluna cheia, link morto) | **D9:** GET mascara via `activePendingEmail`; POST substitui sem bloqueio |
| Spam de `POST email-change` antes do confirm (sem cooldown) | Aceito por D4; rate-limit HTTP existente (`RateLimitingFilter`) ajuda; follow-up se abuso |
| Verify web hoje só redirect boolean — conflito de unique precisa UX | Mapear conflito para query `?error=email_taken` ou página de erro; documentar para Flutter deep link |
| `JsonNullable` + Bean Validation | Validar no service os campos `isPresent()`; anotações BV em getters wrapped exigem cuidado — preferir validação explícita no service para present values |
| H2 vs Postgres partial indexes | Sem unique novo em pending; sem risco de índice parcial novo |
| OAuth user troca e-mail e Google ainda usa outro | Documentado: e-mail Vanep ≠ provider; login local/password usa `users.email`; OAuth continua por `oauth_account` |

---

## Migration Plan

1. Deploy migration (nullable columns — sem backfill).
2. Deploy app com PATCH + email-change (backward compatible: GET `/me` só ganha campos novos).
3. Rollback app: colunas órfãs ok; pending_email não consumido até re-deploy.
4. Não editar migrations antigas.

---

## Closed decisions (ex-open questions)

1. **Cancelar pending** — sem `DELETE`; POST substitui; após TTL o GET mascara (D9).
2. **Duplicata de e-mail** — API `409` com envelope D5 (`code=email_duplicate`) + message key `auth.signup.email.duplicate`; web verify com erro explícito (não silencioso).
3. **Contrato 409** — um shape só (`code` discrimina cooldown vs duplicate); rejeitado dois formatos no mesmo endpoint.
4. **Pending no GET** — só via `activePendingEmail` (coluna + token vivo); helper **somente** no GET (D9).
5. **Tokens anteriores** — invalidação obrigatória na Fase 4 (D10), não follow-up.
6. **Issue GitHub** — linkar no PR quando existir (`Closes #N`).
