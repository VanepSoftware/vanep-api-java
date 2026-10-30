> **Fase 2 do spike "Propostas e Contratos" (V2).** Esta change é dividida em 12 PRs pelas regras 36 a 43, **em sequência** sobre a branch `VAN-30/spec-service-request-proposal`, que só traz esta change do openspec: cada branch nasce da anterior e cada PR aponta a anterior como `--base` (o PR 1 aponta para a `VAN-30/spec-service-request-proposal`). A seção de cada PR tem o número dele. Ver o grafo no fim do `design.md`. A task no Genwork é a **VAN-30**: a chave vai no título e na branch de cada PR (regra 47).
>
> ```
> main ─ spec ─ PR 1 ─ PR 2 ─ … ─ PR 10 ─ PR 11
> ```

> [!WARNING]
> **Sem push nesta change.** As notificações por FCM (VAN-18) ficam para uma fase final. A contraparte é avisada por e-mail (PR 11), num ouvinte de eventos que o push vai reaproveitar.

> [!IMPORTANT]
> **Motoristas aprovados antes do PR 1 somem da busca nos bancos locais (R3).** A aprovação passa a ligar `is_available`, mas quem já estava aprovado continua `false`: recriar o banco ou ligar à mão. O app pode expor o "aceitando novos alunos" no perfil do motorista depois, sem bloquear o merge.
>
> **Ninguém solicita sem a escola e a grade escolar do dependente depois do PR 6 (R12).** A prontidão cobra as duas (`SCHOOL`, `SCHOOL_DAYS`), e o app ainda não tem nenhuma das telas. O app precisa da escolha da escola e da tela da grade (PR 3) antes de expor o "Solicitar orçamento".

## Preparação

- [x] Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] Revisar `proposal.md`, `design.md`, os dez specs novos (`rating-requires-contract`, `driver-availability`, `client-driver-origin`, `client-driver-block`, `contract-readiness`, `service-request`, `proposal`, `negotiation-email`, `address-neighborhood`, `school-address`) e o delta do `dependent`
- [x] **Confirmar os números de Flyway** na `main` e em todas as branches remotas (`git ls-tree`). Esta change usa `V61` a `V65`. **Reconfirmar antes do merge de cada PR com migration** (R6)
- [x] Branch `VAN-30/spec-service-request-proposal` a partir da `main`, só com `openspec/changes/service-request-proposal/`. É a base da pilha

## 1. PR 1 — Avaliação exige contrato e busca só com quem aceita alunos

> Goal: nenhum dos dois lados avalia sem um contrato do vínculo que chegou a `ACTIVE`, e `is_available = false` some da busca e dos recomendados, com o total certo. Tem que estar na `main` antes do PR 6.
> Depends on: `VAN-30/spec-service-request-proposal` | Parallel with: —
> Order: test → policy → repository → service → seeder → mensagens

- [x] 1.1 Criar branch `VAN-30/1-rating-availability` a partir da `VAN-30/spec-service-request-proposal`
- [x] 1.2 Testes da avaliação: `LinkRatingEligibilityPolicy` aceita cada status de `REACHED_ACTIVE` e recusa lista vazia, `AWAITING_SIGNATURES`, `SIGNED` e `CANCELLED`; `DriverRatingService` com vínculo `INACTIVE` de contrato `ENDED` → 201, com vínculo sem contrato → 422 `driver_rating.link.never_contracted`, com contrato removido → 422; `ClientRatingService` com e sem contrato (201 / 422 `client_rating.link.never_contracted`); `findRatingStatus` devolve `canRate = false` sem contrato; slice do `POST` das duas avaliações com o 422
- [x] 1.3 Testes da busca: repositório das áreas com um motorista indisponível na área (fora da lista ordenada, nas duas consultas); recomendados sem indisponível e com o total certo; `DriverSearchService` com o total da página sem o indisponível; perfil de indisponível → 200 com `available = false`; aprovar o motorista liga `available`, recusar não
- [x] 1.4 `ContractStatus.REACHED_ACTIVE` (`ACTIVE`, `SUSPENDED`, `ENDED`, `TERMINATED`, `SUPERSEDED`), com um teste que confere contra a `ContractTransitionPolicy` que nenhum outro status é alcançável só passando por `ACTIVE` (D11)
- [x] 1.5 `clientdriver.service.LinkRatingEligibilityPolicy` pura; `DriverRatingEligibilityPolicy` troca `isLinkActive` por ela e mantém a idade mínima; `ClientRatingService` passa a conferir a regra pelos status de `ContractRepository.findStatusesByClientDriverId`
- [x] 1.6 Mensagens `driver_rating.link.never_contracted` e `client_rating.link.never_contracted` em `messages.properties` e `messages_pt_BR.properties`; remover `driver_rating.link.not_active` (regra 34) e conferir com `grep` que nada mais a usa
- [x] 1.7 Filtro `driver.available = true` em `findDriverMatchesInCity`, `findDriverMatchesCoveringPoint` e `findSearchableNewestFirst` (consulta e `countQuery`); `findSearchableByToken` não muda (D13)
- [x] 1.8 `DriverOnboardingService.approve` grava `is_available = true` junto com `APPROVED`; conferir que os motoristas aprovados do seeder ficam com `is_available = true`, ajustando o seeder se não ficarem
- [x] 1.9 Extrair os helpers genéricos do `scripts/qa/contract-core/qa.sh` (`sql`, `request`, `expect`, `check`, `login`, `in_parallel`…) para `scripts/qa/lib.sh`, com o script da `contract-core` passando a fazer `source` dele; rodar o script da `contract-core` contra a `main` e conferir que todos os cenários continuam passando
- [x] 1.10 `make lint` + `./mvnw verify -B`
- [x] 1.11 QA da regra 55 (`scripts/qa/service-request-proposal/qa.sh`, resultado no `qa.md`): as duas avaliações com contrato `ACTIVE`, `ENDED`, `CANCELLED`, removido e sem contrato, para o cliente e o motorista do par e para um terceiro; busca e recomendados antes e depois de desligar a disponibilidade de um motorista do seed, conferindo a lista e o total; perfil do indisponível; motorista em análise aprovado pelo admin passa a aparecer nos recomendados

