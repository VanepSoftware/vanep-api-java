## Contexto

`ClientModel` e a tabela `client` já possuem o campo decimal e anulável `rating`. Atualmente, `ClientResponseDTO` expõe esse campo nas rotas gerais de listagem e detalhamento de clientes. O backend ainda não armazena avaliações individuais e não possui um modelo canônico de rota, viagem, execução de contrato ou transporte concluído que relacione um motorista a um cliente. Portanto, esta mudança precisa de uma nova persistência, leitura conforme o perfil do usuário e dependência explícita do histórico de transportes.

A regra de domínio é assimétrica: motoristas podem ver a média de um cliente; um cliente pode ver somente a própria média; outro cliente não pode vê-la. As notas individuais e as identidades dos avaliadores não serão expostas pela API.

## Objetivos e itens fora do escopo

**Objetivos:**

- Persistir uma nota inteira e imutável por combinação motorista-cliente.
- Exigir a comprovação de ao menos um transporte concluído antes da avaliação.
- Manter `client.rating` sincronizado como média agregada.
- Permitir que motoristas consultem médias de clientes e que clientes consultem somente a própria média.
- Permitir que somente administradores excluam avaliações por exclusão lógica, sem possibilidade de restauração.
- Remover a nota dos DTOs gerais de cliente que podem ser retornados a outros clientes.
- Seguir a organização por funcionalidade, identificadores públicos opacos, permissões específicas, mensagens via `MessageSource`, desenvolvimento orientado a testes e limites de PR definidos em `constitution.md`.

**Fora do escopo:**

- Atualizar uma avaliação ou permitir sua exclusão por motorista ou cliente.
- Restaurar uma avaliação excluída por administrador.
- Listar notas individuais ou revelar a identidade dos avaliadores.
- Permitir que um cliente consulte a nota de outro cliente.
- Definir nesta mudança o domínio de transporte, rota, proposta ou conclusão de contrato.
- Criar dados de avaliação semelhantes aos de produção antes de existirem dados de transportes concluídos.

## Decisões

### 1. API de criação e consulta agregada, sem CRUD completo

- `POST /api/client-ratings` recebe `clientToken` e `score` inteiro para motoristas.
- `GET /api/client-ratings/clients/{clientToken}` retorna `clientToken`, `average` e `ratingsCount` para um motorista autorizado ou para o próprio cliente.
- `DELETE /api/client-ratings/{token}` realiza exclusão lógica exclusivamente para administradores com `delete_client_rating`.
- Não serão criados endpoints de atualização, restauração, listagem ou detalhamento individual.

Essa API representa diretamente as operações necessárias. Cada avaliação recebe um `token` público, retornado ao motorista como comprovante da criação e usado pelo administrador na exclusão. O CRUD completo foi descartado porque atualização, restauração e leitura individual contrariariam a imutabilidade e a privacidade.

### 2. Persistência com exclusão lógica administrativa e sem restauração

Criar `client_rating` na próxima migration disponível do Flyway com:

```text
id bigint identity primary key
token varchar(32) not null unique
client_id bigint not null references client(id)
driver_id bigint not null references driver(id)
score smallint not null check (score between 0 and 5)
created_at timestamptz not null default now()
deleted_at timestamptz
unique (driver_id, client_id)
index (client_id)
```

Não haverá `updated_at`. O modelo usará `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)`, e a exclusão chamará `repository.delete(model)`. Não será criado método nem endpoint de restauração.

A unicidade de `(driver_id, client_id)` será uma restrição simples, sem índice parcial. Assim, a linha excluída continuará impedindo que o motorista avalie novamente o mesmo cliente, preservando a regra de avaliação única durante todo o histórico. A restrição do banco será a proteção final contra requisições duplicadas concorrentes.

### 3. `client_rating` como fonte de verdade e `client.rating` como projeção

Após inserir ou excluir logicamente uma avaliação, a mesma transação recalcula a média a partir das avaliações ativas e atualiza `client.rating`, respeitando a escala numérica existente. A operação deve bloquear ou atualizar o cliente de forma consistente para impedir que alterações concorrentes deixem a projeção desatualizada. A leitura retorna média e quantidade; a ausência de avaliações ativas resulta em `average = null` e `ratingsCount = 0`.

Calcular apenas durante a leitura foi descartado porque os fluxos existentes já possuem o campo persistido `rating`. O cálculo incremental sem consultar os registros de origem também foi descartado, pois novas tentativas e concorrência aumentariam o risco de divergência.

### 4. Política explícita de visibilidade

O serviço de resumo da avaliação autoriza a leitura considerando permissão e propriedade:

| Ator | Cliente consultado | Resultado |
|---|---|---|
| Motorista com `show_client_rating` | Qualquer cliente ativo | Permitir leitura da média |
| Cliente | Próprio perfil | Permitir leitura da média |
| Cliente | Outro cliente | Negar com 403 |
| Administrador | Qualquer cliente | Não recebe leitura da média; pode excluir por `token` com `delete_client_rating` |
| Outro perfil | Qualquer cliente | Negar |

