## Context

A vanep-api-java (Java 25, Spring Boot 4, JPA/Flyway, OAuth2) já possui `CLIENT` e `DRIVER`. Não existe `UserType.ASSISTANT` nem vínculo motorista–assistente.

O MVP usa **um único artefato** — `driver_link_code` (código aberto, TTL **24h**, uso único) — consumível em dois contextos: signup Thymeleaf (conta nova) e `POST /api/driver-link-codes/consume` (conta existente).

Constraints: `constitution.md`. Espelhar `ClientModel`, `RegistrationService`, `ClientSecurityService`, `SecureTokens`.

## Goals / Non-Goals

**Goals:**

- Schema `assistant` + `driver_link_code` (V11, TTL 24h)
- Auth ASSISTANT + JWT `assistant_status`
- Signup com `linkCode` opcional visível → `UNLINKED` ou `ACTIVE`; OAuth → `UNLINKED` + `/consume` depois
- Generate/cancel/consume; pause/resume/revoke; listagem e `/me`
- Rate limit forte em `/signup/assistant` e `/consume`
- Testes por fase

**Non-Goals:**

- MailService / SMS / push
- Deep links / hidden prefill (future)
- Porta A: `assistant_invite`, e-mail endereçado, invites API, PENDING accept/reject/cancel
- `link_initiated_by`; pedido iniciado pelo assistente
- `GET /api/assistants/{token}`
- Checklist, chat, rota, contratos, financeiro; enforcement de `verification_status`
- CRUD completo de Driver

## Decisions

### 1. Packages: assistant vs driver

- **Decision:** `br.com.vanep.assistant` para perfil/vínculo; `DriverLinkCode*` em `br.com.vanep.driver`. `AssistantLinkService` (ou helper compartilhado) executa o consume atômico usado por signup e API.
- **Rationale:** constitution rule 5.

### 2. Código aberto unificado (sem Porta A)

```
driver_link_code (TTL 24h, single-use)
        │
        ├─ Signup (linkCode opcional) → user + assistant ACTIVE
        └─ POST /consume (JWT)        → assistant UNLINKED → ACTIVE
```

- **Decision:** Uma tabela cobre assíncrono (WhatsApp/share manual) e presencial. Sem `assistant_invite`.
- **Rationale:** Porta A (e-mail endereçado + PENDING + deep link) ainda é non-goal; unificar no open code evita dois sistemas.
- **Trade-off consciente:** código não amarra a um e-mail — quem tiver o código vincula uma vez.

### 2b. TTL 24h (não 30 min)

- **Decision:** `expires_at = now() + 24 hours`. Sem pretendência de “cola presencial de 30 min”.
- **Rationale:** janela para compartilhar e cadastrar depois; 30 min quebrava o assíncrono.
- **Note:** uso continua **único** (não multi-consume); “reutilizável” só no sentido de dois contextos e janela longa.

### 2c. Signup: campo `linkCode` visível opcional

- **Decision:** `AssistantSignupForm.linkCode` opcional; template Thymeleaf com input **visível** (usuário cola o código). Sem hidden/deep link neste MVP.
- **Future:** deep link / hidden prefill sobre o mesmo campo.
- **Invalid/expired code no signup:** rejeita o cadastro com erro localizado (não cria user parcial com vínculo falho) — mesma mensagem genérica de código inválido/expirado quando apropriado.

### 2d. OAuth sem código no complete

- **Decision:** `/signup/complete` como ASSISTANT cria sempre `UNLINKED`. Vínculo depois via app autenticado → `/consume`.
- **Rationale:** evita assinar código em fluxo OAuth nesta change; um canal (password signup) já cobre “conta nova + código”.

### 2e. Rate limit forte

- **Decision:** rate limit em `POST /signup/assistant` (path público) e em `POST /api/driver-link-codes/consume`. Preferir `RateLimitingFilter` por path; senão contador no service.
- **Rationale:** open code + TTL longo + signup público aumentam guessing/spam.

### 3. Sem invite schema / sem PENDING no MVP

- **Decision:** Sem `assistant_invite`, sem `invited_at`, sem endpoints PENDING. Enum `AssistantStatus` pode reservar `PENDING` para o futuro, sem transições no MVP.
- **Rationale:** YAGNI.

### 4. Hash via `SecureTokens`

- **Decision:** só `code_hash` no DB; plaintext uma vez no response do generate. Alfabeto 6 chars sem `0/O/1/I`.
- **Atomic consume:** `UPDATE … WHERE status=ACTIVE AND expires_at > now()`; 0 rows → erro genérico.

### 4c. Sem GET unitário

- **Decision:** só `GET /api/assistants` (DTO completo).

### 5. Transições (MVP)

| De → Para | Quem / como |
|-----------|-------------|
| (novo) → ACTIVE | Signup com `linkCode` válido |
| UNLINKED → ACTIVE | `/consume` autenticado |
| ACTIVE → INACTIVE / resume | Motorista |
| ACTIVE → UNLINKED (revoke) | Motorista ou assistente; limpa `driver_id` |

Elegibilidade `/consume`: `UNLINKED`. `ACTIVE`/`INACTIVE` → 409.

### 6. Schema V11

```sql
-- assistant: user_id unique, driver_id nullable, status,
-- verification_status (default PENDING, sem enforcement), photo,
-- activated_at, timestamps, soft delete; partial unique token/user_id WHERE deleted_at IS NULL

-- driver_link_code: driver_id, code_hash unique, status ACTIVE|CONSUMED|EXPIRED|CANCELLED,
-- expires_at (24h), consumed_*, created_at; index driver_id
```

Gerar: cancela ACTIVE anterior do driver (1 ACTIVE por driver).

### 7. Auth / JWT / permissions

- Role ASSISTANT + perms de perfil; DRIVER: list, pause/resume/revoke, link-code (sem invite)
- Claim `assistant_status`

### 8. Signup / OAuth

- Password: `registerAssistant` — sem code → `UNLINKED`; com code válido → consume atômico + `ACTIVE` + `activated_at`
- OAuth ASSISTANT → `UNLINKED` only
- SecurityConfig: rotas signup públicas

### 9. API surface

**Motorista:** `GET /api/assistants`; `POST …/{token}/pause|resume|revoke`; `POST/DELETE /api/driver-link-codes[…]`

**Assistente:** `GET|PUT /me`; `POST /me/revoke`; `POST /api/driver-link-codes/consume`

### 10. Mensagens e identificadores

- MessageSource EN keys / pt-BR; `token` público

### 11. Fases

| Phase | Contents | Depends on |
|-------|----------|------------|
| 1 | V11, models, repos | — |
| 2 | Auth, signup (+ linkCode), OAuth UNLINKED, rate limit signup | 1 |
| 3 | Link service, generate/cancel/consume API, pause/resume/revoke, rate limit consume | 2 |
| 4 | `/me`, listagem, testes E2E | 3 |

## Risks / Trade-offs

- **[Risk]** Código aberto com TTL 24h → forward/WhatsApp → **Mitigation:** single-use, 1 ACTIVE/driver, rate limit, erro genérico
- **[Risk]** Guessing no signup público → **Mitigation:** rate limit forte + alfabeto restrito
- **[Risk]** Race consume → UPDATE condicional
- **[Trade-off]** OAuth não vincula no complete — precisa `/consume` depois
- **[Trade-off]** Sem e-mail endereçado — quem tem o código vincula

## Migration Plan

1. V11 additive → seed → APIs
2. Deep link / MailService / Porta A = changes futuras (não editar V11)

## Open Questions

- _(nenhuma)_ — TTL fixo **24h**; deep link/Mail = future
