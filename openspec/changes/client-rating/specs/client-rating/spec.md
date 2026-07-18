## ADDED Requirements

### Requirement: Persistência imutável da avaliação do cliente
O sistema MUST persistir cada avaliação com `token` público, referência a um motorista e um cliente, um `score` inteiro de 0 a 5, data de criação e `deleted_at` para exclusão lógica. O banco de dados MUST aplicar `CHECK (score BETWEEN 0 AND 5)` e MUST permitir no máximo uma avaliação para cada combinação `(driver_id, client_id)`, incluindo registros excluídos logicamente. As avaliações MUST NOT permitir atualização ou restauração.

#### Scenario: Avaliação válida é persistida
- **WHEN** um motorista autenticado e elegível envia uma nota inteira de 0 a 5 para um cliente ainda não avaliado por ele
- **THEN** o sistema persiste exatamente uma avaliação imutável para essa combinação motorista-cliente

#### Scenario: Nota fracionária ou fora do intervalo é rejeitada
- **WHEN** um motorista envia uma nota fracionária, menor que 0 ou maior que 5
- **THEN** o sistema retorna HTTP 400 e não persiste a avaliação

#### Scenario: Avaliação duplicada é rejeitada
- **WHEN** um motorista envia uma segunda avaliação para o mesmo cliente
- **THEN** o sistema retorna HTTP 409 e preserva a avaliação original sem alterações

#### Scenario: Avaliação excluída continua impedindo duplicidade
- **WHEN** um motorista tenta avaliar novamente um cliente após sua avaliação anterior ter sido excluída por administrador
- **THEN** o sistema retorna HTTP 409 e não cria outra avaliação

### Requirement: Elegibilidade exige transporte concluído
O sistema MUST permitir que um motorista avalie um cliente somente quando o histórico canônico comprovar que ele concluiu ao menos um transporte associado ao cliente. Proposta, veículo cadastrado, propriedade de dependente ou transporte não concluído MUST NOT estabelecer elegibilidade isoladamente.

#### Scenario: Motorista com transporte concluído pode avaliar
- **WHEN** o motorista autenticado possui ao menos um transporte concluído associado ao cliente
- **THEN** o motorista está elegível para criar a avaliação

#### Scenario: Motorista sem transporte concluído não pode avaliar
- **WHEN** o motorista autenticado não possui transporte concluído associado ao cliente
- **THEN** o sistema retorna HTTP 403 e não persiste a avaliação

### Requirement: Motorista cria avaliação de cliente
O sistema SHALL expor `POST /api/client-ratings` para motorista autenticado com a permissão `create_client_rating`. A requisição MUST identificar o cliente pelo `clientToken` público e conter `score`; ela MUST NOT aceitar identificadores numéricos internos. A resposta de criação MUST retornar o `token` público da avaliação como comprovante para o motorista.

#### Scenario: Motorista elegível cria avaliação
- **WHEN** um motorista elegível envia `clientToken` e `score` válidos
- **THEN** o sistema retorna HTTP 201

#### Scenario: Usuário que não é motorista tenta avaliar
- **WHEN** um usuário autenticado sem permissão de avaliação de motorista chama o endpoint de criação
- **THEN** o sistema retorna HTTP 403

#### Scenario: Criação sem autenticação
- **WHEN** uma requisição não autenticada chama o endpoint de criação
- **THEN** o sistema retorna HTTP 401

### Requirement: Exclusão administrativa não reversível
O sistema SHALL expor `DELETE /api/client-ratings/{token}` exclusivamente para usuários autenticados com a permissão administrativa `delete_client_rating`. A exclusão MUST usar `@SoftDelete`, preencher `deleted_at`, retirar a avaliação do cálculo da média e recalcular `client.rating` na mesma transação. O sistema MUST NOT expor endpoint ou operação de restauração.

#### Scenario: Administrador exclui avaliação ativa
- **WHEN** um administrador com `delete_client_rating` exclui uma avaliação ativa pelo `token`
- **THEN** o sistema retorna HTTP 204, preenche `deleted_at` e recalcula a média somente com avaliações ativas

#### Scenario: Motorista tenta excluir avaliação
- **WHEN** um motorista tenta excluir uma avaliação, inclusive a própria
- **THEN** o sistema retorna HTTP 403 e mantém a avaliação ativa

#### Scenario: Cliente tenta excluir avaliação
- **WHEN** um cliente tenta excluir uma avaliação recebida
- **THEN** o sistema retorna HTTP 403 e mantém a avaliação ativa

#### Scenario: Avaliação já excluída não pode ser excluída novamente
- **WHEN** um administrador tenta excluir uma avaliação inexistente ou já excluída
- **THEN** o sistema retorna HTTP 404

