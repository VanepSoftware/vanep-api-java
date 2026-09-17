## Context

A vanep-api-java (Java 25, Spring Boot 4, JPA/Flyway, OAuth2) já possui `CLIENT` e `DRIVER`. Não existe `UserType.ASSISTANT` nem vínculo motorista–assistente.

O MVP anterior propunha `driver_link_code` (código aberto curto). Esse desenho foi **abandonado**: código sem destinatário é adivinhável/transferível; rate limit e TTL não corrigem a raiz. O vínculo passa a ser **convite endereçado por e-mail**, com aceite/recusa no **app Flutter** via API REST autenticada (o assistente já tem conta e já usa o app).

**Regra de negócio central:** a conta ASSISTANT **sempre existe antes** do convite. A pessoa cria conta normalmente (fluxo igual client/driver, sem campo de convite), fica `UNLINKED`, e só depois um motorista a convida por e-mail. Não há cadastro “puxado” por código/link.

Constraints: `constitution.md`. Espelhar `ClientModel`, `RegistrationService`, `ClientSecurityService`, `MailService`.

## Goals / Non-Goals

**Goals:**

- Schema `assistant` + `assistant_invite` (V11; invite TTL **72h**; `token` opaco público)
- Auth ASSISTANT + JWT `assistant_status`
- Signup/OAuth ASSISTANT sempre `UNLINKED` — formulário **sem** campo de convite
- Motorista convida por e-mail (lookup em `user`); e-mail de **notificação** via MailService/Mailpit
- Assistente aceita/recusa via REST autenticado (`/api/assistants/me/invite/**`) no app Flutter
- Cancelamento manual de convite `PENDING` pelo motorista
- Reenvio = cancela `PENDING` anterior do mesmo par e cria novo invite (novo `token`, novo `expires_at`)
- Expiração **lazy** nos pontos de entrada
- Cooldown **7 dias** pós-`REJECTED` para o mesmo par driver–assistant
- pause/resume/revoke; listagem e `/me`
- Testes por fase

**Non-Goals:**

- `driver_link_code` e qualquer consume/generate de código aberto
- `link_token_hash` / secret de URL no e-mail
- Página web Thymeleaf de aceite/recusa de convite
- SMS / push
- Pedido iniciado pelo assistente; `link_initiated_by`
- `GET /api/assistants/{token}`
- Checklist, chat, rota, contratos, financeiro; enforcement de `verification_status`
- CRUD completo de Driver
- Job agendado de expiração (melhoria futura)

## Decisions

### 1. Packages: tudo em `assistant`

- **Decision:** `br.com.vanep.assistant` concentra perfil, invite e máquina de vínculo. Não há `DriverLinkCode*` em `driver`. O pacote só resolve o `driver_id` do motorista autenticado quando necessário.
- **Rationale:** constitution rule 5; o artefato de vínculo deixou de ser “código do motorista”.

### 2. Conta primeiro, convite depois (sem cadastro por link)

- **Decision:** Signup ASSISTANT é idêntico ao de client/driver — nenhum campo de convite. Sempre cria `assistant(driver_id=NULL, status=UNLINKED)`. O vínculo só ocorre via `assistant_invite` após a conta existir.
- **Rationale:** elimina “cadastro puxado” e remove qualquer tentação de reintroduzir código no formulário.
- **Lookup no convite:** motorista informa e-mail → `user.email` (unique):
  - Existe, `type=ASSISTANT`, `assistant.status=UNLINKED`, fora do cooldown → cria invite
  - Existe, mas `PENDING` / `ACTIVE` / `INACTIVE` → **409**
  - Existe, outro `UserType` → **409**
  - Não existe → erro claro (“nenhuma conta de assistente encontrada com esse e-mail”); **sem** criar conta nem efeito colateral

### 3. `assistant_invite` endereçado com `token` opaco

- **Decision:** Convite nasce amarrado a `assistant_id` + `driver_id`. Coluna pública `token` (opaque, `@PrePersist`) identifica o invite na API (cancelamento pelo motorista, DTO de convite pendente). **Não há** `link_token_hash` nem secret de URL — o aceite é resolvido pelo assistente autenticado via JWT.
- **Rationale:** o assistente já usa o app; não replicar o padrão de verificação de e-mail (link web) para um fluxo fundamentalmente diferente.

### 4. Status `PENDING` no assistant (não só no invite)

```
Convite criado → assistant.status = PENDING (driver_id continua NULL)
Aceito         → assistant.status = ACTIVE, driver_id = X, activated_at = now()
Recusado       → assistant.status = UNLINKED
Expirado       → assistant.status = UNLINKED
Cancelado      → assistant.status = UNLINKED
```

