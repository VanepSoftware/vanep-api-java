## Why

Sem ausência do dia, a rota parte do cadastro estático do aluno. O motorista para numa casa vazia, o checklist gera baixa para quem não vai embarcar, e o responsável não tem como avisar a van **só hoje** sem mexer no contrato (RN-19). A issue #160 (UC10) pede as duas origens: o responsável avisa; o motorista ou o assistente confirma o não comparecimento depois da tolerância de espera.

O desenho de produto nesta change **não** é um botão único “Não vou hoje” para o dia inteiro. Na tela de detalhe da van o responsável escolhe **Avisar ausência**, com três escopos:

| Escolha na UI | Significado operacional | O que some da rota de hoje |
|---|---|---|
| **Ida e volta** | O aluno não usa a van em nenhum trecho do dia | Casa e escola na ida **e** na volta |
| **Só ida** | Casa → escola não acontece; a volta segue prevista | Só o trecho de ida |
| **Só volta** | Escola → casa não acontece; a ida segue prevista | Só o trecho de volta |

Cada trecho é um fato próprio. “Ida e volta” não é uma linha mágica: o backend grava **duas** ausências (uma por trecho). O índice único da issue (`dependent_id`, `absence_date`) impediria isso — a unicidade passa a ser por **trecho** no dia.

`trip` já existe. `contract`, `checklist_entry`, `route` e FCM ainda não. Esta change registra a ausência e os contratos com esses vizinhos para que a #151 e a #45 nasçam lendo ausência, em vez de a ausência nascer fingindo que checklist e rota já existem.

## What Changes

- Nova feature `br.com.vanep.absence` (regra 5): model, repository, enums, mapper, dto, service, controller
- Migration Flyway da tabela `absence` com `deleted_at` (regra 19), `trip_id` **nullable** (ausência pode existir antes da trip do trecho) e índice único parcial `(dependent_id, absence_date, leg) where deleted_at is null`
- Enums `AbsenceSource` (`CLIENT`, `DRIVER`, `ASSISTANT`) e `AbsenceLeg` (`OUTBOUND`, `RETURN`) — o escopo de UI `BOTH` existe só no request do cliente e **não** é valor persistido
- `absence_date` e o dia da operação saem do relógio do servidor em `America/Sao_Paulo`, nunca do aparelho (mesmo D7 de `trip`)
- Identificadores públicos são `token` opaco (regra 13) — nenhum `id` interno no JSON
- **Cliente:** `POST` / `GET` / `DELETE` de ausência do dia no vínculo da van (`client_driver`), com `scope` `OUTBOUND` \| `RETURN` \| `BOTH`
- **Motorista / assistente:** `POST` de não comparecimento na trip corrente, com `reason` obrigatório; um trecho só (o da trip)
- Idempotência por trecho: o índice único não vira HTTP 500; repetir o aviso devolve a linha já gravada
- Desfazer (só origem `CLIENT`): permitido até o trecho correspondente **não** ter trip `IN_PROGRESS` / `COMPLETED` / `CANCELLED`
- Porta de efeito do dia: checklist e recálculo de paradas (RN-15) entram como colaborador, com no-op enquanto #151 / #45 não existirem, e testes do contrato com fake
- Notificação à contraparte (e-mail via `MailService` até existir FCM) e preenchimento de `notified_at`
- Coluna `contract_id` **não** entra agora: `contract` não existe (#41); o vínculo da van é `client_driver_id`. Quando o contrato nascer, uma migration acrescenta a FK

**Fora de escopo:**

- Alterar contrato, `dependent.shift`, dias futuros ou “esse aluno não usa mais a van” (isso não é UC10)
- FCM / push no aparelho — o canal desta change é e-mail, o mesmo já usado em onboarding; o texto da notificação segue o da issue
- Recálculo geométrico de rota (#45) e geração real de `checklist_entry` (#151) — só o contrato (porta) e a leitura de ausência
- CRUD administrativo completo de `absence` (listagem paginada, PATCH, restore) — correção administrativa fica para issue própria
- Desfazer não comparecimento (`DRIVER` / `ASSISTANT`) — é fato operacional; estorno seria outra issue
- Impor no servidor o esgotamento de `waitToleranceMinutes` — o app confirma depois da espera; o backend confia na origem autenticada na própria van

## Capabilities

### New Capabilities

- `absence-client-report`: o responsável avisa ausência de um dependente **na van da tela**, para hoje, escolhendo ida, volta ou as duas; pode desfazer enquanto o trecho ainda não está em andamento.
- `absence-no-show`: motorista ou assistente da própria van registra não comparecimento na trip corrente, com motivo, um trecho só.
- `absence-day-effects`: unicidade e idempotência por trecho, RN-19 (só o dia corrente), efeitos sobre checklist/paradas via porta, e notificação à contraparte com `notified_at`.

### Modified Capabilities

- _(nenhuma spec main pré-existente de absence)_
- `trip` não muda de schema; ausência **lê** `trip.status` / `shift` para amarrar trecho e janela de desfazer.

## Impact

- **Schema:** próxima migration livre após `V51` (reconfirmar na fase 2). Nenhuma tabela existente é alterada.
- **Pacotes:** novo `br.com.vanep.absence`. Policies puras no mesmo pacote `service`, no padrão de `TripTransitionPolicy`.
- **Permissões:** `report_absence` no bundle `CLIENT`; `report_no_show` nos bundles `DRIVER` e `ASSISTANT`.
- **`SecurityEvaluator`:** `isTripOperator` (motorista dono da trip **ou** assistente `ACTIVE` daquele motorista). Posse do dependente e do vínculo continua no serviço, como em `DependentService.assertOwnership` — o endpoint do cliente pende de `client_driver` + dono do aluno, não de um único `isOwner`.
- **Diagrama:** `vanep-diagram.dbml` precisa trocar o unique `(dependent_id, absence_date)` por `(dependent_id, absence_date, leg)`, tornar `trip_id` opcional, acrescentar `leg` e `deleted_at`, e substituir `contract_id` obrigatório por `client_driver_id` até existir `contract`.
- **Mobile:** a tela de detalhe da van deixa de ter um booleano “não vou hoje” e passa a enviar `scope`. Fora deste repositório (`vanep-mobile`).
- **Sem breaking change de API existente.**
