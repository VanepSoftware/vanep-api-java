## ADDED Requirements

### Requirement: Cliente avalia motorista
O cliente cria uma avaliação (score + comment) para o motorista, vinculada ao `client_driver`.

#### Scenario: Criar avaliação
- **WHEN** o cliente envia score (1.00–5.00) e comment (opcional) para um vínculo existente
- **THEN** um `driver_rating` é criado e o trigger recalcula `driver.rating`

#### Scenario: Uma avaliação por vínculo
- **WHEN** já existe um `driver_rating` para o `client_driver_id`
- **THEN** a criação é rejeitada (constraint unique)

#### Scenario: Listar avaliações do motorista
- **WHEN** GET `/api/drivers/{driverId}/ratings`
- **THEN** retorna todas as avaliações (não-deletadas) do motorista

#### Scenario: Editar avaliação
- **WHEN** PUT com novo score/comment
- **THEN** o registro é atualizado e o trigger recalcula a média

#### Scenario: Remover avaliação
- **WHEN** DELETE no rating
- **THEN** soft-delete (set deleted_at)
