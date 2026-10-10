> **Fase 1 do spike "Propostas e Contratos" (V2).** Esta change é dividida em 9 PRs pelas regras 36 a 43, **em sequência** sobre a branch `spec/contract-core`, que só traz esta change do openspec: cada branch nasce da anterior e cada PR aponta a anterior como `--base` (o PR 1 aponta para a `spec/contract-core`). A seção de cada PR tem o número dele. Ver o grafo no fim do `design.md`.
>
> ```
> main ─ spec ─ PR 1 ─ PR 2 ─ PR 3 ─ PR 4 ─ PR 5 ─ PR 6 ─ PR 7 ─ PR 8 ─ PR 9
> ```

> [!IMPORTANT]
> ## `absence` (#160) entra depois desta change e se adapta à convenção do D11
>
> A #160 (`feat/absence-*`, `V52__create_absence_table.sql`) só vai para a `main` depois desta change, combinado com o autor. Esta change não mexe na `absence`: é a branch dela que se ajusta ao entrar.
>
> - **Tipos de turno (D1):** `Shift` deixa de existir. O lado da trip usa `OperationShift` (`AbsenceLegShiftPolicy.uniqueLegOf`, sem o caso vazio do `FULLTIME`) e o lado do dependente usa `SchoolShift` (`AbsenceScopePolicy`)
> - **Trecho (D2):** `absence.enums.AbsenceLeg` dá lugar a `shared.enums.RouteLeg`, criado no PR 2. Os valores são os mesmos, então a coluna `absence.leg` não muda
> - **Referência ao passageiro (D11):** `contract_item_id` **ou** `unlinked_passenger_id`, com o `CHECK` de exatamente um, sem `client_driver_id`, `dependent_id` nem `contract_id`, e a unicidade em dois índices únicos parciais por `(passageiro, absence_date, leg)`. O cliente informa a ausência pelo dependente e o serviço resolve para o `contract_item` `ACTIVE` dele no trecho e na data; o no-show do motorista vale também para aluno não vinculado; o aviso ao responsável só existe para item de contrato (D9)
> - **Migration (R7):** a `V52` fica abaixo da nossa última (`V57`) e o Flyway recusa subir (`outOfOrder` desligado), inclusive no deploy do `cd.yml`. Renumerar para depois da última da `main`
>
> A #151 (`checklist_entry`) segue a mesma convenção. Detalhes no D11 e no R9 do `design.md`.

> [!WARNING]
> **Avaliação de motorista fica bloqueada depois da `V57` (PR 7) — efeito aceito.** Desde a #158 (`dd5f814`), o `DriverRatingService` só aceita vínculo `ACTIVE` (`422 driver_rating.link.not_active`). A `V57` devolve a `PENDING` todo vínculo `ACTIVE` sem contrato, então nenhum cliente avalia motorista até o admin cadastrar o contrato do par. Efeito aceito (R2 do `design.md`); registrar no PR 7.

## Preparação

- [x] Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] Revisar `proposal.md`, `design.md` e os seis specs (`operation-shift`, `contract-schedule`, `contract-core`, `client-driver-derived-status`, `unlinked-passengers`, `route-passengers`)
- [x] **Confirmar os números de Flyway** em `main` e em **todas as branches remotas** (`git ls-tree`). Esta change usa `V53` a `V57`. **Reconfirmar antes do merge de cada PR com migration** (R7)
- [x] Branch `spec/contract-core` a partir da `main`, só com `openspec/changes/contract-core/`. É a base da pilha; a branch `spike/contract-proposal` não vai para a `main`

## 1. PR 1 — Turno escolar e turno da operação

> Goal: `trip` só aceita turno de operação; `dependent` continua igual para o cliente. Sem contrato ainda.
> Depends on: `spec/contract-core` | Parallel with: —
> Order: test → migration → enum → ajustes