#### Scenario: Restauração não está disponível
- **WHEN** qualquer usuário tenta acessar uma rota de restauração de avaliação
- **THEN** nenhuma rota correspondente existe e a avaliação permanece excluída

### Requirement: Média da avaliação do cliente
O sistema MUST calcular a média do cliente somente a partir das avaliações ativas e arredondá-la conforme a escala de `client.rating`. A criação ou exclusão lógica da avaliação e a atualização de `client.rating` MUST ocorrer atomicamente. Quando não houver avaliações ativas, a média MUST ser `null` e a quantidade MUST ser `0`.

#### Scenario: Primeira avaliação define a média
- **WHEN** um cliente sem avaliações recebe nota 4
- **THEN** a média do cliente é 4 e a quantidade de avaliações é 1

#### Scenario: Várias avaliações atualizam a média
- **WHEN** um cliente recebe notas 3 e 5 de motoristas diferentes
- **THEN** a média do cliente é 4 e a quantidade de avaliações é 2

#### Scenario: Cliente não possui avaliações
- **WHEN** um usuário autorizado consulta um cliente sem avaliações
- **THEN** a resposta contém média `null` e quantidade de avaliações 0

#### Scenario: Exclusão administrativa atualiza a média
- **WHEN** um administrador exclui uma das avaliações de um cliente
- **THEN** a média e a quantidade passam a considerar somente as avaliações ativas restantes

#### Scenario: Exclusão da última avaliação limpa a média
- **WHEN** um administrador exclui a única avaliação ativa de um cliente
- **THEN** `client.rating` passa a ser `null` e a quantidade de avaliações passa a ser 0

### Requirement: Visibilidade do resumo conforme o perfil
O sistema SHALL expor `GET /api/client-ratings/clients/{clientToken}` somente para leitura da média e da quantidade de avaliações. Um motorista autenticado com a permissão `show_client_rating` MAY consultar o resumo de um cliente. Um cliente autenticado MAY consultar o resumo somente quando `{clientToken}` identificar seu próprio perfil. Outro cliente MUST NOT consultar o resumo. Esta mudança não concede acesso ao resumo para administradores.

#### Scenario: Motorista consulta o resumo da avaliação
- **WHEN** um motorista autenticado com `show_client_rating` solicita o resumo de um cliente
- **THEN** o sistema retorna HTTP 200 com média e quantidade de avaliações

#### Scenario: Cliente consulta o próprio resumo
- **WHEN** um cliente autenticado solicita o resumo usando o próprio token
- **THEN** o sistema retorna HTTP 200 com sua média e quantidade de avaliações

#### Scenario: Cliente tenta consultar a avaliação de outro cliente
- **WHEN** um cliente autenticado solicita o resumo usando o token de outro cliente
- **THEN** o sistema retorna HTTP 403 sem expor a média nem a quantidade de avaliações

#### Scenario: Perfil não autorizado tenta consultar o resumo
- **WHEN** o usuário autenticado não é um motorista autorizado nem o próprio cliente consultado
- **THEN** o sistema retorna HTTP 403

### Requirement: Avaliações individuais permanecem privadas
O sistema MUST NOT expor endpoints de listagem ou detalhamento de avaliações individuais nem a identidade dos motoristas avaliadores. O `token` da avaliação MAY ser retornado ao motorista somente na criação e usado pelo administrador na exclusão. As respostas gerais de listagem e detalhamento de clientes MUST NOT incluir o campo `rating` quando puderem ser acessadas por outros clientes.

#### Scenario: Motorista vê somente dados agregados
- **WHEN** um motorista consulta o resumo da avaliação de um cliente
- **THEN** a resposta contém somente token do cliente, média e quantidade, sem nota individual ou identidade de motorista

#### Scenario: Resposta geral de cliente omite a avaliação
- **WHEN** um usuário consulta uma resposta geral de listagem ou detalhamento de cliente
- **THEN** a resposta não expõe a avaliação do cliente

### Requirement: Cobertura automatizada da avaliação
O sistema MUST incluir testes de persistência, serviço e `MockMvc` para validação da nota, unicidade permanente, elegibilidade, imutabilidade, exclusão lógica administrativa, ausência de restauração, recálculo da média, autenticação, permissões, acesso do proprietário, acesso de motorista e negação entre clientes.

#### Scenario: Verificação concluída com sucesso
- **WHEN** `mvnw.cmd verify` é executado após a implementação
- **THEN** todos os testes de `client-rating` passam e o limite de cobertura do projeto permanece atendido
