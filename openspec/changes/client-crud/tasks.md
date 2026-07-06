## 1. Repositório

- [x] 1.1 Adicionar `findByToken(String token): Optional<Client>` no `ClientRepository`
- [x] 1.2 Adicionar `findByUserId(Long userId): Optional<Client>` no `ClientRepository`
- [x] 1.3 Adicionar `findAll(Pageable pageable): Page<Client>` (já herdado, verificar se basta)

## 2. DTOs e Mapper

- [x] 2.1 Criar `ClientResponse` record em `br.com.vanep.client.dto` com campos: `token`, `photo`, `rating`, `addressId`, `active`, `createdAt`, e dados do user (`name`, `email`)
- [x] 2.2 Criar `ClientUpdateRequest` record em `br.com.vanep.client.dto` com campos: `photo` (opcional), `addressId` (opcional)
- [x] 2.3 Criar `ClientMapper` em `br.com.vanep.client.mapper` para converter `Client` → `ClientResponse`

## 3. Service

- [x] 3.1 Criar `ClientService` em `br.com.vanep.client.service`
- [x] 3.2 Implementar `findAll(Pageable): Page<ClientResponse>`
- [x] 3.3 Implementar `findByToken(String token): ClientResponse` — lança `404` se não encontrado
- [x] 3.4 Implementar `update(String token, ClientUpdateRequest): ClientResponse` — lança `404` se não encontrado
- [x] 3.5 Implementar `delete(String token): void` — soft delete via `repository.delete()`, lança `404` se não encontrado

## 4. Controller e Autorização

- [x] 4.1 Criar `ClientController` em `br.com.vanep.client.controller` com `@RequestMapping("/api/clients")`
- [x] 4.2 Implementar `GET /api/clients` — admin only, retorna `Page<ClientResponse>`
- [x] 4.3 Implementar `GET /api/clients/{token}` — admin ou próprio client
- [x] 4.4 Implementar `PUT /api/clients/{token}` — apenas o próprio client
- [x] 4.5 Implementar `DELETE /api/clients/{token}` — admin only, retorna `204`
- [x] 4.6 Adicionar regras de autorização no `SecurityConfig` para `/api/clients/**`

## 5. Seeder

- [x] 5.1 Adicionar seed de clients no `DataSeeder` (5 clients com dados fictícios variados)

## 6. Testes

- [x] 6.1 Criar `ClientServiceTest` (unit, Mockito) cobrindo: findAll, findByToken, update, delete e casos de `404`
- [x] 6.2 Criar `ClientControllerTest` (MockMvc) cobrindo todos os cenários da spec: `200`, `204`, `401`, `403`, `404`
- [x] 6.3 Rodar `./mvnw verify` e garantir que JaCoCo passa (≥ 75% linhas)

## 7. Qualidade

- [x] 7.1 Rodar `./mvnw spotless:apply` para formatar o código
- [x] 7.2 Rodar `./mvnw verify` — build verde antes de abrir PR