## 2. PR 2 — Origem e colunas do bloqueio no vínculo

> Goal: todo vínculo tem origem imutável e o banco não aceita bloqueio sem dono. Sem ação de bloquear ainda.
> Depends on: PR 1 | Parallel with: —
> Order: test → migration → enum → model → DTO → service

- [x] 2.1 Criar branch `VAN-30/2-link-origin` de dentro da branch do PR 1
- [x] 2.2 Testes: `POST /api/client-drivers` sem `origin` → 400 e nada criado; com `origin` desconhecida → 400; com `DRIVER_INVITE` → 201 com `origin` e `PENDING`; a resposta de `GET /{token}` e `/me` traz `origin`; repositório grava e relê `origin`, `blocked_by` e `blocked_at`; `origin` não muda num `save` de um vínculo existente com outro valor (`updatable = false`)
- [x] 2.3 Migration `V61__client_driver_origin_and_block.sql`: `origin varchar(16)`, `update … set origin = 'CLIENT_SEARCH'`, `set not null`; `blocked_by bigint references users (id)`, `blocked_at timestamptz`; vínculos `BLOCKED` voltam ao status derivado dos contratos (mesma regra SQL da `V60`); `check ((status = 'BLOCKED') = (blocked_by is not null))` e o mesmo para `blocked_at`; comments (origem imutável e por quê; quem bloqueou)
- [x] 2.4 `clientdriver.enums.LinkOrigin` (`CLIENT_SEARCH`, `DRIVER_INVITE`) e as colunas no `ClientDriverModel` (`origin` com `updatable = false`; `blockedBy` como `UserModel` `EAGER`)
- [x] 2.5 `ClientDriverCreateRequestDTO` com `@NotNull LinkOrigin origin`; `ClientDriverService.create` grava; `ClientDriverResponseDTO` e mapper com `origin`
- [x] 2.6 `ClientDriverSeeder` grava a origem; conferir que nenhum outro ponto do código cria `ClientDriverModel` sem origem (`grep "new ClientDriverModel"`, inclusive nos testes)
- [x] 2.7 Reconferir que `vanep-frontend` e `vanep-mobile` não chamam `POST /api/client-drivers` (R10)
- [x] 2.8 `make lint` + `./mvnw verify -B`
- [x] 2.9 QA da regra 55: `POST` do admin com e sem `origin`, com tipo errado e valor desconhecido; por SQL, o `CHECK` recusa `BLOCKED` sem dono e dono sem `BLOCKED`; a `V61` reaplicada sobre um vínculo `BLOCKED` sem dono o devolve ao status derivado e preenche `origin`

## 3. PR 3 — Grade escolar do dependente e prontidão contratual

> Goal: o dependente tem o período escolar de cada dia da semana, lido e substituído como sub-recurso, e o cliente consulta a prontidão contratual. O bairro mostrado passa a ser o `neighborhood`, e a escola ganha endereço no resolve. Tem que estar na `main` antes do PR 6, que copia o período da grade.
> Depends on: PR 2 | Parallel with: —
> Order: test → migration → model → repository → policy → DTO → service → controller

