## Why

Motoristas precisam vincular assistentes de bordo para operação diária, mas a API ainda não tem o tipo de usuário ASSISTANT nem um fluxo de vínculo. O MVP entrega auth/cadastro, um único mecanismo de código aberto (`driver_link_code`, TTL 48h) consumível no signup ou pós-login, e gestão do vínculo — sem convite por e-mail endereçado.

## What Changes

- Novo `UserType.ASSISTANT` / `RoleName.ASSISTANT`, permissões mínimas, seed e claim JWT `assistant_status`
- Flyway `V11`: tabelas `assistant` e `driver_link_code` (+ `clean.sql`) — **sem** `assistant_invite`
- Pacote `br.com.vanep.assistant` + `DriverLinkCode*` em `br.com.vanep.driver`
- Cadastro Thymeleaf `/signup/assistant` com `linkCode` **opcional visível**; OAuth complete → sempre `UNLINKED` (vínculo depois via `/consume`)
- Vínculo: generate/cancel/consume do mesmo código (signup **ou** pós-login) + pause/resume/revoke + listagem/perfil
- Rate limit forte em signup e em `/consume`
- Testes unitários e de integração

**Fora de escopo:**
- MailService / SMS / push de convite
- Deep links / campo hidden pré-preenchido (future; MVP = campo visível opcional)
- Porta A: `assistant_invite`, convite por e-mail endereçado, `POST /api/assistants/invites`, fluxo PENDING (accept/reject/cancel)
- Pedido de vínculo iniciado pelo assistente; `link_initiated_by`
- `GET /api/assistants/{token}`
- Checklist, chat, rota, contratos, financeiro
- Enforcement de `verification_status`
- CRUD completo de Driver

## Capabilities

### New Capabilities

- `assistant-auth-signup`: Auth ASSISTANT + signup Thymeleaf (opcional `linkCode` → ACTIVE) e OAuth complete (`UNLINKED`)
- `assistant-linking`: Código aberto unificado (`driver_link_code`, TTL 48h, single-use) em signup e `/consume`, pause/resume/revoke, listagem
- `assistant-profile`: CRUD `/api/assistants/me` + listagem do motorista + ownership

### Modified Capabilities

- _(none)_

## Impact

- **Database**: V11 (`assistant`, `driver_link_code` TTL 48h); `clean.sql`
- **Auth / web**: signup com `linkCode` opcional; OAuth sem código; rate limit em rotas públicas de signup
- **API**: `/api/assistants/**`, `/api/driver-link-codes/**`
- **Messages / tests**: MessageSource + JaCoCo
