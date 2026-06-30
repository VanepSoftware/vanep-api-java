## Context

O Vanep API (Spring Boot 4, Java 25, JPA/Flyway/PostgreSQL) organiza código por feature (`br.com.vanep.<feature>`) com subpacotes por papel arquitetural (CONSTITUTION regra 5, PR #56). Entidades `User`, `Client` e `Driver` usam `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)` — Hibernate filtra automaticamente registros deletados e `repository.delete()` faz soft delete (PR #54). A migration `V6__soft_delete_partial_unique_indexes.sql` converteu UNIQUE simples em índices parciais (`WHERE deleted_at IS NULL`) para permitir re-cadastro após soft delete.

**Não há CRUD REST** implementado para entidades de negócio além de `/api/user/profile`.

O schema de `dependent` está definido em `vanep-dbdiagram/vanep-diagram.dbml` (domínio 3 — alunos). O produto (`vanep-project-overview`) exige gestão de dependentes pelo cliente (UC13, RF-119–123, RN12). O frontend ainda não consome esses endpoints.

**Branch de entrega:** `feat/dependentes-crud` (única), com 3 PRs sequenciais para `main`, commits atômicos (1 arquivo por commit), merge commit (não squash).

## Goals / Non-Goals

**Goals:**

- Implementar CRUD completo de dependente com `@SoftDelete` e restore via native query.
- Alinhar schema à tabela `dependent` do DBML (campos e FKs).
- Autorização: `ROLE_CLIENT` (ownership por `client_id`) + `ROLE_ADMIN` (acesso global).
- Implementar RN12 (`is_default`) e promoção de padrão ao deletar o dependente padrão.
- Seeder + testes MockMvc cobrindo todos os endpoints.
- Entregar em 3 PRs pequenas e revisáveis.

**Non-Goals:**

- CRUD de `school` ou `address` (FKs nullable, sem constraint no banco por enquanto).
- Endpoints de proposta/contrato que referenciam `dependent_id`.
- Paginação em `GET /api/dependentes` (pode ser adicionada depois se necessário).
- Upload de foto/avatar do dependente.
- Integração com frontend (ainda não implementada).

## Decisions

### 1. Nomenclatura: tabela `dependent`, rotas `/api/dependentes`

- **Decisão:** tabela `dependent` (inglês, como `client`/`driver`); rotas REST em português `/api/dependentes`.
- **Alternativa:** `/api/dependents` — rejeitada; issue e produto usam "dependentes".
- **Rationale:** consistência interna (DB em inglês) + UX da API em pt-BR.

### 2. Identificador público: `token`, nunca `id`

- **Decisão:** paths usam `{token}`; responses expõem `token` via `DependenteResponseDTO`.
- **Rationale:** CONSTITUTION regra 13; padrão já usado em `User`, `Client`, `Driver`.

### 3. Update via PATCH (parcial)

- **Decisão:** `PATCH /api/dependentes/{token}` apenas.
- **Alternativa:** PUT — rejeitada; atualização parcial é o caso comum (ex.: marcar `is_default`).

### 4. Schema da migration V7

> `V6` já é `soft_delete_partial_unique_indexes.sql` (PR #54). A tabela `dependent` entra em **V7**.

```sql
-- V7__create_dependent_table.sql
create table dependent (
  id          bigint generated always as identity primary key,
  token       varchar(32)  not null,
  client_id   bigint       not null references client (id),
  school_id   bigint,
  address_id  bigint,
  name        varchar(255) not null,
  birth_date  date,
  gender      varchar(16),
  document    varchar(64),
  phone       varchar(32),
  email       varchar(255),
  is_self     boolean      not null default false,
  is_default  boolean      not null default false,
  shift       varchar(16)  not null default 'MORNING',
  created_at  timestamptz  not null default now(),
  updated_at  timestamptz  not null default now(),
  deleted_at  timestamptz
);

-- Índices únicos parciais (padrão V6 — só entre registros ativos)
create unique index dependent_token_active_key
  on dependent (token) where deleted_at is null;
create unique index dependent_document_active_key
  on dependent (document) where deleted_at is null;
```

- **`shift`:** NOT NULL com default `MORNING` (decisão do time; alinhado ao DBML).
- **`gender`:** reutilizar `br.com.vanep.user.Gender` (MALE, FEMALE, OTHER).
- **`shift`:** novo enum `br.com.vanep.dependente.enums.Shift` (MORNING, AFTERNOON, NIGHT, FULLTIME).
- **Sem `UNIQUE` simples** em `token`/`document` — usar índices parciais desde o início.

### 5. Soft delete via `@SoftDelete` (padrão PR #54)

- **Decisão:** `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)` na entity; **sem** campo `deletedAt` mapeado.
- **Delete:** `repository.delete(entity)` — Hibernate preenche `deleted_at` automaticamente.
- **Queries:** Hibernate filtra `deleted_at IS NULL` em todo JPQL/Criteria; usar `findByToken`, `findByClientId` (sem sufixo `AndDeletedAtIsNull`).
- **Restore:** `@SoftDelete` impede ler registros deletados via JPA — restore MUST usar **native query** no repository:

```java
@Modifying
@Query(value = "UPDATE dependent SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
int restoreByToken(@Param("token") String token);

@Query(value = "SELECT count(*) > 0 FROM dependent WHERE token = :token AND deleted_at IS NOT NULL", nativeQuery = true)
boolean existsDeletedByToken(@Param("token") String token);
```

- **Rationale:** alinhado a `User`, `Client`, `Driver`; evita vazamento de registros deletados por esquecer filtro manual.

### 6. Autorização em duas camadas

- **Controller:** `@PreAuthorize("hasAnyRole('CLIENT','ADMIN')")` em todos os endpoints.
- **Service:** resolver `User` pelo JWT subject (email) → `Client` via `ClientRepository.findByUserId` → validar ownership.
- **Admin:** bypass de ownership; pode informar `client_id` no create.
- **Rationale:** CONSTITUTION regras 18–19; defense in depth.

### 7. Resolução de ownership

```
JWT subject (email)
  → UserRepository.findByEmail(email)        -- @SoftDelete filtra automaticamente
  → ClientRepository.findByUserId(user.getId())
  → client.getId() usado como client_id do dependente
```

- **Novo método:** `ClientRepository.findByUserId(Long userId)`.

### 8. RN12 — lógica de `is_default` no service

| Evento | Comportamento |
|--------|---------------|
| Create (0 ativos) | `is_default = true` forçado |
| Create (1+ ativos) | `is_default = false` salvo request explícito |
| Update `is_default=true` | desmarca outros ativos do mesmo `client_id` |
| Delete do padrão, resta 1 | promove o restante |
| Delete do padrão, resta 0 ou 2+ | nenhum padrão |

### 9. Status HTTP

| Operação | Status |
|----------|--------|
| POST create | 201 + body |
| GET list/detail | 200 |
| PATCH update | 200 + body |
| DELETE | 204 |
| POST restore | 200 + body |
| Não autenticado | 401 |
| Sem permissão (ownership) | 403 |
| Não encontrado / soft-deleted (read) | 404 |
| Restore de ativo | 409 |
| Documento duplicado (ativo) | 409 |

### 10. Estrutura de pacotes (CONSTITUTION regra 5)

```
br.com.vanep.dependente/
├── controller/
│   └── DependenteController.java
├── dto/
│   ├── DependenteCreateDTO.java
│   ├── DependenteUpdateDTO.java
│   └── DependenteResponseDTO.java
├── entity/
│   └── DependenteEntity.java       @SoftDelete, tabela "dependent"
├── enums/
│   └── Shift.java
├── mapper/
│   └── DependenteMapper.java
├── repository/
│   └── DependenteRepository.java   + restoreByToken (native)
├── service/
│   └── DependenteService.java
└── seed/
    └── DependenteSeeder.java
```

### 11. Seeder

- `DependenteSeeder` na feature, orquestrado por `DataSeeder` quando `vanep.seed.enabled=true`.
- Pré-requisito: client de teste no seed (user CLIENT + perfil client).
- Cria 2 dependentes: um padrão (`is_default=true`) e um não-padrão.

### 12. Testes

- `DependenteControllerTest`: `@SpringBootTest` + `MockMvc` + `jwt()` (padrão `AuthEndpointsTest`).
- **Cleanup:** estender `src/test/resources/db/clean.sql` com `delete from dependent;` **antes** de `client` — `repository.deleteAll()` é soft delete sob `@SoftDelete` e não limpa dados entre testes.
- Setup: user CLIENT + client + user ADMIN; cenários de ownership, CRUD, restore, RN12.
- `DependenteServiceTest`: unitários para regras RN12 e promoção ao delete.

### 13. Plano de PRs (mesma branch)

| PR | Base | Conteúdo |
|----|------|----------|
| PR1 — Fundação | `main` | V7 migration, `Shift`, `DependenteEntity`, `DependenteRepository`, `ClientRepository.findByUserId` |
| PR2 — API | `main` (após merge PR1) | DTOs, mapper, service, controller |
| PR3 — Seed + testes | `main` (após merge PR2) | seeder, `clean.sql`, testes completos |

Fluxo: `git merge main` na branch após cada merge; merge commit no GitHub.

## Risks / Trade-offs

| Risco | Mitigação |
|-------|-----------|
| `school_id`/`address_id` sem FK — dados órfãos | Colunas nullable; validação futura quando tabelas existirem |
| `shift` NOT NULL no dependente vs turno na proposta | Default `MORNING`; proposta pode sobrescrever depois |
| Restore requer native query (limitação `@SoftDelete`) | Métodos dedicados no repository; testes cobrindo restore |
| PR2 depende de PR1 mergeada | Sequência explícita; branch única com merge de `main` entre fases |
| Primeiro CRUD do projeto — vira template | Seguir CONSTITUTION e padrão `@SoftDelete` do PR #54 |
| `document` unique entre ativos — soft-deleted libera valor | Índice parcial `WHERE deleted_at IS NULL` |

## Migration Plan

1. Deploy da migration V7 via Flyway (automático no startup).
2. Sem dados existentes em `dependent` — rollback = nova migration de drop se necessário (não editar V7).
3. Seed opcional via `vanep.seed.enabled=true` em dev.

## Open Questions

- _(nenhuma em aberto — decisões fechadas na sessão de explore e alinhadas ao PR #54/#56)_
