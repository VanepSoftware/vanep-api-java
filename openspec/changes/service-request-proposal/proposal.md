## Why

A fase 1 (`contract-core`) entregou o contrato, mas ele ainda só nasce pelo admin. Não existe o caminho de negócio que leva até ele: o responsável não tem como pedir orçamento a um motorista, o motorista não tem como responder com uma oferta estruturada, e o vínculo `client_driver` não sabe de onde veio, o que a comissão (D11 do spike) exige.

Esta change é a **fase 2 do spike de Propostas e Contratos**. Ela entrega a solicitação e a proposta como objetos próprios, versionados e imutáveis (D1, D2 do spike), com as máquinas de estado, os prazos e a API REST das duas partes. O aceite do responsável fecha o acordo sobre uma versão; a geração do contrato a partir dele é da fase 3.

Ela também ajusta o que precisa mudar no mesmo momento em que o vínculo passa a nascer por solicitação: a avaliação passa a exigir um contrato que já esteve ativo (E3), o vínculo ganha origem imutável (E2, A8) e bloqueio com direção, e a busca deixa de mostrar quem não está aceitando alunos (A4).

**Sem push.** As notificações por FCM (VAN-18) ficam para uma fase final. Nesta change a contraparte é avisada por e-mail, num ouvinte de eventos que o push vai reaproveitar.

## What Changes

- **Avaliações (E3):** avaliar motorista e avaliar cliente passam a exigir que o vínculo tenha algum contrato que já chegou a `ACTIVE` (`ACTIVE`, `SUSPENDED`, `ENDED`, `TERMINATED` ou `SUPERSEDED`). Hoje a avaliação de motorista exige contrato vigente, e a de cliente não exige nada
- **Busca (A4):** `GET /api/drivers/search` e a lista de recomendados escondem o motorista com `is_available = false`, que passa a significar "aceitando novos alunos". A aprovação do motorista liga a disponibilidade, e ele a desliga quando lotar. A solicitação a esse motorista é recusada
- **Origem do vínculo (E2, A8):** `client_driver.origin` (`CLIENT_SEARCH`, `DRIVER_INVITE`), gravada quando o par nasce e sem caminho de atualização. A primeira solicitação cria o vínculo `PENDING` com `CLIENT_SEARCH`; uma solicitação de um par que já existe reaproveita a linha e mantém a origem. O `POST /api/client-drivers` (só admin) passa a exigir `origin`. **BREAKING** para o admin, sem consumidor
- **Bloqueio com direção:** `client_driver.blocked_by` e `blocked_at`; `POST /api/client-drivers/{token}/block` e `/unblock` para as partes. Só bloqueia sem contrato assinado e não encerrado; só quem bloqueou desbloqueia; bloquear encerra a negociação aberta do par; desbloquear devolve o status derivado dos contratos
- **Grade escolar do dependente** (`dependent_school_day`): o responsável cadastra, uma vez, o período escolar de cada dia da semana (o aluno pode ser integral só em alguns dias), em `GET` e `PUT /api/dependent/{token}/school-days`. O `dependent.shift` sai: a grade é a única fonte do período escolar. **BREAKING** no JSON do dependente, sem consumidor
- **Solicitação** (`service_request`, `service_request_item`, `service_request_day`): o responsável pede orçamento a um motorista para um ou mais dependentes, dizendo em quais dias precisa de ida e de volta, mais início desejado e observações. O período escolar de cada dia vem da grade do dependente e é copiado no envio. O horário de embarque é do motorista, na proposta. Uma em aberto por par, até 5 em aberto por responsável, 72 h para o motorista responder. O motorista vê só primeiro nome e idade do dependente, escola, turno e bairro de embarque (LGPD art. 14)
- **Prontidão contratual (D4 do spike):** a solicitação só é enviada se o responsável e os dependentes já têm os dados que o contrato vai precisar (CPF, telefone, endereço pessoal; nascimento, escola, endereço e grade escolar do dependente). `GET /api/clients/me/contract-readiness` diz o que falta antes do envio, e entra já com a grade escolar, porque depende só do cadastro
- **Dependente travado na negociação e no contrato:** o dependente que está numa solicitação aberta (`OPEN` ou `ANSWERED`) não pode ser removido (`422 dependent.open_negotiation`), para a solicitação nunca apontar para quem sumiu. Com contrato em andamento (`AWAITING_SIGNATURES`, `SIGNED`, `ACTIVE` ou `SUSPENDED`), ele não pode ser removido nem trocar de escola, endereço ou grade (`422 dependent.open_contract`): remover já tira o item do contrato e a criança da rota, e a mudança de verdade é aditivo (fase 8)
- **CPF do dependente e escolas, alinhados ao app:** o `document` do dependente, quando presente, tem de ser um CPF válido e é gravado só com os dígitos; o resolve de escola passa a aceitar também `primary_school` e `secondary_school`, os tipos que o app usa na busca
- **Bairro pelo `neighborhood` (D20):** o bairro mostrado no endereço, no contrato, nos passageiros da rota e na solicitação é sempre o `address.neighborhood`, que o caminho por place também passa a gravar. `address.district_id`, `contract_item.pickup_district_id` e `school.district_id` saem, e `pickupDistrictName`, `district`, `districtName` e `districtToken` saem do JSON do contrato, dos passageiros da rota, dos endereços e da escola. **BREAKING**: os campos dos endereços e do contrato vinham sempre nulos, e o bairro da escola passa a vir do endereço dela. O `district` continua só na busca de motorista e na área de atuação
- **Endereço da escola no resolve (D21):** `POST /api/schools/resolve` grava o endereço da escola a partir do mesmo place (sem rua, a escola nasce sem endereço), completa a escola já conhecida que está sem endereço, e devolve o endereço. O `school` do JSON do dependente traz o endereço
- **Proposta** (`proposal`, `proposal_item`, `proposal_adjustment`): só o motorista escreve; período, valor total, parcelas, vencimento, taxa de reserva, reajuste e rescisão por menu fechado, veículo, validade de até 7 dias e itens por dependente com preço e agenda. Nunca é editada: cada mudança é uma versão nova, e a anterior vira `SUPERSEDED`. O responsável aceita a versão vigente, recusa ou pede ajuste
- **O compromisso se basta:** a solicitação e a proposta guardam a foto do que cada parte viu (dependente, escola, cidade e bairro; veículo oferecido) e só ids simples para o cadastro, e o aceite reconfere se o cadastro mudou no que pesa no preço. Editar, remover ou restaurar o cadastro depois nunca reescreve nem esconde a negociação
- **Prazos:** uma varredura agendada única (a cada 1 minuto por padrão, com teto de linhas por rodada, os dois configuráveis pelo ambiente) expira as solicitações sem resposta e as propostas vencidas. As escritas conferem o prazo por conta própria, então o atraso da varredura nunca deixa aceitar uma proposta vencida. As fases seguintes acrescentam passos a essa varredura em vez de criar outra
- **E-mail para a contraparte** em cada passo que pede ação dela (solicitação recebida, proposta recebida, ajuste pedido, proposta aceita ou recusada, solicitação recusada), disparado depois do commit
- **A solicitação abre a conversa** (spike: "a solicitação é a primeira mensagem"): o envio cria a conversa do par, ou reaproveita a que ele já tem, as duas visões da solicitação trazem o `conversationToken`, e as listas filtram por ele para a conversa achar as negociações do par. O `POST /api/conversations` e a permissão `create_conversation` saem, e ninguém abre conversa fora de uma solicitação. A conversa continua aberta depois que a solicitação se encerra. **BREAKING** para o app, que abria a conversa pelo perfil do motorista
- Superfícies `/me` das duas partes (`/api/clients/me/...` e `/api/drivers/me/...`), com o dono resolvido pelo `Authentication`. Nenhuma permissão nova

