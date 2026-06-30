## Context

O Vanep API (Spring Boot 4, Java 25, JPA/Flyway/PostgreSQL) organiza código por feature (`br.com.vanep.<feature>`). Entidades `client` e `driver` já existem com padrão de `token`, timestamps e `deleted_at`, mas **não há CRUD REST** implementado para nenhuma entidade de negócio além de `/api/user/profile`.

O schema de `dependent` está definido em `vanep-dbdiagram/vanep-diagram.dbml` (domínio 3 — alunos). O produto (`vanep-project-overview`) exige gestão de dependentes pelo cliente (UC13, RF-119–123, RN12). O frontend ainda não consome esses endpoints.

**Branch de entrega:** `feat/dependentes-crud` (única), com 3 PRs sequenciais para `main`, commits atômicos (1 arquivo por commit), merge commit (não squash).

## Goals / Non-Goals

**Goals:**

- Implementar CRUD completo de dependente com soft delete e restore.
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

- **Decisão:** paths usam `{token}`; responses expõem `token` via `DependenteResponse`.
- **Rationale:** CONSTITUTION regra 13; padrão já usado em `User`, `Client`, `Driver`.

### 3. Update via PATCH (parcial)

- **Decisão:** `PATCH /api/dependentes/{token}` apenas.
- **Alternativa:** PUT — rejeitada; atualização parcial é o caso comum (ex.: marcar `is_default`).

### 4. Schema da migration V6

```sql
dependent (
  id            bigint identity PK
  token         varchar(32) NOT NULL UNIQUE
  client_id     bigint NOT NULL REFERENCES client(id)
  school_id     bigint NULL          -- sem FK até existir school
  address_id    bigint NULL          -- sem FK até existir address
  name          varchar NOT NULL
  birth_date    date
  gender        varchar(16)          -- enum Gender existente
  document      varchar UNIQUE
  phone         varchar
  email         varchar
  is_self       boolean NOT NULL DEFAULT false
  is_default    boolean NOT NULL DEFAULT false
  shift         varchar(16) NOT NULL DEFAULT 'MORNING'
  created_at    timestamptz NOT NULL
  updated_at    timestamptz NOT NULL
  deleted_at    timestamptz
)
```

- **`shift`:** NOT NULL com default `MORNING` (decisão do time; alinhado ao DBML).
- **`gender`:** reutilizar `br.com.vanep.user.Gender` (MALE, FEMALE, OTHER).
- **`shift`:** novo enum `br.com.vanep.dependente.Shift` (MORNING, AFTERNOON, NIGHT, FULLTIME).

### 5. Soft delete manual (sem `@SQLDelete` / `@Where`)

- **Decisão:** setar `deletedAt = Instant.now()` no service; queries filtram `deletedAt IS NULL`.
- **Rationale:** padrão já usado em `User`, `Client`, `Driver`; restore explícito é mais claro.

### 6. Autorização em duas camadas

- **Controller:** `@PreAuthorize("hasAnyRole('CLIENT','ADMIN')")` em todos os endpoints.
- **Service:** resolver `User` pelo JWT subject (email) → `Client` via `ClientRepository.findByUserId` → validar ownership.
- **Admin:** bypass de ownership; pode informar `client_id` no create.
- **Rationale:** CONSTITUTION regras 18–19; defense in depth.

### 7. Resolução de ownership

```
JWT subject (email)
  → UserRepository.findByEmailAndDeletedAtIsNull
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
| Documento duplicado | 409 |

### 10. Estrutura de pacotes

```
br.com.vanep.dependente/
├── controller/DependenteController.java
├── dto/CreateDependenteRequest.java
├── dto/UpdateDependenteRequest.java
├── dto/DependenteResponse.java
├── Dependent.java              (entity; tabela "dependent")
├── Shift.java
├── mapper/DependenteMapper.java
├── repository/DependentRepository.java
├── service/DependenteService.java
└── seed/DependenteSeeder.java
```

> **Nota:** entity `Dependent` (inglês singular, convenção JPA); pacote `dependente` (português, convenção de feature do produto).

### 11. Seeder

- `DependenteSeeder` na feature, orquestrado por `DataSeeder` quando `vanep.seed.enabled=true`.
- Pré-requisito: client de teste no seed (user CLIENT + perfil client).
- Cria 2 dependentes: um padrão (`is_default=true`) e um não-padrão.

### 12. Testes

- `DependenteControllerTest`: `@SpringBootTest` + `MockMvc` + `jwt()` (padrão `AuthEndpointsTest`).
- Setup: user CLIENT + client + user ADMIN; cenários de ownership, CRUD, restore, RN12.
- `DependenteServiceTest`: unitários para regras RN12 e promoção ao delete.

### 13. Plano de PRs (mesma branch)

| PR | Base | Conteúdo |
|----|------|----------|
| PR1 — Fundação | `main` | migration, `Shift`, `Dependent`, `DependentRepository`, `ClientRepository.findByUserId` |
| PR2 — API | `main` (após merge PR1) | DTOs, mapper, service, controller |
| PR3 — Seed + testes | `main` (após merge PR2) | seeder, testes completos |

Fluxo: `git merge main` na branch após cada merge; merge commit no GitHub.

## Risks / Trade-offs

| Risco | Mitigação |
|-------|-----------|
| `school_id`/`address_id` sem FK — dados órfãos | Colunas nullable; validação futura quando tabelas existirem |
| `shift` NOT NULL no dependente vs turno na proposta | Default `MORNING`; proposta pode sobrescrever depois |
| PR2 depende de PR1 mergeada | Sequência explícita; branch única com merge de `main` entre fases |
| Primeiro CRUD do projeto — vira template | Seguir CONSTITUTION rigorosamente; documentar no design |
| `document` unique global — conflito cross-client | Alinhado ao DBML; retornar 409 |

## Migration Plan

1. Deploy da migration V6 via Flyway (automático no startup).
2. Sem dados existentes em `dependent` — rollback = nova migration de drop se necessário (não editar V6).
3. Seed opcional via `vanep.seed.enabled=true` em dev.

## Open Questions

- _(nenhuma em aberto — decisões fechadas na sessão de explore)_
