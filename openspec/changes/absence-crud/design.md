## Context

A issue #160 descreve UC10 com um único fato por aluno por dia: unique `(dependent_id, absence_date)`, botão “Não vou hoje”, `trip_id` no modelo canônico. O time decidiu outra UX: **Avisar ausência** com **ida e volta / só ida / só volta**. Isso quebra o unique da issue e o único `trip_id` obrigatório.

O que existe hoje e importa:

- `trip` (V31): uma linha por `(driver_id, service_date, shift)`; `Shift` é `MORNING` \| `AFTERNOON` \| `NIGHT` \| `FULLTIME`; status `SCHEDULED` \| `IN_PROGRESS` \| `COMPLETED` \| `CANCELLED`; `service_date` do servidor em `America/Sao_Paulo`
- `dependent.shift` diz quais trechos o aluno costuma usar — não é contrato, é cadastro
- `client_driver` com `RelationshipStatus.ACTIVE` é o proxy de “tem van / contrato ativo” enquanto #41 não existe
- Assistente `ACTIVE` aponta para `assistant.driver_id` — é assim que se prova “própria van”
- Não existem `contract`, `checklist_entry`, `route` nem FCM
- Notificação operacional hoje é e-mail (`MailService` + Thymeleaf), já usada na aprovação do motorista
- A proposta gerada pelo Gemini em `feature/absence-crud-docs` misturava camadas, usava `id` numérico no JSON, `period` manhã/tarde e `trip_id NOT NULL` — esta revisão substitui esse desenho

## Goals / Non-Goals

**Goals**

- Um fato persistido por `(aluno, dia, trecho)`, garantido pelo banco
- Cliente escolhe escopo de UI; persistência explode `BOTH` em duas linhas
- Motorista/assistente registra um trecho só, o da trip em que estão
- Ausência de hoje não escreve em contrato nem em `dependent`
- Repetir o mesmo aviso não gera 500
- Cliente desfaz só enquanto o trecho ainda não “pegou a rua”
- Contratos explícitos para checklist, paradas e notificação, testáveis com fake

**Non-Goals**

- Gerar checklist, geometria de rota ou push FCM
- Tabela `contract` e FK obrigatória para ela
- Admin CRUD, PATCH de motivo, desfazer não comparecimento
- Derivar o trecho pela hora do relógio

## Decisions

### D1 — O fato persistido é o trecho, não o dia

A UI oferece três escolhas. O banco guarda duas:

```
AbsenceLeg = OUTBOUND | RETURN
```

`BOTH` existe só no request do cliente (`AbsenceScope`). Gravá-lo como valor de coluna faria o unique da issue “funcionar” e tornaria “só ida depois de ter avisado a volta” um update destrutivo. Duas linhas deixam cada trecho independente: o cliente pode avisar a ida de manhã e a volta à tarde; o motorista pode registrar no-show só na volta.

Índice: `unique (dependent_id, absence_date, leg) where deleted_at is null`.

Rejeitado: unique da issue `(dependent_id, absence_date)`. Com ida e volta no mesmo dia, a segunda linha seria 500 ou um update que apaga o primeiro trecho.

Rejeitado: unique `(dependent_id, trip_id)` com `trip_id NOT NULL`. A própria issue exige ausência **antes** da trip existir (“o aluno não entra na geração das entries”). `trip_id` nulo + unique em `trip_id` também falha no `FULLTIME`: os dois trechos apontariam para a mesma trip.

### D2 — Mapa trecho ↔ `Shift` da trip (e cadastro do aluno)

Escolar padrão:

| Trecho (`AbsenceLeg`) | Trip do dia (`Shift`) | `dependent.shift` que autoriza o aviso |
|---|---|---|
| `OUTBOUND` (ida, casa → escola) | `MORNING` (e `NIGHT`, tratado como ida) | `MORNING`, `NIGHT`, `FULLTIME` |
| `RETURN` (volta, escola → casa) | `AFTERNOON` | `AFTERNOON`, `FULLTIME` |

Cliente `BOTH` só é aceito se o cadastro autorizar os dois trechos (`FULLTIME`). `MORNING` + `BOTH` → 400 (`absence.scope.not_in_shift`). `AFTERNOON` + `OUTBOUND` → 400.

