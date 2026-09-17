## Context

`address` (V14) é um registro de lugar: sem dono, CRUD em `/api/addresses` por permissão admin (`create_address`, …). `client`, `dependent` e `school` guardam `address_id` bigint **sem FK**. O client autentica `PUT /api/clients/{token}` com `addressToken` (resolve id e grava o ponteiro). O JWT CLIENT **não** inclui `create_address` (bundle só de dependents). `GET /api/clients/me` não devolve endereço. Dependent ainda rejeita `addressToken` com 400 de fase. School aceita `Long addressId` (viola identificador opaco).

O seeder é idempotente por CEP+número — reforça catálogo compartilhado.

Cidade/estado **continuam** catálogo geográfico. Só `address` deixa de ser o quarto nível de master data.

Constraints: Java 25 / Spring Boot 4 / Flyway (não editar V14); próxima migration **V20**; soft delete; tokens opacos; MessageSource; ownership em `@sec`; test-first; fases = PRs.

## Goals / Non-Goals

**Goals:**

- 1:1: cada linha `address` ligada a no máximo um client, ou um dependent, ou uma school.
- Upsert/clear no recurso do dono com `AddressRequestDTO` / `AddressResponseDTO` reutilizados.
- FKs + unique parcial nos ponteiros. Sem SQL de clone/backfill.
- App Flutter e admin editam endereço **só** no recurso do dono (sem catálogo HTTP).
- Dependent e school no mesmo modelo nesta change (spec completa).

**Non-Goals:**

- ViaCEP / BrasilAPI / autocomplete de CEP.
- Import IBGE / `list_cities` público / criar cidade on the fly.
- Endereço de driver ou assistant.
- Embedar colunas de endereço em `client`/`dependent`/`school`.
- Ligar dependent→school (`schoolToken` continua 400 nesta change).
- Unique em CEP+número.
- Manter `/api/addresses` “só para admin” — isso **é** o contrato de catálogo (órfãos, list/show globais) e sai nesta change.

## Decisions

### D1 — Tabela `address` permanece; exclusividade no ponteiro

Não embedar. Não inverter FK para `address.client_id` (três donos opcionais + CHECK quebra H2/Flyway e duplica o modelo atual).

- Unique parcial: `client (address_id) WHERE address_id IS NOT NULL AND deleted_at IS NULL` (idem dependent, school).
- Isso impede dois clients na mesma linha. **Dois tipos diferentes** (client + school no mesmo id) ainda passariam no unique por tabela.

**Exclusividade cruzada:** `AddressService` conta só donos **ativos** (`deleted_at IS NULL`; o `@SoftDelete` do Hibernate já filtra). Se o id já está em outro dono ativo → `409` `address.already_owned`. Upsert do dono atual atualiza in-place. Contar ponteiro em dono soft-deleted **não** entra nessa conta — por isso o delete do dono **tem** de encerrar o endereço (D10), senão a linha `address` fica ativa sem dono ativo e o unique do dono já a “liberou”.

Não usar trigger PostgreSQL — testes são H2.

**Alternativa rejeitada:** `owner_kind` em `address` — três donos já estão nos ponteiros. **Alternativa rejeitada:** catálogo + copy-on-write. **Alternativa rejeitada:** `/api/addresses` admin com órfãos — replica o contrato errado.

### D2 — Upsert no `AddressService`, donos só orquestram

Métodos explícitos (nomes com verbo), sem `handle`/`process`:

- `upsertForClient(Long clientId, AddressRequestDTO)`
- `upsertForDependent(Long dependentId, AddressRequestDTO)`
- `upsertForSchool(Long schoolId, AddressRequestDTO)`
- `clearForClient` / `clearForDependent` / `clearForSchool` — `repository.delete` na linha do dono, depois `addressId = null`.

`applyRequest` já existente continua o mapeamento CEP/rua/cidade. `resolveAddressId(token)` **não** é usado para “escolher do catálogo”. Nenhum controller chama create/update de endereço sem um dono.

### D10 — Soft delete do dono cascateia o endereço

