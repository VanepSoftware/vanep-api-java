## ADDED Requirements

### Requirement: Admin pode listar todos os clientes
O sistema SHALL retornar uma lista paginada de clientes ativos quando requisitado por um usuário com `ROLE_ADMIN`.

#### Scenario: Listagem bem-sucedida
- **WHEN** admin faz `GET /api/clients?page=0&size=20`
- **THEN** sistema retorna `200 OK` com `Page<ClientResponse>` contendo apenas clientes não deletados

#### Scenario: Acesso negado para não-admin
- **WHEN** usuário com `ROLE_CLIENT` ou `ROLE_DRIVER` faz `GET /api/clients`
- **THEN** sistema retorna `403 Forbidden`

#### Scenario: Acesso negado sem autenticação
- **WHEN** requisição sem Bearer token faz `GET /api/clients`
- **THEN** sistema retorna `401 Unauthorized`

---

### Requirement: Client pode ser lido por token
O sistema SHALL retornar o perfil de um client quando o token público for válido e o solicitante tiver permissão.

#### Scenario: Admin lê qualquer client
- **WHEN** admin faz `GET /api/clients/{token}`
- **THEN** sistema retorna `200 OK` com `ClientResponse` do client correspondente

#### Scenario: Client lê o próprio perfil
- **WHEN** client autenticado faz `GET /api/clients/{token}` onde token corresponde ao seu perfil
- **THEN** sistema retorna `200 OK` com seus próprios dados

#### Scenario: Client tenta ler perfil de outro
- **WHEN** client faz `GET /api/clients/{token}` de outro client
- **THEN** sistema retorna `403 Forbidden`

#### Scenario: Token inexistente
- **WHEN** qualquer usuário faz `GET /api/clients/{token}` com token que não existe
- **THEN** sistema retorna `404 Not Found`

---

### Requirement: Client pode atualizar o próprio perfil
O sistema SHALL permitir que o client atualize `photo` e `addressId` do seu perfil.

#### Scenario: Atualização bem-sucedida
- **WHEN** client faz `PUT /api/clients/{token}` com body `{ "photo": "...", "addressId": 42 }`
- **THEN** sistema retorna `200 OK` com `ClientResponse` atualizado

#### Scenario: Client tenta atualizar perfil de outro
- **WHEN** client faz `PUT /api/clients/{token}` de outro client
- **THEN** sistema retorna `403 Forbidden`

#### Scenario: Admin não pode atualizar perfil de client via este endpoint
- **WHEN** admin faz `PUT /api/clients/{token}`
- **THEN** sistema retorna `403 Forbidden`

---

### Requirement: Admin pode remover client via soft delete
O sistema SHALL marcar o client como deletado (soft delete) sem remover o registro do banco.

#### Scenario: Remoção bem-sucedida
- **WHEN** admin faz `DELETE /api/clients/{token}`
- **THEN** sistema retorna `204 No Content` e `deleted_at` é preenchido no banco

#### Scenario: Client deletado não aparece na listagem
- **WHEN** admin faz `GET /api/clients` após deletar um client
- **THEN** o client deletado não aparece nos resultados

#### Scenario: Acesso negado para não-admin
- **WHEN** usuário com `ROLE_CLIENT` faz `DELETE /api/clients/{token}`
- **THEN** sistema retorna `403 Forbidden`

#### Scenario: Token inexistente
- **WHEN** admin faz `DELETE /api/clients/{token}` com token inválido
- **THEN** sistema retorna `404 Not Found`
