## Why

Na operação diária de transporte escolar (UC22), é frequente que um responsável precise que seu dependente seja deixado em um local alternativo no mesmo dia — casa dos avós, casa de parentes ou reforço escolar. Trata-se de uma **solicitação**, não de uma ordem: depende da anuência do motorista que está operando a rota, tem validade **estritamente para a viagem daquele dia/turno** e mantém o endereço cadastrado do dependente inalterado (RN-19).

Com a entrega da âncora `trip` (#150), o backend já possui a viagem do dia com status (`SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`). A presente change implementa o ciclo completo de solicitação de ajuste de parada (`stop_change_request`): o cliente solicita um novo local de desembarque com motivo, o motorista aprova ou recusa, e ambos os desfechos são registrados com auditoria (`responded_by_user_id`, `responded_at`).

## What Changes

- Nova migration **V40**: tabela `stop_change_request` com `token`, `contract_id` (preparatório para #41), `dependent_id`, `trip_id`, `service_date`, `requested_by_user_id`, `new_dropoff_address_id`, `reason`, `status`, `responded_by_user_id`, `responded_at`, `created_at`, `updated_at` e `deleted_at`.
- Índice único parcial em `token` e índice único parcial para garantir **no máximo uma solicitação pendente por dependente na mesma viagem** (`WHERE status = 'PENDING' AND deleted_at IS NULL`).
- Novo enum `StopChangeStatus`: `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`, `CANCELLED`.
- Novo pacote `br.com.vanep.stopchange` (feature-based, regra 5) com `model`, `repository`, `service`, `controller`, `dto`, `mapper` e `policy`.
- `StopChangeTransitionPolicy`: máquina de estados pura e testável sem Spring/JPA, governando as transições válidas e rejeitando respostas a solicitações já finalizadas.
- Endpoints do cliente sob `/api/clients/me/stop-change-requests`:
  - `POST /`: cliente solicita ajuste de parada para um dependente seu em uma viagem ativa (`trip`), informando o novo endereço de desembarque (por `placeId` ou `addressToken`) e o motivo.
  - `GET /today`: cliente lista suas solicitações de ajuste para a data de serviço de hoje.
  - `POST /{token}/cancel`: cliente cancela uma solicitação ainda pendente.
- Endpoints do motorista sob `/api/drivers/me/stop-change-requests`:
  - `GET /today`: motorista lista solicitações pendentes e respondidas para as suas viagens de hoje.
  - `POST /{token}/approve`: motorista aprova a solicitação, gravando carimbo e usuário respondente.
  - `POST /{token}/reject`: motorista recusa a solicitação com motivo/registro.
- Endpoints administrativos e auditoria sob `/api/stop-change-requests`:
  - `GET /{token}`: detalhes da solicitação com autorização compartilhada (cliente dono, motorista responsável ou admin).
  - `DELETE /{token}` e `POST /{token}/restore`: soft delete e restauração administrativa.
- Eventos de domínio desacoplados (`StopChangeRequestedEvent`, `StopChangeApprovedEvent`, `StopChangeRejectedEvent`) prontos para acoplar recálculo de rota (#45) e push FCM (#153).

**Fora de escopo:**
- Recálculo de geometria de rota e ETA (depende de #45 `route`/`route_stop`).
- Envio real de push notification via Firebase (depende de #153 infraestrutura de FCM).
- Validação contra baixa de desembarque individual (depende de #151 `checklist_entry`). A trava de rota finalizada (`COMPLETED`/`CANCELLED`) já fica garantida aqui.

## Capabilities

### New Capabilities

- `stop-change-request-lifecycle`: solicitação idempotente de alteração de parada, máquina de estados com transições legais (`PENDING -> APPROVED`, `PENDING -> REJECTED`, `PENDING -> CANCELLED`, `PENDING -> EXPIRED`) e auditoria de resposta.
- `stop-change-request-client-driver`: experiência `/me` para o responsável criar/cancelar solicitações e para o motorista aprovar/recusar com segurança baseada no vínculo da viagem.
- `stop-change-request-admin-crud`: consulta detalhada, exclusão lógica e restauração para suporte e administração.

## Impact

- **Schema:** migration `V40__create_stop_change_request_table.sql`. Nenhuma tabela existente sofre alteração breaking.
- **Pacotes:** novo pacote coeso `br.com.vanep.stopchange`.
- **Permissões:** permissões dedicadas no `PermissionEnum` para os bundles `CLIENT`, `DRIVER` e `ADMIN`.
- **Compatibilidade:** compatível com a ausência de `contract` e `checklist_entry`; pronto para expansão futura sem retrabalho de migrations.
