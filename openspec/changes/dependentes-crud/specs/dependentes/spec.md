## ADDED Requirements

### Requirement: Persistência de dependentes com soft delete

O sistema MUST persistir dependentes na tabela `dependent` conforme o schema do DBML (`vanep-dbdiagram`), incluindo coluna `deleted_at` para soft delete. Registros com `deleted_at` preenchido MUST ser tratados como excluídos em operações de leitura e listagem padrão.

#### Scenario: Registro ativo visível

- **WHEN** um dependente existe com `deleted_at` nulo
- **THEN** o sistema o inclui em listagens e permite leitura por `token`

#### Scenario: Registro soft-deleted oculto

- **WHEN** um dependente possui `deleted_at` preenchido
- **THEN** o sistema retorna HTTP 404 em leitura por `token` nas rotas padrão
- **AND** o registro permanece no banco para eventual restore

### Requirement: Identificadores públicos via token

O sistema MUST expor e aceitar identificadores de dependente como `token` opaco (string de 25 caracteres). O sistema MUST NOT expor o `id` numérico interno em URLs ou corpos de resposta da API.

#### Scenario: Resposta sem id numérico

- **WHEN** um cliente consulta um dependente
- **THEN** a resposta contém `token` e não contém o campo `id` interno

### Requirement: Criação de dependente

O sistema MUST permitir que um usuário autenticado com `ROLE_CLIENT` crie um dependente vinculado ao seu `client_id`. O sistema MUST permitir que um usuário com `ROLE_ADMIN` crie dependente para qualquer `client_id` informado no request.

Campos aceitos na criação: `name` (obrigatório), `birth_date`, `gender`, `document`, `phone`, `email`, `is_self`, `shift`, `school_id`, `address_id`, e `client_id` (apenas para `ROLE_ADMIN`).

#### Scenario: Cliente cria dependente com sucesso

- **WHEN** um usuário `ROLE_CLIENT` autenticado envia `POST /api/dependentes` com `name` válido
- **THEN** o sistema retorna HTTP 201
- **AND** o dependente é persistido com `client_id` do cliente autenticado
- **AND** um `token` único é gerado automaticamente

#### Scenario: Criação sem autenticação

- **WHEN** uma requisição `POST /api/dependentes` é enviada sem JWT válido
- **THEN** o sistema retorna HTTP 401

#### Scenario: Nome obrigatório

- **WHEN** um usuário autenticado envia criação sem `name`
- **THEN** o sistema retorna HTTP 400 com mensagem de validação em pt-BR

### Requirement: Listagem de dependentes

O sistema MUST permitir `GET /api/dependentes` para usuários com `ROLE_CLIENT` ou `ROLE_ADMIN`. Usuários `ROLE_CLIENT` MUST receber apenas dependentes ativos (`deleted_at` nulo) do seu `client_id`. Usuários `ROLE_ADMIN` MUST receber todos os dependentes ativos.

#### Scenario: Cliente lista apenas os seus

- **WHEN** um `ROLE_CLIENT` autenticado chama `GET /api/dependentes`
- **THEN** o sistema retorna HTTP 200 com lista contendo somente dependentes do seu `client_id`
- **AND** nenhum dependente soft-deleted é incluído

#### Scenario: Admin lista todos

- **WHEN** um `ROLE_ADMIN` autenticado chama `GET /api/dependentes`
- **THEN** o sistema retorna HTTP 200 com todos os dependentes ativos do sistema

### Requirement: Leitura de dependente por token

O sistema MUST permitir `GET /api/dependentes/{token}` para `ROLE_CLIENT` (apenas se for dono) e `ROLE_ADMIN` (qualquer registro ativo).

#### Scenario: Cliente acessa dependente próprio

- **WHEN** um `ROLE_CLIENT` consulta `GET /api/dependentes/{token}` de um dependente do seu `client_id`
- **THEN** o sistema retorna HTTP 200 com o `DependenteResponse`

#### Scenario: Cliente acessa dependente de outro

- **WHEN** um `ROLE_CLIENT` consulta `GET /api/dependentes/{token}` de dependente de outro cliente
- **THEN** o sistema retorna HTTP 403

#### Scenario: Dependente inexistente ou deletado

- **WHEN** o `token` não existe ou o registro está soft-deleted
- **THEN** o sistema retorna HTTP 404 com mensagem em pt-BR

### Requirement: Atualização de dependente

O sistema MUST permitir `PATCH /api/dependentes/{token}` para atualização parcial dos campos editáveis. As mesmas regras de ownership de leitura MUST aplicar (`ROLE_CLIENT` só edita os seus; `ROLE_ADMIN` edita qualquer).

#### Scenario: Atualização parcial bem-sucedida

