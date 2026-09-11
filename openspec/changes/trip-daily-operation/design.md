## Context

`trip` é a execução diária da rota de um motorista (UC08). O BRD §13.5 desenha o ciclo como `[Rota não iniciada] → "Iniciar rota" → [Em rota — checklist ativo] → "Finalizar rota" → [Rota finalizada]`, e o modelo canônico (`vanep-diagram.dbml`) já fixa colunas e o índice único `(driver_id, service_date, shift)`.

O que existe hoje no repositório e importa para esta change:

- `DriverModel` tem `approvalStatus` (`PENDING`/`APPROVED`/`REJECTED`), `workStartTime`, `workEndTime`, `workDays` (jsonb) e `waitToleranceMinutes` — herdados do V3, nunca lidos por regra de negócio
- `Shift` (`MORNING`, `AFTERNOON`, `NIGHT`, `FULLTIME`) existe em `br.com.vanep.dependent.enums`, usado só por dependent
- O padrão `/me` já está estabelecido em `DriverServiceAreaController`: recurso resolvido do `Authentication`, `@PreAuthorize("isAuthenticated()")`, sem token na URL
- `SecurityEvaluator` (`@sec`) tem seis métodos `is<Entity>Owner`, todos para recursos endereçados por token na URL

Nada de `checklist_entry`, `absence`, `route` ou `contract` existe. Esta change entrega a âncora e precisa **não fechar portas** para eles.

## Goals / Non-Goals

**Goals**

- Uma linha `trip` por `(motorista, data, turno)`, garantida pelo banco
- Transições legais provadas por teste sem banco
- Início e fim idempotentes sob chamada repetida e sob corrida
- O app do motorista consegue perguntar "em que estado está a operação de hoje?" numa chamada

**Non-Goals**

