## Why

A operação diária é o coração do produto (BRD §8.6.1), e hoje não existe nada dela no backend. O motorista tem perfil, CNH, documentos, veículo e áreas de atuação — mas não tem **o dia de trabalho**. Sem uma linha que represente "a rota do motorista X em 10/09 no turno da manhã", não há onde pendurar baixa de checklist, ausência, ajuste de parada nem geometria de rota.

O mobile já expõe a intenção sem lastro: `DriverHomeState` tem `shift: on|off` e `sharingLocation` como estado puramente local, e as abas "Propostas" e "Alunos" do `driver_shell` são `VanepComingSoon`. O botão "Iniciar rota" da tela S19 não tem endpoint para chamar.

`trip` é a raiz do bloco de operação: `checklist_entry.trip_id`, `absence.trip_id`, `stop_change_request.trip_id` e `route.trip_id` são todos obrigatórios ou centrais no modelo canônico. Enquanto ela não existir, as issues #151, #152, #160 e #45 não têm por onde começar.

## What Changes

- Nova migration **V31**: tabela `trip` com `driver_id`, `service_date`, `shift`, `status`, `started_at`, `finished_at`, `deleted_at` e índices únicos **parciais** em `token` e em `(driver_id, service_date, shift)`, ambos `where deleted_at is null`
- Novo enum `TripStatus` (`SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`) conforme `vanep-diagram.dbml` — **não** `FINISHED` (D1)
- Promoção do enum `Shift` de `br.com.vanep.dependent.enums` para área partilhada — passa a ser usado por duas features (regra 5)
- Novo pacote `br.com.vanep.trip` com `model`, `repository`, `service`, `controller`, `dto` e duas policies puras
- `TripTransitionPolicy` — máquina de estados testável sem Spring, JPA ou servlet
- `WorkWindowPolicy` — decide se o início está dentro da janela de expediente do motorista; **registra, não bloqueia** (D2)
- Fluxo do motorista sob `/api/drivers/me/trips`: `POST /start`, `POST /finish`, `GET /today`
- **CRUD administrativo completo** sob `/api/trips`: `POST`, `GET` paginado, `GET /{token}`, `PATCH /{token}` (regra 16, `JsonNullable`), `DELETE /{token}` (soft delete) e `POST /{token}/restore`
- `@sec.isTripOwner` no `SecurityEvaluator` (regra 22) — o motorista lê e corrige a própria trip por token; remover é só do admin
- Início **idempotente** garantido pelo índice único, não por check-then-insert (D5)
- `service_date` derivado do relógio do servidor em `America/Sao_Paulo`, nunca do dispositivo (D7)
- Novas permissões `start_trip` e `finish_trip` no bundle `DRIVER`; `list_trips`, `show_trip`, `create_trip`, `update_trip`, `delete_trip` e `restore_trip` no bundle `ADMIN`
- Gate RN-02: só motorista com `approval_status = APPROVED` inicia ou finaliza rota

**Fora de escopo:**

- `checklist_entry`, `absence`, `stop_change_request`, `route` (issues #151, #160, #152, #45) — esta change entrega só a âncora
- Compartilhamento de localização em tempo real e `driver_status_log` — sem infraestrutura de tracking (issues #153, #159); o status da `trip` é o que o app lê (D8)
- Notificação push ao cliente (issue #153)
- Recálculo de rota, ETA e tudo que depende de `route` (#45)

## Capabilities

### New Capabilities

- `trip-lifecycle`: criação idempotente da viagem do dia, transições `SCHEDULED → IN_PROGRESS → COMPLETED`, rejeição das transições inválidas e gate de motorista aprovado.
- `trip-daily-query`: leitura do estado da operação do dia pelo app do motorista, incluindo o registro de início fora da janela de expediente.
- `trip-admin-crud`: CRUD por token para correção administrativa, com soft delete, restore e separação entre permissão de admin e posse do motorista.

## Impact

- **Schema:** `V31` cria `trip`. Nenhuma tabela existente é alterada.
- **Pacotes:** novo `br.com.vanep.trip`; `Shift` muda de pacote (import quebra em `DependentModel`, `DependentMapper` e DTOs de dependent — refactor mecânico, sem mudança de comportamento).
- **Permissões:** oito entradas novas no `PermissionEnum`, divididas entre os bundles `DRIVER` e `ADMIN` do seeder. Tokens emitidos antes exigem novo login para acessar as rotas novas.
- **Mobile:** `DriverHomeState` pode deixar de ser estado local e passar a refletir `GET /api/drivers/me/trips/today`. Fora do escopo desta change (repo `vanep-mobile`).
- **Modelo canônico:** o `vanep-diagram.dbml` não dá `deleted_at` a `trip`; o D4 revisto dá. O diagrama precisa ser atualizado na mesma sprint (R7), senão nasce a mesma dívida que o V16 do `driver_rating` criou.
- **Sem breaking change de API.** Nenhum endpoint existente muda de contrato.