- [x] 3.1 Criar branch `VAN-30/3-school-days` de dentro da branch do PR 2
- [x] 3.2 Testes da grade: repositório grava e relê a grade de um dependente, sem as linhas removidas; a resposta vem em ordem de segunda a domingo; serviço substitui a grade inteira (linhas antigas com soft delete, novas gravadas, numa transação), lista vazia limpa, dia repetido → 400 e a grade gravada não muda; substituir a grade não muda nenhum outro campo do dependente; dependente removido e restaurado volta com a grade; o `POST` do dependente ignora `shift` e a resposta não o traz; slice do `GET` e do `PUT` para o dono (200), outro cliente (403), admin (200), sem token (401), dependente desconhecido ou removido (404), `weekday` ou `schoolShift` faltando, valor desconhecido, JSON de tipo errado e malformado (400)
- [x] 3.3 Testes da `ContractReadinessPolicy`: cada campo da tabela do spec `contract-readiness` faltando sozinho, inclusive `SCHOOL_DAYS` para dependente sem grade; vários juntos, na ordem da tabela; nada faltando → `ready`
- [x] 3.4 Migration `V62__create_dependent_school_day_and_drop_shift.sql`: `dependent_school_day` (`id`, `dependent_id` FK, `weekday varchar(16)`, `school_shift varchar(16)`, timestamps, `deleted_at`), `unique (dependent_id, weekday) where deleted_at is null`; `drop column dependent.shift`, sem converter em grade; comments (período por dia é fato da criança; por que o `shift` sai sem virar grade)
- [x] 3.5 `dependent.model.DependentSchoolDayModel` (`@SoftDelete`, `weekday` como `DayOfWeek` e `schoolShift` como `SchoolShift`, os dois `@Enumerated(STRING)`) e `DependentSchoolDayRepository`; o `shift` sai do `DependentModel`, dos DTOs do dependente, do mapper, do serviço e do seeder
- [x] 3.6 `ContractReadinessPolicy` pura (entrada com os campos do cliente e dos dependentes, saída na ordem da tabela do spec)
- [x] 3.7 `DependentSchoolDayRequestDTO` (`weekday`, `schoolShift`, `@NotNull`), `DependentSchoolDaysRequestDTO` (`days`, `@NotNull @Valid`) e `DependentSchoolDayResponseDTO`
- [x] 3.8 `DependentService.findSchoolDays` e `replaceSchoolDays`, com o dono resolvido como no `findActiveForAccess` existente e o `replaceSchoolDays` travando o dependente (`PESSIMISTIC_WRITE`); dia repetido → 400 no serviço
- [x] 3.9 `GET` e `PUT /api/dependent/{token}/school-days` no `DependentController`, com `show_dependent` e `update_dependent`
- [x] 3.10 `DependentSeeder`: os dependentes do seed ganham grade (um deles integral só em alguns dias, para o QA), CPF de teste válido, nascimento e gênero, e são reconhecidos pelo cliente e pelo nome; o `ContractSeeder` cria o contrato do seed para o dependente padrão
- [x] 3.11 Acrescentar `dependent_school_day` ao `clean.sql` na ordem de FK
- [x] 3.12 Testes da prontidão: serviço (dados do cliente e dos dependentes entregues à policy na ordem pedida, token repetido conferido uma vez, sem tokens só o cliente, dependente de outro cliente ou removido → 404, chamador que não é cliente → 403); rota (`ready` e `missing`, `dependentToken` ausente nas entradas do cliente, tokens como parâmetro repetido e separados por vírgula, sem token → 401, motorista → 403, token de outro cliente → 404); dependentes e grades lidos no mesmo número de consultas para um e para três dependentes
- [x] 3.13 `ContractReadinessService`, `ContractReadinessResponseDTO` e `ContractReadinessController` (`GET /api/clients/me/contract-readiness`, `@PreAuthorize("isAuthenticated()")`), com `DependentRepository.findByTokenInAndClientId` e `DependentSchoolDayRepository.findByDependentIdIn`
- [x] 3.14 CPF do dependente (D18): testes primeiro (CPF inválido no `POST` e no `PATCH` → 400 e nada gravado, CPF com máscara gravado só com dígitos, duplicidade conferida pelos dígitos, `document: null` limpa); mover `@Cpf`, `CpfConstraintValidator` e `CpfValidator` de `auth.validation` para `shared.validation`; `@Cpf` no `document` dos dois DTOs do dependente, no lugar do `@Size`; mensagem `dependent.document.invalid`, e `dependent.document.too_long` sai
- [x] 3.15 Tipos de escola (D18): teste primeiro de um lugar só com `primary_school` aceito no resolve; `SchoolResolveService` aceita `school`, `primary_school`, `secondary_school` e `educational_institution`
- [x] 3.16 Remover o dependente grava `address_id` nulo antes do soft delete: teste primeiro (depois de remover e restaurar, o `address_id` é nulo), depois o `flush` no `DependentService.delete`
- [x] 3.17 `make lint` + `./mvnw verify -B`
- [x] 3.18 QA da regra 55: a prontidão para o cliente do seed com dependentes prontos e incompletos, sem tokens, com token repetido e de outro cliente, sem token de acesso e como motorista; CPF inválido e com máscara no dependente; o QA da `contract-core` deixa de testar o `dependent.shift` e reaplica a `V56` com a coluna recriada só durante a execução; cada cenário do delta do `dependent` para o cliente dono, outro cliente e o admin; mau uso (sem token, token inválido, dia repetido, valores desconhecidos, tipos errados, JSON malformado, dependente removido); por SQL, o índice recusa dia repetido ativo e aceita depois do soft delete; a substituição deixa as linhas antigas com `deleted_at`
- [x] 3.19 Bairro (D20), testes primeiro: `StreetAddressExtractor.findNeighborhood` devolve o `sublocality` mais raso (SP com `sublocality_level_1`; DF com `sublocality_level_2` sob o `administrative_area_level_4`) e vazio sem `sublocality`; o `applyPlace` grava o `neighborhood`; o JSON do endereço, do endereço pessoal, da escola, do item do contrato e dos passageiros da rota não traz mais o `district`
- [x] 3.20 Migration `V63__drop_district_from_address_contract_item_and_school.sql`: copia o nome do nó para o `neighborhood` vazio do endereço e para o `pickup_neighborhood` vazio do item do contrato, e o nome do nó da escola para o `neighborhood` vazio do endereço dela; remove `address.district_id`, `contract_item.pickup_district_id` e `school.district_id`
- [x] 3.21 O `district` sai do `AddressModel`, do `ContractItemModel` e do `SchoolModel`, das consultas do contrato e do aluno não vinculado, do `SchoolResolveService`, do `SchoolResponseDTO` (`districtName`, `districtToken`), do `AddressResponseDTO` (`district`), do `PersonalAddressResponseDTO` (`districtName`, `districtToken`), do `ContractItemResponseDTO` e do `RoutePassengerDTO` (`pickupDistrictName`); o `AddressPlaceResolverService` grava o `neighborhood` do place
- [x] 3.22 Endereço da escola (D21), testes primeiro: criar a escola pelo resolve grava o endereço do mesmo place, sem nova chamada ao Google; place sem rua cria a escola sem endereço; escola conhecida sem endereço ganha o endereço no resolve, e com endereço só fecha a sessão; passado o limite por usuário, a escola conhecida volta sem endereço e sem erro; a resposta do resolve traz o endereço; o place novo gravado pelo admin no endereço da escola move também a cidade da escola
- [x] 3.23 `AddressPlaceResolverService.buildFromPlace` (endereço a partir de um place já buscado, vazio sem rua), `AddressService.createForSchoolFromPlace`, `SchoolResolveService` gravando e completando o endereço, e a `Resolution` com o endereço; `AddressService.upsertForSchool` levando a cidade do endereço para a escola
- [x] 3.24 `DependentSchoolDTO.address`, teste primeiro: a lista de dependentes lê os endereços dos dependentes e das escolas numa consulta só, e a leitura de um dependente traz o endereço da escola
- [x] 3.25 `make lint` + `./mvnw verify -B`
- [x] 3.26 QA da regra 55: resolve das duas escolas das fixtures (endereço, bairro e CEP gravados; repetir o resolve não cria outro endereço); escola conhecida com o endereço removido por SQL se completa no resolve; o JSON do dependente traz o endereço da escola; o JSON da escola, do endereço do dependente, do endereço pessoal, do contrato e dos passageiros da rota não tem mais `district`; por SQL, a `V63` copia o nome do nó para o bairro vazio, inclusive o da escola para o endereço dela, e remove as três colunas

