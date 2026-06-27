## ADDED Requirements

### Requirement: Hub do vínculo client-driver
A tabela `client_driver` representa o relacionamento N:N entre clientes e motoristas. É a entidade central de onde pendem ratings, contratos e conversas.

#### Scenario: Criar vínculo
- **WHEN** um client e um driver são associados
- **THEN** um registro `client_driver` é criado com status PENDING

#### Scenario: Unicidade do vínculo
- **WHEN** já existe um `client_driver` para o par (client_id, driver_id)
- **THEN** não é possível criar outro (constraint unique)
