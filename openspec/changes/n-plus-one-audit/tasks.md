Issue no Genwork: **VAN-81**. Branch: `VAN-81/n-plus-one-audit`.

## Fase 1 — Validar

- [x] 1.1 Inventário: repositórios com `@Query` / derivados que retornam entidades; cruzar com quem já usa `SqlStatementCounter`
- [x] 1.2 `NPlusOneRepositoryAuditTest` comparando a contagem de statements antes/depois de +3 linhas
- [x] 1.3 `make audit-n-plus-one` e `validation-report.md` com hotspots
- [x] 1.4 Validação extra: associações diferentes por linha, leitura em transação, páginas e lista do admin

## Fase 2 — Corrigir + constitution + gate

- [x] 2.1 Corrigir cada hotspot do relatório (`@EntityGraph` em assistant, vehicle e driver document)
- [x] 2.2 Sonda de regressão por repositório tocado no `NPlusOneRepositoryAuditTest`
- [x] 2.3 Constitution regra 17: testar a contagem de statements antes de mergear uma query de lista
- [x] 2.4 Audit falha quando a contagem cresce com N e roda no `make test` / CI
