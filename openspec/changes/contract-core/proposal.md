## Why

O contrato é o que diz **quem o motorista transporta**. Hoje essa informação não existe no backend: `client_driver` diz que um cliente e um motorista se conhecem, mas não diz qual dependente vai na van, em que dias, em que trecho, de onde sai, nem até quando. Sem isso:

- a #151 (`checklist_entry`) e a #160 (`absence`) não têm de onde tirar a lista de passageiros da `trip` — a change `trip-daily-operation` entregou a âncora e parou aí;
- o motorista que já tem 30 alunos não consegue montar a rota no app até que cada família feche contrato pela Vanep;
- `client_driver.status` é escrito à mão pelas duas partes e pelo admin, então `ACTIVE` não significa nada verificável.

Esta change é a **fase 1 do spike de Propostas e Contratos** (V2). Ela entrega o núcleo do contrato completo, sem proposta, sem PDF e sem assinatura: o contrato nasce pelo admin e pelo seeder, e o motorista cadastra os alunos que já tem como **alunos não vinculados**. As fases seguintes (proposta, geração, assinatura, ativação, chat, convite da família, ciclo de vida, cobrança) penduram tabelas novas neste núcleo sem alterar suas colunas.

Ela também corrige estruturas atuais que contradizem o modelo — o contrato define as regras, e o código existente se ajusta a ele, não o contrário.

## What Changes

