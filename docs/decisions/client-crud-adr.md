# ADR — CRUD de Client (task #28)

**Status:** Planejamento concluído, implementação pendente (`/opsx:apply`)
**Data:** 2026-06-30
**Autores:** Claude Code + time Vanep

---

## O que foi feito

Criamos o planejamento completo da feature via OpenSpec (`openspec/changes/client-crud/`):

| Artefato | Conteúdo |
|---|---|
| `proposal.md` | Escopo, motivação, impacto |
| `design.md` | Decisões técnicas e riscos |
| `specs/client-management/spec.md` | 4 requisitos, 12 cenários testáveis |
| `tasks.md` | 18 tasks em ordem de dependência |

**Nenhum código foi implementado ainda.** O próximo passo é `/opsx:apply`.

---

## Endpoints planejados

| Método | Rota | Quem acessa | Status HTTP |
|---|---|---|---|
| `GET` | `/api/clients` | ROLE_ADMIN | 200 (paginado) |
| `GET` | `/api/clients/{token}` | ROLE_ADMIN ou dono | 200 / 403 / 404 |
| `PUT` | `/api/clients/{token}` | Apenas o dono | 200 / 403 / 404 |
| `DELETE` | `/api/clients/{token}` | ROLE_ADMIN | 204 / 403 / 404 |

---

## O que ignoramos da task original e por quê

### POST /api/clients (create) — REMOVIDO

**Por quê:** O `RegistrationService.registerClient()` já cria o perfil de client como parte do fluxo de signup. Ter um segundo endpoint de criação quebraria a invariante de que todo client nasce verificado, com `users` criado junto, e com token único gerado no `@PrePersist`. Dois caminhos de criação = duas fontes de inconsistência.

### POST /api/clients/{token}/restore — REMOVIDO

**Por quê:** Nenhum caso de uso foi definido no produto. "Soft delete + restore" como padrão genérico é um anti-pattern quando não há motivo de negócio real. Se no futuro aparecer um caso de uso ("admin precisa reativar conta cancelada"), abre-se uma task específica com o contexto correto.

### Migration da tabela de client — REMOVIDO

**Por quê:** Já existe. A migration `V3__create_client_and_driver_tables.sql` criou a tabela com `deleted_at timestamptz` desde o início. A entidade `Client.java` já tem `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)`. Criar uma migration nova seria erro de Flyway (checksum mismatch) ou redundância.

### "dependente" no checklist original — IGNORADO

**Por quê:** A task foi criada via copy-paste de um template genérico de CRUD. Todas as referências a "dependente" no objetivo, critérios e checklist são da task original não adaptada. Não há entidade "dependente" no projeto.

---

## Regras da CONSTITUTION seguidas

| Regra | Aplicação |
|---|---|
| #5 — pacotes por feature | `br.com.vanep.client.{controller,dto,service,mapper}` |
| #11 — nunca bind entity no request | `ClientUpdateRequest` DTO separado da entidade |
| #12 — response DTOs explícitos | `ClientResponse` record, nunca `Client` entity exposta |
| #13 — token como identificador público | `{token}` em todos os endpoints, nunca `{id}` |
| #15 — prefixo `/api` | `/api/clients/**` |
| #18/19 — autorização explícita | regras por endpoint definidas, task 4.6 no SecurityConfig |
| #20 — testes junto com o código | tasks 6.1 (unit) e 6.2 (MockMvc) |
| #21 — JaCoCo ≥ 75% | tasks 6.3 e 7.2 |
| #39 — ordem de implementação | repository → dto → mapper → service → controller → testes |
| #42 — Spotless antes do PR | task 7.1 |
| #43 — mensagens em pt-BR | respostas de erro em pt-BR |

---

## Auditoria de segurança do design (pré-implementação)

### ✅ O que está correto

- **Token como ID público** — sem exposição de `id` sequencial, sem IDOR por enumeração
- **Soft delete automático** — `@SoftDelete` Hibernate filtra `deleted_at IS NULL` em todas as queries, inclusive nas que vamos adicionar (`findByToken`, `findAll`)
- **`rating` imutável via API** — campo calculado externamente, não aceito no `PUT`
- **DTOs separados** — nenhuma entidade JPA exposta diretamente (previne mass assignment)
- **Autenticação em `/api/**`** — `apiSecurityFilterChain` já exige Bearer token em toda a rota

### ⚠️ Gap encontrado — verificação de ownership

**Problema:** O design diz usar `SecurityConfig` para todas as regras de autorização. Mas `GET /api/clients/{token}` e `PUT /api/clients/{token}` exigem verificar se o client autenticado é o *dono* do perfil — isso requer comparar o path variable `{token}` com o claim `uid` do JWT. Isso **não é possível fazer só com URL patterns no SecurityConfig**.

**Solução planejada para implementação:** Verificação de ownership no service:
```java
// No ClientService
String callerUid = jwt.getClaim("uid");           // token do user autenticado
Client client = findByToken(token);               // client do path
boolean isOwner = client.getUser().getToken().equals(callerUid);
boolean isAdmin = jwt.getClaim("roles").contains("ROLE_ADMIN");
if (!isOwner && !isAdmin) throw new ResponseStatusException(403);
```

Essa lógica vai para o `ClientService`, não para o `SecurityConfig`. A task 4.6 precisa ser atualizada para refletir isso.

### ⚠️ Gap encontrado — tamanho máximo de página

**Problema:** `GET /api/clients` com `Pageable` sem limite de `size` permite `?size=100000`, varrendo a tabela inteira em uma requisição.

**Solução planejada:** Configurar `spring.data.web.pageable.max-page-size=100` em `application.properties` — Spring MVC limita automaticamente.

---

## Checklist original vs. o que entrega

| Critério original | Entrega | Observação |
|---|---|---|
| Todos os endpoints funcionais com status corretos | ✅ | 4 endpoints, 12 cenários com status mapeados |
| Migration com soft deletes | ✅ | Já existe desde V3 |
| Endpoint restore funcional | ⛔ removido | Sem caso de uso definido |
| Seeder com dados válidos | ✅ | Task 5.1 — 5 clients no DataSeeder |
| Testes para todos os endpoints | ✅ | Unit (Mockito) + MockMvc |
| Rotas protegidas com autenticação/autorização | ✅ | Bearer token + roles + ownership check |
| Respostas no padrão da API | ✅ | ClientResponse record, token como ID, erros em pt-BR |