- **Decision:** Ao criar invite, `assistant.status → PENDING`. Elegibilidade para **receber** convite: somente `UNLINKED` → no máximo **um PENDING global** por assistente (impede cortejo simultâneo por dois motoristas).
- **Rationale:** leitura rápida no perfil (“você tem um convite pendente”); regra de concorrência simples. Histórico fino (cooldown, reenvio) continua consultando `assistant_invite`.

### 5. Reenvio = cancelar anterior + criar novo

- **Decision:** Se o motorista convidar o mesmo e-mail com `PENDING` vigente **do mesmo driver**, cancela o invite anterior (`CANCELLED`), cria novo (`novo token`, novo `expires_at`); `assistant.status` permanece `PENDING`.
- **Rationale:** histórico limpo; sem ambiguidade de “resetar TTL no mesmo registro”; alinhado a “reenvio = novo evento”.
- **Nota:** se o `PENDING` vigente for de **outro** driver, a elegibilidade (`assistant` já `PENDING`) já bloqueia com 409 — não há “reenvio” cruzado.

### 6. Expiração lazy (MVP)

- **Decision:** Sem job. Em todo ponto de entrada relevante, se `status=PENDING` e `expires_at < now()`:
  1. Marca invite `EXPIRED`, `responded_at` conforme implementação
  2. Reverte `assistant.status → UNLINKED` (se ainda `PENDING` por esse invite)
  3. Segue o fluxo (erro expirado, rejeita ação, ou permite novo convite)
- **Pontos:** `GET /api/assistants/me/invite`; POST accept/reject; criação de novo convite (PENDING expirado “fantasma” não bloqueia — trata como inexistente).
- **Future:** job ativo para limpeza/relatórios.

### 7. Cancelamento manual pelo motorista

- **Decision:** `DELETE /api/assistants/invites/{token}` (`token` = opaque público do invite), restrito ao motorista dono. Efeito: invite → `CANCELLED`; assistant → `UNLINKED` (se ainda `PENDING` por esse invite).
- **Rationale:** evita travar 72h após e-mail errado / desistência.

### 8. Aceite/recusa via REST autenticado (app Flutter)

- **Decision:** `GET /api/assistants/me/invite` retorna convite pendente com dados do motorista. `POST .../accept` e `POST .../reject` exigem JWT do assistente convidado. Sem Thymeleaf, sem token na URL, sem secret no e-mail.
- **Rationale:** assistente já tem conta e já usa o app; o e-mail é apenas notificação de aviso.

### 9. Cooldown 7 dias pós-recusa (mesmo par)

- **Decision:** Além de `assistant.status == UNLINKED`, a elegibilidade do motorista consulta histórico: existe `assistant_invite` com mesmo `driver_id` + `assistant_id`, `status=REJECTED`, `responded_at` dentro de **7 dias** → **409** (“você não pode reconvidar essa pessoa ainda”). Outro motorista **pode** convidar normalmente.
- **Rationale:** impede assédio por reenvio imediato após recusa; bloqueio permanente seria desproporcional.

### 10. Transições (MVP)

| De → Para | Quem / como |
|-----------|-------------|
| (novo) → UNLINKED | Signup / OAuth |
| UNLINKED → PENDING | Motorista cria invite (e-mail encontrado, elegível) |
| PENDING → ACTIVE | Assistente aceita via REST (`POST /me/invite/accept`) |
| PENDING → UNLINKED | Recusa / expiração lazy / cancelamento pelo motorista |
| ACTIVE → INACTIVE / resume | Motorista (pause/resume) |
| ACTIVE → UNLINKED (revoke) | Motorista ou assistente; limpa `driver_id` |

`REJECTED` / `EXPIRED` / `CANCELLED` são status do **invite**; o assistente volta a `UNLINKED` nesses desfechos.

### 11. Schema V11

```sql
-- assistant: user_id unique, driver_id nullable, status,
-- verification_status (default PENDING, sem enforcement), photo,
-- activated_at, timestamps, soft delete;
-- partial unique token/user_id WHERE deleted_at IS NULL

-- assistant_invite:
--   token (opaque público API, unique parcial soft-delete)
--   driver_id NOT NULL
--   assistant_id NOT NULL  -- conta ASSISTANT já existente; UNLINKED no momento do convite
--   status: PENDING | ACCEPTED | REJECTED | EXPIRED | CANCELLED (default PENDING)
--   expires_at (72h)
--   responded_at nullable
--   created_at, soft delete
-- indexes: driver_id, assistant_id
```