`FULLTIME` na **trip** (Q1 da change `trip-daily-operation`, ainda aberta na #151): os dois trechos podem apontar para a **mesma** `trip_id` quando ela existir. Por isso a unicidade não é `(dependent, trip)`.

Não se deriva trecho da hora. O mapa acima é a convenção; mudar isso é migration de significado, não de coluna.

### D3 — `trip_id` é preenchido quando dá, nunca exigido no aviso do cliente

Fluxo do cliente, trip ainda não iniciada: grava `absence` com `trip_id` nulo. Quando a trip daquele `shift` nascer, quem gerar checklist consulta ausência por `(dependent, service_date, leg)` — não precisa da FK.

Fluxo do cliente, trip já `SCHEDULED` ou `IN_PROGRESS`: o serviço **amarra** `trip_id` se encontrar a trip do motorista do vínculo naquele turno e dia.

Fluxo de no-show: `trip_id` vem da URL e é obrigatório; o trecho sai do `shift` da trip (D2). Trip `COMPLETED` ou `CANCELLED` recusa no-show (`409`).

### D4 — O vínculo da van é `client_driver_id`, não `contract_id`

O aviso acontece na tela de detalhe da van. Sem contrato (#41), o hub já existe: `client_driver`.

Pré-condição do cliente: o chamador é dono do dependente **e** o `client_driver` da URL está `ACTIVE` **e** o `client_id` do vínculo é o do chamador. Isso substitui “contrato ativo” até a #41. `PENDING` / `INACTIVE` / `BLOCKED` → 409 `absence.link.not_active`.

Quando `contract` nascer: migration acrescenta `contract_id` nullable ou NOT NULL com backfill; o unique não muda.

Rejeitado: gravar `contract_id` sem tabela. Coluna sem FK é decoração, e o D1 de `trip` existe justamente para não divergir do canônico “no chute”. Aqui o canônico está errado para o estado do repo — registramos a divergência e atualizamos o DBML.

### D5 — Superfície HTTP

Cliente (tela da van = token do vínculo):

```
POST   /api/client-drivers/{linkToken}/absences
GET    /api/client-drivers/{linkToken}/absences/today?dependentToken=
DELETE /api/client-drivers/{linkToken}/absences
```

Corpo de `POST` e `DELETE`:

```json
{ "dependentToken": "…", "scope": "OUTBOUND" | "RETURN" | "BOTH" }
```

Sem `absenceDate`. O dia é `LocalDate.now(America/Sao_Paulo)`.

`201` se pelo menos um trecho novo foi inserido; `200` se todos os trechos pedidos já existiam (D7). `DELETE` → `204`. Resposta de `POST`/`GET` é uma lista de `AbsenceResponseDTO` (um item por trecho), nunca o model.

Não comparecimento:

```
POST /api/trips/{tripToken}/no-shows
```

```json
{ "dependentToken": "…", "reason": "…" }
```

`reason` `@NotBlank`. Origem: `DRIVER` se o chamador é o motorista da trip; `ASSISTANT` se é assistente `ACTIVE` daquele motorista. Outro motorista → 403.

`@sec.isTripOperator(#tripToken, authentication)` no no-show. Endpoints do cliente: `hasAuthority('report_absence')` + checagens no serviço (vínculo + dono do aluno). Não criar `AbsenceSecurityService` (regra 22).

### D6 — Desfazer só o aviso do cliente, e só antes do trecho pegar a rua

A issue pediu a decisão documentada. Decisão:

O responsável pode desfazer o **próprio** aviso (`source = CLIENT`) dos trechos pedidos no `DELETE`, no dia corrente, enquanto **cada** trecho alvo:

- não tem trip, ou
- a trip do turno está `SCHEDULED`

Qualquer trecho `IN_PROGRESS`, `COMPLETED` ou `CANCELLED` → `409 absence.undo.trip_started` e **nenhum** trecho é apagado (tudo ou nada). Soft delete (`repository.delete`). Desfazer `BOTH` quando só a ida existia apaga só a ida (idempotente).

Não comparecimento não tem `DELETE`. Reabrir o aluno na rota no meio da trip é recálculo + checklist — outra issue.

Rejeitado: sem desfazer na V1. O custo de reinserir o aluno **antes** da trip começar é um soft delete; o custo de não ter desfazer é o responsável ligar para o motorista.

### D7 — Idempotência é insert-then-catch, por trecho

Mesmo D5 de `trip`. `BOTH` insere os trechos em sequência; violação em um trecho relê a linha vencedora e segue o outro. Nunca 500 por unique.

`201` se houve insert novo; `200` se o pedido inteiro já estava gravado. Pedir `BOTH` com a ida já gravada insere só a volta e devolve `201` (algo novo nasceu).

H2 não exerce o índice (R4 de `trip`). Teste de corrida na suíte: repositório mockado lança `DataIntegrityViolationException`; o serviço relê. Garantia do banco: verificação manual da migration contra PostgreSQL, na fase de schema.

### D8 — RN-19: o dia corrente é invariante

Nenhuma escrita em `dependent`, `client_driver` ou (futuro) `contract`. `absence_date` não aceita ontem nem amanhã. Aviso depois das 00:00 de São Paulo já é o dia novo — o aviso de ontem não se aplica.

No-show para trip cujo `service_date` não é hoje → `409 absence.trip.not_today`.

### D9 — Efeitos do dia são uma porta, não um `ChecklistService` imaginário

```
AbsenceDayEffectPort
  excludeFromChecklistGeneration(dependentId, date, leg)
  dropPendingChecklistEntries(tripId, dependentId)
  markStopsForRouteExclusion(tripId, dependentId)  // RN-15
```

Implementação desta change: no-op concreto **nomeado** (`NoOpAbsenceDayEffectPort`) mais testes da porta com fake que registra as chamadas. Quando #151 e #45 existirem, substituem o no-op numa change delas — não nesta, para não misturar camadas com código que ainda não tem tabela.

O serviço de ausência **sempre** chama a porta depois de persistir (e no undo, a inversa não existe: se a trip ainda está `SCHEDULED`, checklist provavelmente nem gerou; se gerou no futuro, a #151 lê ausência na geração e não precisa de “recolocar”).

Ausência **antes** da trip: a geração (futura) consulta o repositório e não cria entry. Ausência **durante** `IN_PROGRESS`: `dropPending` + `markStops`. Entry já `DONE` não é mexida — o aluno que embarcou na ida e avisa a volta só afeta o trecho `RETURN`.

### D10 — Notificação: e-mail agora, FCM depois; `notified_at` só depois do envio

Textos (keys MessageSource; pt-BR servido):

- Cliente avisou → motorista (e-mail da conta do motorista do vínculo): `absence.notify.client_report` — “Não vou hoje” / o escopo (ida, volta, ida e volta) entra nos args
- No-show → cliente: `absence.notify.no_show` — “Seu aluno não foi encontrado na parada às {0}” com horário `America/Sao_Paulo`

Falha de e-mail **não** desfaz a ausência (o fato operacional já é verdade). `notified_at` permanece nulo; um retry futuro pode preencher. Testes stubam `MailService` (regra 50).

Rejeitado: marcar `notified_at` antes de enviar. A coluna mentiria.

### D11 — Soft delete como o resto do repo

`deleted_at` + `@SoftDelete(TIMESTAMP)`, unique parcial. A issue e o DBML não têm a coluna; o precedente é o D4 revisto de `trip`. `CANCELLED` de trip e ausência apagada são coisas diferentes: trip cancelada é a rota que não sai; ausência removida é “o responsável desistiu do aviso”.

Restore administrativo não entra nesta change.

### D12 — Motivo só no no-show

`reason` nullable no banco. Serviço do cliente ignora qualquer motivo (não tem campo no DTO). Serviço de no-show exige `@NotBlank` no DTO (regra 10) e persiste.

## Risks / Trade-offs

| # | Risco | Mitigação |
|---|---|---|
| **R1** | Unique da issue e o unique desta change divergem; alguém implementa o texto da #160 | D1 explícito na PR e na issue; atualizar DBML na mesma sprint |
| **R2** | Q1 de `trip` (`FULLTIME` = uma trip ou duas) | Unique por `leg`, não por `trip_id` (D1, D2) |
| **R3** | `contract` nascer com `contract_id` NOT NULL no diagrama | D4: `client_driver_id` agora; FK de contrato depois |
| **R4** | H2 não aplica o unique | Verificação manual PostgreSQL na fase de schema; catch de `DataIntegrityViolationException` no serviço |
| **R5** | Porta no-op faz a #151 “esquecer” de ler ausência | Spec `absence-day-effects` + comentário no no-op apontando a issue; a #151 deve depender desta change |
| **R6** | E-mail não é o push da issue | D10; issue de FCM depois; `notified_at` já está no modelo |
| **R7** | Cliente avisa `BOTH` e a volta do cadastro não existe | D2: 400 se o `shift` do aluno não cobre o trecho |
| **R8** | Desfazer depois que o checklist da #151 já materializou entries em `SCHEDULED` | D6 limita undo a `SCHEDULED`/sem trip; a #151 deve reler ausência antes de gerar e ao iniciar |

## Open Questions

| | Pergunta | Estado |
|---|---|---|
| **Q1** | `FULLTIME` na trip é uma sessão ou duas? | 🔴 Continua na #151. D1/D2 não fecham a porta |
| **Q2** | O cliente pode desfazer o aviso? Até quando? | ✅ **D6** — sim, origem `CLIENT`, dia corrente, enquanto o trecho não está `IN_PROGRESS`/`COMPLETED`/`CANCELLED` |
| **Q3** | Canal da notificação enquanto não há FCM? | ✅ **D10** — e-mail; `notified_at` só após envio |

## Migration Plan

Próximo número livre na `main` ao escrever: **V52** (última é `V51__client_rates_driver_and_rating_recount.sql`). Reconfirmar com `git ls-tree` em **todas** as branches remotas na tarefa de schema — outras pilhas podem tomar o número.

```sql
-- esboço; a migration real não usa CREATE TYPE nativo (padrão do repo: varchar + enum Java)

create table absence (
    id               bigint generated always as identity primary key,
    token            varchar(32)  not null,
    client_driver_id bigint       not null references client_driver (id),
    dependent_id     bigint       not null references dependent (id),
    trip_id          bigint       references trip (id),
    absence_date     date         not null,
    leg              varchar(16)  not null,
    source           varchar(16)  not null,
    reason           varchar(255),
    notified_at      timestamptz,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    deleted_at       timestamptz
);

create unique index absence_token_active_key
    on absence (token) where deleted_at is null;

create unique index absence_dependent_date_leg_active_key
    on absence (dependent_id, absence_date, leg) where deleted_at is null;
```

Sem backfill. Sem alterar tabela existente.

## Rollout — grafo de dependência e plano de PRs

```
main
 └─ feature/absence-crud-docs ..................... PR 1 → main   (esta change OpenSpec)
      └─ feat/absence-schema ...................... PR 2 → PR 1
           └─ feat/absence-policies ............... PR 3 → PR 2
                └─ feat/absence-service ........... PR 4 → PR 3
                     └─ feat/absence-client-http .. PR 5 → PR 4
                          └─ feat/absence-no-show-http  PR 6 → PR 5
                               └─ feat/absence-effects-notify  PR 7 → PR 6
```

| Fase | Conteúdo | Depends on | Parallel with |
|---|---|---|---|
| 1 | OpenSpec + visão de produto (ida / volta / ambas) | — | — |
| 2 | Enums, V5x, `AbsenceModel`, `AbsenceRepository`, `clean.sql` | Fase 1 | — |
| 3 | Policies puras: mapa trecho↔shift, escopo vs cadastro, janela de undo | Fase 2 | — |
| 4 | `AbsenceService` (create/list/undo/no-show), permissões, MessageSource, insert-then-catch. Porta ainda fake no teste | Fase 3 | — |
| 5 | HTTP do cliente (`POST`/`GET`/`DELETE`), DTOs, mapper, MockMvc | Fase 4 | — |
| 6 | HTTP de no-show, `@sec.isTripOperator`, MockMvc motorista e assistente | Fase 5 | — |
| 7 | `AbsenceDayEffectPort` + no-op, `MailService` + templates, `notified_at` | Fase 6 | — |

Cadeia sem paralelismo: cada fase consome o tipo da anterior. PRs empilhadas (`--base` a anterior); merge pela última (`merge stack`). A fase 1 é a única que sai de `main` com documentação só.

Interface da porta e implementação no-op na **mesma** fase 7 violariam a regra 39 se a fase 7 também entregasse o consumidor. O consumidor (`AbsenceService`) já existe na fase 4 e recebe a porta por construtor com um fake de teste; a fase 7 **só** troca o fake pelo no-op + mail. Se a fase 7 estourar 10 arquivos, fatiar notify (7a) e porta (7b).
