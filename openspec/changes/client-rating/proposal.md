## Why

O backend possui um campo agregado de nota no perfil do cliente, mas ainda não registra quem avaliou, não impede avaliações repetidas e não consegue comprovar que o motorista transportou o cliente. É necessário criar uma fonte de verdade para avaliações imutáveis e definir sua visibilidade: motoristas podem consultar a média dos clientes, enquanto cada cliente só pode consultar a própria média.

## What Changes

- Criar o recurso `client_rating` para registrar uma nota inteira de 0 a 5 dada por um motorista a um cliente.
- Permitir somente uma avaliação por combinação motorista-cliente, após ao menos um transporte concluído entre eles.
- Tornar as avaliações imutáveis para motoristas e clientes: não haverá atualização; somente administradores poderão realizar exclusão lógica.
- Não disponibilizar restauração de avaliações excluídas por administradores.
- Manter a restrição de uma única avaliação por motorista-cliente mesmo após a exclusão administrativa.
- Expor criação de avaliação para motoristas autenticados.
- Expor a média e a quantidade de avaliações para motoristas autenticados ao consultarem um cliente.
- Permitir que clientes consultem somente a própria média; impedir que consultem a média de outros clientes.
- Não expor avaliações individuais nem a identidade dos motoristas avaliadores.
- Remover a nota de respostas gerais que possam ser consumidas por outros clientes e fornecer respostas específicas conforme o perfil autenticado.
- Recalcular o campo agregado `client.rating` a partir das avaliações persistidas.
- Não criar um populador de avaliações enquanto não existir uma fonte canônica de transportes concluídos capaz de produzir dados elegíveis.

## Capabilities

### New Capabilities

- `client-rating`: criação imutável de avaliações, exclusão administrativa não reversível, elegibilidade por transporte concluído, média agregada e visibilidade baseada no tipo e na identidade do usuário autenticado.

### Modified Capabilities

- _(nenhuma)_

## Impact

- **Banco de dados**: nova tabela `client_rating`, relacionada a `client` e `driver`, com `deleted_at`, restrições de intervalo e unicidade permanente motorista-cliente; atualização transacional do agregado `client.rating`.
- **API**: novos endpoints de criação, leitura controlada da média e exclusão administrativa; não haverá endpoint de restauração.
- **Segurança**: novas permissões e regras que distinguem criação por motorista, leitura por motorista ou cliente proprietário e exclusão exclusiva por administrador.
- **Dependência de domínio**: a criação depende de uma fonte canônica de transporte concluído. O backend atual ainda não possui rota, viagem ou contrato executado que comprove essa relação; a implementação da elegibilidade fica bloqueada até essa capacidade existir.
- **Testes**: cobertura de persistência, intervalo de 0 a 5, duplicidade, elegibilidade, imutabilidade, exclusão administrativa, ausência de restauração, média e matriz de visibilidade.