## 4. PR 4 — Schema e estados da solicitação

> Goal: `service_request`, `service_request_item` e `service_request_day` existem, com uma aberta por par garantida pelo banco, e a matriz de estados da solicitação é uma classe pura. Sem serviço, sem HTTP.
> Depends on: PR 3 | Parallel with: —
> Order: test → migration → enum → policy → model → repository

- [x] 4.1 Criar branch `VAN-30/4-request-schema` de dentro da branch do PR 3
- [x] 4.2 Testes de repositório: solicitação com dois itens e a grade por dia (períodos diferentes em dias diferentes, um dia só com ida), relida fora da transação com a foto (nome, nascimento, escola, cidade e bairro) e os dias (prova o fetch join); a foto inteira sobrevive a editar, remover e restaurar o dependente e a remover a escola, e com um dos dois irmãos removido a solicitação é relida por token e na página com os dois itens; o JSON gravado tem a data em ISO; busca por token dentro do cliente e dentro do motorista (token de outro não resolve); lista paginada por cliente e por motorista com filtro de status; contagem das abertas de um cliente; solicitação aberta de um par; `OPEN` vencidas por `expires_at`; token opaco gerado no persist; escrita condicional de status (muda uma linha do status esperado, zero de outro)
- [x] 4.3 Testes da `ServiceRequestTransitionPolicy`: matriz do spec `service-request` inteira, um teste por par (de, para), permitido e recusado
- [x] 4.4 Migration `V64__create_service_request_tables.sql` com as colunas, os `CHECK`s e os índices do Migration Plan do `design.md`; comments (o que cada status aberto espera; por que o item guarda a foto e ids simples e não tem endereço exato; `service_request_day` é o pedido do cliente, e turno da rota e janela vêm da proposta)
- [x] 4.5 `servicerequest.enums.ServiceRequestStatus` (`OPEN`, `ANSWERED`, `CONVERTED`, `DECLINED`, `EXPIRED`, `WITHDRAWN`) com o conjunto dos abertos (`OPEN_STATUSES`), e a `ServiceRequestTransitionPolicy` pura
- [x] 4.6 `ServiceRequestModel`, `ServiceRequestItemModel`, `ServiceRequestDayModel` (sem `@SoftDelete`, D15; o item guarda `dependentId`, `schoolId` e `pickupCityId` como colunas simples, sem associação, e a foto como `record` (`@JdbcTypeCode(SqlTypes.JSON)`, `updatable = false`, D19); `config.HibernateJsonFormatMapper` grava o JSON do banco com o `JsonMapper` do Spring; `weekday` como `DayOfWeek` e `schoolShift` como `SchoolShift`, os dois `@Enumerated(STRING)`; dias por `cascade = ALL` a partir do item, itens a partir da solicitação)
- [x] 4.7 `ServiceRequestRepository` com as consultas do 4.2 (fetch join até os dias, regra 17) e a escrita condicional de status (`update … where id = :id and status in :from`, devolve o número de linhas)
- [x] 4.8 Acrescentar as três tabelas ao `src/test/resources/db/clean.sql` na ordem de FK
- [x] 4.9 `make lint` + `./mvnw verify -B`
- [x] 4.10 QA da regra 55, por SQL no PostgreSQL da API: segunda aberta do par recusada (`OPEN` e `ANSWERED`), aberta depois de uma `EXPIRED` aceita; dia repetido no item recusado; dia sem ida e sem volta recusado; dia só com ida ou só com volta aceito; token repetido recusado; foto sem uma das chaves recusada; a consulta de vencidas usa o índice parcial

## 5. PR 5 — Schema e estados da proposta

> Goal: `proposal`, `proposal_item` e `proposal_adjustment` existem, com uma `SENT` por par e a cadeia de versões garantidas pelo banco, e a matriz de estados da proposta é uma classe pura.
> Depends on: PR 4 | Parallel with: —
> Order: test → migration → enum → policy → model → repository

- [x] 5.1 Criar branch `VAN-30/5-proposal-schema` de dentro da branch do PR 4
- [x] 5.2 Testes de repositório: proposta com itens, agendas e ajustes, relida fora da transação com a foto do veículo; a foto sobrevive a editar, remover e restaurar o veículo; cadeia de uma solicitação em ordem de versão; proposta `SENT` de um par; busca por token dentro do cliente e dentro do motorista; `SENT` vencidas por `valid_until`; escrita condicional de status (muda uma linha de `SENT`, zero de outro status)
- [x] 5.3 Testes da `ProposalTransitionPolicy`: só sai de `SENT`, para os cinco destinos; terminais não saem; um teste por par
- [x] 5.4 Migration `V65__create_proposal_tables.sql` com as colunas, os `CHECK`s e os índices do Migration Plan do `design.md`; comments (imutável, nova versão substitui; a foto do veículo; `resulting_contract_id` da fase 3; `source_contract_id` da fase 8; menus provisórios até a revisão jurídica)
- [x] 5.5 Enums em `proposal.enums`: `ProposalStatus` (`SENT`, `ACCEPTED`, `SUPERSEDED`, `DECLINED`, `EXPIRED`, `WITHDRAWN`), `ProposalAdjustmentPolicy`, `ProposalCancellationPolicy`, `ProposalAdjustmentField` (D6, D7); e a `ProposalTransitionPolicy` pura
- [x] 5.6 `ProposalModel` (termos com `updatable = false`; `vehicleId`, `parentProposalId`, `sourceContractId` e `resultingContractId` como colunas simples; a foto do veículo como `record`, D19), `ProposalItemModel` (`dependentId` como coluna simples; agenda como `@OneToOne` para `ScheduleModel` com `cascade = ALL`, como o `contract_item`), `ProposalAdjustmentModel`
- [x] 5.7 `ProposalRepository` e `ProposalAdjustmentRepository` com as consultas do 5.2 (regra 17)
- [x] 5.8 Acrescentar as três tabelas ao `clean.sql` na ordem de FK
- [x] 5.9 `make lint` + `./mvnw verify -B`
- [x] 5.10 QA da regra 55, por SQL: segunda `SENT` do par recusada; versão repetida na solicitação recusada; `ACCEPTED` sem `accepted_at` recusado e o inverso; `resulting_contract_id` repetido recusado; ajuste sem valor e sem nota recusado; foto sem o veículo recusada; cada faixa numérica no limite aceita e um passo além recusada

