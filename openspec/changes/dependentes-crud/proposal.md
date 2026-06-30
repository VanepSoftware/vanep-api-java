## Why

O produto Vanep exige que clientes (responsáveis) cadastrem e gerenciem dependentes — alunos transportados — para reutilização em propostas e contratos (UC13, RF-119 a RF-123, RN12). O backend ainda não possui tabela, endpoints nem testes para essa entidade; sem isso o frontend (S14) e o fluxo de propostas (S10) ficam bloqueados.

## What Changes

- Nova migration Flyway `V7__create_dependent_table.sql` com coluna `deleted_at` e índices únicos parciais (`WHERE deleted_at IS NULL`), alinhado ao padrão da `V6__soft_delete_partial_unique_indexes.sql`.
- Novo pacote feature `br.com.vanep.dependente` com subpacotes por papel (`controller`, `dto`, `entity`, `enums`, `mapper`, `repository`, `service`, `seed`) e sufixos explícitos (CONSTITUTION regra 5).
- Entity `DependenteEntity` com `@SoftDelete(columnName = "deleted_at", strategy = TIMESTAMP)` — mesmo padrão de `User`, `Client` e `Driver` (PR #54).
- Novo enum `Shift` (`MORNING`, `AFTERNOON`, `NIGHT`, `FULLTIME`).
- Endpoints REST autenticados sob `/api/dependentes`:
  - `POST /api/dependentes` — criar
  - `GET /api/dependentes` — listar (filtrado por ownership para `ROLE_CLIENT`)
  - `GET /api/dependentes/{token}` — detalhe
  - `PATCH /api/dependentes/{token}` — atualizar (inclui `is_default`)
  - `DELETE /api/dependentes/{token}` — soft delete via `repository.delete()` (`@SoftDelete`)
  - `POST /api/dependentes/{token}/restore` — restaurar via native query (`deleted_at = NULL`)
- Autorização: `ROLE_CLIENT` acessa apenas dependentes do seu `client_id`; `ROLE_ADMIN` acessa todos.
- Regra RN12: dependente único vira padrão automaticamente; com dois ou mais, o cliente define manualmente via `is_default`.
- Regra de delete do padrão: se restar exatamente um dependente ativo, promovê-lo a padrão; se restarem zero ou dois ou mais, nenhum padrão ativo até o cliente redefinir.
- Seeder de dependentes de teste (requer client no seed).
- Testes automatizados cobrindo todos os endpoints (MockMvc + JWT); cleanup com `db/clean.sql` (DELETE nativo).
- Extensão de `ClientRepository` com `findByUserId` para resolver ownership a partir do JWT.

## Capabilities

### New Capabilities

- `dependentes`: CRUD REST de dependentes com `@SoftDelete`, restore via native query, regras de dependente padrão (RN12), autorização por role e seeder de dados de teste.

### Modified Capabilities

- _(nenhuma — não há specs existentes em `openspec/specs/`)_

## Impact

- **Banco**: nova tabela `dependent` (FK `client_id → client.id`; `school_id` e `address_id` nullable sem FK até existirem as tabelas `school` e `address`); índices únicos parciais em `token` e `document`.
- **API**: novos endpoints em `/api/dependentes/**`; identificadores públicos via `token` (nunca `id` numérico).
- **Segurança**: `@PreAuthorize` e validação de ownership no service; rotas sob `/api/**` já exigem JWT.
- **Seed**: extensão do `DataSeeder` ou seeder dedicado na feature.
- **Testes**: novos testes de slice; `clean.sql` estendido com `dependent`; JaCoCo no `verify`.
- **Entrega**: branch única `feat/dependentes-crud` com 3 PRs sequenciais para `main` (fundação → API → seed/testes), commits atômicos (1 arquivo por commit).