Hoje `ClientService.delete` / `DependentService.delete` / `SchoolService.delete` só fazem `repository.delete(owner)`. O unique parcial `WHERE deleted_at IS NULL` no dono **libera** o `address_id`; a linha `address` continua ativa → órfão. Restore de dependent/school hoje também ignora endereço.

**Decisão:** no mesmo `@Transactional` do delete do dono, **antes** de soft-deletar o dono, chamar `clearForClient` / `clearForDependent` / `clearForSchool` (soft-delete do `address` + `address_id = null`). É o mesmo caminho do DELETE explícito de endereço.

**Restore** (dependent, school; client não tem restore): o dono volta **sem** endereço. Admin/app preenche de novo. Não restaurar a linha `address` junto — evita unique quebrando se outro ativo tivesse reutilizado o id (o unique só vale para donos ativos).

**Não fazer:** unique sem filtro de `deleted_at` no ponteiro (prender o id para sempre no fantasma). **Não fazer:** apagar só o `address` e deixar `address_id` no dono deletado (restore veria FK para linha `@SoftDelete` invisível).

Contagem `already_owned` = só donos ativos, alinhada ao unique parcial.

### D3 — Contrato HTTP do responsável

Tela de perfil ≠ foto.

- `GET /api/clients/me` inclui `address` (`AddressResponseDTO` ou `null`).
- `PUT /api/clients/me/address` body = `AddressRequestDTO` (`@Valid`): cria ou substitui a linha do caller (`UserType.CLIENT`).
- `DELETE /api/clients/me/address` → 204, soft delete da linha.
- Auth: `@PreAuthorize("isAuthenticated()")` + serviço exige CLIENT (mesmo padrão de `getMyProfile`).
- `PUT /api/clients/{token}` **remove** `addressToken` (**BREAKING**). Foto permanece. Dono: `@sec.isClientOwner`.
- `GET /api/clients/{token}` / list: `addressToken` vira objeto `address` aninhado (**BREAKING** para consumidores do token solto).

**Alternativa rejeitada:** só aninhar no PUT de client — mistura foto e endereço e força o app a mandar photo no save de CEP.

### D4 — Dependent: PATCH completo (JsonNullable) + endereço aninhado

Hoje `DependentUpdateDTO` usa `String`/`LocalDate` e `applyUpdate` faz `if (getX() != null)`. Omitir funciona; **`"phone": null` não limpa** (mesmo `null` Java). A spec de endereço pede `"address": null` = clear — isso **exige** `JsonNullable`. Padronizar com school e `UserProfileUpdateRequestDTO`.

- Create (`POST`): `address` opcional aninhado (`AddressRequestDTO`); sem `addressToken`. `schoolToken` presente (valor ou `null`) continua HTTP 400 nesta change.
- PATCH: `DependentUpdateDTO` (ou record) com `JsonNullable` em **todos** os campos mutáveis (constitution regra 16): `name`, `birthDate`, `gender`, `document`, `phone`, `email`, `isSelf`, `isDefault`, `shift`, `address`. Compact `undefined()` no construtor. `schoolToken` omitido = não mexe `school_id`; **presente** (valor ou `null`) → HTTP 400 (`schoolToken` ainda fora de escopo).
- Merge `isPresent()`; **não** o `if (getX() != null)` atual. `DependentService.update` já aplica RN12 (`isDefault` true/false) depois do mapper; o mapper não copia o campo. Com `JsonNullable`, o mesmo RN12 deve usar `isPresent()` — não `Boolean.TRUE.equals(getIsDefault())` como se omit fosse null.
- Semântica:
  - omitido → não altera.
  - **`name` presente:** non-blank → persiste; `null`/blank → 400 (`name` NOT NULL).
  - **`isSelf` / `isDefault` / `shift` presentes e `null`** → 400 (colunas NOT NULL). Presente non-null → persiste (`isDefault` true/false continua RN12).
  - **`birthDate` / `gender` / `document` / `phone` / `email` presentes e non-null** → persiste; JSON `null` → **clear**.
  - **`document` presente, non-null, diferente do gravado** → 409 se outro dependent ativo já usa (excluir o próprio token). Mesmo document reenviado → no-op. Clear não checa duplicata.
  - **`address` objeto** → upsert da linha **daquele** dependent; **`address: null`** → clear.