## 6. PR 6 — Serviço da solicitação e prontidão

> Goal: enviar, ler, listar, retirar e recusar solicitações, com o vínculo criado ou reaproveitado, e travar o dependente em negociação aberta ou com contrato em andamento. Sem HTTP.
> Depends on: PR 5 | Parallel with: —
> Order: test → request DTO → service → response DTO → mapper → mensagens

- [x] 6.1 Criar branch `VAN-30/6-request-service` de dentro da branch do PR 5
- [x] 6.2 Testes do serviço, um por linha das tabelas de recusa do spec `service-request` (cada uma sem nada gravado), mais: envio feliz com dois dependentes; vínculo criado `PENDING` com `CLIENT_SEARCH`; vínculo existente reaproveitado com a origem mantida; perda da corrida no índice do par (`DataIntegrityViolationException`) → 409 `service_request.open_exists`; a quinta aberta passa e a sexta não; o item grava a foto (nome, nascimento, escola, cidade com a UF e bairro) e os ids, e cada dia pedido copia o período da grade do dependente (integral só em alguns dias, sem volta num dia); dia fora da grade → 422 `service_request.day.no_school`; mudar a grade depois não muda a solicitação; retirar e recusar uma `ANSWERED` fecham a proposta `SENT` (com o repositório da proposta mockado); retirar ou recusar uma terminal → 409; token de outro cliente ou motorista → 404; chamador do tipo errado → 403; o filtro de status com um ou mais status, e o vazio lista todos
- [x] 6.3 Testes da visão do motorista: primeiro nome, idade em anos completos (aniversário hoje e amanhã), sem sobrenome, nascimento, rua, CEP, `google_place_id`, documento, telefone ou e-mail do cliente
- [x] 6.4 Testes da trava do dependente (D17): remover com solicitação `OPEN` ou `ANSWERED` → 422 `dependent.open_negotiation` e nada muda, e depois da retirada a remoção passa; remover com contrato `AWAITING_SIGNATURES`, `SIGNED`, `ACTIVE` ou `SUSPENDED` → 422 `dependent.open_contract`, com o contrato e a rota intactos, e com contrato `ENDED`, `TERMINATED`, `CANCELLED` ou `SUPERSEDED`, ou removido pelo admin, a remoção passa; com contrato em andamento, `PATCH` com `schoolToken`, `PATCH` com `address` e `PUT` da grade → 422 e nada muda, e `PATCH` só com `name` passa; o admin também é travado; restaurar não é travado
- [x] 6.5 `ServiceRequestCreateRequestDTO`, `ServiceRequestItemRequestDTO`, `ServiceRequestDayRequestDTO` (`weekday`, `needsOutbound`, `needsReturn`; sem período, que vem da grade) e `ServiceRequestDeclineRequestDTO` com a Bean Validation do spec (`@FutureOrPresent`, tamanhos, listas não vazias); dependente repetido, dia repetido no item e dia sem ida e sem volta → 400 no serviço, como a `ScheduleSlotPolicy`
- [x] 6.6 `ClientDriverService.findOrCreateForRequest(client, driver)` (D9)
- [x] 6.7 `ServiceRequestService` (enviar, ler, listar com filtro por um ou mais status, retirar, recusar), com o envio travando os dependentes do pedido (`lockByToken`) e conferindo a prontidão pelo `ContractReadinessService` do PR 3, e a foto do item montada num só lugar (`ServiceRequestMapper.toItemSnapshot`)
- [x] 6.8 `DependentRepository.existsInOpenServiceRequest` e `existsInOpenContract` (nativas, D17); `DependentService` travando a linha do dependente (`lockByToken`) na remoção, na edição e na troca da grade, e recusando com 422 a remoção em negociação aberta e, com contrato em andamento, a remoção, o `PATCH` com `schoolToken` ou `address` e a troca da grade; `ContractService.createItem` travando o dependente
- [x] 6.9 DTOs de resposta das duas visões (`ServiceRequestClientResponseDTO` com a nota do motorista, `ServiceRequestDriverResponseDTO`) e `ServiceRequestMapper`, com os dias em ordem de segunda a domingo, o dependente, a escola, a cidade e o bairro lidos da foto do item e o token do dependente lido numa consulta só para a página, incluindo os removidos
- [x] 6.10 Mensagens `service_request.*`, `dependent.open_negotiation` e `dependent.open_contract` em `messages.properties` e `messages_pt_BR.properties`
- [x] 6.11 Seed: telefone e endereço pessoal da cliente do seed, para ela estar pronta (`contract-readiness`), e uma solicitação `OPEN` dela a um motorista sem vínculo com ela, pelo `ServiceRequestService`, idempotente como o `ContractSeeder`; os scripts de QA continuam partindo do banco semeado
- [x] 6.12 `make lint` + `./mvnw verify -B`
- [x] 6.13 QA da regra 55: sem rota nova; com uma solicitação `OPEN` e outra `ANSWERED` inseridas por SQL, `DELETE /api/dependent/{token}` → 422 com a mensagem em pt-BR e o dependente intacto, e 204 depois de a solicitação ir para um status terminal; com um contrato `ACTIVE` criado pelo admin para um dependente do QA, `DELETE` (do cliente e do admin), `PATCH` com `schoolToken`, `PATCH` com `address` e `PUT .../school-days` → 422 com a mensagem em pt-BR e o dependente e a grade intactos, `PATCH` só com `name` → 200, e 204 na remoção depois de o contrato ir para `ENDED`; a solicitação do seed conferida por SQL; regressão dos PRs anteriores e da `contract-core` no `qa.md`, os dois scripts partindo do seed novo

