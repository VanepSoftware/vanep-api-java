## Abordagem

Reutilizar `SqlStatementCounter` (Hibernate `Statistics.getPrepareStatementCount()`), já usado em contrato e vínculo. O audit compara a contagem antes e depois de criar mais 3 linhas (`@SpringBootTest` + H2), imitando o padrão dos testes existentes.

## Harness

```
make audit-n-plus-one
```

- Teste: `NPlusOneRepositoryAuditTest`, roda também no `make test` / CI e falha se alguma sonda crescer.
- Cada sonda roda numa transação read-only e cria associações diferentes por linha (mídia, usuário, revisor), senão um `EAGER` vazio esconde o N+1.
- Saída legível: linhas `N+1-AUDIT …` no stdout.

## Limitações

- Não cobre camada HTTP nem serviços inteiros — só repositório + touch das associações que o mapper usaria.
- Não substitui a revisão de cada query nova; a regra 17 exige a sonda no PR que cria a lista.
