## Why

Motoristas precisam vincular assistentes de bordo para operação diária, mas a API ainda não tem o tipo de usuário ASSISTANT nem um fluxo de vínculo seguro. O mecanismo anterior (`driver_link_code`, código aberto de 6 caracteres) foi descartado: código curto sem destinatário é inseguro por natureza (força bruta / forwarding), e nenhuma mitigação (hash, TTL, rate limit) resolve a raiz do problema. Com MailService/Mailpit já funcionais no MVP, o vínculo passa a ser **convite endereçado por e-mail** — a conta ASSISTANT sempre existe antes do convite; o motorista convida pelo e-mail; o assistente aceita/recusa no **app Flutter** via API REST autenticada.

## What Changes

- Novo `UserType.ASSISTANT` / `RoleName.ASSISTANT`, permissões mínimas, seed e claim JWT `assistant_status`
- Flyway `V11`: tabelas `assistant` e `assistant_invite` (+ `clean.sql`) — **sem** `driver_link_code` e **sem** `link_token_hash`
- Pacote `br.com.vanep.assistant` concentra perfil, invite e máquina de vínculo (resolve `driver_id` do motorista autenticado sem domínio de link-code em `driver`)
- Cadastro Thymeleaf `/signup/assistant` **idêntico** ao de client/driver — sem campo de convite; nasce sempre `UNLINKED`
- Vínculo: motorista convida por e-mail → e-mail de **notificação** (MailService/Mailpit) → assistente aceita/recusa no app via `GET|POST /api/assistants/me/invite/**` → pause/resume/revoke
- Remoção completa do desenho `driver_link_code` / generate / cancel / consume / `linkCode` no signup
- Testes unitários e de integração

**Fora de escopo:**
- SMS / push de convite
- Página web Thymeleaf de aceite/recusa de convite (fluxo é nativo no Flutter)
- Pedido de vínculo iniciado pelo assistente; `link_initiated_by`
- `GET /api/assistants/{token}`
- Checklist, chat, rota, contratos, financeiro
- Enforcement de `verification_status`
- CRUD completo de Driver
- Job agendado de expiração (MVP = lazy nos pontos de entrada)

## Capabilities

### New Capabilities

- `assistant-auth-signup`: Auth ASSISTANT + signup Thymeleaf/OAuth sempre `UNLINKED` (sem campo de convite)
- `assistant-linking`: Convite por e-mail (`assistant_invite`), REST autenticado de aceite/recusa, pause/resume/revoke, cooldown pós-recusa
- `assistant-profile`: CRUD `/api/assistants/me` + listagem do motorista + ownership

### Modified Capabilities

- _(none)_

## Impact

- **Database**: V11 (`assistant`, `assistant_invite` com `token` opaco público, TTL 72h); `clean.sql`
- **Auth / web**: signup ASSISTANT sem convite (Thymeleaf de cadastro apenas)
- **Mail**: template de notificação de convite + envio real via MailService (Mailpit no MVP)
- **API**: `/api/assistants/**`, `/api/assistants/invites/**`, `/api/assistants/me/invite/**` — **sem** `/api/driver-link-codes/**` e **sem** `/assistant-invite/**`
- **Messages / tests**: MessageSource + JaCoCo