- **Separação do turno (E6):** `Shift` vira `SchoolShift` (`MORNING`, `AFTERNOON`, `NIGHT`, `FULLTIME` — turno escolar, usado por `dependent` e `unlinked_passenger`) e nasce `OperationShift` (`MORNING`, `AFTERNOON`, `NIGHT` — turno da operação, usado por `trip` e `schedule_slot`). `trip` deixa de aceitar `FULLTIME`. Responde a Q1 da `trip-daily-operation`: aluno integral gera **duas** trips
- **Agenda:** tabelas `schedule` e `schedule_slot` (`weekday`, `leg` `OUTBOUND`/`RETURN`, `shift` explícito, janela de embarque). Cada dono aponta para o seu `schedule`; sem FK polimórfica
- **Contrato:** tabelas `contract` (status com o enum completo, período obrigatório de até 12 meses, valor total, parcelas, vencimento, `supersedes_contract_id`) e `contract_item` (dependente, escola, endereço de embarque **copiado**, preço, agenda)
- **Aluno não vinculado:** tabela `unlinked_passenger`, cadastrada e gerida pelo próprio motorista em `/api/drivers/me/unlinked-passengers`, com endereço próprio na tabela `address` e agenda
- **Consulta de passageiros:** `RoutePassengerQueryService` devolve, para um motorista, uma data e um `OperationShift`, os itens de contratos `ACTIVE` + os alunos não vinculados, cada um com a **origem** (`CONTRACT_ITEM` ou `UNLINKED_PASSENGER`) e o token
- **Convenção para a operação:** `checklist_entry` (#151) e `absence` (#160) apontam para o passageiro com `contract_item_id` **ou** `unlinked_passenger_id` (CHECK de exatamente um). Esta change fixa a convenção; as tabelas são daquelas issues. A `absence` está sendo feita em paralelo, fora da convenção, e entra na `main` depois desta change, adotando a convenção ao entrar (aviso no topo do `tasks.md`)
- **Status do vínculo derivado (E1):** `client_driver.status` deixa de ter caminho de escrita direto. Sai do `POST` e o `PATCH /api/client-drivers/{token}` é removido; o status passa a ser calculado a partir dos contratos do par
- **Saneamento (E5):** migration devolve a `PENDING` os vínculos `ACTIVE` sem contrato (inclusive os criados pela V38/V39) — o valor que a regra derivada dá a quem nunca teve contrato; `ClientDriverSeeder` cria o vínculo `PENDING` e o novo `ContractSeeder` cria o contrato que o ativa
- **CRUD administrativo do contrato** em `/api/contracts` (`POST`, `GET` paginado, `GET /{token}`, `PATCH /{token}` só de `status`, `DELETE`, `POST /{token}/restore`) e leitura pelas partes (`GET /api/contracts/{token}` e `GET /api/client-drivers/{token}/contracts`)
- **Comment em `dependent.shift` (A1):** é o turno escolar; quem diz dias e horários atendidos é a agenda do contrato
- Permissões novas no bundle `ADMIN`: `list_contracts`, `show_contract`, `create_contract`, `update_contract`, `delete_contract`, `restore_contract`. `update_client_driver` deixa de existir

**Fora de escopo:**

- `service_request`, `proposal`, `client_driver.origin` e `blocked_by`, trava das avaliações por contrato (fase 2, `service-request-proposal`)
- `contract_template`, `contract_document`, PDF (fase 3) e assinatura (fase 4)
- Guardas no dependente com contrato ativo — exclusão e troca de escola/turno (fase 5, `contract-activation`)
- Telas do motorista para alunos não vinculados, "convidar família" e troca pelo dependente real (fase 7, `driver-family-invite`)
- `contract_event`, aditivo, rescisão, renovação, arrependimento, cronograma de parcelas, jobs de ativação/encerramento por data (fase 8, `contract-lifecycle`)
- `checklist_entry`, `stop_change_request`, `route` (#151, #152, #45)
- A `absence` (#160) em si: ela entra depois desta change e se adapta à convenção do D11 (tipos de turno, `RouteLeg`, referência ao passageiro e migration renumerada)

## Capabilities

### New Capabilities

- `operation-shift`: separação entre turno escolar e turno da operação; `trip` só aceita turno de operação.
- `contract-core`: contrato e itens por dependente, regras de período e valores, máquina de estados, CRUD administrativo e leitura pelas partes.
- `contract-schedule`: agenda por dono em `schedule` + `schedule_slot`, com turno explícito por horário.
- `unlinked-passengers`: alunos não vinculados geridos pelo motorista, sem contrato e sem aviso a responsável.
- `route-passengers`: lista de passageiros de uma trip a partir das duas fontes, com a origem de cada um, e a convenção de referência para a operação.

- `client-driver-derived-status`: o status do vínculo passa a ser derivado dos contratos; sem `status` no `POST` e sem `PATCH`.

### Modified Capabilities

Nenhuma em `openspec/specs/`. A mudança no vínculo entra como capability nova (`client-driver-derived-status`) porque a change `client-driver-link` ainda não foi arquivada — não há spec base para um delta `MODIFIED`. Ao arquivar as duas, a `client-driver-derived-status` substitui os requisitos de escrita de status da `client-driver-link`.

## Impact

- **Schema:** `V53` (dados de `trip` com `FULLTIME` + comments), `V54` (`schedule`, `schedule_slot`, `unlinked_passenger`), `V55` (`contract`, `contract_item`), `V56` (permissões do bundle `ADMIN` em ambiente sem seeder), `V57` (saneamento de `client_driver`). Nenhuma migration aplicada é editada (regra 2). Conferido em 07/10: a `main` está em `V51` (a #158 entrou como `V50`/`V51`) e a `V52` está em uso pela `absence` (#160, branches `feat/absence-*`), e entra depois desta change, combinado com o autor: a `absence` renumera a migration dela para depois da nossa última. Reconfirmar antes do merge de cada PR com migration
- **Pacotes novos:** `br.com.vanep.schedule`, `br.com.vanep.contract`, `br.com.vanep.unlinkedpassenger`, `br.com.vanep.routepassenger`
- **Pacotes alterados:** `shared.enums` (`Shift` → `SchoolShift` + `OperationShift`; `RouteLeg` novo), `trip`, `dependent` (tipo do enum), `clientdriver` (sem `PATCH`, sem `status` no create), `address` (`upsertForUnlinkedPassenger`), `auth.security` (`@sec.isContractParty`, `PermissionEnum`), `seed`
- **API — breaking, sem consumidor:** `PATCH /api/client-drivers/{token}` some e o `POST` ignora `status`; `trip` passa a recusar `FULLTIME` (`400`). Verificado em 03/10: nem `vanep-mobile` nem `vanep-frontend` chamam `/client-drivers` ou usam `FULLTIME`
- **Dados:** depois da `V57`, todo vínculo que estava `ACTIVE` sem contrato fica `PENDING` até o admin cadastrar o contrato
- **⚠️ Avaliação de motorista fica bloqueada até haver contrato (aceito):** desde a #158 (`dd5f814`, já na `main`), o `DriverRatingService` só aceita vínculo `ACTIVE` e responde `422 driver_rating.link.not_active` para os outros. Depois da `V57`, nenhum cliente avalia motorista até o admin cadastrar o contrato do par. Decisão de produto (07/10): aceitável, porque avaliar sem nunca ter contratado é exatamente o que o E3 do spike quer impedir. A avaliação de cliente (`ClientRatingService`) não confere status e não muda
- **Divergência com a `absence` (#160):** a tabela `absence`, feita em paralelo, aponta para `client_driver_id` + `dependent_id`, e não para o passageiro como define o D11. Ela entra depois desta change e faz o ajuste do lado dela (ver o aviso no topo do `tasks.md`)
- **Responde perguntas abertas de outras changes:** Q1 da `trip-daily-operation` (integral = duas trips) e Q1/Q2/Q3 da `client-driver-link` (o status é derivado; o admin não cria vínculo `ACTIVE`; o bloqueio com direção é da fase 2)
