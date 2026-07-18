## 0. Dependência obrigatória

- [ ] 0.1 Identificar a tabela, o estado final e a consulta canônica de transporte concluído que relacionam um motorista autenticado a um cliente
- [ ] 0.2 Confirmar que propostas, veículos, dependentes e rotas planejadas não serão aceitos como substitutos de um transporte concluído
- [ ] 0.3 Integrar ou disponibilizar a capacidade de transporte concluído antes de iniciar a implementação da aplicação
- [ ] 0.4 Revisar e aprovar o grafo de dependências e o plano de PRs por fase de `design.md`

## 1. Fase 1 — Base de persistência

- [ ] 1.1 Criar primeiro os testes de persistência do repositório para notas inteiras de 0 a 5, unicidade permanente motorista-cliente, registros imutáveis e exclusão lógica
- [ ] 1.2 Adicionar a próxima migration disponível do Flyway para `client_rating`, com `token`, chaves estrangeiras de cliente e motorista, validação da nota, unicidade simples motorista-cliente, data de criação, `deleted_at` e índice de consulta por cliente
- [ ] 1.3 Criar `br.com.vanep.clientrating.model.ClientRatingModel` com `@SoftDelete`, sem campo de atualização e sem operação de restauração
- [ ] 1.4 Criar em `ClientRatingRepository` as consultas de detecção de duplicidade histórica, busca ativa por `token`, média e quantidade somente de avaliações ativas
- [ ] 1.5 Verificar a migration e o repositório com SQL compatível com PostgreSQL e testes em H2
- [ ] 1.6 Executar `mvnw.cmd spotless:check` e `mvnw.cmd verify` para a fase 1
- [ ] 1.7 Abrir o PR de persistência da fase 1 em pt-BR, informando o resultado dos testes e da formatação

## 2. Fase 2 — Autorização e regras de negócio

- [ ] 2.1 Criar primeiro os testes de serviço para criação elegível, ausência de transporte concluído, duplicidade histórica, validação da nota, exclusão administrativa, atualização da média e criação concorrente
- [ ] 2.2 Adicionar `create_client_rating`, `show_client_rating` e `delete_client_rating` a `PermissionEnum` e ao registro; conceder criação/leitura ao motorista e exclusão somente ao administrador
- [ ] 2.3 Adicionar chaves de `MessageSource` e mensagens em pt-BR para nota inválida, falta de elegibilidade, avaliação duplicada, cliente inexistente, avaliação inexistente e acessos proibidos
- [ ] 2.4 Criar `ClientRatingCreateRequestDTO` com `clientToken`, `score` inteiro e validações do Bean Validation
- [ ] 2.5 Implementar a consulta canônica de elegibilidade por transporte concluído usando o `driver_id` autenticado e o `client_id` consultado
- [ ] 2.6 Implementar a criação em `ClientRatingService`, tratando a unicidade do banco e retornando o `token` público da avaliação
- [ ] 2.7 Implementar em `ClientRatingService` a exclusão lógica por administrador, sem restauração e sem liberar nova avaliação para a mesma combinação motorista-cliente
- [ ] 2.8 Recalcular `client.rating` a partir das avaliações ativas na mesma transação de criação ou exclusão e retornar média nula e quantidade zero quando não houver avaliações ativas
- [ ] 2.9 Implementar a autorização do resumo: motorista pode consultar cliente, cliente pode consultar somente a si mesmo, outro cliente recebe 403 e os demais perfis não recebem leitura da média
- [ ] 2.10 Executar `mvnw.cmd spotless:check` e `mvnw.cmd verify` para a fase 2
- [ ] 2.11 Abrir o PR de serviço e segurança da fase 2 em pt-BR, informando o resultado dos testes e da formatação

## 3. Fase 3 — API REST e privacidade

- [ ] 3.1 Criar primeiro os testes com `MockMvc` para POST 201/400/401/403/404/409, DELETE 204/401/403/404 e toda a matriz de visibilidade do resumo
- [ ] 3.2 Criar `ClientRatingController` com `POST /api/client-ratings` protegido por `create_client_rating`
- [ ] 3.3 Criar `ClientRatingCreateResponseDTO` com o `token` público retornado ao motorista após a criação
- [ ] 3.4 Criar `ClientRatingSummaryResponseDTO` contendo somente `clientToken`, `average` e `ratingsCount`
- [ ] 3.5 Adicionar `GET /api/client-ratings/clients/{clientToken}` para acesso de motorista autorizado e acesso do cliente ao próprio perfil
- [ ] 3.6 Adicionar `DELETE /api/client-ratings/{token}` protegido por `delete_client_rating` e exclusivo para administradores
- [ ] 3.7 Remover `rating` das respostas gerais de listagem e detalhamento de `ClientResponseDTO` e atualizar testes e consumidores afetados
- [ ] 3.8 Adicionar testes negativos comprovando que motorista e cliente não podem excluir, que outro cliente não pode consultar o resumo e que nenhum endpoint expõe notas individuais ou identidades de motoristas
- [ ] 3.9 Confirmar que não existem rotas de atualização, restauração, listagem geral ou detalhamento individual de `client-rating`
- [ ] 3.10 Executar `mvnw.cmd spotless:check` e `mvnw.cmd verify` para a fase 3
- [ ] 3.11 Abrir o PR de API e privacidade da fase 3 em pt-BR, informando o resultado dos testes e da formatação

## 4. Validação final

- [ ] 4.1 Validar toda a mudança conforme `specs/client-rating/spec.md`
- [ ] 4.2 Verificar com testes de integração o acesso do motorista, o acesso do cliente ao próprio perfil e a negação entre clientes diferentes
- [ ] 4.3 Verificar que somente administradores excluem avaliações, que a média é recalculada e que não existe restauração
- [ ] 4.4 Verificar que a avaliação excluída continua impedindo nova avaliação do mesmo motorista para o mesmo cliente
- [ ] 4.5 Confirmar que nenhum populador de `client-rating` foi adicionado sem dados válidos de transportes concluídos
- [ ] 4.6 Revisar e validar a mudança com a equipe antes do merge
