## ADDED Requirements

### Requirement: Motorista avalia cliente
O motorista cria uma avaliação (score + comment) para o cliente, vinculada ao `client_driver`.

#### Scenario: Criar avaliação
- **WHEN** o motorista envia score (1.00–5.00) e comment (opcional) para um vínculo existente
- **THEN** um `client_rating` é criado e o trigger recalcula `client.rating`

#### Scenario: Uma avaliação por vínculo
- **WHEN** já existe um `client_rating` para o `client_driver_id`
- **THEN** a criação é rejeitada (constraint unique)

#### Scenario: Listar avaliações do cliente
- **WHEN** GET `/api/clients/{clientId}/ratings`
- **THEN** retorna todas as avaliações (não-deletadas) do cliente

#### Scenario: Editar avaliação
- **WHEN** PUT com novo score/comment
- **THEN** o registro é atualizado e o trigger recalcula a média

#### Scenario: Remover avaliação
- **WHEN** DELETE no rating
- **THEN** soft-delete (set deleted_at)