## 7. PR 7 — HTTP da solicitação

> Goal: as duas partes usam as rotas da solicitação.
> Depends on: PR 6 | Parallel with: —
> Order: test → controller

- [x] 7.1 Criar branch `VAN-30/7-request-http` de dentro da branch do PR 6
- [x] 7.2 Testes slice (`MockMvc` + segurança) de cada rota da tabela do spec: sem token → 401, tipo errado → 403, token de outro → 404, corpo inválido (campo faltando, lista vazia, data passada, JSON de tipo errado e malformado) → 400, e o caminho feliz com o status e o corpo; o filtro com dois status (separados por vírgula e com o parâmetro repetido) e com um status inexistente → 400
- [x] 7.3 `ClientServiceRequestController` (`/api/clients/me/service-requests`), `DriverServiceRequestController` (`/api/drivers/me/service-requests`), finos (regra 7), com `@PreAuthorize("isAuthenticated()")` e o `status` das listas como conjunto
- [x] 7.4 `make lint` + `./mvnw verify -B`
- [x] 7.5 QA da regra 55: cada cenário do spec `service-request` para cliente e motorista do seed, inclusive o envio recusado por prontidão; mau uso da regra 55 (sem token, token inválido, outro cliente, outro motorista, admin, campos faltando e em branco, tipos errados, JSON malformado, `notes` com 1000 e 1001 caracteres, cinco e seis abertas, token desconhecido, filtro por dois status e por um inexistente, nota do motorista na lista e no detalhe do cliente); duas solicitações do mesmo par em paralelo → uma 201 e uma 409, e um vínculo só; uma recusada não deixa linha nem vínculo

## 8. PR 8 — Serviço da proposta

> Goal: enviar e versionar propostas, aceitar, recusar, pedir ajuste e retirar, com a corrida decidida pela escrita condicional. Sem HTTP.
> Depends on: PR 7 | Parallel with: —
> Order: test → request DTO → service → response DTO → mapper → mensagens

- [x] 8.1 Criar branch `VAN-30/8-proposal-service` de dentro da branch do PR 7
- [x] 8.2 Testes do serviço, um por linha das tabelas de recusa do spec `proposal` (cada uma sem nada gravado), mais: primeira versão com `version = 1` e a solicitação `ANSWERED`; segunda versão com o pai e a anterior `SUPERSEDED`; versão depois de uma retirada ou expirada tem o pai certo; perda da corrida no índice da `SENT` → 409 `proposal.sent_conflict`; escrita condicional que não muda linha → 409 `proposal.not_current`; aceite grava `accepted_at`, a solicitação `CONVERTED` e não cria contrato nem muda o vínculo; aceite depois do `valid_until` → 409 `proposal.expired`; recusa leva a solicitação a `WITHDRAWN`; retirada devolve a solicitação a `OPEN` com `expires_at` novo; ajuste sem valor e sem nota → 422; a visão do motorista mostra o dependente só pelo primeiro nome; o aceite recusa com 409 `proposal.accept.stale` em cada linha da tabela de reconferência do spec e passa com nome, telefone ou nascimento corrigidos; a proposta mostra o veículo da foto depois de o motorista editá-lo; a solicitação traz a proposta vigente (a `SENT` da `ANSWERED`, a `ACCEPTED` da `CONVERTED`, `null` nos outros status, com o início proposto e o ajuste pendente só na `SENT`), lida numa consulta só por página; `installmentAmount` com os centavos truncados
- [x] 8.3 Testes da `ProposalAcceptancePolicy`: um por linha da tabela de reconferência do spec, mais o caso sem mudança e o de nome corrigido
- [x] 8.4 `ProposalCreateRequestDTO`, `ProposalItemRequestDTO` (slots com o `ScheduleSlotRequestDTO` existente), `ProposalDeclineRequestDTO`, `ProposalAdjustmentRequestDTO`, com as faixas do D6
- [x] 8.5 `proposal.service.ProposalAcceptancePolicy` pura, que compara as fotos da solicitação e da proposta com o cadastro de agora e devolve o que mudou
- [x] 8.6 `ProposalService`: enviar (termos pela `ContractTermsPolicy` com as chaves `proposal.period.*`, agenda pelo `ScheduleService`, veículo ativo do próprio motorista, gravado na foto), ler, cadeia, aceitar (travando os dependentes e o veículo e reconferindo pela `ProposalAcceptancePolicy`, D19), recusar, pedir ajuste e retirar, com o fechamento da proposta num método que o PR 10 reaproveita para a expiração; as transições de `SENT` só pela escrita condicional (D5)
- [x] 8.7 DTOs de resposta (`ProposalResponseDTO` com `installmentAmount`, itens, slots, o veículo da foto e ajustes; visão do motorista com o primeiro nome do dependente lido da foto da solicitação) e `ProposalMapper`; `ProposalSummaryResponseDTO` como `currentProposal` das duas visões da solicitação, pela projeção `ProposalRepository.CurrentProposalRow` (D22)
- [x] 8.8 Mensagens `proposal.*`, com `proposal.accept.stale`, nos dois `messages*.properties`
- [x] 8.9 Seed: uma proposta `SENT` para a solicitação do seed, pelo `ProposalService`
- [x] 8.10 `make lint` + `./mvnw verify -B`
- [x] 8.11 QA da regra 55: sem rota nova; a lista e o detalhe da solicitação do seed trazem a proposta do seed como vigente; regressão no `qa.md`

