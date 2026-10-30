## Context

Esta change é a **fase 2** do spike "Propostas e Contratos" (V2, revisto em 17/10/2026). A motivação está no `proposal.md`, e os requisitos nos specs. Este documento registra como a fase é construída e onde ela se afasta do texto do spike.

O que existe hoje e importa aqui:

- `client_driver` (V36, ajustado pela `contract-core`): um vínculo por par (`client_driver_pair_active_key`), com status derivado dos contratos pela `LinkStatusPolicy` e recalculado pelo `ContractService`. `BLOCKED` não tem caminho de escrita desde a `V60`. O `POST /api/client-drivers` é só do admin (`create_client_driver` está só no bundle `ADMIN`) e cria `PENDING`
- `contract`, `contract_item`, `schedule` e `schedule_slot` (V57, V58). O `ScheduleService` cria e substitui agendas a partir de `ScheduleSlotRequestDTO` (`weekday`, `leg`, `shift`, `windowStart` obrigatório, `windowEnd`), validados pela `ScheduleSlotPolicy`. A `ContractTermsPolicy` confere a ordem das datas e o teto de 12 meses
- Avaliações: o `DriverRatingService` exige vínculo `ACTIVE` (`DriverRatingEligibilityPolicy`, #158) e cinco minutos de idade; o `ClientRatingService` só exige que o vínculo exista. As duas são apagadas fisicamente (regra 19)
- Busca: `DriverSearchService` ordena os motoristas pelas áreas de atendimento e filtra por ativo e `APPROVED`. `driver.is_available` nasce `false` e só é exibido
- Endereço pessoal em `users.address_id`; o onboarding já cobra o `PERSONAL_ADDRESS`. `dependent.birth_date` é opcional
- O dependente tem `school_id` e um único `shift` (`SchoolShift`). Nada no dependente nem na escola diz o período por dia da semana. As rotas do dependente (`/api/dependent`) usam as permissões `crudFor("dependents")` do bundle `CLIENT`, com o dono resolvido no `DependentService` (403 para dependente de outro cliente)
- E-mail: `MailService.send` (Thymeleaf, síncrono, registra a falha no log e não lança). O chat usa `ApplicationEventPublisher`. Não existe nada agendado (`@Scheduled`) no repositório
- Superfícies `/me` com o dono resolvido pelo `Authentication` e o tipo conferido no serviço: `/api/drivers/me/unlinked-passengers`, `/api/drivers/me/service-areas`, `/api/clients/me/address`

## Goals / Non-Goals

**Goals**

- A negociação inteira (pedir, propor, versionar, aceitar, recusar, pedir ajuste, retirar, expirar) existe na API, com o estado de cada passo garantido pelo banco onde houver corrida
- O aceite fica registrado sobre uma versão específica, com data, e a fase 3 só precisa acrescentar a geração do contrato nesse mesmo ponto
- Nenhuma regra nova depende do chat nem do push

**Non-Goals**

- Leitura das negociações pelo admin: nenhuma permissão nova nesta fase. O suporte entra com o painel do admin
- Mexer no chat (#161) além de quem abre a conversa (D23): cartões e bloqueio ficam para a fase 6

## Decisions

### D1 — Duas features, superfícies `/me`, nenhuma permissão nova

`br.com.vanep.servicerequest` e `br.com.vanep.proposal`, com os subpacotes da regra 5. A proposta aponta para a solicitação, nunca o contrário: a solicitação não guarda versões nem termos. O serviço da solicitação só toca a proposta para fechar a `SENT` do par quando uma solicitação `ANSWERED` é retirada ou recusada, pela escrita condicional do `ProposalRepository` (D5).

Cada parte usa a sua superfície (`/api/clients/me/...` e `/api/drivers/me/...`) com `@PreAuthorize("isAuthenticated()")` e o `UserType` conferido no serviço (403), o mesmo padrão do aluno não vinculado. O token na URL é resolvido **dentro do conjunto do chamador**: token de outra pessoa é 404, sem revelar que existe.

Rejeitado: permissões nos bundles `CLIENT` e `DRIVER` mais um `@sec.isServiceRequestParty`. Cada ação tem um único autor possível (só o cliente aceita, só o motorista propõe), então a permissão diria só o tipo do usuário, que o serviço já confere. Seriam seis permissões, uma migration de bundles e três métodos novos no `@sec` para chegar ao mesmo resultado.

### D2 — Solicitação aberta é `OPEN` ou `ANSWERED`, e a vez de cada um é o status

O spike desenha a solicitação linear (`OPEN → ANSWERED → CONVERTED`). Esta change usa os dois estados abertos para dizer **de quem é a vez**:

| Status | Esperando | Prazo |
|---|---|---|
| `OPEN` | o motorista mandar uma proposta | `expires_at` (72 h) |
| `ANSWERED` | o cliente responder à proposta `SENT` | `valid_until` da proposta |

```
OPEN ──proposta──→ ANSWERED ──aceite──→ CONVERTED
 ↑                    │
 └─retirada/expirou───┘          (expires_at = agora + 72 h)
OPEN, ANSWERED ─→ DECLINED (motorista) | WITHDRAWN (cliente)
OPEN ─→ EXPIRED
```

Quando a proposta vigente é retirada pelo motorista ou expira, a solicitação volta a `OPEN` com um novo prazo de 72 h. Assim nenhuma solicitação fica parada em `ANSWERED` sem proposta, e uma negociação abandonada sempre termina em `EXPIRED`. Recusar a proposta encerra a solicitação (`WITHDRAWN`): o caminho de quem quer outro valor é o pedido de ajuste (D7).

**Uma aberta por par** é um índice único parcial `(client_driver_id) where status in ('OPEN', 'ANSWERED')`. A corrida entre duas solicitações do mesmo par esbarra nele e vira `409 service_request.open_exists`, como no `writeKeepingOneActiveContract` do contrato.

**Até 5 abertas por cliente** é regra do serviço (`count` antes de gravar). Duas solicitações simultâneas do mesmo cliente para motoristas diferentes podem passar juntas e chegar a 6. Aceito: o limite é contra spam, não um invariante (R4).

**Par com contrato assinado e não encerrado não abre solicitação** (`422 service_request.link.has_contract`). Um irmão novo ou uma mudança nesse par é aditivo, que é da fase 8 (`proposal.source_contract_id`).

### D3 — O pedido é uma grade por dia; o horário é da proposta

Existem dois "turnos" diferentes, e cada parte diz o seu:

| | Quem diz | Onde fica |
|---|---|---|
| período escolar de cada dia (`SchoolShift`: matutino, vespertino, noturno, integral) | o cliente, uma vez, no dependente | `dependent_school_day` (D16) |
| em quais dias precisa de ida e de volta | o cliente, em cada pedido | `service_request_day`, uma linha por dia pedido, com o período copiado da grade |
| turno da rota (`OperationShift`) e janela de embarque de cada (dia, trecho) | o motorista | `schedule` + `schedule_slot` da proposta, o mesmo modelo do `contract_item` |

O período escolar muda de um dia para outro: o aluno pode ser integral só às terças e quintas, e a volta às 17h cai numa rota diferente da volta ao meio-dia. Esse período é um fato da criança, igual para qualquer motorista, e por isso mora no dependente (D16). Já a necessidade de van muda de um arranjo para outro (a ida com um motorista e a volta com outro, "às quartas a mãe busca"), e por isso é do pedido.

`service_request_day` tem `weekday`, `school_shift`, `needs_outbound` e `needs_return`, com um dia por item (`unique (service_request_item_id, weekday)`) e `check (needs_outbound or needs_return)`: um dia sem trecho não é pedido, é ausência de linha. O cliente manda só o dia e os trechos; o `school_shift` é copiado da grade do dependente no envio, e pedir um dia que não está na grade é `422 service_request.day.no_school`. A cópia segue a mesma lógica da escola e do bairro (D4): o motorista precificou o período daquele momento.

O cliente não informa horário de embarque nem turno da rota. Mudar o horário depois de contratado é aditivo (fase 8), proposto pelo motorista.

A proposta usar o mesmo modelo do contrato é o que permite à fase 3 copiar o item aceito para o contrato sem conversão. A `ScheduleSlotPolicy` vale para a proposta como vale para o contrato. O app pode pré-preencher o formulário da proposta a partir da grade (cada dia com ida ou volta vira um horário a preencher), mas a proposta não é obrigada a repetir a grade: o motorista pode oferecer menos trechos, e o cliente decide.

Rejeitado: `schedule_id` no `service_request_item`, como no spike. `schedule_slot` exige `shift` e `window_start`, que são a resposta do motorista, e não tem onde guardar o período escolar.

Rejeitado: tornar `shift` e `window_start` anuláveis em `schedule_slot`. As tabelas do núcleo não mudam (D3 da `contract-core`), e a agenda do contrato perderia a garantia de ter janela.

Rejeitado: a grade como `jsonb` no item. É a mesma escolha que o spike critica no `driver.work_days` (A3): sem `CHECK`, sem unicidade por dia, e o formato só existe no código.

### D4 — Foto na solicitação e visão restrita do motorista (LGPD art. 14)

Cada item grava, no envio, a foto do que o motorista vai precificar (`snapshot jsonb`, D19): o nome e a data de nascimento do dependente, o nome da escola, e a cidade (com a UF) e o bairro (`neighborhood`, D20) do endereço dele. Os ids (`dependent_id`, `school_id`, `pickup_city_id`) ficam ao lado, como colunas simples, para navegar e para a reconferência do aceite; a grade do D3 fica como foi enviada. As duas visões mostram o dependente, a escola, a cidade e o bairro da foto, nunca do cadastro: se o cliente editar, remover ou restaurar o dependente depois, ou o admin remover a escola, a solicitação continua inteira e dizendo o que foi pedido, e o cartão do chat que aponta para ela (fase 6) sempre abre. Não há rua, número, CEP nem `google_place_id`: o endereço exato só chega ao motorista com o contrato (fase 3).

A visão do motorista é um DTO próprio, e não o DTO do cliente com campos nulos: primeiro nome, idade em anos completos (calculada na leitura a partir do nascimento da foto), escola, cidade e bairro, e a grade por dia. O primeiro nome do cliente também. Um DTO separado impede que um campo novo no DTO do cliente vaze para o motorista por descuido.

Ponto de embarque diferente do endereço do dependente ("casa da avó") fica para quando o endereço exato for para o contrato (Q2).

### D5 — Proposta imutável, versionada por solicitação, com a corrida decidida no banco

A proposta nunca é editada. Cada mudança é uma linha nova:

- `version` começa em 1 e é única por solicitação (`unique (service_request_id, version)`); `parent_proposal_id` aponta para a proposta anterior da mesma solicitação
- **uma `SENT` por par:** índice único parcial `(client_driver_id) where status = 'SENT'`. É ele que garante que só a versão vigente pode ser aceita (spike, "Regras do modelo")
- as transições saem de `SENT` por **escrita condicional**: `update proposal set status = :to where id = :id and status = 'SENT'`, e o serviço confere que uma linha mudou. Aceite, substituição, retirada, recusa e expiração passam por ela. Se o aceite e uma versão nova chegam juntos, só um encontra a proposta `SENT`; o outro recebe 409. É o equivalente, sem lock, ao teste de concorrência que o spike pede, e o QA da regra 55 dispara os dois ao mesmo tempo
- a versão nova primeiro move a anterior para `SUPERSEDED` (escrita condicional) e só depois insere a nova; se duas versões chegam juntas, a segunda esbarra no índice e recebe `409 proposal.sent_conflict`
- **a solicitação vai primeiro:** toda escrita que muda a solicitação e a proposta juntas (retirar ou recusar a solicitação; aceitar, recusar ou retirar a proposta; mandar versão nova; expirar) faz antes a escrita condicional na solicitação, mesmo quando ela continua `ANSWERED`. A linha fica travada até o commit, então duas ações do mesmo par se enfileiram nela: não há deadlock, e uma versão nova nunca nasce numa solicitação que acabou de ser encerrada

O par fica no `proposal.client_driver_id` mesmo sendo dedutível da solicitação, porque o índice único precisa dele na própria tabela.

**Os itens cobrem exatamente os dependentes da solicitação** (`422 proposal.items.mismatch`). O motorista que não tem vaga para um dos irmãos recusa ou conversa no chat; aceitar uma proposta parcial deixaria o cliente fechando por um dependente que não pediu sozinho.

**O veículo oferecido fica na foto da proposta** (`snapshot jsonb`, D19): placa, marca, modelo, ano, cor e capacidade, gravados no envio a partir do veículo ativo do próprio motorista, com `vehicle_id` ao lado como coluna simples. O motorista pode editar, remover e restaurar o veículo; a proposta continua mostrando a van que foi oferecida, e o aceite confere se ela ainda é a mesma. O `proposal_item` guarda o dependente só por id, e o nome vem da foto do item da solicitação. `parent_proposal_id`, `source_contract_id` e `resulting_contract_id` também são colunas simples: o contrato tem soft delete.

**O aceite não cria o contrato nesta fase.** A proposta vira `ACCEPTED` com `accepted_at`, a solicitação `CONVERTED`, e o vínculo não muda. A fase 3 gera o contrato na mesma transação do aceite e preenche `resulting_contract_id`, que nasce nulo aqui, assim como `source_contract_id` (aditivo e renovação, fase 8). As duas colunas nascem agora pela mesma razão do enum completo do contrato: acrescentar coluna depois custa migration e revisão de todos os consumidores.

### D6 — Os termos seguem as regras do contrato

O que a proposta aceita vira contrato na fase 3, então ela não pode aceitar o que o contrato recusaria:

| Regra | Onde |
|---|---|
| faixas de `total_amount`, `installments` (1 a 12), `due_day` (1 a 28), `enrollment_fee` (≥ 0), `monthly_amount`, `valid_days` (1 a 7) | Bean Validation no DTO (400), como no `ContractCreateRequestDTO`, e `CHECK` no banco |
| ordem das datas e teto de 12 meses | a mesma `ContractTermsPolicy`, com as chaves `proposal.period.*` (422) |
| agenda | a mesma `ScheduleSlotPolicy` (400) |

**Menus fechados** (spike, "multa, reajuste e rescisão devem ser opções de um menu fechado"). Valores provisórios até a revisão jurídica (E2), com o texto da cláusula escrito no template da fase 3:

| Enum | Valor | Significado |
|---|---|---|
| `ProposalAdjustmentPolicy` | `NONE` | preço fixo no período; a renovação negocia de novo |
| | `IPCA_AT_RENEWAL` | preço fixo no período; a renovação corrige pelo IPCA |
| `ProposalCancellationPolicy` | `NOTICE_30_DAYS` | aviso de 30 dias, sem multa |
| | `NOTICE_30_DAYS_WITH_FEE` | aviso de 30 dias e multa de 10% do saldo, limitada a uma parcela (pergunta jurídica 2) |

Como o contrato tem no máximo 12 meses, não existe reajuste dentro do período: os dois valores de reajuste só diferem na renovação.

**Validade** é `valid_days` (1 a 7, padrão 7), convertida em `valid_until` no envio. O spike diz "7 dias, configurável pelo motorista"; o teto em 7 evita oferta presa por semanas.

**Taxa de reserva** é opcional e não é conciliada com o total, como o `monthly_amount` do item não é conciliado com o total do contrato (R6 da `contract-core`): o resumo da fase 3 mostra os dois.

### D7 — O pedido de ajuste é uma tabela

O spike descreve o "pedir ajuste" como um cartão estruturado no chat, mas o chat só mostra o que existe fora dele (D1 do spike). Esta change cria `proposal_adjustment` (`proposal_id`, `field`, `suggested_value`, `note`, `created_at`):

- `field` é `ProposalAdjustmentField` (`PRICE`, `SCHEDULE`, `PERIOD`, `INSTALLMENTS`, `OTHER`); `suggested_value` é texto curto ("320,00", "só ida às quartas"), porque a sugestão de cada campo tem um formato diferente e o motorista só a lê
- a proposta continua `SENT`; o motorista responde mandando a próxima versão
- sem soft delete e sem edição: é registro do que foi pedido. A fase 6 desenha o cartão a partir dela

### D8 — Prazos: uma varredura agendada e a conferência nas escritas

**Uma varredura só para todos os prazos do módulo.** `@EnableScheduling` numa classe de `config`, ligada por `vanep.expiry-sweep.enabled`. Um `ExpirySweepService` em `br.com.vanep.shared.service` tem um único método `@Scheduled(fixedDelayString = "${vanep.expiry-sweep.interval}")` que roda os passos em ordem. O nome não amarra na negociação porque as fases seguintes acrescentam passos a ele em vez de criar outra agenda: a assinatura sem resposta em 48 h (fase 4) entra aqui. Os prazos por data (início e fim do contrato, lembrete de renovação, fase 8) não precisam de minutos e entram numa agenda diária própria, com o cron também vindo do ambiente. O módulo fica com no máximo duas agendas, na thread única do agendador do Spring.

| Propriedade | Valor | Teste |
|---|---|---|
| `vanep.expiry-sweep.enabled` | `${EXPIRY_SWEEP_ENABLED:true}` | `false` em `application-test.properties` |
| `vanep.expiry-sweep.interval` | `${EXPIRY_SWEEP_INTERVAL:PT1M}` | — |
| `vanep.expiry-sweep.batch-size` | `${EXPIRY_SWEEP_BATCH_SIZE:200}` | — |

Nada fixo no código (regras 1 e 3): o intervalo e o teto mudam pelo `.env` sem recompilar, e as três variáveis ficam no `.env.example`.

Os passos desta change, nesta ordem:

1. `ProposalService.expireOverdue(now, limit)`: busca as propostas `SENT` com `valid_until <= now` (índice parcial), das mais antigas para as mais novas, até `limit`, e expira cada uma pelo mesmo caminho da retirada, que devolve a solicitação a `OPEN` com novo prazo;
2. `ServiceRequestService.expireOverdue(now, limit)`: busca as solicitações `OPEN` com `expires_at <= now` (índice parcial), das mais antigas para as mais novas, até `limit`, e expira cada uma.

**Teto por rodada.** Cada passo processa no máximo `batch-size` linhas; o que sobrar fica para a próxima rodada, um intervalo depois. A busca é `order by` o prazo com `limit`, servida pelo mesmo índice parcial, então quem venceu primeiro expira primeiro e nenhuma linha fica para trás. O teto limita a memória e a duração de uma rodada quando muitos prazos vencem juntos (a API volta depois de horas fora, ou o pico de matrícula), para que uma rodada longa não segure os outros passos na thread única do agendador. Uma linha que ficou para a rodada seguinte não muda a correção, pelo mesmo motivo do intervalo: as escritas conferem o prazo por conta própria.

Cada linha roda na própria transação, por escrita condicional: se a parte agir no mesmo instante, quem chegar primeiro vence, e a varredura só pula a linha. Uma linha que falha por outro motivo também é pulada, com `log.warn`, e volta na rodada seguinte: como a busca é pelo prazo mais antigo, deixar o erro subir faria essa linha encabeçar toda rodada e travar as que vêm atrás dela. A ordem importa: a proposta vencida primeiro reabre a solicitação, que ganha 72 h novas em vez de expirar junto.

**Custo.** Cada passo é uma consulta num índice parcial que só contém as linhas abertas; sem nada vencido, a rodada custa menos de 1 ms. O intervalo não decide a correção (as escritas conferem o relógio, abaixo), só por quanto tempo uma listagem mostra `SENT` para algo já vencido.

O `ExpirySweepService` mora em `shared` porque orquestra prazos de mais de uma feature (regra 5). Ele só chama os métodos `expireOverdue` de cada serviço; a regra de cada prazo continua no serviço dono dela.

**As escritas não dependem da varredura.** Aceitar confere `valid_until` e mandar proposta confere `expires_at` (422/409 dos specs). A leitura pode mostrar `SENT` por até um intervalo depois do vencimento; isso é aceito.

Rejeitado: só expirar ao tocar (sem job). A unicidade de "uma aberta por par" e o limite de 5 contam linhas que ninguém mais tocaria, e cada leitura e listagem teria de lembrar de expirar antes.

Rejeitado: `ShedLock` ou lock distribuído. O deploy do `cd.yml` roda uma instância só, e a escrita condicional já torna a varredura repetida inofensiva (R7).

### D9 — Origem do vínculo

`LinkOrigin` (`CLIENT_SEARCH`, `DRIVER_INVITE`) em `clientdriver.enums`, coluna `origin varchar(16) not null`. Nasce com os dois valores, embora só o `CLIENT_SEARCH` seja gravado pelo fluxo desta fase: o convite é da fase 7, e o admin já pode criar `DRIVER_INVITE` para os casos reais.

- **Sem caminho de escrita depois da criação** (spike, "Regras do modelo"): `origin` só existe no DTO de criação do admin e na criação pela solicitação. `@Column(updatable = false)` no model, para que nem um `save` acidental a mude
- **O vínculo nasce na primeira solicitação**, na mesma transação, em `PENDING`. O método fica no `ClientDriverService` (`findOrCreateForRequest`), porque criar vínculo é regra do vínculo. A corrida de duas primeiras solicitações do mesmo par esbarra no `client_driver_pair_active_key` e vira 409
- **Vínculos existentes recebem `CLIENT_SEARCH`** na `V61`. Não há dados de produção (só bancos locais de desenvolvimento, R2 da `contract-core`), e a busca é a única porta que existe hoje

### D10 — Bloqueio com direção

`client_driver.blocked_by` (FK para `users`, como o `driver.reviewed_by`) e `blocked_at`, com `check ((status = 'BLOCKED') = (blocked_by is not null))` e o mesmo para `blocked_at`. O banco não aceita bloqueio sem dono, que é o que a `client-driver-link` deixou em aberto (Q3 de lá).

- **Bloquear** (`@sec.isClientDriverLinkParty`, já existente): recusado com contrato `SIGNED_AND_NOT_ENDED`, porque a relação com contrato termina pelo contrato (rescisão, fase 8). Encerra a negociação aberta do par na mesma transação, com o status que diz quem desistiu (tabela no spec). O admin não bloqueia: não é parte
- **Corrida com uma solicitação nova:** o envio da solicitação trava a linha do vínculo (`lockByPair`, `select ... for no key update`) antes de conferir se está bloqueado, e o bloqueio escreve nela antes de encerrar a negociação. Os dois se enfileiram na linha: um envio que chega depois do bloqueio vê `BLOCKED` e recebe 422; um bloqueio que chega depois do envio encontra a solicitação e a encerra
- **Desbloquear**: só quem bloqueou. Limpa as colunas e recalcula o status pela `LinkStatusPolicy` passando o status atual como `PENDING` (a policy mantém `BLOCKED` se receber `BLOCKED`)
- **Resposta**: `blockedBy` como `CLIENT` ou `DRIVER`, calculado comparando `blocked_by` com o usuário de cada parte. O app mostra "Desbloquear" só a quem pode
- **Vínculos `BLOCKED` sem dono** (escritos pelo `PATCH` que a `contract-core` removeu) voltam na `V61` ao status derivado dos contratos, com a mesma regra SQL da `V60`. Sem isso o `CHECK` falharia, e ninguém conseguiria desbloquear esses pares

O bloqueio não alcança o chat (#161), que é por par e independente do `client_driver`. Fica para a fase 6, com o resto do chat.

### D11 — Avaliação exige contrato que já esteve ativo (E3)

`ContractStatus.REACHED_ACTIVE = {ACTIVE, SUSPENDED, ENDED, TERMINATED, SUPERSEDED}`. Pela `ContractTransitionPolicy`, esses são exatamente os status que só se alcançam passando por `ACTIVE` (`CANCELLED` sai de `AWAITING_SIGNATURES` ou `SIGNED`). Sem `contract_event` (fase 8), o status atual é a única memória de que o contrato rodou, e ele basta.

Uma policy pura no `clientdriver` (`LinkRatingEligibilityPolicy`), porque a regra é do vínculo e as duas avaliações a usam (regra 5). Ela recebe os status de `ContractRepository.findStatusesByClientDriverId`, que já ignora contratos removidos (`@SoftDelete`). O `DriverRatingEligibilityPolicy` troca o `isLinkActive` por ela e mantém a idade mínima de cinco minutos; o `ClientRatingService` passa a conferir a mesma regra.

A chave `driver_rating.link.not_active` sai dos `messages*.properties` (regra 34) e entram `driver_rating.link.never_contracted` e `client_rating.link.never_contracted`.

### D12 — Prontidão contratual

`ContractReadinessPolicy` pura: recebe os campos do usuário cliente e dos dependentes e devolve a lista do que falta, na ordem da tabela do spec. Usada pelo `GET /api/clients/me/contract-readiness` e pela criação da solicitação, que responde `422 service_request.not_ready` sem a lista: o app consulta a prontidão antes de mostrar o botão de envio (D4 do spike).

Nome e e-mail não entram: são obrigatórios no cadastro. O turno também não: o período de cada dia vem da grade (`SCHOOL_DAYS`). O que falta do motorista para o contrato (autorização escolar, A5) é da fase 3.

**A consulta entra no PR 3**, com a policy e a grade, e não no PR 7. Ela depende só do cadastro (usuário, dependentes e grades), e o app (`dependents-redesign` no vanep-mobile) mostra o "Cadastro incompleto" antes de a solicitação existir. `dependentTokens` é opcional: sem ele, só os dados do cliente são conferidos, e um token repetido conta uma vez. Os dependentes e as grades são lidos em uma consulta cada, qualquer que seja o número de dependentes (regra 17).

A prontidão confia no `dependent.address_id`. Remover o dependente apagava o endereço de embarque, mas o `address_id` ficava gravado, porque o Hibernate descarta o update pendente de uma entidade que vai remover; depois do `restore`, o dependente apontava para um endereço apagado e passava por pronto. A remoção passa a gravar a limpeza antes do soft delete.

### D13 — Disponibilidade na busca

O filtro `driver.available = true` entra nas consultas que **ordenam** a busca (`findDriverMatchesInCity`, `findDriverMatchesCoveringPoint`) e na lista de recomendados, e não só no carregamento da página. Hoje o total da página é o tamanho da lista ordenada; filtrar depois deixaria o total contando quem não aparece.

O perfil (`findSearchableByToken`) não muda: o cliente que tem o link do motorista continua vendo o perfil, com `available = false`, e a solicitação é recusada no serviço.

**A aprovação liga a disponibilidade.** `DriverOnboardingService.approve` grava `is_available = true` junto com `APPROVED`. A coluna continua nascendo `false`, porque o motorista em análise ou recusado não aceita alunos. Ser aprovado já diz que o motorista quer trabalhar; desligar fica para quem lotou, que é a exceção.

Rejeitado: manter `false` até o motorista marcar. Todo motorista recém-aprovado sumiria da busca, e o PR 1 dependeria de o app ter a tela do controle antes do merge.

`base_price` continua com esse nome no JSON (A2): trocar para `startingPrice` quebraria o app sem mudar o que o dado significa. O "a partir de" é texto da tela.

### D14 — E-mail por evento, depois do commit

Os serviços publicam eventos de domínio (records em `servicerequest.event` e `proposal.event`, como o evento do chat em `conversation.event`) com `ApplicationEventPublisher`. Um `NegotiationEmailService` em `proposal.service` escuta com `@TransactionalEventListener(phase = AFTER_COMMIT)` e chama o `MailService` com um template por evento em `templates/email/`.

- **Depois do commit**: um 409 ou um rollback não manda e-mail. O `MailService` já registra a falha sem lançar, então o e-mail nunca derruba a requisição
- **O push entra como outro ouvinte** dos mesmos eventos, na fase final, sem tocar nos serviços
- os eventos carregam tokens, e o ouvinte relê o que precisa, já com a visão restrita (D4) no e-mail ao motorista

O envio continua síncrono, como o e-mail de aprovação do motorista. A latência de um SMTP lento cai na requisição já commitada (R8).

### D15 — Sem soft delete nas tabelas da negociação

Solicitação, proposta e seus filhos não são removíveis: o fim de cada um é um status, e o histórico é a prova do que foi pedido e oferecido. Por isso não têm `deleted_at` nem `restore`, e a regra 19 não se aplica. Os índices únicos são parciais por status, não por `deleted_at`. A agenda da proposta mora em `schedule`, que tem soft delete, mas nunca é removida por esta change.

### D16 — Grade escolar no dependente

`dependent_school_day` (`dependent_id`, `weekday`, `school_shift`, timestamps, `deleted_at`), uma linha por dia com aula, `unique (dependent_id, weekday) where deleted_at is null`. Dia sem aula é ausência de linha.

- **Sub-recurso do dependente**, lido e substituído como conjunto: `GET` e `PUT /api/dependent/{token}/school-days`, com `show_dependent` e `update_dependent` e o dono resolvido pelo mesmo `findActiveForAccess` do `DependentService`. É o `PUT` de sub-recurso que a regra 16 permite, como `PUT /api/clients/me/address`, e o mesmo modo de edição da agenda (D2 da `contract-core`): substituir apaga as linhas antigas (soft delete) e grava as novas, numa transação. A linha não tem token nem rota própria
- **Uma tabela, não sete colunas nem `jsonb`**, pelos mesmos motivos do D3: o banco garante um dia por dependente, e o código trabalha com uma lista tipada
- **`dependent.shift` sai** na `V62`, do model e do JSON do dependente. A grade é a única fonte do período: um turno único podia contradizê-la, e nada o lia (fora do CRUD do dependente, nem a API, nem o app, nem o frontend). Os valores gravados não viram grade, porque uma grade de segunda a sexta afirmaria dias que ninguém informou. **BREAKING** no JSON do dependente, sem consumidor
- **Remover e restaurar o dependente** não mexe na grade: as linhas ficam, escondidas junto com o dependente, e voltam com o `restore`
- **A prontidão cobra a grade** (`SCHOOL_DAYS`): sem ela, a solicitação não tem de onde tirar o período de cada dia
- A ausência (VAN-13) e o aditivo por mudança de turno (fase 8) podem consultar a grade depois; esta change não os altera

Rejeitado: manter o `dependent.shift` como período padrão para pré-preencher a grade. O pré-preenchimento é um atalho da tela (escolher um período para a semana inteira), não um dado guardado ao lado da grade.

Rejeitado: colocar o período por dia só na solicitação. O responsável que pede orçamento a até 5 motoristas repetiria a grade em cada pedido, e o dado da criança ficaria espalhado em pedidos em vez de no cadastro dela.

### D17 — Dependente em negociação aberta ou com contrato em andamento fica travado

O app passa a ter "Remover dependente". Remover (soft delete) um dependente que está numa solicitação `OPEN` ou `ANSWERED` deixaria a negociação seguir até o aceite de quem saiu do cadastro. Com contrato é pior, e já acontece na `main`: o `contract_item` aponta para o dependente por associação, o Hibernate esconde a linha removida em toda junção, e o contrato passa a ser lido sem o item e a rota do motorista deixa de listar a criança. Depois de a negociação fechar, a remoção não apaga o histórico: o item lê o nome e a idade da cópia (D4).

| Enquanto o dependente estiver | Recusa com 422 | Mensagem |
|---|---|---|
| num item de solicitação `OPEN` ou `ANSWERED` | `DELETE /api/dependent/{token}` | `dependent.open_negotiation` |
| num item de contrato `AWAITING_SIGNATURES`, `SIGNED`, `ACTIVE` ou `SUSPENDED` | `DELETE`; `PATCH` que mande `schoolToken` ou `address`; `PUT .../school-days` | `dependent.open_contract` |

- A proposta `SENT` só existe com a solicitação `ANSWERED`, então os dois status cobrem a negociação inteira. Durante a negociação, escola, endereço e grade continuam editáveis: o aceite reconfere (D19)
- Com contrato em andamento, escola, endereço e grade são o objeto do contrato, e mudá-los é aditivo (fase 8). Nome, nascimento, documento, telefone e e-mail continuam editáveis. A trava vale para quem manda o campo, mesmo com o valor de hoje (o app só manda o que a pessoa mexeu), e vale também para o admin
- As duas consultas ficam no `DependentRepository`, nativas (`exists` em `service_request_item` com `service_request.status in ('OPEN', 'ANSWERED')`; `exists` em `contract_item` com `contract.status in ('AWAITING_SIGNATURES', 'SIGNED', 'ACTIVE', 'SUSPENDED')`, sem itens nem contratos removidos), para que o `dependent` não dependa de tipos do `servicerequest` nem do `contract`; a dependência continua só deles para o `dependent`
- **Corrida:** a remoção, a edição e a troca da grade travam a linha do dependente (`lockByToken`, o mesmo do D16), e o envio da solicitação e a criação do item de contrato (`ContractService.createItem`) também. Uma remoção e um envio, ou uma remoção e um contrato novo, do mesmo dependente nunca se intercalam: quem chega depois vê o resultado do outro
- Depois de retirar, recusar ou expirar a solicitação, ou de o contrato terminar (`ENDED`, `TERMINATED`, `CANCELLED` ou `SUPERSEDED`), a remoção volta a passar. O contrato encerrado ainda é lido sem o item do dependente removido até a fase 3 (R14)
- Restaurar não é travado: com a remoção travada, o dependente de um contrato em andamento nunca está removido, e restaurar só o traz de volta

Rejeitado: retirar as solicitações abertas ao remover o dependente. Encerraria a negociação sem o responsável pedir, e dependeria dos métodos de encerramento do PR 10.

Rejeitado: deixar a trava por contrato para a fase 5, como no spike. Qualquer responsável já tira a criança da rota pelo app, e a trava é o mesmo tipo de consulta, no mesmo ponto do serviço, que a da negociação.

Rejeitado: recusar só quando o valor muda. O app já manda só o que mudou, e comparar o endereço exigiria resolver o catálogo antes de recusar.

### D18 — CPF do dependente e tipos de escola, alinhados ao app

O app (`dependents-redesign`) confere o CPF do dependente e busca escolas no Places por tipo. O back acompanha as duas regras, para não aceitar o que o app recusa nem recusar o que o app mostra:

- **CPF do dependente:** `document`, quando presente, tem de ser um CPF válido (dígitos verificadores), como no cadastro do usuário; senão, 400 `dependent.document.invalid`. Ele é gravado só com os dígitos, então `529.982.247-25` e `52998224725` são o mesmo documento na checagem de duplicidade. A anotação `@Cpf` e o `CpfValidator` saem de `auth.validation` para `shared.validation`, porque passam a servir a duas features (regra 5). Os documentos já gravados não são revalidados; só uma escrita nova passa pela regra
- **Tipos de escola no resolve:** o app filtra a busca por `school`, `primary_school` e `secondary_school`, e o `SchoolResolveService` aceitava só `school` e `educational_institution`. Ele passa a aceitar os quatro, para que um lugar que o app mostra nunca seja recusado ao salvar

### D19 — O compromisso se basta: colunas para a regra, foto para o que foi visto

Solicitação, proposta e, na fase 3, contrato são registros de compromisso: tudo o que foi pedido, oferecido ou aceito precisa ser mostrado a partir deles, como foi visto, mesmo depois de o cadastro mudar. O cadastro muda por correção, por engano ou de má-fé (o cliente que pede orçamento num bairro e se muda para longe antes de aceitar; o motorista que oferece uma van e troca o cadastro dela depois), e o dependente, o veículo e a escola têm soft delete e `restore`.

| O quê | Onde | Por quê |
|---|---|---|
| ids, status, prazos, termos e valores | colunas, com `CHECK`, FK e índice | o banco garante a regra, e o serviço consulta e compara |
| o que cada parte viu (dependente, escola, cidade e bairro; veículo) | `snapshot jsonb not null`, um `record` por tabela, escrito uma vez (`updatable = false`) e lido inteiro | é foto, não regra: cresce sem migration e não depende de junção com cadastro removível |
| a grade pedida por dia | tabela (`service_request_day`, D3) | tem regra: um dia por item e o `CHECK` do trecho |

- **Copia o que as partes editam e que define o acordo; referencia o que é cadastro do sistema.** O id fica como coluna simples, sem associação JPA para tabela com soft delete: o Hibernate esconde a linha removida em toda junção (com `join fetch` a solicitação sumia; com `left join fetch`, `FetchNotFoundException`)
- **A foto tem versão** (`version` no JSON) e leitura tolerante: campo novo é opcional, campo antigo nunca sai. Um `CHECK` garante as chaves essenciais
- **Nunca se filtra nem se ordena por campo da foto**: ela só é lida inteira, e o H2 dos testes não tem os operadores do `jsonb`. Busca por campo é coisa de suporte, direto no PostgreSQL
- **O JSON do banco usa o `JsonMapper` do Spring** (`config.HibernateJsonFormatMapper`). Sem ele, o Hibernate 7.2 escolhe o Jackson 2 e grava datas como `[2016,4,2]`
- **O aceite reconfere** (PR 8). A foto prova o que foi combinado, mas o contrato da fase 3 vai operar com o cadastro de agora. Com os dependentes e o veículo travados, o aceite confere que nada que pesa no preço mudou: dependentes ativos, com a escola, a cidade e o bairro da foto; cada dia pedido ainda na grade, com o mesmo período; o veículo ativo, do motorista, com a placa e a capacidade da foto; o motorista ativo e aprovado. Se algo mudou, `409 proposal.accept.stale`, e o caminho é uma nova versão ou uma nova solicitação. Corrigir nome, telefone ou nascimento não bloqueia
- **A trava fica para o contrato em andamento** (D17). Durante a negociação a reconferência basta e não impede a mudança legítima; com o contrato, remover o dependente ou trocar a escola, o endereço ou a grade vira aditivo (fase 8)
- **A prova final é o documento** (fase 3, D5 do spike): `contract_document.terms_snapshot` no mesmo formato, mais o PDF e o SHA-256
- **LGPD:** a foto guarda dado de criança. A base é o procedimento preliminar ao contrato a pedido do responsável (art. 7º, V) e o exercício regular de direitos (art. 7º, VI); o motorista continua vendo só o primeiro nome e a idade. Anonimizar as negociações que não viraram contrato fica para depois

Rejeitado: só a referência, lendo o cadastro inclusive o removido. A tela mostraria o cadastro de hoje, e uma edição de má-fé reescreveria o que foi combinado.

Rejeitado: uma coluna por campo copiado. Seriam duas aqui e seis ali, e cada campo novo uma migration, sem ganho de regra.

Rejeitado: versionar o cadastro (Hibernate Envers ou tabela de versões). Reconstrói qualquer momento, mas toda escrita gera versão e as leituras ficam complexas; é peso demais para o que a negociação precisa.

### D20 — O bairro mostrado é o `neighborhood`; o `district` fica só na busca

O `district` é a árvore de subdivisões da cidade que nasce do Google Places. Ela serve para casar a área de atuação do motorista com o lugar que a cliente busca (`GET /api/drivers/search` e `driver_service_area`): "atendo Taguatinga" encontra quem busca "Taguatinga Norte". Desde a `ibge-postal-address`, o endereço pessoal e o do dependente vêm do formulário por CEP e gravam `district_id = null`, com o bairro em texto no `address.neighborhood`. O contrato, os passageiros da rota e o endereço continuaram lendo o bairro do `district` e o mostravam vazio.

- **O bairro mostrado é sempre o `address.neighborhood`**: no endereço, no item do contrato (`pickup_neighborhood`, que ele já copiava), nos passageiros da rota e na foto da solicitação
- **O caminho por place também grava o `neighborhood`**, com o `sublocality` mais raso do lugar ("Vila Mariana" em São Paulo, "Taguatinga Norte" no DF). O nó mais fundo da árvore não serve: no DF ele é a quadra ("QI 21")
- **`address.district_id`, `contract_item.pickup_district_id` e `school.district_id` saem na `V63`**, que antes copia o nome do nó para o `neighborhood` vazio (o da escola vai para o endereço dela). `pickupDistrictName` sai do JSON do contrato e dos passageiros da rota, `district` sai do JSON dos endereços, e `districtName` e `districtToken` saem do JSON do endereço pessoal e da escola. **BREAKING**: os campos dos endereços e do contrato vinham sempre nulos, e o bairro da escola passa a vir do endereço dela (D21)
- **A solicitação não guarda id de bairro**: a foto guarda o `neighborhood`, e o aceite (PR 8) compara o bairro da foto com o do endereço de agora
- **Continuam:** a árvore, a busca de motorista e a área de atuação. A escola guarda o `google_place_id`, de onde a cadeia de nós pode ser montada de novo se uma regra vier a precisar dela

Rejeitado: ligar o endereço do CEP à árvore, procurando o nó pelo nome do bairro. O bairro dos Correios nem sempre tem o nome que o Google dá ao nó, e nada hoje precisa dessa ligação.

Rejeitado: manter as colunas nulas. Ficariam como convite para ler de novo um campo que nunca é gravado.

Rejeitado: manter o `school.district`. Nenhuma regra o lê, ele só aparecia no JSON da escola, e no DF o nó gravado é a quadra ("QI 21"), não o bairro.

### D21 — A escola ganha endereço no resolve

O `POST /api/schools/resolve` já busca o place da escola, com rua, número, CEP e bairro, mas gravava só a escola, a cidade e o `district`. O endereço da escola só nascia pelo CRUD do admin.

- **Criar a escola grava o endereço** a partir do mesmo place, na mesma transação e sem nova chamada ao Google: o `AddressPlaceResolverService` passa a montar o endereço a partir de um place já buscado. Sem rua no place, a escola nasce sem endereço, em vez de falhar como o `applyPlace` do admin, que continua exigindo a rua
- **A escola já conhecida e sem endereço se completa** quando o resolve a encontra. O place é buscado na chamada que já fechava a sessão do autocomplete, agora com os campos do endereço, sob o mesmo limite por usuário da criação; passado o limite, o resolve devolve a escola como está. Sem script de backfill
- **A resposta do resolve traz o endereço**, e o `school` do JSON do dependente também (`DependentSchoolDTO.address`), com os endereços das escolas lidos na mesma consulta dos endereços dos dependentes (regra 17)
- **A cidade da escola segue o endereço.** O `school.city_id` fica porque a escola cujo place não tem rua nasce sem endereço e só tem a cidade. Quando ela tem endereço, a cidade mora nos dois lugares, então o place novo que o admin grava no endereço da escola move também o `school.city_id`, e as duas nunca divergem

Rejeitado: backfill por script de todas as escolas. Gastaria uma chamada paga por escola, inclusive das que ninguém mais escolhe.

### D22 — A lista de solicitações traz a proposta vigente, e a parcela vem pronta

Pedido do app: os cards das listas (Propostas do cliente, aba do motorista, Início) mostram o valor, a validade e se há ajuste pedido. Sem isso na lista, o app faz uma chamada por card para ler a cadeia de propostas (N+1).

- **Cada solicitação traz `currentProposal`**, nas listas e no detalhe, nas duas visões: a proposta `SENT` da `ANSWERED` e a `ACCEPTED` da `CONVERTED`; nos outros status, `null`. O resumo leva token, versão, status, total, parcelas, `installmentAmount`, `validUntil`, `startsOn` (o início proposto pode não ser o pedido) e `hasPendingAdjustment` (a `SENT` tem pedido de ajuste; a versão nova responde os pedidos). Não leva dado do dependente, então serve igual à visão do motorista
- **Uma consulta por página**: projeção (`ProposalRepository.CurrentProposalRow`) com o `exists` do ajuste no mesmo `select`, só para as solicitações `ANSWERED` e `CONVERTED` da página. A projeção evita carregar `ProposalModel`, cujo vínculo é EAGER
- **`installmentAmount` = `totalAmount / installments`, centavos truncados**, na resposta da proposta e no resumo; a última parcela fica com o resto. O total segue sem conciliação com o `monthlyAmount` dos itens (R6 da `contract-core`): o desconto de irmão, a taxa abatida e o parcelamento diferente do número de meses continuam possíveis, e o app não faz conta de dinheiro
- **A visão do cliente traz `driverRating`**, a média que as avaliações já mantêm em `driver.rating`, lida junto com o motorista que a consulta da lista já busca
- **O filtro `status` aceita uma lista** (`?status=OPEN,ANSWERED` ou o parâmetro repetido), porque a aba "Em aberto" são dois status e "Encerradas" são quatro

Rejeitado: conciliar o total com a soma dos itens vezes os meses. Recusaria os casos que a R6 aceita de propósito, e "meses do período" é ambíguo quando o período não fecha em meses inteiros. Rejeitado também: o valor da versão anterior no resumo; a tela tirou a comparação.

### D23 — A solicitação abre a conversa

O spike decide que a solicitação é a primeira mensagem: o envio abre a conversa, e o responsável não conversa antes de pedir. O chat entregue (#161) deixava o cliente abrir a conversa direto do perfil, antes de qualquer solicitação (R5). Do lado do motorista, a conversa não dizia de que par era, e ele não tinha como abrir a conversa de um pedido.

- **O envio abre a conversa.** O `ServiceRequestService.send` chama o `ConversationService.findOrCreateForRequest(client, driver)` depois de gravar a solicitação, na mesma transação. A corrida já foi decidida antes, pelo índice de solicitação aberta do par: só uma solicitação de cada par chega a esse ponto, então o índice de par da conversa nunca decide uma corrida aqui. Uma solicitação recusada (400, 404, 409, 422) não abre conversa
- **A conversa é do par, não da solicitação.** Uma solicitação nova do mesmo par traz a mesma conversa, e ela continua aberta depois que a solicitação se encerra (decisão de 26/10). O bloqueio do par não corta a conversa nesta fase (fase 6)
- **`conversationToken` nas duas visões da solicitação**, nas listas e no detalhe, com uma consulta por página (`ConversationRepository.findTokensByClientIdInAndDriverIdIn`, filtrada pelos pares da página). Vem `null` só se o admin removeu a conversa do par
- **Da conversa para a negociação:** as duas listas aceitam `?conversationToken=`, combinável com `status`. O `ConversationService.findPairByToken` resolve o par, e o `ServiceRequestRepository.findPageByPairAndStatusIn` lista as solicitações dele, da mais nova para a mais antiga. A tela da conversa monta a linha do tempo da negociação com uma chamada só. O `conversationToken` é o único identificador que as duas partes têm: o motorista não recebe o token do cliente. A conversa de outro par e o token desconhecido devolvem página vazia, sem consulta; um 404 diria se a conversa existe. O `ConversationResponseDTO` não muda
- **Sai a abertura direta:** `POST /api/conversations`, `ConversationCreateRequestDTO`, `create_conversation` no `PermissionEnum` e no bundle `CLIENT` do seeder, e as mensagens `conversation.client.not_found` e `conversation.driver.not_found`. A `V66` tira a permissão dos bundles e abre a conversa dos pares que já têm solicitação. As conversas abertas antes, sem solicitação, ficam como estão (decisão de 26/10)

Rejeitado: `client_driver_id` na conversa. A conversa é do par e sobrevive ao vínculo (`V53`), e o vínculo tem soft delete (R13). Rejeitado também: manter o `POST` respondendo 422 sem solicitação, porque seriam dois caminhos para abrir a mesma coisa. E rejeitado o token da solicitação atual no `ConversationResponseDTO`: a conversa passaria a ler solicitações, e os dois pacotes dependeriam um do outro; uma conversa tem várias solicitações ao longo do tempo, e o campo ficaria velho a cada mudança de estado, ao contrário dos cartões da fase 6, que só apontam.

## Desvios do texto do spike

| Spike | Esta change | Por quê |
|---|---|---|
| Solicitação linear | `OPEN` ⇄ `ANSWERED` | D2: a negociação abandonada sempre expira |
| `service_request_item.schedule_id` e horários da escola | grade escolar no dependente (`dependent_school_day`) + `service_request_day` com ida/volta por dia | D3 e D16: o período muda de um dia para outro e é da criança; turno da rota e janela são do motorista |
| "Pedir ajuste" como cartão do chat | tabela `proposal_adjustment` | D7: o chat só mostra o que existe fora dele |
| índice `proposal (client_driver_id, created_at)` | único `(service_request_id, version)` | a cadeia é por solicitação |
| `start_date`, `end_date` | `starts_on`, `ends_on` | mesmos nomes do `contract` |
| Push nos passos da negociação | e-mail (D14); push numa fase final | decisão de 17/10 |
| `vehicle`, `driver` + autorização escolar na fase 2 | fase 3 | o contrato é quem cita a autorização (A5) |
| A2 e A3 na fase 2 | fora | A2 é texto da tela (D13); A3 mexe na janela de trabalho da trip e fica para uma change própria |
| Guardas no dependente contratado na fase 5 | trava por contrato no PR 6 (D17) | remover o dependente de um contrato já tira o item do contrato e a criança da rota |

## Risks / Trade-offs

| # | Risco | Mitigação |
|---|---|---|
| **R1** | A suíte roda em H2 sem Flyway: nenhum índice parcial nem `CHECK` desta change é exercitado em teste | QA da regra 55 em cada PR, com SQL contra o PostgreSQL da API e as corridas (solicitações do mesmo par, duas versões, aceite contra versão nova) disparadas em paralelo |
| **R2** | Entre esta change e a fase 3, a proposta aceita não vira contrato: o par fica `PENDING`, sem contrato e sem solicitação aberta, e pode negociar de novo | Não há usuários reais. A fase 3 gera o contrato no aceite; as propostas aceitas antes dela ficam sem contrato |
| **R3** | Motoristas aprovados antes do PR 1 têm `is_available = false` e, com o filtro, somem da busca | Não há dados de produção (R2 da `contract-core`): os bancos locais são recriados ou ligados com um `update`. Os motoristas do seeder ficam `true`. Uma migration só para isso deslocaria a `V61` a `V65` |
| **R4** | O limite de 5 solicitações abertas é do serviço e pode ser ultrapassado por envios simultâneos | É limite contra spam; uma solicitação a mais não quebra nenhum invariante |
| **R5** | O chat (#161) deixava o cliente falar com o motorista antes de qualquer solicitação, e a origem `CLIENT_SEARCH` podia ser gravada depois da troca de WhatsApp | Resolvido no PR 12 (D23): a conversa nasce com a solicitação, depois do vínculo e da origem |
| **R6** | Os números das migrations colidem com trabalho em paralelo: a `absence` (VAN-13) está aberta e renumera para depois da última da `main` | Esta change usa `V61` a `V65`. Reconfirmar antes de cada merge com migration; quem entrar por último renumera |
| **R7** | Varredura em mais de uma instância | Escrita condicional: a segunda instância só não encontra a linha `SENT`/`OPEN`. Hoje o deploy roda uma instância |
| **R8** | E-mail síncrono depois do commit atrasa a resposta quando o SMTP está lento | Mesmo comportamento do e-mail de aprovação. Se pesar, o ouvinte vira `@Async` sem mudar os serviços |
| **R9** | Os menus de reajuste e rescisão são provisórios até a revisão jurídica (E2) | Valores de enum gravados como texto; sem dados de produção, trocar um valor agora é uma migration de `update` |
| **R10** | `POST /api/client-drivers` passa a exigir `origin` | Só o admin chama; verificado em 03/10 que `vanep-mobile` e `vanep-frontend` não usam `/client-drivers`. Reconferir no PR 2 |
| **R11** | A avaliação de cliente fica mais restrita: hoje qualquer vínculo permite | É o objetivo do E3. As avaliações já gravadas não mudam |
| **R12** | A prontidão cobra a escola e a grade escolar, e o app não tem tela para nenhuma das duas: nenhum dependente cadastrado pelo app consegue solicitar | Os dependentes do seeder ganham grade no PR 3. O app precisa da escolha da escola (`POST /api/schools/resolve`, depois `schoolToken` no `PATCH`) e da tela da grade antes de expor o "Solicitar orçamento"; a prontidão diz exatamente o que falta (`SCHOOL`, `SCHOOL_DAYS`) |
| **R13** | O vínculo também tem soft delete, e a solicitação e a proposta apontam para ele por associação: um admin que remove o vínculo esconde as negociações do par | Só o admin remove vínculos, e raramente; os contratos já funcionam assim. Escola e cidade deixaram de ser risco: a tela lê da foto (D19) |
| **R14** | O `contract_item` também aponta para o dependente, a escola e a cidade por associação: removido um deles, o item some da leitura do contrato e da rota. O bairro é texto copiado (D20) | O dependente de contrato em andamento não sai (D17), e escola e cidade só o admin remove. A fase 3, que passa a montar o item a partir da proposta aceita, troca essas associações por ids simples e lê da foto (D19). Até lá, o contrato encerrado de um dependente removido, ou o que o admin restaura depois de o dependente sair, é lido sem o item |
| **R15** | A escola conhecida cujo place não tem rua nunca ganha endereço, mostra só a cidade, e cada resolve dela busca o place de novo (D20, D21) | O cache do `PlacesClient` segura as repetições próximas, e o limite por usuário vale para essa busca. Escola sem rua no Google é exceção; o admin pode gravar o endereço pelo CRUD |
| **R16** | O app da `main` abre a conversa pelo `POST /api/conversations`, que o PR 12 tira | Não há usuários reais (R2). A VAN-78 do app tira o "Conversar" do perfil e abre a conversa pelo `conversationToken`; o PR 12 entra na `main` junto com ela |

## Open Questions

| | Pergunta | Estado |
|---|---|---|
| **Q1** | Mostrar ao motorista, na proposta, a comissão embutida e quanto ele recebe (atualização de 17/10 do spike) | 🟡 Depende dos valores do E1 e do recolhimento (fase 9). Entra como campos só de leitura na visão do motorista, sem mudar os termos nem o aceite |
| **Q2** | Ponto de embarque diferente do endereço do dependente | 🟡 Quando o endereço exato for para o contrato (fase 3). Acrescentar um endereço alternativo ao item é aditivo |

## Migration Plan

| Migration | PR | Conteúdo |
|---|---|---|
| **V61** | 2 | `client_driver`: `origin` (preenche `CLIENT_SEARCH`, depois `NOT NULL`), `blocked_by` (FK `users`), `blocked_at`; vínculos `BLOCKED` sem dono voltam ao status derivado (mesma regra da `V60`); `CHECK`s do bloqueio; comments |
| **V62** | 3 | `dependent_school_day`; remove `dependent.shift` |
| **V63** | 3 | copia o nome do nó para o `neighborhood` vazio do `address` e para o `pickup_neighborhood` vazio do `contract_item`; copia o nome do nó da escola para o `neighborhood` vazio do endereço dela; remove `address.district_id`, `contract_item.pickup_district_id` e `school.district_id` (D20) |
| **V64** | 4 | `service_request`, `service_request_item`, `service_request_day` |
| **V65** | 5 | `proposal`, `proposal_item`, `proposal_adjustment` |
| **V66** | 12 | tira `create_conversation` de todos os bundles; abre a conversa de cada par que tem solicitação e não tem conversa (D23) |

Nenhuma migration aplicada é editada (regra 2). Nenhuma permissão nova (D1; a grade usa as permissões do dependente); a `V66` só tira a `create_conversation`. As tabelas novas seguem o padrão do repositório (`id bigint generated always as identity`, `created_at`/`updated_at timestamptz`). As da negociação não têm `deleted_at` (D15); a `dependent_school_day` tem, porque substituir a grade remove linhas (D16). Só `service_request` e `proposal` têm `token`, porque são as únicas expostas por token.

**Colunas e `CHECK`s:**

| Tabela | Colunas | `CHECK` |
|---|---|---|
| `dependent_school_day` | `dependent_id`, `weekday varchar(16)`, `school_shift varchar(16)`, `deleted_at` | — |
| `service_request` | `token`, `client_driver_id`, `status varchar(16)`, `desired_start_date`, `notes varchar(1000)`, `decline_reason varchar(500)`, `expires_at` | — |
| `service_request_item` | `service_request_id`, `dependent_id`, `school_id`, `pickup_city_id`, `snapshot jsonb` | `snapshot ?& array['version', 'dependent', 'school', 'pickup']` |
| `service_request_day` | `service_request_item_id`, `weekday varchar(16)`, `school_shift varchar(16)`, `needs_outbound boolean`, `needs_return boolean` | `needs_outbound or needs_return` |
| `proposal` | `token`, `client_driver_id`, `service_request_id`, `version smallint`, `parent_proposal_id`, `status varchar(16)`, `valid_until`, `starts_on`, `ends_on`, `total_amount numeric(12,2)`, `installments`, `due_day`, `enrollment_fee numeric(12,2)`, `adjustment_policy`, `cancellation_policy`, `vehicle_id`, `message varchar(1000)`, `decline_reason varchar(500)`, `accepted_at`, `source_contract_id`, `resulting_contract_id`, `snapshot jsonb` | `snapshot ?& array['version', 'vehicle']`; `version >= 1`; `ends_on > starts_on`; `total_amount > 0`; `installments between 1 and 12`; `due_day between 1 and 28`; `enrollment_fee >= 0`; `(status = 'ACCEPTED') = (accepted_at is not null)`; `resulting_contract_id` só com `ACCEPTED` |
| `proposal_item` | `proposal_id`, `dependent_id`, `monthly_amount numeric(12,2)`, `schedule_id` | `monthly_amount > 0` |
| `proposal_adjustment` | `proposal_id`, `field`, `suggested_value varchar(100)`, `note varchar(500)`, `created_at` | `suggested_value is not null or note is not null` |

**Índices** — cada um vem de uma consulta ou regra:

| Tabela | Índice | Para quê |
|---|---|---|
| `client_driver` | — (os de V36 bastam) | a busca por par já é única |
| `dependent_school_day` | único `(dependent_id, weekday) where deleted_at is null` | um dia por dependente (D16); também serve a leitura da grade |
| `service_request` | único `(token)` | leitura por token |
| `service_request` | único `(client_driver_id) where status in ('OPEN', 'ANSWERED')` | uma aberta por par (D2) |
| `service_request` | `(expires_at) where status = 'OPEN'` | varredura (D8) |
| `service_request` | `(client_driver_id)` | listas das partes e contagem das abertas do cliente |
| `service_request_item` | `(service_request_id)` | itens de uma solicitação |
| `service_request_item` | `(dependent_id)` | solicitações abertas de um dependente (retirada na fase 5) |
| `service_request_day` | único `(service_request_item_id, weekday)` | um dia por item (D3) |
| `proposal` | único `(token)` | leitura por token |
| `proposal` | único `(client_driver_id) where status = 'SENT'` | uma versão vigente por par (D5) |
| `proposal` | único `(service_request_id, version)` | cadeia de versões |
| `proposal` | `(valid_until) where status = 'SENT'` | varredura (D8) |
| `proposal` | único `(resulting_contract_id) where resulting_contract_id is not null` | uma proposta gera no máximo um contrato (fase 3) |
| `proposal` | `(source_contract_id) where source_contract_id is not null` | aditivos e renovações de um contrato (fase 8) |
| `proposal_item` | `(proposal_id)` | itens de uma proposta |
| `proposal_item` | único `(schedule_id)` | um dono por agenda (D2 da `contract-core`) |
| `proposal_adjustment` | `(proposal_id)` | ajustes de uma proposta |

## Rollout — grafo de dependência e plano de PRs

```
main
 └─ VAN-30/spec-service-request-proposal   (só a change do openspec)
      └─ VAN-30/1-rating-availability
           └─ VAN-30/2-link-origin
                └─ VAN-30/3-school-days
                     └─ VAN-30/4-request-schema
                          └─ VAN-30/5-proposal-schema
                               └─ VAN-30/6-request-service
                                    └─ VAN-30/7-request-http
                                         └─ VAN-30/8-proposal-service
                                              └─ VAN-30/9-proposal-http
                                                   └─ VAN-30/10-block-expiry
                                                        └─ VAN-30/11-email
                                                             └─ VAN-30/12-conversation-on-request
```

| PR | Conteúdo | Depends on | Parallel with |
|---|---|---|---|
| 0 | Branch `VAN-30/spec-service-request-proposal`: só `openspec/changes/service-request-proposal/` | — | — |
| 1 | Avaliações (`ContractStatus.REACHED_ACTIVE`, `LinkRatingEligibilityPolicy`, as duas avaliações, mensagens) e filtro de `is_available` na busca e nos recomendados, com a aprovação ligando a disponibilidade e o seeder; extração dos helpers do QA para `scripts/qa/lib.sh` | 0 | — |
| 2 | `V61`, `LinkOrigin`, colunas de origem e bloqueio no model, `origin` no `POST` do admin e na resposta | 1 | — |
| 3 | `V62`, `DependentSchoolDayModel`, repositório, DTOs, serviço e as rotas `GET`/`PUT /api/dependent/{token}/school-days`; remoção do `dependent.shift`; grade no seeder do dependente; `ContractReadinessPolicy`, `ContractReadinessService` e `GET /api/clients/me/contract-readiness` (D12); CPF do dependente e tipos de escola (D18); `V63`, bairro pelo `neighborhood` e `district` fora do endereço, do contrato, da rota e da escola (D20); endereço da escola no resolve e no JSON do dependente (D21) | 2 | — |
| 4 | `V64`, `ServiceRequestStatus`, `ServiceRequestTransitionPolicy`, models (com a foto, D19) e repositórios da solicitação; `HibernateJsonFormatMapper` | 3 | — |
| 5 | `V65`, `ProposalStatus`, os três enums de menu e de ajuste, `ProposalTransitionPolicy`, models (com a foto do veículo, D19) e repositórios da proposta | 4 | — |
| 6 | `ServiceRequestService`, `ClientDriverService.findOrCreateForRequest`, trava do dependente em negociação aberta e com contrato em andamento (D17), DTOs das duas visões, mapper, mensagens, seed de uma solicitação | 5 | — |
| 7 | Controllers da solicitação (cliente e motorista) | 6 | — |
| 8 | `ProposalService` (enviar, versionar, aceitar, recusar, ajustar, retirar), `ProposalAcceptancePolicy` (reconferência do aceite, D19), DTOs, mapper, mensagens, seed de uma proposta | 7 | — |
| 9 | Controllers da proposta (cliente e motorista) | 8 | — |
| 10 | Bloqueio e desbloqueio (serviço, endpoints, mensagens); `@EnableScheduling` condicional, `ExpirySweepService` com os dois passos, `expireOverdue` nos serviços, propriedades | 9 | — |
| 11 | Eventos de domínio, `NegotiationEmailService`, templates de e-mail | 10 | — |
| 12 | A solicitação abre a conversa e traz o `conversationToken`, e as listas filtram por ele; sai o `POST /api/conversations` e a `create_conversation`; `V66` (D23) | 11 | — |

A pilha segue o modelo da `contract-core`: a `VAN-30/spec-service-request-proposal` traz só a change, e cada PR nasce do anterior e aponta para ele como `--base`. O PR 1 precisa estar na `main` antes do PR 6, quando o vínculo começa a nascer por solicitação (spike: "a mudança nas avaliações precisa entrar junto com a fase 2"), e o PR 3 antes do PR 6, que copia o período da grade.

**Tamanho.** O teto desta change é de cerca de 1.000 linhas por PR, contando testes, script e `qa.md`. Os PRs pequenos foram juntados por afinidade: avaliação e busca (ajustes de regra em features que já existem, sem schema), cada policy pura com o que ela governa (a prontidão com a grade, a matriz de estados com o enum de cada tabela), e bloqueio com varredura (os dois encerram negociações pelos mesmos métodos de fechamento). Juntar a policy com o schema, e serviço com HTTP no PR 3 e no PR 10, flexibiliza a regra 39, como o PR 8 da `contract-core` fez com o aluno não vinculado. Os PRs 6, 8 e 9 passam do teto sozinhos (a régua da `contract-core` é ~1.900 linhas para o serviço e ~1.160 para o HTTP), como lá; se o PR 8 passar muito, o pedido de ajuste sai para um PR próprio entre o 8 e o 9.