- Response: `AddressResponseDTO` no lugar de `DependentAddressDTO`.
- D10: delete chama `clearForDependent` antes; restore sem endereço.
- Teste de regressão **nomeado:** PATCH só `{ "name": "Novo" }` → phone, email, birthDate, address inalterados.

**Alternativa rejeitada:** `JsonNullable` só em `address` (duas semânticas no mesmo DTO).

### D5 — School: PATCH completo (merge campo a campo); PUT some

- `POST /api/schools` continua create: `SchoolRequestDTO` com `name` obrigatório (`@NotBlank`) e `address` opcional (`AddressRequestDTO` no lugar de `Long addressId`). Se `address` presente, upsert depois de persistir a school.
- **BREAKING:** `PUT /api/schools/{token}` é **removido** (sem alias). Update passa a `PATCH /api/schools/{token}` com `SchoolUpdateRequestDTO`.
- **Todo** campo do PATCH usa `JsonNullable<T>` (constitution regra 16; mesmo padrão de `UserProfileUpdateRequestDTO`: compact canonical `undefined()` no construtor se o record vier com null de deserialização): `name`, `cnpj`, `phone`, `email`, `address`. Só embrulhar `address` **não** basta — `String cnpj` no DTO faz omit e `"cnpj": null` virarem o mesmo `null` Java; PATCH só com `name` zeraria CNPJ/telefone/e-mail, o bug do PUT atual.
- Merge no service: `isPresent()` por campo; **não** reusar `applyRequest` do create.
- Semântica:
  - **omitido** (`undefined`) → não altera.
  - **`name` presente:** valor non-blank → persiste; `null` ou blank → HTTP 400 (coluna `name` é `NOT NULL`; escola sem nome não é válido). Validação **no service** (ou validator custom) — **não** `@NotBlank` no DTO do PATCH (quebraria omit).
  - **`cnpj` / `phone` / `email` presentes e non-null** → persiste (formato: CNPJ 14 dígitos / e-mail, como no create, só se o valor veio).
  - **`cnpj` / `phone` / `email` presentes e JSON `null`** → **clear** (colunas são nullable). Permitido.
  - **`address` presente objeto** → upsert; **`address: null`** → clear (D10/clearForSchool).
- CNPJ duplicado no PATCH: só quando `cnpj` está **presente, non-null, e diferente** do valor persistido. Checar existência em **outra** school ativa (`existsByCnpj` **excluindo** o `id`/`token` da própria). Mesmo CNPJ reenviado → no-op, sem 409. Clear (`cnpj: null`) não dispara duplicidade.
- `SchoolResponseDTO`: `AddressResponseDTO address` no lugar de `Long addressId`.
- Permissão: `update_school` no PATCH.
- Teste de regressão **nomeado:** PATCH só `{ "name": "Novo" }` → `cnpj`, `phone`, `email` e `address` inalterados.

**Alternativa rejeitada:** PUT com “omitir address = mantém”. **Alternativa rejeitada:** PATCH só com `JsonNullable` em `address`.

### D6 — Sem recurso HTTP `/api/addresses`

O contrato errado **era** tratar endereço como entidade listável/criável sozinha (igual `city`). Não preservamos uma versão “admin-only” disso.

- Remover `AddressController` e `AddressControllerTest`.
- Admin lê/edita endereço da escola no `/api/schools`; casa do client no GET de client / não precisa varrer `/addresses`.
- `AddressService` + `AddressRepository` + DTOs/mapper **permanecem** (lógica compartilhada, DRY).
- Remover `LIST_ADDRESSES`, `SHOW_ADDRESS`, `CREATE_ADDRESS`, `UPDATE_ADDRESS`, `DELETE_ADDRESS` de `PermissionEnum` e de qualquer bundle (ADMIN sync de “todas as permissões” deixa de incluí-las).
- Escrita **sempre** cria/atualiza já linkada ao dono. Sem POST que deixe `address` sem ponteiro.

### D7 — Migration V20, sem backfill

Não alterar V14 (checksum). Só ambiente de **dev**: se o Postgres local ainda tiver catálogo compartilhado, **recriar o banco** (apagar volume Docker / drop schema) e subir Flyway + seeder de novo. Não há script de clonar `address_id` compartilhado nem de soft-delete de órfãos na V20.

