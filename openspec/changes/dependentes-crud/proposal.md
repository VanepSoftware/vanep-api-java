## Why

O produto Vanep exige que clientes (responsáveis) cadastrem e gerenciem dependentes — alunos transportados — para reutilização em propostas e contratos (UC13, RF-119 a RF-123, RN12). O backend ainda não possui tabela, endpoints nem testes para essa entidade; sem isso o frontend (S14) e o fluxo de propostas (S10) ficam bloqueados.

## What Changes

- Nova migration Flyway `V6__create_dependent_table.sql` com soft delete (`deleted_at`).
- Novo pacote feature `br.com.vanep.dependente` (entity, repository, DTOs, mapper, service, controller).
- Novo enum `Shift` (`MORNING`, `AFTERNOON`, `NIGHT`, `FULLTIME`).
- Endpoints REST autenticados sob `/api/dependentes`:
  - `POST /api/dependentes` — criar
  - `GET /api/dependentes` — listar (filtrado por ownership para `ROLE_CLIENT`)
  - `GET /api/dependentes/{token}` — detalhe
  - `PATCH /api/dependentes/{token}` — atualizar (inclui `is_default`)
  - `DELETE /api/dependentes/{token}` — soft delete
  - `POST /api/dependentes/{token}/restore` — restaurar registro deletado
- Autorização: `ROLE_CLIENT` acessa apenas dependentes do seu `client_id`; `ROLE_ADMIN` acessa todos.
- Regra RN12: dependente único vira padrão automaticamente; com dois ou mais, o cliente define manualmente via `is_default`.
- Regra de delete do padrão: se restar exatamente um dependente ativo, promovê-lo a padrão; se restarem zero ou dois ou mais, nenhum padrão ativo até o cliente redefinir.
- Seeder de dependentes de teste (requer client no seed).
- Testes automatizados cobrindo todos os endpoints (MockMvc + JWT).
- Extensão de `ClientRepository` com `findByUserId` para resolver ownership a partir do JWT.

## Capabilities

### New Capabilities

- `dependentes`: CRUD REST de dependentes com soft delete, restore, regras de dependente padrão (RN12), autorização por role e seeder de dados de teste.

### Modified Capabilities

- _(nenhuma — não há specs existentes em `openspec/specs/`)_

## Impact

- **Banco**: nova tabela `dependent` (FK `client_id → client.id`; `school_id` e `address_id` nullable sem FK até existirem as tabelas `school` e `address`).
- **API**: novos endpoints em `/api/dependentes/**`; identificadores públicos via `token` (nunca `id` numérico).
- **Segurança**: `@PreAuthorize` e validação de ownership no service; rotas sob `/api/**` já exigem JWT.
- **Seed**: extensão do `DataSeeder` ou seeder dedicado na feature.
- **Testes**: novos testes de slice; JaCoCo no `verify`.
- **Entrega**: branch única `feat/dependentes-crud` com 3 PRs sequenciais para `main` (fundação → API → seed/testes), commits atômicos (1 arquivo por commit).
