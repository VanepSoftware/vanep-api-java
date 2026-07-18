## Why

A entidade `driver` (motorista) e a tabela correspondente no banco de dados já existem, sendo criadas no fluxo de signup (`RegistrationService`). Porém, não existem endpoints REST para que os motoristas gerenciem seus perfis (bio, CNPJ, experiência, disponibilidade, dias e horários de trabalho, preço base) e para que os clientes/administradores visualizem e listem esses motoristas. 

Esta tarefa visa implementar o CRUD completo (leitura, atualização, deleção e restauração) para motoristas no backend.

## What Changes

- **Novas permissões:** Adição de permissões para listagem, leitura, atualização, deleção e restauração de motoristas no `PermissionEnum.java`.
- **Endpoints REST sob `/api/drivers`:**
  - `GET /api/drivers` — Listagem paginada de motoristas (acessível por `ROLE_ADMIN` e `ROLE_CLIENT`).
  - `GET /api/drivers/{token}` — Visualização detalhada de um motorista pelo seu token público (acessível por `ROLE_ADMIN`, `ROLE_CLIENT` e o próprio `ROLE_DRIVER`).
  - `PUT /api/drivers/{token}` — Atualização do perfil do motorista (acessível pelo próprio motorista ou `ROLE_ADMIN`).
  - `DELETE /api/drivers/{token}` — Soft delete do motorista (acessível por `ROLE_ADMIN` ou pelo próprio motorista).
  - `POST /api/drivers/{token}/restore` — Restauração de um motorista deletado (acessível por `ROLE_ADMIN`).
- **Seeder:** Expansão de motoristas no seeder para testar paginação e diferentes status.
- **Testes automatizados:** Testes unitários para a camada de serviço (`DriverServiceTest`) e testes de slice HTTP/segurança para a camada de controle (`DriverControllerTest`).

**Fora de Escopo:**
- `POST /api/drivers` (create) — Motoristas são criados exclusivamente no fluxo de signup (`RegistrationService`).

## Capabilities

### New Capabilities

- `driver-management`: Endpoints REST para gerenciar perfis de motoristas (detalhar, atualizar, remover, restaurar e listar com paginação), integrando regras de propriedade (ownership) e segurança.

### Modified Capabilities

- _Nenhuma_

## Impact

- **Pacote de Negócio:** Criação do pacote `br.com.vanep.driver` com subpacotes `controller`, `dto`, `service`, `mapper` e `security` (se necessário).
- **Segurança:** Configuração de regras de autorização no `SecurityConfig` para proteger as rotas sob `/api/drivers/**`.
- **Seeder:** Adição de novos motoristas em `DataSeeder.java`.
- **Database:** Sem nova migration de tabela (tabela `driver` e suporte a `deleted_at` já existem na migration `V3`). Se necessário, faremos uma nova migration caso precisemos ajustar permissões iniciais no banco, ou lidamos com isso no seeder.