As respostas gerais de `ClientResponseDTO` deixarão de incluir `rating`. Um `ClientRatingSummaryResponseDTO` específico evita exposição acidental e não contém informações do motorista.

### 5. Elegibilidade baseada no histórico canônico de transportes concluídos

O serviço deve consultar a futura capacidade canônica de conclusão de transportes usando o `driver_id` autenticado e o `client_id` consultado. A elegibilidade não pode ser deduzida de proposta, veículo cadastrado, propriedade de dependente ou rota planejada. A criação da avaliação não poderá ser concluída até essa capacidade existir; a dependência está registrada como a primeira etapa das tarefas.

### 6. Estrutura da funcionalidade e permissões

O novo código ficará em `br.com.vanep.clientrating`, com subpacotes e classes nomeados conforme sua função arquitetural. As permissões `create_client_rating`, `show_client_rating` e `delete_client_rating` serão adicionadas ao registro. O conjunto de permissões de motorista receberá criação e leitura; o conjunto de administrador receberá exclusão; o acesso do cliente ao próprio resumo também será protegido pela verificação de propriedade. Erros apresentados ao usuário usarão chaves de `MessageSource` com traduções em pt-BR.

### 7. Exclusão administrativa definitiva

O serviço localizará somente uma avaliação ativa pelo `token`, verificará `delete_client_rating`, executará a exclusão lógica e recalculará a média na mesma transação. Uma avaliação inexistente ou já excluída retornará HTTP 404. Motoristas e clientes receberão HTTP 403. Não haverá endpoint de restauração, e a linha excluída continuará bloqueando nova avaliação do mesmo motorista para o mesmo cliente.

### 8. Sem populador de avaliações nesta mudança

Um populador de avaliações violaria a elegibilidade ou inventaria uma relação de transporte inexistente. Os testes poderão construir dados de transportes concluídos quando essa dependência estiver disponível. Um populador futuro só poderá adicionar avaliações depois que o populador de transportes tiver criado transportes concluídos válidos.

### 9. Grafo de dependências e plano de PRs por fase

```text
capacidade canônica de transporte concluído
                |
                v
migration de client_rating -> modelo -> repositório
                |
                v
permissões + DTO de requisição -> serviço/regras de criação e exclusão
                |
                v
controlador + DTOs de resposta + privacidade da resposta de cliente
```

| Fase | Conteúdo | Depende de | Paralelo com |
|---|---|---|---|
| 0 | Confirmar e integrar a capacidade canônica de transporte concluído | Mudança externa de transporte | Nenhuma |
| 1 | Testes de persistência, migration com `deleted_at`, modelo e repositório | Contrato da fase 0 | Nenhuma |
| 2 | Testes de serviço, permissões, elegibilidade, criação, exclusão administrativa e cálculo da média | Fase 1 | Nenhuma |
| 3 | Testes com `MockMvc`, endpoints, DTOs e remoção da nota da resposta geral | Fase 2 | Nenhuma |

Cada fase terá sua própria branch e seu próprio PR, respeitará os limites do repositório, incluirá os testes pertinentes e deverá passar por `spotless:check` e `verify` antes da revisão.

## Riscos e contrapartidas

- [A capacidade de transporte concluído não existe] -> Bloquear a implementação da aplicação após o planejamento até que uma fonte canônica seja integrada; não aceitar evidências mais fracas.
- [Avaliações concorrentes podem desatualizar a média] -> Recalcular a partir dos registros de origem na transação de criação, com controle de consistência do cliente e testes de concorrência.
- [Consumidores existentes esperam `ClientResponseDTO.rating`] -> Tratar a remoção como mudança no contrato de resposta, atualizar testes e consumidores e direcionar leituras autorizadas ao endpoint de resumo.
- [Conjuntos de permissões podem expor resumos acidentalmente] -> Testar toda a matriz entre ator e cliente consultado, especialmente a negação entre clientes diferentes.
- [Exclusão administrativa indevida não pode ser restaurada pela API] -> Exigir permissão exclusiva, confirmação na interface administrativa e testes de autorização; o registro permanece no banco para auditoria.

## Plano de migração

1. Integrar a capacidade canônica de transporte concluído e documentar seu contrato de consulta de elegibilidade.
2. Adicionar uma nova migration do Flyway usando a próxima versão disponível; nunca editar uma migration já aplicada.
3. Implantar a persistência e verificar as restrições antes de habilitar os endpoints.
4. Implantar as fases de serviço e API com a atualização dos conjuntos de permissões.
5. Remover `rating` das respostas gerais de cliente na mesma fase da API que introduzir o endpoint autorizado de resumo e a exclusão administrativa.
6. Em caso de reversão, desabilitar os novos endpoints, preservar a tabela e usar uma nova migration para qualquer alteração inversa do esquema.

## Questões em aberto

- Qual tabela e qual estado futuro representarão a comprovação canônica de um transporte concluído? Isso deve ser resolvido na fase 0 antes do início da implementação da aplicação.
