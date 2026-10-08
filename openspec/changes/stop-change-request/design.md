## Context

O UC22 e a issue #152 tratam da solicitação de alteração de parada no meio da rota: o cliente pede que seu dependente seja deixado em outro local naquele dia. Trata-se de uma **solicitação**, sujeita à aprovação do motorista, com validade restrita àquela viagem do dia e sem alterar o contrato nem o endereço cadastrado do dependente (RN-19).

O modelo canônico (`vanep-diagram.dbml`) define:
```
stop_change_request: id, token, contract_id, dependent_id, trip_id, service_date,
                     requested_by_user_id, new_dropoff_address_id, reason,
                     status (stop_change_status, default PENDING),
                     responded_by_user_id, responded_at
```

Na `main` do backend atual:
- `trip` existe (`V31`), representando a rota do dia do motorista com status `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.
- `dependent`, `client`, `driver`, `users` e `address` existem.
- `contract` (#41), `checklist_entry` (#151) e `route` (#45) ainda não existem na `main`.
- A branches `feat/30-client-driver-schema` e agregadas utilizam as migrations V36 a V39. Portanto, o próximo número de migration livre e seguro para não colidir é **`V40`**.

## Goals / Non-Goals

**Goals:**
- Ciclo completo de solicitação, aprovação, recusa e cancelamento de ajuste de parada.
- Garantia pelo banco de no máximo uma solicitação `PENDING` por dependente na mesma viagem (`dependent_id, trip_id`).
- Autorização estrita: apenas o cliente responsável pelo dependente pode solicitar; apenas o motorista escalado para a `trip` pode responder.
- Não alteração do endereço cadastrado do dependente (RN-19 preservada).
- Transições de estado puras, testáveis sem banco nem Spring via `StopChangeTransitionPolicy`.
- Soft delete e restore consistentes com a regra 19 da Constituição.
- Publicação de eventos de domínio para futura integração de notificações (FCM) e recálculo de rota.

**Non-Goals:**
- Geometria e reordenação de waypoints (depende de #45 `route`).
- Push notifications diretas via Firebase FCM (depende de #153).
- Checagem de desembarque unitário no checklist (depende de #151 `checklist_entry`). A trava de rota finalizada cobre o nível macro da viagem.

## Decisions

### D1 — Próxima migration é `V40`
Embora a `main` esteja na `V35`, branches remotas ativas (`origin/feat/30-client-driver-schema`, `origin/feat/client-rating-link`) já utilizam `V36`, `V37`, `V38` e `V39`. Usar `V40` garante que nenhuma integração futura cause conflito de versionamento no Flyway (mesmo padrão adotado na `V31` do `trip`).

### D2 — `contract_id` opcional sem FK estrita até #41
Como a tabela `contract` ainda não foi criada, não é possível criar uma chave estrangeira física (`references contract(id)`), pois o PostgreSQL falharia com erro de tabela inexistente. A coluna `contract_id bigint` entra na tabela como nullable, pronta para a migration da issue #41 adicionar a constraint sem necessidade de reestruturação da tabela.

### D3 — Autorização sem dependência de `contract`
A autorização vincula os atores diretamente através das entidades existentes:
- **Solicitante:** o dependente informado pertence ao cliente autenticado (`dependent.clientId == client.id` e `client.user.id == caller.id`).
- **Respondente:** a `trip` informada pertence ao motorista autenticado (`trip.driver.id == driver.id` e `driver.user.id == caller.id`).

### D4 — Unicidade de solicitação pendente no banco
Para atender ao requisito de no máximo uma solicitação pendente por aluno/dia, criamos um índice único parcial:
```sql
create unique index stop_change_request_pending_dependent_trip_active_key
    on stop_change_request (dependent_id, trip_id)
    where status = 'PENDING' and deleted_at is null;
```
Isso impede no nível de banco qualquer corrida que pudesse abrir duas solicitações ativas concorrentes para o mesmo aluno na mesma viagem.

### D5 — Transições de estado e idempotência
O enum `StopChangeStatus` contém: `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`, `CANCELLED`.
A máquina de estados legal é governada pela policy pura `StopChangeTransitionPolicy`:
- `PENDING -> APPROVED`
- `PENDING -> REJECTED`
- `PENDING -> CANCELLED` (apenas pelo cliente solicitante antes da resposta)
- `PENDING -> EXPIRED` (automático quando a rota é encerrada sem resposta)
Uma solicitação já respondida não pode ter seu status alterado para outro (ex: tentar aprovar uma já rejeitada lança `409 stop_change.already_resolved`). Chamadas idempotentes (aprovar uma já aprovada pelo mesmo motorista) retornam `200`.

### D6 — Proteção de ciclo de vida da Trip
Solicitações só podem ser criadas e aprovadas para viagens que estejam em `SCHEDULED` ou `IN_PROGRESS`. Se a viagem já estiver `COMPLETED` ou `CANCELLED`, a solicitação é recusada com `409 trip.already_completed` ou `trip.cancelled`.

### D7 — Suporte ao novo endereço de desembarque
O cliente pode passar:
- `addressToken` existente (caso selecione um endereço já conhecido).
- Dados de criação de endereço com `placeId`, número e complemento (reaproveitando o `AddressPlaceResolverService` existente).
O endereço original do dependente (`dependent.addressId`) permanece 100% inalterado.

### D8 — Soft delete e restore (Regra 19)
A tabela possui coluna `deleted_at` e a entidade utiliza `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)`. Índices únicos são parciais com `WHERE deleted_at IS NULL`.

## Risks / Trade-offs

| Risco | Impacto | Mitigação |
|---|---|---|
| **R1: Corrida de duas solicitações simultâneas** | Criação indevida de duas solicitações pendentes para o mesmo aluno | Resolvido pelo índice parcial `stop_change_request_pending_dependent_trip_active_key` com insert-then-catch no service. |
| **R2: Ausência da tabela `contract`** | Falta de FK relacional formal | `contract_id` criado como nullable e autorização resolvida via `dependent.client` e `trip.driver`. |
| **R3: Aluno já desembarcado** | Motorista aprovar alteração quando a criança já desceu | Como a issue #151 (`checklist_entry`) ainda não existe, travamos na finalização da `trip` (`COMPLETED`) e deixamos o ponto de extensão pronto para validar o status do checklist individual quando entregue. |
