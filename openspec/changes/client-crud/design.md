## Context

A entidade `Client` e a tabela `client` já existem (migration V3). A entidade tem `@SoftDelete` configurado via Hibernate. O `ClientRepository` existe mas está vazio — sem queries customizadas. Não existe nenhum controller, service, DTO ou mapper para client.

O padrão atual do projeto (ver `ProfileController`, `RegistrationService`) usa:
- `token` como identificador público (nunca `id`)
- `record` para response DTOs simples
- Serviços finos com transações explícitas
- MockMvc para testes de endpoint

## Goals / Non-Goals

**Goals:**
- Expor 4 endpoints REST para o perfil de client
- Seguir a estrutura de pacotes da CONSTITUTION (`controller`, `dto`, `service`, `mapper`)
- Autorização explícita no `SecurityConfig`
- Cobertura com testes unit (service) + MockMvc (controller)

**Non-Goals:**
- Criar client via API (responsabilidade do signup)
- Endpoint de restore
- Nova migration (soft delete já existe)
- Alterar o campo `rating` via API (calculado externamente)
- Endereço completo (apenas `address_id` por ora — tabela de endereços não existe ainda)

## Decisions

**D1 — Autorização por papel**
- `GET /api/clients` → apenas `ROLE_ADMIN`
- `GET /api/clients/{token}` → `ROLE_ADMIN` ou o próprio client (verifica `uid` do JWT)
- `PUT /api/clients/{token}` → apenas o próprio client
- `DELETE /api/clients/{token}` → apenas `ROLE_ADMIN`

Alternativa considerada: Spring Security method-level (`@PreAuthorize`). Escolhido por ser consistente com o `SecurityConfig` existente que centraliza as regras.

**D2 — Soft delete já resolvido**
`@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)` na entidade faz Hibernate filtrar automaticamente registros deletados em todas as queries. `DELETE` na camada de serviço chama apenas `repository.delete(client)` — sem SQL manual.

**D3 — Paginação no list**
`GET /api/clients` retorna `Page<ClientResponse>` via `Pageable`. Padrão Spring MVC (`?page=0&size=20&sort=createdAt,desc`).

**D4 — Campos editáveis no PUT**
Apenas `photo` e `addressId`. `rating` é calculado externamente. `token`, `user`, `createdAt` são imutáveis.

## Risks / Trade-offs

- **`ClientRepository` está vazio** → precisará de `findByToken()` e `findByUserId()`. Sem risco, adição simples.
- **`address_id` é um Long solto** → a tabela de endereços não existe ainda. O campo é aceito e persistido mas sem FK validada. Risco baixo enquanto a tabela de endereços não existir.
- **Soft delete + list admin** → `findAll` com Hibernate `@SoftDelete` já filtra `deleted_at IS NULL` automaticamente. Não expor registros deletados na listagem é o comportamento default.