## 9. PR 9 — HTTP da proposta

> Goal: as duas partes negociam pelas rotas da proposta.
> Depends on: PR 8 | Parallel with: —
> Order: test → controller

- [x] 9.1 Criar branch `VAN-30/9-proposal-http` de dentro da branch do PR 8
- [x] 9.2 Testes slice de cada rota da tabela do spec `proposal`: 401, 403 para o tipo errado, 404 para token de outro, 400 para corpo inválido, 409 para proposta que não é a vigente, e o caminho feliz; nenhuma rota de edição (`PATCH`/`PUT` → 405); as listas de solicitações das duas partes acompanham a proposta vigente no ajuste, na versão nova, no aceite e na retirada
- [x] 9.3 `DriverProposalController` e `ClientProposalController`, finos, com `@PreAuthorize("isAuthenticated()")`; a cadeia nas rotas `/service-requests/{token}/proposals` das duas partes
- [x] 9.4 `make lint` + `./mvnw verify -B`
- [x] 9.5 QA da regra 55: cada cenário do spec `proposal` para o motorista e o cliente do seed; mau uso (tipos errados, faixas no limite e um passo além, período de 12 meses e 12 meses e um dia, veículo de outro motorista e inativo, itens a mais e a menos, `validDays` 0 e 8); corridas em paralelo: duas versões ao mesmo tempo (uma 201, uma 409, uma `SENT` só) e aceite contra versão nova (exatamente uma vence, nunca `ACCEPTED` e `SENT` juntas); a proposta lida depois do aceite é igual à enviada; reconferência de ponta a ponta: o cliente muda o bairro do dependente depois da proposta e o aceite dá 409 `proposal.accept.stale`, o motorista troca a placa do veículo e o aceite dá 409 com a proposta ainda mostrando a placa enviada, e corrigir o nome do dependente não bloqueia; a proposta vigente nas listas e no detalhe das solicitações (ajuste pendente, aceite, recusa, retirada), a parcela de 1.000,00 em 3 vezes com um início diferente do pedido e a aba "Em aberto" (`OPEN` e `ANSWERED`) numa chamada

## 10. PR 10 — Bloqueio com direção e varredura de prazos

> Goal: as partes bloqueiam e desbloqueiam, e solicitações sem resposta e propostas vencidas expiram sozinhas. Os dois encerram negociações pelos mesmos métodos de fechamento dos PRs 6 e 8, e a varredura nunca briga com a ação das partes.
> Depends on: PR 9 | Parallel with: —
> Order: test → config → service → controller → mensagens

- [x] 10.1 Criar branch `VAN-30/10-block-expiry` de dentro da branch do PR 9
- [x] 10.2 Testes do bloqueio: cliente e motorista bloqueiam (status, `blockedBy`, `blocked_at`); com contrato `SIGNED`, `ACTIVE` ou `SUSPENDED` → 422; já bloqueado → 409; bloquear fecha a solicitação e a proposta com os status da tabela do spec, para cada lado; desbloquear pelo bloqueador volta a `PENDING` sem contrato e a `INACTIVE` com contrato encerrado; pela outra parte → 403 `client_driver.unblock.not_blocker`; não bloqueado → 409; admin e terceiro → 403 nas duas rotas; solicitação a par bloqueado → 422 `service_request.link.blocked`
- [x] 10.3 Testes da varredura com `Clock` fixo: `ProposalService.expireOverdue` expira a proposta vencida e reabre a solicitação com 72 h novas; `ServiceRequestService.expireOverdue` expira a `OPEN` vencida; o `ExpirySweepService` chama a proposta antes da solicitação (uma solicitação `ANSWERED` cuja proposta venceu não expira na mesma rodada); linha que mudou entre a busca e a escrita é pulada sem erro; linha que falha por outro erro é registrada e pulada, e as seguintes expiram; com mais vencidas que o teto, cada passo expira só as `limit` de prazo mais antigo e a rodada seguinte expira o resto; nada vencido, nada muda
- [x] 10.4 `ClientDriverBlockService.block` e `unblock` (status pela `LinkStatusPolicy` no desbloqueio, D10), usando os métodos de fechamento da solicitação e da proposta
- [x] 10.5 `POST /api/client-drivers/{token}/block` e `/unblock` com `@sec.isClientDriverLinkParty`; `blockedBy` (`CLIENT`/`DRIVER`) no `ClientDriverResponseDTO`
- [x] 10.6 Configuração de agendamento com `@EnableScheduling` condicional a `vanep.expiry-sweep.enabled`, e as propriedades no `application.properties` vindas do ambiente: `vanep.expiry-sweep.enabled=${EXPIRY_SWEEP_ENABLED:true}` e `vanep.expiry-sweep.interval=${EXPIRY_SWEEP_INTERVAL:PT1M}` e `vanep.expiry-sweep.batch-size=${EXPIRY_SWEEP_BATCH_SIZE:200}`; `enabled=false` no `application-test.properties`; as três variáveis no `.env.example` (regras 1 e 3)
- [x] 10.7 `expireOverdue(now, limit)` no `ProposalService` e no `ServiceRequestService`, com a busca ordenada pelo prazo e limitada a `limit`, cada linha na própria transação pela escrita condicional, e um erro inesperado numa linha vira `log.warn` sem interromper o passo; `shared.service.ExpirySweepService` com um único `@Scheduled(fixedDelayString = "${vanep.expiry-sweep.interval}")` que chama os dois passos em ordem, passando `vanep.expiry-sweep.batch-size` (D8)
- [x] 10.8 Mensagens `client_driver.block.*`, `client_driver.already_blocked`, `client_driver.not_blocked`, `client_driver.unblock.not_blocker`
- [x] 10.9 `make lint` + `./mvnw verify -B`
- [x] 10.10 QA da regra 55 do bloqueio: cada cenário do spec `client-driver-block`; bloquear durante uma negociação e conferir por SQL os status da solicitação e da proposta; bloqueio repetido e em paralelo; o par bloqueado tenta nova solicitação
- [x] 10.11 QA da regra 55 da varredura, com a API subida com o padrão de 1 minuto (e uma rodada com `EXPIRY_SWEEP_ENABLED=false`, conferindo que nada expira sozinho): `expires_at` e `valid_until` puxados para o passado por SQL; depois de um intervalo, os status e o novo `expires_at` conferidos; aceite de proposta vencida antes da varredura → 409 `proposal.expired`; com `EXPIRY_SWEEP_BATCH_SIZE=2` e três solicitações vencidas, uma rodada expira as duas de prazo mais antigo e a seguinte, a terceira