**Fora de escopo:**

- Geração do contrato a partir da proposta aceita, `contract_template`, `contract_document`, PDF, tela de resumo (fase 3). Nesta fase o aceite fecha a proposta e a solicitação, e o contrato ainda não nasce
- Ids simples no lugar das associações do `contract_item` com o dependente, a escola e a cidade (fase 3, R14)
- Autorização escolar de motorista e veículo (A5, fase 3)
- Retirada das solicitações concorrentes ao assinar (fase 5)
- Cartões tipados no chat e o bloqueio do par cortando a conversa (fase 6). Do chat (#161), esta change só muda quem abre a conversa
- Convite da família, que cria vínculo com origem `DRIVER_INVITE` (fase 7)
- Aditivo e renovação (`proposal.source_contract_id` nasce nulo) (fase 8)
- Comissão: valor, exibição para o motorista e recolhimento (fase 9, depois do E1)
- Push / FCM (VAN-18), numa fase final
- `driver.work_days` (A3), troca de `base_price` por `startingPrice` (A2), leitura das negociações pelo admin

## Capabilities

### New Capabilities

- `rating-requires-contract`: as duas avaliações exigem um contrato do vínculo que já chegou a `ACTIVE`.
- `driver-availability`: `is_available` como "aceitando novos alunos", filtrando a busca e a solicitação.
- `client-driver-origin`: origem imutável do vínculo, gravada quando o par nasce.
- `client-driver-block`: bloqueio e desbloqueio com direção pelas partes.
- `contract-readiness`: conferência dos dados do responsável e dos dependentes antes da solicitação.
- `service-request`: solicitação de orçamento, estados, prazo de 72 h e visão restrita do motorista.
- `proposal`: proposta versionada do motorista, aceite, recusa e pedido de ajuste do responsável, prazo de validade.
- `negotiation-email`: e-mail à contraparte nos passos da negociação.
- `address-neighborhood`: o bairro mostrado vem do `address.neighborhood`, e o `district` sai do endereço, do contrato e da rota.
- `school-address`: o resolve de escola grava e completa o endereço da escola a partir do place.
- `conversation-opening`: a conversa do par nasce com a primeira solicitação, e o cliente não abre mais conversa direto.

### Modified Capabilities

- `dependent`: ganha a grade escolar da semana (período por dia), lida e substituída como sub-recurso do dependente, fica travado em negociação aberta e com contrato em andamento, e a escola dele vem com o endereço.

A `contract-core` e a `client-driver-link` ainda não foram arquivadas, então não há spec base para delta delas. Ao arquivar, a `rating-requires-contract` substitui a regra de vínculo `ACTIVE` da #158, e a `client-driver-origin` e a `client-driver-block` completam a `client-driver-derived-status`. A `client-driver-chat` também não foi arquivada: ao arquivar, a `conversation-opening` substitui o requisito "Only the client opens a conversation".

## Impact

- **Schema:** `V61` (`client_driver.origin`, `blocked_by`, `blocked_at`), `V62` (`dependent_school_day`; remove `dependent.shift`), `V63` (bairro para o `neighborhood`; remove `address.district_id`, `contract_item.pickup_district_id` e `school.district_id`), `V64` (`service_request`, `service_request_item`, `service_request_day`), `V65` (`proposal`, `proposal_item`, `proposal_adjustment`), `V66` (`create_conversation` sai dos bundles; a conversa dos pares que já têm solicitação). A `main` está em `V60`. Se a `absence` (VAN-13) entrar antes, os números andam (R6 do `design.md`)
- **Pacotes novos:** `br.com.vanep.servicerequest`, `br.com.vanep.proposal`
- **Pacotes alterados:** `address` e `routepassenger` (bairro pelo `neighborhood`), `school` (endereço no resolve; sem `district`), `unlinkedpassenger` (consultas sem o `district`), `dependent` (grade escolar, travas, endereço da escola), `clientdriver` (origem, bloqueio, criação pela solicitação), `driverrating` e `clientrating` (elegibilidade), `driver` (busca), `contract` (`ContractStatus.REACHED_ACTIVE`; a criação do item trava o dependente; o item sem `pickup_district_id`), `config` (agendamento), `shared` (a varredura de prazos), `conversation` (a abertura pela solicitação; sai o `POST`), `seed` (o bundle `CLIENT` sem `create_conversation`), `auth.security` (nada novo no `@sec`: as superfícies são `/me`), templates de e-mail
- **API:** endpoints novos em `/api/dependent/{token}/school-days`, `/api/clients/me/service-requests`, `/api/clients/me/proposals`, `/api/clients/me/contract-readiness`, `/api/drivers/me/service-requests`, `/api/drivers/me/proposals`, `/api/client-drivers/{token}/block` e `/unblock`. `POST /api/client-drivers` exige `origin` (só admin, sem consumidor). `DELETE` e `PATCH /api/dependent/{token}` e o `PUT` da grade passam a responder 422 com as travas. A busca deixa de devolver motoristas indisponíveis. `POST /api/conversations` sai, as duas visões da solicitação ganham `conversationToken`, e as duas listas aceitam `?conversationToken=`. `POST /api/schools/resolve` e o `school` do dependente trazem o endereço da escola. Saem `pickupDistrictName` do item do contrato e dos passageiros da rota, `district` do endereço e `districtName`/`districtToken` do endereço pessoal e da escola
- **Dados:** a `V61` grava `CLIENT_SEARCH` nos vínculos existentes e devolve ao status derivado os vínculos `BLOCKED` sem quem bloqueou, porque ninguém poderia desbloqueá-los. A `V66` abre a conversa de cada par que já tem solicitação e não tem conversa; as conversas abertas antes, sem solicitação, ficam como estão
- **Disponibilidade na aprovação:** a aprovação do motorista passa a gravar `is_available = true`. Motoristas já aprovados nos bancos locais continuam `false` e somem da busca até recriar o banco (R3)
- **Task:** VAN-30 no Genwork (`[Contract e Proposal - 2] Pedido de orçamento e proposta`). Cobre as antigas issues #42 (proposta) e #48 (resposta à proposta) do GitHub, no formato do spike; a VAN-20 (antiga #149, arquivada) e a VAN-12 (antiga #174) ficam de fora por decisão de 17/10