1. `ALTER TABLE … ADD CONSTRAINT … FOREIGN KEY (address_id) REFERENCES address (id)`.
2. Unique parciais nos três ponteiros.
3. Comentário de tabela: endereço **do dono**, não da plataforma.

Se V20 rodar em cima de dados sujos (dois clients no mesmo `address_id`), o unique **falha** — isso é esperado; o remédio é o fresh, não um backfill.

H2 nos testes: banco vazio a cada run; seguir o estilo de V10/V6 (partial unique).

### D8 — Mensagens

Keys em inglês, texto pt-BR em `messages_pt_BR.properties`. Exemplos: `address.not_found`, `address.already_owned`, `city.not_found` (já existe), `client.profile.not_found`. Validação de CEP/rua permanece nas anotações do DTO (hoje strings pt-BR hardcoded nas annotations — **não** refatorar MessageSource das Bean Validation nesta change, para não inflar o PR).

### D9 — Seeder

Cada entidade seed com endereço próprio (cópias mesmo que o CEP seja igual). Remover `existsByZipCodeAndNumber` como regra de “um lugar”; o método pode permanecer só se algum teste de repo ainda o usa, ou sair se ficar morto.

## Risks / Trade-offs

- **[Risco] Linhas duplicadas (mesmo prédio, N pessoas)** → Aceito: complemento e edição isolada. Disco irrelevante no MVP.
- **[Risco] Dois tipos apontando para o mesmo id (SQL manual)** → Unique por tabela não pega; mitigar no `AddressService`. Sem trigger PG.
- **[Risco] Restore de dependent/school perde o endereço** → Aceito (D10). Alternativa (restaurar address + unique incluindo deletados) é mais frágil.
- **[Risco] BREAKING no Flutter / admin school** → Change nasce junto com a tela de endereço; documentar no PR.
- **[Risco] PUT school some (admin/clientes HTTP)** → Aceito; PATCH documentado. Atualizar testes MockMvc que hoje usam `put("/api/schools/...")`.
- **[Risco] Admin perdeu listagem global de endereços** → Aceito; o dado vive no dono. Listar “todos os CEPs” não é caso de uso.
- **[Risco] Bundles ADMIN no banco ainda listam `create_address`** → Phase 5 remove do enum; seeder ADMIN re-sincroniza; dados já persistidos em `role_permission.permissions` podem ficar com strings órfãs até o sync — o JWT ignora permissões fora do registry se o validador filtrar; confirmar `PermissionRegistry` e limpar JSON do bundle no seed.
- **[Risco] PR 2 estoura ~600 linhas / 10 arquivos** → Mitigado: client é fase 2; dependent merge é fase 3; school merge é fase 4.
- **[Risco] Dependent PATCH + JsonNullable** → Resolvido: fase 3 reescreve o merge inteiro (não só `address`), igual school.

## Migration Plan

Dev: recrear o banco, aplicar Flyway (V20 só FKs + uniques), seeder 1:1. Rollback: nova migration se V20 já tiver sido aplicada em algum ambiente; não editar V20 depois. Sem migração de dados de catálogo.

## Open Questions

Nenhum bloqueante. ViaCEP fica para change futura.

## PR plan

```
              V20 + AddressService
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       Client      Dependent     School
       /me         PATCH JSON     PATCH
                    nullable
          │            │            │
          └────────────┼────────────┘
                       ▼
            Remover /api/addresses + seeder
```

| Phase | Contents | Depends on | Parallel with |
| --- | --- | --- | --- |
| 1 | V20 FKs + uniques; `AddressService` upsert/clear; fresh do DB local | — | — |
| 2 | Client `/me` address | 1 | 3, 4 |
| 3 | Dependent PATCH JsonNullable + endereço aninhado | 1 | 2, 4 |
| 4 | School PATCH completo (PUT removido); endereço aninhado | 1 | 2, 3 |
| 5 | Remover `/api/addresses` + permissões; seeder 1:1 | 2, 3, 4 | — |

Cinco PRs. 2, 3 e 4 não compartilham arquivos de feature e podem ir em paralelo depois da 1.