## 11. PR 11 — E-mail da negociação

> Goal: a contraparte recebe um e-mail em cada passo que pede ação dela, só depois do commit.
> Depends on: PR 10 | Parallel with: —
> Order: test → evento → serviço → templates → mensagens

- [x] 11.1 Criar branch `VAN-30/11-email` de dentro da branch do PR 10
- [x] 11.2 Testes: cada evento da tabela do spec `negotiation-email` gera um envio ao destinatário certo (`MailService` mockado); nenhum envio para expiração, retirada e bloqueio; com o `@TransactionalEventListener`, um envio que falha no serviço (409) não manda e-mail; o e-mail ao motorista tem o primeiro nome do dependente, lido da foto, e não tem o sobrenome; falha do `MailService` não derruba a requisição
- [x] 11.3 Records de evento publicados pelo `ServiceRequestService` e pelo `ProposalService` (só tokens), com `ApplicationEventPublisher`
- [x] 11.4 `NegotiationEmailService` com `@TransactionalEventListener(phase = AFTER_COMMIT)` (D14)
- [x] 11.5 Templates em `src/main/resources/templates/email/` (um por evento, no padrão do `driver-approved`) e os assuntos como chaves nos dois `messages*.properties`
- [x] 11.6 `make lint` + `./mvnw verify -B`
- [x] 11.7 QA da regra 55 com o envio desligado (`VANEP_MAIL_ENABLED=false`, que registra o e-mail no log): cada evento aparece uma vez no log com o destinatário certo; uma requisição recusada não aparece; o log do e-mail ao motorista não tem o sobrenome do dependente

## 12. PR 12 — A solicitação abre a conversa

> Goal: a conversa do par nasce com a primeira solicitação (spike: "a solicitação é a primeira mensagem"), as duas visões da solicitação trazem o `conversationToken`, as listas filtram por ele, e ninguém abre conversa direto (D23).
> Depends on: PR 11 | Parallel with: —
> Order: test → migration → repository → service → response DTO → seeder → mensagens

- [x] 12.1 Criar branch `VAN-30/12-conversation-on-request` de dentro da branch do PR 11
- [x] 12.2 Testes: o envio abre a conversa do par depois de gravar a solicitação, e reaproveita a que existe; envio recusado (validação, 409 do par, corrida no índice) não abre conversa; as leituras e as listas das duas partes trazem o `conversationToken`, com uma consulta por página e nenhuma para página vazia; par sem conversa → `null`; o filtro `conversationToken` lista só o par da conversa nas duas partes, combinado com `status`, e a conversa de outro par ou o token desconhecido devolvem página vazia sem consulta; `findTokensByClientIdInAndDriverIdIn` devolve só os pares pedidos e deixa de fora a conversa removida; `findPageByPairAndStatusIn` pagina só o par; `findPairByToken` devolve o par ou nada; slice: o envio devolve a conversa gravada, uma nova solicitação ao mesmo motorista mantém a conversa, o motorista escreve na conversa do pedido, o filtro pela conversa nas duas listas (outro par e token desconhecido → página vazia), e o `POST /api/conversations` responde 404 mesmo com a permissão antiga no token
- [x] 12.3 `V66`: tira `create_conversation` de todos os bundles e abre a conversa dos pares que têm solicitação e não têm conversa, idempotente
- [x] 12.4 `ConversationRepository.findTokensByClientIdInAndDriverIdIn` e `ServiceRequestRepository.findPageByPairAndStatusIn`; `ConversationService.findOrCreateForRequest`, `findTokensOfPairs` e `findPairByToken`; sai `openConversation`
- [x] 12.5 `ServiceRequestService` abre a conversa no `send` e preenche o `conversationToken` nas leituras e nas listas, e as duas listas aceitam `?conversationToken=`; `conversationToken` nos dois DTOs de resposta e no mapper
- [x] 12.6 Sai o `POST /api/conversations`, o `ConversationCreateRequestDTO`, `CREATE_CONVERSATION` do `PermissionEnum` e do bundle `CLIENT` do seeder, e as mensagens `conversation.client.not_found` e `conversation.driver.not_found` (regra 34)
- [x] 12.7 `make lint` + `./mvnw verify -B`
- [x] 12.8 QA da regra 55 (`conversation.sh`): a `V66` no banco (permissão, conversa do pedido sem conversa, idempotência); o `POST` direto para cliente, motorista e sem token; solicitações recusadas sem conversa; a conversa nas duas visões, nas listas e nas duas caixas de entrada; o filtro pela conversa nas duas listas, com `status`, com a conversa de outro par e com token desconhecido; o motorista escreve primeiro; a conversa depois da retirada e da recusa, e uma nova solicitação do par com a mesma conversa; seis primeiras solicitações em paralelo com uma conversa só. O `qa.sh` do `client-driver-chat` passa a inserir as conversas por SQL, e a regressão roda o `qa.sh` e o `email.sh` desta change
