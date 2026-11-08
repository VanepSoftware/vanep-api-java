## Why

A regra 17 da constitution já proíbe N+1, e vários módulos críticos já têm teste com `SqlStatementCounter` — mas a cobertura é pontual (contrato, vínculo, passageiros, readiness). Várias consultas derivadas (`findByDriverId`, etc.) carregam grafos `EAGER` sem `join fetch`, e não há um inventário nem um harness que mostre onde a contagem de statements escala com o tamanho da coleção.

Genwork **VAN-81** pede validar todas as queries, facilitar o teste com um caso geral, e endurecer a constitution para exigir prova antes de merge.

## What Changes

- `NPlusOneRepositoryAuditTest` sonda as listas de alto risco (antes/depois de +3 linhas, em transação, com associações diferentes por linha) e roda no `make test`; falha se alguma contagem crescer.
- Alvo `make audit-n-plus-one` para rodar só o audit.
- Corrige os hotspots encontrados com `@EntityGraph`: `AssistantRepository.findByDriverId`, `VehicleRepository.findByDriverId`, listas e páginas de `DriverDocumentRepository` (inclusive `findAll(Pageable)` do admin).
- Constitution regra 17: toda query de lista nova ou alterada traz a prova de contagem estável de statements.
- `validation-report.md` com as três rodadas do audit.

## Referências

- Genwork: **VAN-81**
- Constitution regra 17; testes existentes com `SqlStatementCounter` em `ClientDriverRepositoryTest`, `ContractRepositoryTest`, `UnlinkedPassengerRepositoryTest`, `ContractReadinessControllerTest`.
