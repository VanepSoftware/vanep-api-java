## Why

O Vanep é transporte escolar: cada responsável tem a casa dele, cada passageiro tem o ponto de embarque dele, cada escola tem a sede dela. Hoje `address` é um catálogo de lugar da plataforma (CRUD admin, sem dono, `address_id` compartilhado). O app de perfil não consegue editar endereço com segurança — `PUT` num token alheio mudaria o mesmo registro para todo mundo. A issue #37 pediu CRUD geográfico; a regra de negócio do passageiro nunca entrou. Precisamos corrigir o modelo **antes** de expor edição no Flutter.

## What Changes

- Cada linha `address` passa a ser **exclusiva de um dono**: no máximo um `client`, um `dependent` ou uma `school` aponta para ela (1:1). Mesmo CEP+número **não** reutiliza linha.
- Nova migration (não editar V14): FKs reais `*_address_id → address.id` e unique parcial por ponteiro. **Sem backfill.** Só ambiente de dev: recrear o banco (volume/schema) e deixar o seeder popular 1:1.
- Upsert aninhado no recurso do dono (payload de endereço, não `addressToken` / `addressId` numérico). Sem linha → cria e linka; com linha → atualiza só essa; limpar → soft delete da linha do dono. Soft delete do **dono** (client/dependent/school) também faz clear do endereço na mesma transação; restore do dono **não** ressuscita o endereço.
- **BREAKING** `PUT /api/clients/{token}`: deixa de aceitar `addressToken`. Foto permanece nesse PUT. Casa do client: `GET /api/clients/me` + `PUT`/`DELETE /api/clients/me/address` (replace do sub-recurso, constitution regra 16).
- **BREAKING** school: create/update deixam de aceitar `addressId` (Long). Create (`POST`) aceita `address` aninhado. Update deixa de ser `PUT /api/schools/{token}` e passa a `PATCH /api/schools/{token}` (`JsonNullable` em todos os campos: omitir = não mexe, `null` = limpa se a coluna for opcional). O PUT some; não fica alias.
- **BREAKING** dependent PATCH: deixa o `if (getX() != null)` (null = omit). `DependentUpdateDTO` passa a `JsonNullable` em todos os campos mutáveis, igual school/`PATCH /api/user/me`. Sem `addressToken`; endereço aninhado. `schoolToken` presente continua 400.
- `GET /api/clients/me` (e respostas de dependent/school) devolvem endereço **completo** (`AddressResponseDTO`), não só token.
- **BREAKING:** remover o recurso HTTP `/api/addresses` (list/show/create/update/delete/restore). Endereço **não** é catálogo admin nem gera linha órfã. Persistência só via dono (`AddressService` + client/dependent/school). Permissões `list_addresses` / `show_address` / `create_address` / `update_address` / `delete_address` saem do enum e dos bundles (código morto).
- Seeder deixa de ser idempotente por CEP+número como “lugar único”; cada seed entity ganha a própria linha já linkada.
- Fora desta change: ViaCEP/autocomplete, catálogo IBGE de cidades, endereço de driver/assistant, embedar colunas em `client`/`dependent`/`school`.

## Capabilities

### New Capabilities

- `owned-address`: regra 1:1, schema (FK + unique), serviço de upsert/soft-delete por dono, **sem** API de catálogo `/api/addresses`, sem órfãos no fluxo de escrita, mensagens e erros.
- `client-home-address`: leitura e escrita do endereço de casa do client autenticado (e dono via `@sec.isClientOwner` no PUT existente).
- `school-owned-address`: escola com endereço próprio; API sem `id` numérico; update via PATCH (PUT removido).

### Modified Capabilities

- `dependent`: PATCH com `JsonNullable` (omit ≠ null); endereço próprio aninhado; sem `addressToken`.

## Impact

- **Schema:** nova Flyway (após V19); FKs em `client`, `dependent`, `school`; uniques parciais. Dev: `fresh` do Postgres (apagar volume / drop schema) antes de aplicar V20 se o banco local ainda tiver `address_id` compartilhado.
- **Código:** `AddressService` (upsert por dono); remover `AddressController` e testes de catálogo; `ClientService` / `DependentService` / `SchoolService`; DTOs; mappers; `AddressSeeder`; `PermissionEnum`; testes unit + MockMvc; MessageSource pt-BR.
- **Auth:** client write via `isAuthenticated()` + tipo CLIENT e/ou `@sec.isClientOwner`; dependent via permissões CLIENT já existentes; school via permissões de school. Não existe write de endereço por `create_address`.
- **App Flutter:** contrato de perfil de endereço muda de token de catálogo para objeto aninhado — alinhado à tela de edição.
- **Delivery:** 5 fases / 5 PRs (schema+service → client ∥ dependent PATCH ∥ school PATCH → remover catálogo + seeder); test-first; Spotless + `verify` por fase.
