## Why

Para sustentar o sistema de reputação da plataforma Vanep, os clientes precisam de um mecanismo para avaliar os motoristas pós-atendimento (notas de 1 a 5 estrelas e comentários). Além disso, os motoristas e administradores precisam visualizar esses históricos de avaliação. 

Esta tarefa implementa a entidade, o banco de dados, as regras de recálculo da média do motorista, a infraestrutura de segurança e os endpoints REST para `driver_rating`.

## What Changes

- **Nova Migration:** `V16__create_driver_rating_table.sql` com FKs para `driver` e `client`, índice único parcial para evitar avaliações duplicadas do mesmo cliente para o mesmo motorista, e suporte a soft delete (`deleted_at`).
- **Pacote de Negócio:** `br.com.vanep.driverrating` (`controller`, `dto`, `mapper`, `model`, `repository`, `service`).
- **Novas Permissões:** `LIST_DRIVER_RATINGS`, `SHOW_DRIVER_RATING`, `CREATE_DRIVER_RATING`, `UPDATE_DRIVER_RATING`, `DELETE_DRIVER_RATING`, `RESTORE_DRIVER_RATING` no `PermissionEnum.java`.
- **Segurança Global:** Inclusão do método `isDriverRatingOwner` no `SecurityEvaluator.java`.
- **Endpoints REST sob `/api/driver-ratings`:**
  - `POST /api/driver-ratings` — Criação de avaliação (cliente avalia motorista).
  - `GET /api/driver-ratings` — Listagem paginada (com filtro opcional por `driverToken`).
  - `GET /api/driver-ratings/{token}` — Detalhamento da avaliação.
  - `PUT /api/driver-ratings/{token}` — Atualização de nota/comentário (próprio cliente ou admin).
  - `DELETE /api/driver-ratings/{token}` — Soft delete da avaliação.
  - `POST /api/driver-ratings/{token}/restore` — Restauração de avaliação deletada (Admin).
- **Recálculo Automático de Média:** Atualização automática do campo `rating` do `DriverModel` a cada inclusão, edição, remoção ou restauração de avaliação.
- **Seeder:** Adição de avaliações de teste.
- **Testes automatizados:** `DriverRatingServiceTest` e `DriverRatingControllerTest`.

## Capabilities

### New Capabilities

- `driver-rating-management`: Endpoints REST para gerenciamento de avaliações de motoristas, controle de acesso por propriedade, validações de nota (1 a 5) e recálculo dinâmico da média geral do motorista.

### Modified Capabilities

- `driver-management`: Média geral do motorista (`rating`) é atualizada automaticamente quando avaliações mudam.

## Impact

- **Database:** Nova tabela `driver_rating` na migration `V16`.
- **Entity:** Nova entidade `DriverRatingModel`.
- **Driver:** Média recalculada via query agregada (`AVG`).
- **Security:** `@sec.isDriverRatingOwner(...)` no `SecurityEvaluator`.