- [x] 1.1 Criar branch `feat/contract-core-shift-split` a partir da `spec/contract-core` (o PR aponta para ela como `--base`)
- [x] 1.2 Testes: `POST /api/drivers/me/trips/start` com `FULLTIME` → 400 e nenhuma trip; com `MORNING` → 200 (já coberto, continua); `POST /api/trips` e `PATCH /api/trips/{token}` do admin com `FULLTIME` → 400 (o `PATCH` mantém o turno gravado); dependent com `FULLTIME` continua 201, devolve e grava `FULLTIME`; repositório de trip grava e lê `OperationShift` (testes existentes, com o tipo trocado)
- [x] 1.3 Migration `V53__trip_operation_shift.sql`: `update trip set shift = 'MORNING' where shift = 'FULLTIME'`; `comment on column trip.shift` (turno da operação, sem `FULLTIME`); `comment on column dependent.shift` (turno escolar; dias e horários vêm da agenda do contrato — A1)
- [x] 1.4 Renomear `shared.enums.Shift` para `SchoolShift` e ajustar `DependentModel`, `DependentMapper`, DTOs, `DependentService` e `DependentSeeder` (refactor mecânico, JSON igual)
- [x] 1.5 Criar `shared.enums.OperationShift` (`MORNING`, `AFTERNOON`, `NIGHT`) e trocar o tipo em `TripModel`, `TripRepository`, DTOs de trip, `TripService` e `TripSeeder`
- [x] 1.6 Conferir que a `absence` (#160) não está na `main`: ela entra depois desta change e troca os tipos de turno nas policies dela ao entrar (aviso no topo)
- [x] 1.7 Aplicar a `V53` num PostgreSQL descartável com trips `FULLTIME` e `AFTERNOON` semeadas à mão: `FULLTIME` vira `MORNING`, `AFTERNOON` fica intacta, e os comments de `trip.shift` e `dependent.shift` são gravados (R1)
- [x] 1.8 `make lint` + `./mvnw verify -B`

## 2. PR 2 — Schema da agenda e do aluno não vinculado

> Goal: `schedule`, `schedule_slot` e `unlinked_passenger` existem, com a unicidade garantida pelo banco. Sem serviço, sem HTTP.
> Depends on: PR 1 | Parallel with: —
> Order: test → migration → model → repository

- [x] 2.1 Criar branch `feat/contract-core-schedule-schema` de dentro da branch do PR 1
- [x] 2.2 Testes de repositório: salvar agenda com slots e reler; aluno não vinculado por motorista, com escola, endereço e slots carregados fora da transação (prova o fetch join, `open-in-view` desligado); aluno de outro motorista não aparece; token só resolve dentro do motorista; token opaco gerado no persist; soft delete esconde a linha e mantém no banco, e o do aluno leva junto a agenda e os slots. A ordem dos slots não vem do banco: fica no `ScheduleMapper` (tarefa 5.10)
- [x] 2.3 Migration `V54__create_schedule_and_unlinked_passenger.sql`:
    - `schedule` (`id`, `created_at`, `updated_at`, `deleted_at`)
    - `schedule_slot` (`id`, `schedule_id` FK, `weekday varchar(16)`, `leg varchar(16)`, `shift varchar(16)`, `window_start time not null`, `window_end time`, timestamps, `deleted_at`), `check (window_end is null or window_end > window_start)`
    - `unlinked_passenger` (`id`, `token`, `driver_id` FK, `name`, `school_id` FK, `school_shift`, `address_id` FK, `notes`, `schedule_id` FK, timestamps, `deleted_at`)
    - índices do `design.md` (Migration Plan): único `(schedule_id, weekday, leg)`, `(schedule_id, weekday, shift)`, `(driver_id)`, único `(address_id)`, único `(schedule_id)`, único `(token)` — todos `where deleted_at is null`
    - comments explicando o "porquê" de cada tabela (dono único da agenda, aluno sem contrato e sem aviso)
- [x] 2.4 **Trecho da agenda = `shared.enums.RouteLeg`** (`OUTBOUND`, `RETURN`), sem criar `ScheduleLeg` (D2, regras 5 e 6). Criar `RouteLeg` aqui; a `absence` troca o `AbsenceLeg` por ele ao entrar (aviso no topo)
- [x] 2.5 Criar `schedule.model.ScheduleModel` e `ScheduleSlotModel` (`@SoftDelete`, `weekday` como `DayOfWeek` com `@Enumerated(STRING)`, `shift` como `OperationShift`)
- [x] 2.6 Criar `unlinkedpassenger.model.UnlinkedPassengerModel` (`@SoftDelete`, token no `@PrePersist` como os demais models)
    - todo to-one `EAGER`, como no resto do repo; N+1 evitado pelo fetch join das consultas
    - endereço como `@ManyToOne` (o dono único é o índice parcial); agenda como `@OneToOne` com `cascade = ALL`
- [x] 2.7 Criar `ScheduleRepository` e `UnlinkedPassengerRepository` (busca por token **dentro do motorista**, lista por motorista com fetch join de escola, endereço e slots — regra 17). Sem `ScheduleSlotRepository`: toda escrita de slot passa pelo `ScheduleModel` (`cascade = ALL`, `orphanRemoval = true`)
- [x] 2.8 Acrescentar `schedule_slot`, `schedule` e `unlinked_passenger` ao `src/test/resources/db/clean.sql` na ordem de FK
- [x] 2.9 **Aplicar a `V54` num PostgreSQL descartável e provar cada índice** com inserts manuais: slot duplicado no mesmo `(schedule, weekday, leg)` recusado; ida e volta no mesmo dia aceitas; slot soft-deletado e recriado aceito; `window_end` antes do início recusado; dois donos no mesmo `schedule` ou no mesmo endereço recusados; token repetido recusado; dono soft-deletado libera endereço e agenda; a consulta por `(schedule_id, weekday, shift)` usa o índice (R1)
- [x] 2.10 `make lint` + `./mvnw verify -B`

## 3. PR 3 — Schema do contrato

> Goal: `contract` e `contract_item` existem, com um `ACTIVE` por vínculo garantido pelo banco.
> Depends on: PR 2 | Parallel with: —
> Order: test → migration → model → repository

- [x] 3.1 Criar branch `feat/contract-core-contract-schema` de dentro da branch do PR 2
- [x] 3.2 Testes de repositório: contrato com dois itens, lidos com escola, endereço copiado e slots; contrato por token com o vínculo e as duas partes; contratos e status de um vínculo; contrato `ACTIVE` de um vínculo; slots de um dependente nos contratos `ACTIVE`; página com total; tokens opacos; soft delete esconde contrato, itens, agendas e slots e mantém as linhas
- [x] 3.3 Migration `V55__create_contract_tables.sql`:
    - `contract` (`id`, `token`, `client_driver_id` FK, `status varchar(24)`, `starts_on`, `ends_on`, `total_amount numeric(12,2)`, `installments smallint`, `due_day smallint`, `supersedes_contract_id` FK nula, timestamps, `deleted_at`) com `check (ends_on > starts_on)`, `check (total_amount > 0)`, `check (installments between 1 and 12)`, `check (due_day between 1 and 28)`
    - `contract_item` (`id`, `token`, `contract_id` FK, `dependent_id` FK, `school_id` FK, `pickup_city_id` FK, `pickup_zip_code`, `pickup_street`, `pickup_number`, `pickup_complement`, `pickup_neighborhood`, `pickup_district_id` FK, `pickup_google_place_id`, `monthly_amount numeric(12,2)`, `schedule_id` FK, timestamps, `deleted_at`) com `check (monthly_amount > 0)`
    - índices do `design.md`: único `(client_driver_id) where status = 'ACTIVE'`, `(client_driver_id)`, `(starts_on) where status = 'SIGNED'`, `(ends_on) where status = 'ACTIVE'`, `contract_item (contract_id)`, `(dependent_id)`, único `(schedule_id)`, únicos de `token` — todos com `deleted_at is null`
    - comments: contrato é o acordo (o documento é outra tabela, fase 3); endereço do item é cópia deliberada
- [x] 3.4 Criar `contract.enums.ContractStatus` com os oito valores (D4)
- [x] 3.5 Criar `contract.model.ContractModel` e `ContractItemModel` (`@SoftDelete`; o endereço copiado como colunas `pickup_*`, sem `@ManyToOne` para `address`). `items` como `Set` com `cascade = ALL`; `supersedes_contract_id` como `Long`, sem associação
- [x] 3.6 Criar `ContractRepository` (por token com fetch join do vínculo e partes; por vínculo; `ACTIVE` por vínculo; status de todos os contratos de um vínculo; página para o admin) e `ContractItemRepository` (itens de um contrato; slots de um dependente, para a colisão do D6)
- [x] 3.7 Acrescentar `contract_item` e `contract` ao `clean.sql` antes de `schedule_slot`/`schedule` e de `client_driver`/`dependent`
- [x] 3.8 **Aplicar a `V55` num PostgreSQL descartável e provar** o único `ACTIVE` por vínculo (segundo `ACTIVE` recusado; `ENDED` + `ACTIVE` aceito; `ACTIVE` soft-deletado + novo `ACTIVE` aceito), os `CHECK`s e a agenda única por item (R1). O `contract_active_ends_on_idx` só se confirma com volume de dados (fase 8)
- [x] 3.9 `make lint` + `./mvnw verify -B`

## 4. PR 4 — Policies puras

> Goal: as regras decidem sozinhas, sem Spring, JPA ou servlet (regras 8 e 9). Nada é fiado ainda.
> Depends on: PR 3 | Parallel with: —
> Order: test → request DTO → policy

- [x] 4.1 Criar branch `feat/contract-core-policies` de dentro da branch do PR 3
- [x] 4.2 Teste de `ContractTransitionPolicy` com a **matriz inteira** (64 pares), aceitando só as transições do D4; terminais não saem para lugar nenhum
- [x] 4.3 Implementar `contract.service.ContractTransitionPolicy`
- [x] 4.4 Teste de `ContractTermsPolicy` — **só as regras que cruzam campos** (D5): período de exatamente 12 meses aceito; 12 meses + 1 dia recusado (`contract.period.too_long`); ano letivo julho–junho aceito; `ends_on` igual a `starts_on` ou anterior recusado (`contract.period.invalid`). As faixas de `installments`, `due_day` e `total_amount` **não** entram na policy: são Bean Validation no DTO (tarefa 5.6). Devolve `Optional` de uma violação cujo enum carrega a chave de MessageSource (padrão `TripCoherencePolicy`)
- [x] 4.5 Implementar `contract.service.ContractTermsPolicy`
- [x] 4.6 Teste de `LinkStatusPolicy` com a tabela do D8: sem contrato → `PENDING`; só `ENDED` → `INACTIVE`; `ENDED` + `ACTIVE` → `ACTIVE`; só `SUSPENDED` → `ACTIVE`; só `SIGNED` → `ACTIVE`; `BLOCKED` permanece `BLOCKED` em todos os casos
- [x] 4.7 Implementar `clientdriver.service.LinkStatusPolicy`
- [x] 4.8 Teste de `ScheduleSlotPolicy`: lista vazia recusada; `(weekday, leg)` repetido recusado; `window_end` anterior ou igual a `window_start` recusado; `window_end` nulo aceito; integral (ida `MORNING`, volta `AFTERNOON` no mesmo dia) aceito
- [x] 4.9 Criar `schedule.dto.ScheduleSlotRequestDTO` (`weekday`, `leg`, `shift` obrigatórios; `windowStart` obrigatório; `windowEnd` opcional) — compartilhado pelo contrato e pelo aluno não vinculado
- [x] 4.10 Implementar `schedule.service.ScheduleSlotPolicy` sobre `List<ScheduleSlotRequestDTO>`, devolvendo `Optional<ScheduleSlotViolation>` com a chave de MessageSource (as chaves entram na tarefa 5.7)
- [x] 4.11 `make lint` + `./mvnw verify -B`

## 5. PR 5 — Serviço do contrato, permissões e acesso das partes

> Goal: o contrato é criado, lido, corrigido, removido e restaurado pela camada de serviço, e o vínculo acompanha. Sem controller ainda.
> Depends on: PR 4 | Parallel with: —
> Order: test → migration → security/authorization → request DTO → messages → service → response DTO → mapper

- [x] 5.1 Criar branch `feat/contract-core-service` de dentro da branch do PR 4
- [x] 5.2 Testes unitários de `ContractService` (Mockito):
    - criar com dois irmãos → contrato `ACTIVE`, itens com escola e endereço **copiados**, vínculo recalculado para `ACTIVE`
    - dependente de outro cliente → 422 `contract.item.dependent_not_in_link`
    - dependente sem escola / sem endereço → 422 `contract.item.school_required` / `contract.item.address_required`
    - dependente repetido no mesmo contrato → 400 `contract.item.duplicate_dependent`
    - colisão de `(weekday, leg)` com outro contrato assinado e não encerrado (`SIGNED`, `ACTIVE` ou `SUSPENDED`) do mesmo dependente → 409 `contract.item.slot_conflict`; ida com A e volta com B → aceito
    - vínculo já com `ACTIVE` → 409 `contract.active_conflict`
    - motorista não aprovado → 422 `contract.driver.not_approved`; vínculo `BLOCKED` → 422 `contract.link.blocked`
    - período invertido ou acima de 12 meses → a chave da `ContractTermsPolicy` (422)
    - `PATCH` `ACTIVE → ENDED` → vínculo `INACTIVE`; `ACTIVE → SUSPENDED` → vínculo continua `ACTIVE`; `ENDED → ACTIVE` → 409 `contract.status.invalid_transition`; `SUPERSEDED` → 422 `contract.status.requires_successor`; `status: null` → 400 `contract.status.required`; `SUSPENDED → ACTIVE` com outro `ACTIVE` no par → 409 `contract.active_conflict`; `PATCH` vazio não grava nada
    - remover → vínculo recalculado; restaurar → itens, agendas e slots de volta e vínculo recalculado; restaurar com outro `ACTIVE` no par → 409 `contract.active_conflict`; restaurar com horário tomado por outro contrato assinado e não encerrado → 409 `contract.item.slot_conflict`; restaurar não colide com a própria agenda; restaurar contrato encerrado não confere colisão
- [x] 5.3 Migration `V56__admin_manages_contracts.sql`: acrescentar as seis permissões ao bundle `ADMIN`, idempotente, no padrão da `V48`. Provar num PostgreSQL descartável: bundle parcial recebe só as que faltam, `CLIENT` intocado, reaplicar não muda nada
- [x] 5.4 Acrescentar `LIST_CONTRACTS`, `SHOW_CONTRACT`, `CREATE_CONTRACT`, `UPDATE_CONTRACT`, `DELETE_CONTRACT`, `RESTORE_CONTRACT` ao `PermissionEnum` (o bundle `ADMIN` do seeder já sincroniza com o registro)
- [x] 5.5 Acrescentar `isContractParty(String token, Authentication authentication)` ao `SecurityEvaluator`, resolvendo o token do vínculo pelo contrato e reaproveitando a comparação do `isClientDriverLinkParty` (regra 22). Teste unitário: cliente do vínculo → true; motorista do vínculo → true; outro cliente → false; token inexistente → false
- [x] 5.6 Criar `ContractCreateRequestDTO` (vínculo por token, datas `@NotNull`, `totalAmount` `@DecimalMin(value = "0", inclusive = false)`, `installments` `@Min(1) @Max(12)`, `dueDay` `@Min(1) @Max(28)` — cada uma com `message = "{chave}"`, padrão do `DriverUpdateRequestDTO` — e itens `@NotEmpty`), `ContractItemRequestDTO` (dependente por token, `monthlyAmount` maior que zero e slots do `ScheduleSlotRequestDTO`) e `ContractUpdateRequestDTO` (`JsonNullable<ContractStatus> status`, construtor compacto com `undefined()`)
- [x] 5.7 Chaves de MessageSource (EN + pt-BR) para todas as mensagens acima, mais `contract.not_found`, `contract.period.too_long`, `contract.period.invalid`, `contract.amount.invalid`, `contract.installments.invalid`, `contract.due_day.invalid`, `contract.items.required` (estas quatro usadas pelas anotações dos DTOs da tarefa 5.6, `message = "{...}"`), `schedule.slot.duplicate`, `schedule.slot.window_invalid`, `schedule.slots.required` (regra 46)
- [x] 5.8 Implementar `schedule.service.ScheduleService` (`create(slots)` e `replace(schedule, slots)`, aplicando a `ScheduleSlotPolicy`; `replace` soft-deleta os slots antigos na mesma transação) com testes unitários próprios
    - `replace` = `schedule.getSlots().clear()` + `flush` + `addSlot` dos novos + `save` da agenda; o `orphanRemoval` soft-deleta os antigos antes dos inserts. Sem repositório de slot (tarefa 2.7)
    - **Provar num PostgreSQL descartável** (o H2 não tem o índice parcial): trocar uma agenda com "segunda, ida" por outra com "segunda, ida" em outro horário, pelo `ScheduleService`, passa sem violar `schedule_slot_weekday_leg_active_key`
- [x] 5.9 Implementar `contract.service.ContractService` (criar, buscar, listar por vínculo, página, mudar status, remover, restaurar). O recálculo do vínculo usa a `LinkStatusPolicy` e roda na mesma transação de toda escrita (D8)
    - `ContractStatus.SIGNED_AND_NOT_ENDED` (`SIGNED`, `ACTIVE`, `SUSPENDED`) é o conjunto usado pela `LinkStatusPolicy` e pela colisão de horários do D6
    - `ContractItemRepository`: itens de vários contratos numa consulta (`findByContractIdIn`), para página e lista sem N+1; slots de um dependente por status do contrato (`findSlotsByDependentIdAndContractStatusIn`), com a agenda junto
    - a colisão de `(weekday, leg)` vale na criação e na restauração de um contrato assinado e não encerrado, ignorando a própria agenda
    - toda transição para `ACTIVE` confere o `ACTIVE` do par antes de gravar
    - restaurar volta contrato, itens, agendas e slots por SQL nativo (padrão do `restoreByToken`), com o conflito de `ACTIVE` conferido antes do `UPDATE`
    - provar os testes de repositório de contrato e agenda num PostgreSQL descartável com as migrations até a `V56`
- [x] 5.10 Criar `schedule.dto.ScheduleSlotResponseDTO` e `schedule.mapper.ScheduleMapper` — compartilhados pelas duas superfícies HTTP (PR 6 e PR 8). O mapper devolve os slots ordenados por dia (`DayOfWeek`) e trecho (`OUTBOUND` antes de `RETURN`), com teste: o banco não ordena o `weekday`, que é texto (tarefa 2.2)
- [x] 5.11 Criar `ContractResponseDTO`, `ContractItemResponseDTO` e `ContractMapper` (slots pelo `ScheduleMapper` da tarefa 5.10) (nunca devolver model — regra 12)
- [x] 5.12 `make lint` + `./mvnw verify -B`

## 6. PR 6 — HTTP do contrato

> Goal: CRUD do admin e leitura das partes respondem.
> Depends on: PR 5 | Parallel with: —
> Order: test → controller

- [x] 6.1 Criar branch `feat/contract-core-contract-http` de dentro da branch do PR 5
- [x] 6.2 Testes MockMvc com segurança: sem JWT → 401; admin cria → 201; cliente cria → 403; parte lê → 200; outro cliente lê → 403; parte lista os contratos do vínculo → 200; motorista faz `PATCH` → 403; admin faz `PATCH` `ENDED` → 200; `DELETE` → 204 e some da listagem; `restore` → 200; corpo sem `endsOn` → 400; slot sem `shift` → 400; `dueDay = 31`, `installments = 13` e `totalAmount = 0` → 400 com a mensagem da chave (D5); `endsOn` igual a `startsOn` → 422
- [x] 6.3 **Teste nomeado do PATCH vazio** (regra 16): corpo `{}` devolve 200 e nenhum campo muda
- [x] 6.4 **Teste nomeado de não vazamento:** nenhuma resposta contém `id`, só `token` (regra 13)
- [x] 6.5 Criar `contract.controller.ContractController` em `/api/contracts` (`POST`, `GET` paginado, `GET /{token}`, `PATCH /{token}`, `DELETE /{token}`, `POST /{token}/restore`) e `contract.controller.ClientDriverContractController` em `GET /api/client-drivers/{token}/contracts`, com as autorizações do D12
- [x] 6.6 Confirmar no `SecurityConfig` que nenhuma rota nova fica pública por omissão (regras 20 e 21)
- [x] 6.7 `make lint` + `./mvnw verify -B`

## 7. PR 7 — Vínculo com status derivado e saneamento

> Goal: não existe mais caminho de escrita direta do status do vínculo, e o banco fica coerente com a regra.
> Depends on: PR 6 | Parallel with: —
> Order: test → migration → security → request DTO → service → controller → seed

- [x] 7.1 Criar branch `feat/contract-core-client-driver-status` de dentro da branch do PR 6
- [x] 7.2 Testes: `POST /api/client-drivers` com `"status": "ACTIVE"` → 201 e `PENDING`; `PATCH /api/client-drivers/{token}` → 405; seeder em banco vazio → vínculo com contrato `ACTIVE` e status `ACTIVE`
- [x] 7.3 Testes de avaliação: conferir que os testes da #158 criam o vínculo `ACTIVE` direto no repositório, sem depender do `ClientDriverSeeder`, e que o caso "vínculo `PENDING` → 422 `driver_rating.link.not_active`" continua coberto e passa a cobrir o efeito da `V57`
- [x] 7.4 Migration `V57__client_driver_status_follows_contracts.sql`: vínculo `ACTIVE` sem contrato → `PENDING`; `update role_permissions set permissions = permissions - 'update_client_driver'`. Comment em `client_driver.status` (derivado dos contratos; `BLOCKED` é o único valor manual, fase 2)
- [x] 7.5 Remover `UPDATE_CLIENT_DRIVER` do `PermissionEnum`
- [x] 7.6 Remover `status` do `ClientDriverCreateRequestDTO`; remover `ClientDriverUpdateRequestDTO`
- [x] 7.7 `ClientDriverService.create` grava sempre `PENDING`; remover `ClientDriverService.update` e a chave `client_driver.status.required` se ficar sem uso (regra 34)
- [x] 7.8 Remover o `@PatchMapping` do `ClientDriverController`
- [x] 7.9 `ClientDriverSeeder` cria o vínculo `PENDING`; criar `contract.seed.ContractSeeder` (seeder por feature, não no `DataSeeder`) que cria pelo `ContractService.create` um contrato `ACTIVE` do vínculo semeado com um item para o dependente do cliente — ida e volta seg–sex, `MORNING`/`AFTERNOON` — e pula com log quando o dependente não tem escola ou endereço; criar `school.seed.SchoolSeeder`, que cria a escola de seed em São Paulo (IBGE `3550308`; sem a cidade, pula com log), e o `DependentSeeder` passa a criar os dependentes nessa escola, cada um com endereço completo (CEP, rua, número e bairro) na cidade dela; chamar o `SchoolSeeder` antes do `DependentSeeder` e o `ContractSeeder` no `DataSeeder` depois do `clientDriverSeeder`. O `GeographicDataSeeder` roda antes do `DataSeeder` (`@Order`), para o catálogo de cidades já existir no primeiro boot
- [x] 7.10 Aplicar a `V57` num PostgreSQL descartável com um vínculo `ACTIVE` sem contrato e conferir que vira `PENDING`
- [x] 7.11 **Registrar na descrição do PR** o efeito aceito (aviso no topo, R2): depois da `V57`, a avaliação de motorista fica bloqueada até o admin cadastrar o contrato de cada par
- [x] 7.12 `make lint` + `./mvnw verify -B`

## 8. PR 8 — Aluno não vinculado: serviço e HTTP

> Goal: o motorista cria, lê, atualiza, troca a agenda e remove os próprios alunos não vinculados por `/api/drivers/me/unlinked-passengers`.
> Depends on: PR 7 | Parallel with: —
> Order: test → request DTO → messages → service → response DTO → mapper → controller

- [x] 8.1 Criar branch `feat/contract-core-unlinked-service` de dentro da branch do PR 7
- [x] 8.2 Testes unitários de `UnlinkedPassengerService` e dos métodos novos do `AddressService`: criar com endereço e agenda; motorista em análise cria; usuário `CLIENT` → 403; escola inexistente → 404 `unlinked_passenger.school.not_found`; agenda inválida → 400 antes de gravar o endereço; token de outro motorista → 404 `unlinked_passenger.not_found`; `PATCH` só de `notes` mantém o resto; `name` nulo ou em branco → 400; `schoolToken`, `schoolShift` ou `address` nulos → 400 `unlinked_passenger.field.null`; trocar a agenda mantém a mesma agenda com os slots novos; remover é soft delete e libera o endereço para o dono único
- [x] 8.3 Criar `UnlinkedPassengerCreateRequestDTO` (endereço pelo `DependentAddressRequestDTO`, slots pelo `ScheduleSlotRequestDTO` do PR 4), `UnlinkedPassengerUpdateRequestDTO` (`JsonNullable` em `name`, `schoolToken`, `schoolShift`, `address`, `notes`, construtor compacto com `undefined()`) e `UnlinkedPassengerScheduleRequestDTO` (corpo do `PUT /schedule`)
- [x] 8.4 Chaves de MessageSource (EN + pt-BR): `unlinked_passenger.not_found`, `unlinked_passenger.name.required`, `unlinked_passenger.name.too_long`, `unlinked_passenger.notes.too_long`, `unlinked_passenger.school.not_found`, `unlinked_passenger.field.null`
- [x] 8.5 Acrescentar `AddressService.upsertForUnlinkedPassenger` e `clearForUnlinkedPassenger`, reaproveitando o fluxo por catálogo de `upsertForDependent` (regra 6)
- [x] 8.6 Implementar `unlinkedpassenger.service.UnlinkedPassengerService` resolvendo o motorista com `requireByTokenAndType(uid, UserType.DRIVER)`. Sem gate de aprovação: o motorista em análise pode preparar a rota, e quem bloqueia a operação é o início da trip (RN-02)
- [x] 8.7 Criar `UnlinkedPassengerResponseDTO` e `UnlinkedPassengerMapper` (endereço pelo `AddressMapper`, slots pelo `ScheduleMapper` do PR 5)
- [x] 8.8 Testes MockMvc: sem JWT → 401; `CLIENT` → 403; motorista cria → 201; lista → só os dele; `GET` de outro motorista → 404; `PATCH` parcial → 200; `PUT /schedule` → 200 com os slots novos; `DELETE` → 204; corpo com `birthDate` não persiste nada além dos campos do D9
- [x] 8.9 **Teste nomeado do PATCH parcial** (regra 16): só `notes` → nome, escola, turno, endereço e agenda inalterados
- [x] 8.10 Criar `unlinkedpassenger.controller.UnlinkedPassengerController` com `@PreAuthorize("isAuthenticated()")`, no padrão do `DriverServiceAreaController`
- [x] 8.11 `make lint` + `./mvnw verify -B`

## 9. PR 9 — Passageiros da rota

> Goal: a #151 tem de onde tirar a lista de passageiros.
> Depends on: PR 8 | Parallel with: —
> Order: test → repository → service

- [x] 9.1 Criar branch `feat/contract-core-route-passengers` de dentro da branch do PR 8
- [x] 9.2 Testes (repositório + serviço): item de contrato `ACTIVE` e aluno não vinculado no mesmo slot → dois passageiros, com `source` diferente; contrato `SUSPENDED` → nenhum; contrato `ACTIVE` com `ends_on` passado → nenhum; `starts_on` futuro → nenhum; outro turno → vazio; outro dia da semana → vazio; soft-deletado → nenhum; contagem de queries constante com N passageiros (regra 17), medida pelo `Statistics` do Hibernate; serviço junta as duas fontes e ordena pela janela
- [x] 9.3 Acrescentar as duas consultas de passageiros (por motorista, dia da semana, turno e data) a `ContractItemRepository` e `UnlinkedPassengerRepository`, cada uma projetando direto no `RoutePassengerDTO` (`select new`, com join até slot, escola e endereço): um statement por fonte, sem carregar entidades nem as associações EAGER
- [x] 9.4 Criar `routepassenger.enums.PassengerSource` (`CONTRACT_ITEM`, `UNLINKED_PASSENGER`) e `routepassenger.dto.RoutePassengerDTO`
- [x] 9.5 Implementar `routepassenger.service.RoutePassengerQueryService.findPassengers(driver, serviceDate, OperationShift)`, ordenando por `window_start` (Q2)
- [x] 9.6 `make lint` + `./mvnw verify -B`