**Removido:** `link_token_hash`, qualquer tabela/model/enum `driver_link_code` / `DriverLinkCodeStatus`, rotas `/assistant-invite/**`.

### 12. Auth / JWT / permissions

- Role ASSISTANT + perms de perfil, convite pendente (show/update) e revoke próprio
- DRIVER: list, pause/resume/revoke, create/cancel invite (sem perms de link-code)
- Claim `assistant_status`

### 13. Fluxo completo de convite

1. Pessoa cria conta ASSISTANT → `UNLINKED`
2. Motorista → “Convidar assistente” → e-mail → lookup:
   - ASSISTANT `UNLINKED` + fora do cooldown → cria `assistant_invite(PENDING)`, `assistant → PENDING`, envia e-mail de notificação
   - Já vinculado / PENDING / outro tipo / inexistente → erros acima
   - Mesmo driver com PENDING vigente → Decision 5 (cancela + novo)
3. E-mail (Mailpit no MVP): nome do motorista, prazo (72h), instrução para abrir o app
4. Assistente abre o app → `GET /api/assistants/me/invite` (lazy expiry) → UI com motorista + Aceitar/Recusar
5. Aceitar → `POST /me/invite/accept` → `ACTIVE` + `driver_id` + `activated_at`; invite `ACCEPTED`
6. Recusar → `POST /me/invite/reject` → invite `REJECTED`; assistant `UNLINKED` (dispara cooldown 7d para aquele driver)

### 14. API surface

**Motorista (API):**

- `POST /api/assistants/invites` — body `{ "email" }` → cria (ou reenvio Decision 5) + dispara e-mail
- `DELETE /api/assistants/invites/{token}` — cancela PENDING próprio
- `GET /api/assistants` — lista vinculados (ACTIVE/INACTIVE)
- `POST /api/assistants/{token}/pause|resume|revoke`

**Assistente (API):**

- `GET|PUT /api/assistants/me`
- `GET /api/assistants/me/invite`
- `POST /api/assistants/me/invite/accept`
- `POST /api/assistants/me/invite/reject`
- `POST /api/assistants/me/revoke`

**Removido:** `/api/driver-link-codes/**`, `/assistant-invite/**`, `linkCode` no signup.

### 15. Mail

- Template Thymeleaf de e-mail (`email/assistant-invite`): nome do motorista, expiração, instrução para abrir o app — **sem link funcional**
- Config via env / `application-*.properties` — constitution rules 1 e 3
- Reenvio coberto pela Decision 5

### 16. Mensagens e identificadores

- MessageSource EN keys / pt-BR; identificadores públicos = `token` opaco

### 17. Fases

| Phase | Contents | Depends on | Parallel with |
|-------|----------|------------|---------------|
| 1 | V11 (`assistant`, `assistant_invite`), models, repos, enums, clean.sql | — | — |
| 2 | Auth, signup/OAuth UNLINKED (sem linkCode), JWT claim, seed | 1 | — |
| 3 | Invite create/cancel/eligibility/cooldown + MailService template; pause/resume/revoke | 2 | — |
| 4 | REST accept/reject + `/me` + listagem; E2E convite → aceite no app | 3 | — |

## Risks / Trade-offs

- **[Risk]** Motorista digita e-mail errado → convite vai para outra pessoa ou falha o lookup → **Mitigation:** erro claro se não houver ASSISTANT; cancelamento manual; TTL 72h; aceitar exige JWT da conta destinatária
- **[Risk]** Convite não aceito a tempo → **Mitigation:** expiração lazy; motorista pode reenviar (novo registro) ou cancelar e convidar outro
- **[Risk]** Reenvio agressivo / assédio após recusa → **Mitigation:** cooldown 7 dias no mesmo par driver–assistant; um PENDING global por assistente
- **[Risk]** PENDING “fantasma” expirado no banco até alguém tocar → **Mitigation:** aceitável no MVP; checagem lazy em todos os entry points; job futuro se precisar de limpeza
- **[Trade-off]** Sem convite para quem ainda não tem conta — onboarding é “crie conta ASSISTANT, depois peça para o motorista convidar”

## Migration Plan

1. V11 additive (`assistant`, `assistant_invite` sem `link_token_hash`) → seed roles/perms → APIs + mail
2. **Não** criar `driver_link_code`; se alguma branch experimental já tiver, não mergear esse caminho
3. Job de expiração / SMS = changes futuras (não editar V11 após aplicado em produção)

## Open Questions

- _(nenhuma)_ — decisões fechadas: PENDING no assistant; reenvio = cancel+create; expiração lazy; cancel manual sim; REST accept no app; um PENDING global + cooldown 7d pós-REJECTED