- **WHEN** o dono envia `PATCH /api/dependentes/{token}` com campos válidos
- **THEN** o sistema retorna HTTP 200 com o dependente atualizado
- **AND** `updated_at` é atualizado

#### Scenario: Cliente tenta editar dependente alheio

- **WHEN** um `ROLE_CLIENT` envia `PATCH` para dependente de outro `client_id`
- **THEN** o sistema retorna HTTP 403

### Requirement: Soft delete de dependente

O sistema MUST permitir `DELETE /api/dependentes/{token}` preenchendo `deleted_at` com o timestamp atual, sem remover o registro do banco.

#### Scenario: Delete bem-sucedido

- **WHEN** o dono ou admin envia `DELETE /api/dependentes/{token}` para dependente ativo
- **THEN** o sistema retorna HTTP 204
- **AND** `deleted_at` é preenchido

#### Scenario: Delete de registro já deletado

- **WHEN** se tenta deletar dependente já soft-deleted
- **THEN** o sistema retorna HTTP 404

### Requirement: Restore de dependente soft-deleted

O sistema MUST permitir `POST /api/dependentes/{token}/restore` para limpar `deleted_at` e reativar o registro.

#### Scenario: Restore bem-sucedido

- **WHEN** o dono ou admin envia `POST /api/dependentes/{token}/restore` para dependente soft-deleted
- **THEN** o sistema retorna HTTP 200 com o dependente restaurado
- **AND** `deleted_at` volta a ser nulo

#### Scenario: Restore de registro ativo

- **WHEN** se tenta restaurar dependente que não está soft-deleted
- **THEN** o sistema retorna HTTP 409 com mensagem em pt-BR

### Requirement: Dependente padrão (RN12)

O sistema MUST implementar a regra RN12 via campo `is_default`:

- Ao criar o **primeiro** dependente ativo de um `client_id`, o sistema MUST definir `is_default = true` automaticamente.
- Ao criar dependentes adicionais, o sistema MUST definir `is_default = false` por padrão, salvo se o request solicitar explicitamente `is_default = true`.
- Ao definir `is_default = true` em um dependente, o sistema MUST definir `is_default = false` nos demais dependentes ativos do mesmo `client_id`.
- Apenas um dependente ativo por `client_id` MUST ter `is_default = true` em qualquer momento.

#### Scenario: Primeiro dependente vira padrão

- **WHEN** um cliente sem dependentes ativos cria o primeiro
- **THEN** o dependente criado tem `is_default = true`

#### Scenario: Definir novo padrão desmarca os outros

- **WHEN** um cliente com dois dependentes ativos atualiza um para `is_default = true`
- **THEN** apenas esse dependente permanece com `is_default = true`
- **AND** o anterior é atualizado para `is_default = false`

### Requirement: Promoção de padrão ao deletar dependente padrão

Ao soft-deletar o dependente que possui `is_default = true`, o sistema MUST aplicar:

- Se restar **exatamente um** dependente ativo no `client_id`, MUST promovê-lo a `is_default = true`.
- Se restarem **zero** ou **dois ou mais** dependentes ativos, MUST deixar nenhum com `is_default = true` até o cliente redefinir manualmente.

#### Scenario: Delete do padrão com um restante

- **WHEN** um cliente com dois dependentes ativos (um padrão) deleta o padrão
- **THEN** o dependente restante passa a ter `is_default = true`

#### Scenario: Delete do padrão com múltiplos restantes

- **WHEN** um cliente com três dependentes ativos deleta o padrão
- **THEN** os dois restantes ficam com `is_default = false`

### Requirement: Validação de entrada via DTOs

O sistema MUST validar requests com Bean Validation em DTOs dedicados (`@Valid` no controller). O sistema MUST NOT aceitar binding direto de request body em entidade JPA.

#### Scenario: Documento duplicado

- **WHEN** um create ou update define `document` já usado por outro dependente ativo
- **THEN** o sistema retorna HTTP 409 com mensagem em pt-BR

### Requirement: Seeder de dependentes de teste

Com `vanep.seed.enabled=true`, o sistema MUST popular dependentes de teste válidos vinculados a um client de seed, incluindo cenário com dependente padrão.

#### Scenario: Seed idempotente

- **WHEN** o seeder roda e os dependentes de teste já existem
- **THEN** o seeder não duplica registros

### Requirement: Cobertura de testes automatizados

O sistema MUST incluir testes automatizados cobrindo todos os endpoints (`create`, `read`, `update`, `delete`, `restore`), incluindo cenários de autenticação (401), autorização (403), not found (404) e regras RN12.

#### Scenario: CI verde

- **WHEN** `./mvnw verify` é executado após a implementação
- **THEN** todos os testes passam e a cobertura mínima JaCoCo é atingida
