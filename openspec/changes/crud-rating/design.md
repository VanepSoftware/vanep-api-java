## Context

O Vanep é uma plataforma de transporte escolar com Spring Boot + PostgreSQL. Já existem as entidades `Client` e `Driver` (1:1 com `User`), ambas com campo `rating numeric(3,2)`. Não existe CRUD de domínio ainda — só auth. Este é o primeiro CRUD de negócio.

A tabela `client_driver` (hub N:N) não existe no código Java ainda, mas é pré-requisito para ratings.

## Goals / Non-Goals

**Goals:**
- Criar `client_driver` como entidade independente (pacote próprio)
- CRUD completo de `driver_rating` dentro de `driver.rating`
- CRUD completo de `client_rating` dentro de `client.rating`
- Triggers PostgreSQL para média automática
- Validação: só vínculo existente pode avaliar, 1 rating por vínculo

**Non-Goals:**
- Autenticação/autorização por role nos endpoints (será feito depois)
- Paginação avançada / filtros
- Notificações de avaliação

## Decisions

1. **`ClientDriver` em pacote próprio** (`br.com.vanep.clientdriver`): não fica aninhado em client nem driver, é uma entidade de relacionamento independente.

2. **Rating feature-based dentro da entidade pai**: `driver.rating` e `client.rating` como sub-pacotes.

3. **Constraint de unicidade no banco**: `client_driver_id` UNIQUE em `driver_rating` e `client_rating` — garante 1 avaliação por vínculo (RN03).

4. **Triggers no PostgreSQL** (não no Java): recalcula `AVG(score)` no INSERT/UPDATE. O campo `rating` nas entidades Java é read-only (não tem setter exposto nos endpoints).

5. **Soft-delete**: `deleted_at` em todas as tabelas, consistente com o padrão existente.

6. **Migration única (V6)**: `client_driver` + `driver_rating` + `client_rating` + triggers — são tabelas acopladas.

7. **REST pattern**: recursos aninhados — `/api/drivers/{driverId}/ratings` e `/api/clients/{clientId}/ratings`.

## Risks / Trade-offs

- **Trigger no banco vs evento Java**: trigger é mais simples e consistente, mas não emite evento Spring. Se precisar de notificação futura, adicionar listener.
- **Sem auth nos endpoints por agora**: qualquer request autenticado pode criar rating. Será refinado com RBAC.