- Tracking de localização, push, checklist, ausência, ajuste de parada, geometria de rota
- Recálculo de rota, ETA e qualquer coisa que dependa de `route` (#45)

## Decisions

### D1 — `trip_status` segue o DBML canônico: `COMPLETED`, não `FINISHED`

O texto da issue #150 diz `SCHEDULED → IN_PROGRESS → FINISHED`. O `vanep-diagram.dbml` define `Enum trip_status { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }`. **Vale o DBML.**

Rejeitado: seguir o texto da issue. O precedente é caro e recente — o `V16__create_driver_rating_table.sql` ligou `driver_rating` direto em `driver_id` + `client_id` enquanto o canônico manda pendurar em `client_driver_id`. A divergência passou despercebida, está em produção, e hoje é uma migration de backfill esperando para ser escrita. Divergir do DBML por conveniência de texto de issue já custou uma vez.

`CANCELLED` entra no enum desde o início mesmo sem endpoint que o produza: acrescentar valor a um enum Java é trivial, mas o `check` da coluna e o `ALTER TYPE` correspondente exigem migration nova. Custo zero agora, migration a menos depois.

### D2 — Início fora da janela de expediente registra, não bloqueia

O UC08 lista "dentro do horário de expediente" como **pré-condição**, não como validação. A decisão é permitir e marcar.

`WorkWindowPolicy` recebe `workDays`, `workStartTime`, `workEndTime` e o instante do início, e devolve um booleano. A resposta de `POST /start` e de `GET /today` carrega `outsideWorkWindow`.

Rejeitado: `400 trip.outside_work_window`. Três motivos, do mais grave ao menos:

1. **A falha é dura num caso legítimo e frequente.** Van quebrada, trânsito, reposição de feriado, motorista que abre o app 6h55 com `work_start_time = 07:00`. Bloquear transforma cada um desses num chamado de suporte, e não existe override.
2. **O dado não sustenta a trava.** `work_days` é `jsonb` livre, sem constraint, nunca validado, e nenhum fluxo de cadastro obriga preenchê-lo. Um motorista com `work_days` nulo seria bloqueado para sempre por um campo que ninguém pediu que ele preenchesse.
3. **A decisão é reversível nesta direção e não na outra.** Registrar agora produz o dado que diz quantos inícios caem fora da janela; com esse número em mãos dá para endurecer depois. Bloquear primeiro não gera dado nenhum — só ausência de trips.

### D3 — Finalizar com checklist pendente preserva o pendente

Ao finalizar, entries de `checklist_entry` em `PENDING` **permanecem** `PENDING`. Baixa tentada depois de `finished_at` retorna `409`.

Rejeitado: marcar automaticamente como `SKIPPED`/`DONE` no fim da rota. O UC09 declara a confirmação do motorista/assistente a **única fonte de verdade** do embarque, explicitamente sem 2FA do responsável. Marcar sozinho grava que uma criança embarcou ou desembarcou sem que ninguém tenha observado — num app de transporte de menores isso não é dado incompleto, é dado falso, e é o único registro que existiria numa disputa. O CA-047 já resolve a experiência do lado certo: a UI avisa antes de confirmar quando há pendentes; o banco não inventa.

`checklist_entry` não existe ainda (#151). Esta change não implementa a regra — ela **fixa o contrato** para que a #151 nasça com ele, e a `trip` guarda `finished_at`, que é o carimbo contra o qual a baixa tardia será recusada.

### D4 — `trip` tem soft delete (decisão revista)

> **Esta decisão foi invertida.** A versão original dizia "`trip` não tem soft delete", argumentando que o DBML não dá `deleted_at` a `trip` — e é verdade: são 16 tabelas sem a coluna, e `trip` está no grupo dos eventos operacionais (`checklist_entry`, `absence`, `stop_change_request`, `contract_event`, `payment`, `log`). O time decidiu o contrário, e o registro do porquê fica aqui.

`trip` segue a **regra 19** como todo o resto do repositório: coluna `deleted_at`, `@SoftDelete(strategy = TIMESTAMP)`, `DELETE` lógico e `restore`. O peso decisivo é consistência: 15 das 17 features com controller têm CRUD completo com restore, e uma exceção só para `trip` obrigaria todo consumidor a saber que aquela feature é diferente.

**`CANCELLED` e `deleted_at` não são a mesma coisa, e ambos existem:**

| | Significado | Quem faz |
|---|---|---|
| `status = CANCELLED` | a rota **não vai acontecer** — fato operacional do dia | motorista/admin, é dado de negócio |
| `deleted_at` | a linha **não deveria existir** — erro de cadastro | admin, é correção administrativa |

Uma rota cancelada continua aparecendo na listagem e no histórico; uma removida some.

**Duas consequências que esta inversão cria e que não podem ser esquecidas:**

1. **Os índices únicos passam a ser parciais** (`where deleted_at is null`), como no V26. Sem isso, uma trip removida pelo admin travaria o motorista de iniciar de novo a rota do mesmo dia e turno — o caso que o `restore` deveria resolver viraria um bloqueio permanente. Há teste cobrindo (`allowsANewTripAfterTheSameSlotWasSoftDeleted`).
2. **O `vanep-diagram.dbml` precisa ganhar `deleted_at` em `trip`.** Enquanto não ganhar, schema e diagrama divergem — exatamente a dívida que o D1 existe para evitar. Ver R7.

### D5 — Idempotência vem do índice único, não de check-then-insert

`POST /start` chamado duas vezes devolve a mesma trip. A garantia é o índice único `(driver_id, service_date, shift)`.

A implementação **não** é "consultar, se não achar inserir": entre a consulta e o insert cabe outra requisição, e duas chamadas simultâneas do mesmo aparelho (duplo toque, retry de rede) produziriam duas trips ou um `500` de constraint. O serviço tenta inserir, captura `DataIntegrityViolationException` e relê a linha vencedora.

É o mesmo princípio do PR #135: a garantia é do schema, não da disciplina. Se o índice sumisse, o teste de corrida falharia — que é o que se quer de um teste.

`201` na criação, `200` quando a trip já existia.

### D6 — O turno vem no corpo da requisição

`POST /start` recebe `shift`. Não é derivado da hora nem dos contratos.

Derivar da hora exigiria uma fronteira arbitrária (12h? 13h?) que quebra no primeiro motorista que sai 11h40 no vespertino. Derivar dos contratos é o certo a prazo e impossível agora: `contract` não existe (#41).

O enum `Shift` é reutilizado (regra 6), não recriado. Como passa a ser usado por `dependent` e `trip`, sai de `br.com.vanep.dependent.enums` para área partilhada (regra 5).

### D7 — `service_date` é do servidor, em `America/Sao_Paulo`

A data que compõe a chave única nunca vem do cliente. Aparelho com data errada, fuso trocado em viagem ou relógio manual criariam uma segunda trip para o mesmo dia — o índice único não protege contra isso, porque a chave em si estaria errada.

O timezone é fixo e explícito (`America/Sao_Paulo`), não o default da JVM: o container roda UTC, e `LocalDate.now()` viraria o dia às 21h local, no meio do turno vespertino.

### D8 — Os efeitos colaterais do UC08 são derivados, não colunas

O UC08 diz que iniciar rota "altera o status para *Em rota*, ativa o compartilhamento de localização e libera o checklist". Nenhum dos três vira coluna nova nesta change:

| Efeito | Onde mora | Por quê |
|---|---|---|
| status "Em rota" | derivado de `trip.status` | `driver.is_available` é disponibilidade comercial, outra coisa; `driver_status_log` é auditoria (fora de escopo) |
| localização ativa | `vanep-mobile` + #153/#159 | não há infraestrutura de tracking; o toggle é preferência do aparelho |
| checklist liberado | derivado de `trip.status = IN_PROGRESS` | a #151 lê o estado da trip, não um flag |

A `trip` é a fonte única do estado da operação. Um flag redundante seria uma segunda verdade a manter em sincronia.

### D9 — Superfície HTTP sob `/api/drivers/me/trips`

```
POST /api/drivers/me/trips/start    → 201 (criou) | 200 (já existia)
POST /api/drivers/me/trips/finish   → 200
GET  /api/drivers/me/trips/today    → 200 (lista, ver D10)
```

Segue `DriverServiceAreaController`: o motorista sai do `Authentication`, não de um id na URL. O `403` para quem não é motorista vem do `requireByTokenAndType`, e o gate RN-02 (aprovado) fica no serviço.

> **Revisto pelo D11.** A versão original desta decisão dizia que **`SecurityEvaluator` não ganha método novo**, porque não há dono a comparar quando o recurso é sempre "o meu" — a mesma conclusão do PR #135. Isso continua verdadeiro para os três endpoints acima, mas o CRUD administrativo do D11 endereça trips por token na URL, e ali o dono existe. `@sec.isTripOwner` passa a ser necessário.

### D11 — CRUD administrativo completo, por token, ao lado do fluxo `/me`

A issue #150 pede "CRUD de trip". A superfície fica em duas camadas, com propósitos distintos:

```
FLUXO DO MOTORISTA (/me, sem token na URL)     CRUD ADMINISTRATIVO (por token)
POST /api/drivers/me/trips/start               POST   /api/trips
POST /api/drivers/me/trips/finish              GET    /api/trips            (paginado)
GET  /api/drivers/me/trips/today               GET    /api/trips/{token}
                                               PATCH  /api/trips/{token}
                                               DELETE /api/trips/{token}
                                               POST   /api/trips/{token}/restore
```

O motorista **não** usa a camada de baixo para operar: `start`/`finish` carregam a máquina de estados, a idempotência e o `outsideWorkWindow`. O `POST /api/trips` cria uma linha crua e existe para correção administrativa — reconstituir um dia que não foi registrado, por exemplo.

`PATCH` segue a **regra 16** (`JsonNullable` em todo campo mutável), não `PUT`. Campos mutáveis: `shift`, `status`, `startedAt`, `finishedAt`. Isso significa que o admin **pode** escrever `status` diretamente, contornando a `TripTransitionPolicy` — é deliberado: a policy governa o fluxo do motorista, e a correção administrativa existe justamente para consertar estado que a policy não deixaria alcançar. O que a policy protege é o endpoint do motorista.

Autorização em duas vias, como no resto do repositório:

```java
@PreAuthorize("hasAuthority('update_trip') or @sec.isTripOwner(#token, authentication)")
```

O motorista lê e edita a própria trip por token; o admin alcança todas. `DELETE` e `restore` são **só** `hasAuthority` — remover a própria operação do dia não é caso de uso de motorista.

### D10 — `GET /today` devolve uma lista, não uma trip (decidido na fase 1, com evidência)

O desenho original desta change dizia "a trip do dia", no singular, e `204` quando não houvesse nenhuma. **A fase 1 refutou.** O teste `keepsTwoShiftsOfTheSameDayAsSeparateTrips` é a evidência: a chave única é `(driver_id, service_date, shift)`, então um motorista que roda matutino e vespertino tem **duas** trips no mesmo dia — e é o caso comum, não a exceção. Não existe "a trip do dia".

`GET /today` devolve a lista das trips do dia, ordenada por `id` (ordem de criação, que é a ordem cronológica em que o motorista as iniciou). Dia sem operação devolve `200` com lista vazia, não `204`: lista vazia é a resposta correta para uma coleção sem elementos, e poupa o app de tratar dois formatos. Isso também encerra a **Q2**, que perguntava entre `204` e corpo nulo — a pergunta partia do mesmo pressuposto errado.

Ordenar por `shift` foi tentado e descartado: com `@Enumerated(STRING)` o banco ordena pelo nome, e `AFTERNOON` vem antes de `MORNING` em ordem alfabética. Ordem alfabética de enum não significa nada; ordem de criação significa.

### D12 — A correção administrativa alcança estado ilegal, mas nunca estado impossível

O D11 abriu o `PATCH` para escrever `status` direto, contornando a `TripTransitionPolicy`. Isso é necessário — reabrir rota fechada por engano é exatamente o que a correção existe para fazer — mas criou uma regressão real em relação ao desenho anterior desta change: quando o único escritor era a máquina de estados, **era estruturalmente impossível gravar uma linha incoerente**. A garantia não vinha de validação, vinha de não haver porta.

Com `POST /api/trips` e `PATCH /api/trips/{token}` a porta existe, e sem D12 passaria: `COMPLETED` sem `started_at`, `IN_PROGRESS` com `finished_at` preenchido, rota que terminou antes de começar.

`TripCoherencePolicy` devolve essa propriedade sem devolver a rigidez. Ela é pura (sem Spring, JPA ou servlet) e responde uma pergunta só: esta combinação de `status`, `started_at` e `finished_at` **pode existir**?

| status | `started_at` | `finished_at` |
|---|---|---|
| `SCHEDULED` | proibido | proibido |
| `IN_PROGRESS` | obrigatório | proibido |
| `COMPLETED` | obrigatório | obrigatório |
| `CANCELLED` | opcional | opcional |
| *qualquer* | — | nunca anterior ao início |

**A distinção que sustenta o D11 e o D12 juntos:** transição é sobre *caminho* — como se chegou aqui; coerência é sobre *estado* — se isto pode existir. O admin pode pular caminho (reabrir uma rota `COMPLETED`), nunca gravar estado impossível (marcar `IN_PROGRESS` sem limpar o `finished_at`). Há teste dos dois lados: `adminReopensACompletedRoute` passa, `reopeningWithoutClearingTheFinishIsRefused` devolve `400`.

`CANCELLED` fica permissivo de propósito: a rota pode ser cancelada antes de sair ou no meio do trajeto, e não existe fluxo que a produza ainda (D1). Restringir agora seria inventar regra para um caso que o produto não descreveu.

A policy **não** é aplicada em `start`/`finish`: aquele fluxo é coerente por construção, e validar ali seria checar o que a máquina de estados já garante.

## Risks / Trade-offs

| # | Risco | Mitigação |
|---|---|---|
| **R1** | Corrida entre dois `POST /start` simultâneos cria duplicata ou `500` | D5: insert-then-catch com releitura. **A fase 1 mostrou que o teste concorrente real é impossível na suíte** (ver R4): o índice não existe em H2, então nada é violado e nada é lançado. A fase 3 testa o comportamento com o repositório mockado lançando `DataIntegrityViolationException`, e a garantia do banco fica coberta pela verificação manual da tarefa 1.6 |
| **R2** | Rota que cruza a meia-noite fica com `service_date` do dia anterior e `finished_at` do seguinte | Aceito. `service_date` é o dia **de serviço**, não o do carimbo; escolar não opera nessa faixa. Registrado para não ser redescoberto |
| **R3** | `work_days` é `jsonb` livre e pode vir nulo, vazio ou malformado | D2 já não bloqueia; `WorkWindowPolicy` trata ausência como "não dá para afirmar que está fora" → `outsideWorkWindow = false`. Teste cobrindo nulo e vazio |
| **R4** | `V31` não roda na suíte (H2 com `ddl-auto`), então o índice único nunca é exercitado em teste | **Confirmado na fase 1**: um teste que salvava duas trips iguais esperando `DataIntegrityViolationException` passou sem lançar nada, e foi removido. Declarar `uniqueConstraints` no `@Table` só para o teste está descartado — é a mesma decisão registrada pelo `owned-address-model` na tarefa 1.1 ("do not add a test-only unique index"), e `DriverServiceAreaModel` também não declara. Resta a verificação manual contra PostgreSQL 17 (tarefa 1.6) |
| **R5** | Mover `Shift` de pacote quebra imports em dependent | Refactor mecânico numa tarefa isolada da fase 1; `./mvnw verify` cobre porque dependent já tem testes |
| **R6** | `FULLTIME` numa chave `(driver, date, shift)` deixa ambíguo se ida e volta são uma trip ou duas | Q1 em aberto. O enum inteiro é aceito agora, o que mantém as duas leituras possíveis; a #151 decide |
| **R7** | O `vanep-diagram.dbml` não tem `deleted_at` em `trip`; o D4 revisto tem. Schema e diagrama divergem — a dívida que o D1 existe para evitar | Atualizar `trip` no `vanep-dbdiagram` **na mesma sprint**, não depois. Tarefa 5.7. Enquanto não for feito, o diagrama está errado, não o código |
| **R8** | `checklist_entry.trip_id` é `NOT NULL`; soft-deletar uma trip deixa as baixas do dia apontando para uma linha que o Hibernate não enxerga mais | `checklist_entry` é da #151 e ainda não existe. O contrato fica fixado aqui: **remover uma trip com checklist associado deve ser recusado** (`409`), não cascatear. A #151 implementa a checagem; esta change registra a exigência para que não nasça sem ela |

## Open Questions

| | Pergunta | Estado |
|---|---|---|
| **Q1** | Um motorista de turno integral faz **uma** trip cobrindo as 4 fases ou **duas** (ida e volta)? O happy path 1.3 lê como uma sessão contínua, mas há ~5h entre a fase 2 (07:18) e a fase 3 (12:55) — manter `IN_PROGRESS` a manhã toda é estranho | 🔴 Decidir na #151, quando `checklist_entry` der forma às fases. Esta change aceita os quatro valores de `Shift`, o que não fecha nenhuma das duas portas |
| **Q2** | `GET /today` devolve `204` ou `200` com corpo nulo quando não há trip? | ✅ **Respondida, e a pergunta estava errada.** Não existe "a trip do dia" — um motorista de dois turnos tem duas. `GET /today` devolve lista; dia vazio é `200` com lista vazia. Ver D10 |
| **Q3** | Finalizar exige que todas as fases do checklist tenham sido tentadas? | 🟢 Não. D3 resolve: finaliza sempre, pendente continua pendente |

## Migration Plan

| Migration | Conteúdo |
|---|---|
| **V31** | `create table trip` com `deleted_at` (D4) + FK `driver_id → driver(id)` + índices únicos **parciais** em `token` e em `(driver_id, service_date, shift)`, ambos `where deleted_at is null` |

Sem backfill: não há dado histórico de operação para migrar. Sem alteração de tabela existente. `V31` é o próximo número livre — a `main` está em `V30__client_can_read_drivers.sql`; **reconfirmar na tarefa 0.3**, porque outras stacks podem mergear antes.

As três colunas da chave são `NOT NULL`, então não há questão de `NULLS NOT DISTINCT` como houve no `district` (D-location/R2). O que importa aqui é o `where deleted_at is null`: sem ele, uma trip removida pelo admin travaria para sempre aquele `(motorista, data, turno)` — ver D4.

## Rollout — grafo de dependência e plano de PRs

```
main
 └─ spec/trip-daily-operation ............. PR 0 (planejamento, sem código)
      │
      └─ feat/trip-daily-operation-schema ......... PR 1 → base: main
           └─ …-policies ......................... PR 2 → base: PR 1
                └─ …-service ..................... PR 3 → base: PR 2
                     └─ …-driver-http ............ PR 4 → base: PR 3
                          └─ …-admin-crud ........ PR 5 → base: PR 4
```

| Fase | Conteúdo | Depends on | Parallel with |
|---|---|---|---|
| 0 | Preparation — branch, confirmar V31, revisar artefatos | — | — |
| 1 | `Shift` partilhado, `TripStatus`, V31 com soft delete, `TripModel`, `TripRepository` | — | — |
| 2 | `TripTransitionPolicy`, `WorkWindowPolicy` (puras, sem Spring) | Fase 1 | — |
| 3 | `TripService` (start/finish/today), permissões, gate RN-02, messages | Fase 2 | — |
| 4 | `TripController` `/me`, `TripStartRequestDTO`, `TripResponseDTO`, `TripMapper` | Fase 3 | — |
| 5 | CRUD admin por token, `@sec.isTripOwner`, `TripSeeder` | Fase 4 | — |

É uma cadeia, sem paralelismo real: cada fase consome o tipo criado pela anterior. As PRs são **empilhadas** — cada uma aponta como base a anterior, e o merge sai de uma vez pela última (`merge stack`).

A fase 5 é a última de propósito: o CRUD administrativo depende do `TripMapper` e do `TripResponseDTO` da fase 4, e é a parte que menos bloqueia — a #151 precisa da tabela e da máquina de estados (fases 1–3), não do endpoint de correção.
