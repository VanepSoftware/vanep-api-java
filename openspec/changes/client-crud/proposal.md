## Why

O perfil de client existe no banco (tabela `client`, V3) e é criado automaticamente no signup, mas não há endpoints REST para leitura, atualização ou remoção. Administradores e o próprio cliente precisam de acesso a esses dados via API.

## What Changes

- Novo endpoint `GET /api/clients` — listagem paginada de clientes (admin)
- Novo endpoint `GET /api/clients/{token}` — leitura de um client por token público
- Novo endpoint `PUT /api/clients/{token}` — atualização de dados do perfil (foto, endereço)
- Novo endpoint `DELETE /api/clients/{token}` — soft delete (infraestrutura já existe via `@SoftDelete` e `deleted_at`)
- Seeder de clients para ambiente de desenvolvimento
- Testes automatizados (unit + MockMvc) para todos os endpoints

**Fora de escopo:**
- `POST /api/clients` (create) — client é criado exclusivamente no fluxo de signup (`RegistrationService`)
- Restore — sem caso de uso definido no produto

## Capabilities

### New Capabilities

- `client-management`: Endpoints REST para leitura, atualização e remoção de perfis de client. Inclui listagem paginada para admin e acesso individual por token.

### Modified Capabilities

## Impact

- Novo pacote `br.com.vanep.client.controller`, `br.com.vanep.client.dto`, `br.com.vanep.client.service`, `br.com.vanep.client.mapper`
- `SecurityConfig` — novas regras de autorização para as rotas `/api/clients/**`
- `DataSeeder` — seed de clients de teste
- Sem migration nova (tabela e soft delete já existem em V3)
- Sem alteração em `RegistrationService` ou fluxo de signup
