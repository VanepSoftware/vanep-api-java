## Why

O Vanep precisa de um sistema de avaliação bidirecional: clientes avaliam motoristas e motoristas avaliam clientes. Isso é fundamental para a confiança da plataforma e para a descoberta de motoristas (ranking por nota). As tabelas `client.rating` e `driver.rating` já existem no schema, aguardando os ratings que alimentam a média via trigger.

## What Changes

- Criar tabela `client_driver` (hub do vínculo N:N entre client e driver)
- Criar tabela `driver_rating` (cliente avalia motorista, 1x por vínculo)
- Criar tabela `client_rating` (motorista avalia cliente, 1x por vínculo)
- Criar triggers PostgreSQL para recalcular a média em `driver.rating` e `client.rating`
- CRUD REST completo para ambos os ratings
- Seeder com dados de teste
- Testes unitários

## Capabilities

### New Capabilities
- `client-driver-binding`: Entidade hub do vínculo N:N entre client e driver
- `driver-rating`: CRUD de avaliação que o cliente dá ao motorista (via vínculo)
- `client-rating`: CRUD de avaliação que o motorista dá ao cliente (via vínculo)

### Modified Capabilities

## Impact

- Migration V6: novas tabelas e triggers
- Novos pacotes: `clientdriver`, `driver.rating`, `client.rating`
- Novos endpoints REST: `/api/drivers/{driverId}/ratings`, `/api/clients/{clientId}/ratings`
- Campo `rating` em `client` e `driver` passa a ser atualizado por trigger (read-only no Java)
