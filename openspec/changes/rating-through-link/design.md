## Context

A #30 entregou o `client_driver`. Esta change é a primeira a consumir o hub, e ela consome **para trás**: pega duas features que já estão em produção, com dado dentro, e as reapoia na linha canônica.

Estado de hoje, medido no código:

| | `driver_rating` (V16) | `client_rating` (V33) |
|---|---|---|
| par | `driver_id` + `client_id` | `driver_id` + `client_id` |
| `deleted_at` | tem | **não tem** |
| `@SoftDelete` no model | tem | **não tem** |
| índice único do par | parcial | **total** |
| `delete()` | soft | **físico** |
| `update` | tem | não tem |
| `restore` | tem | não tem |
| permissões | 6 | 4 |

As duas nasceram de PRs diferentes, meses apart, sem uma revisar a outra. O resultado não é uma decisão — é deriva.

## Goals / Non-Goals

**Goals**

- O par passa a existir em **um** lugar: `client_driver`
- As duas avaliações ficam com o mesmo comportamento de remoção
- Avaliar exige vínculo; os dois TODOs saem do código
- O dado existente sobrevive à migração, com os vínculos que faltam criados

**Non-Goals**

- Fechar toda a assimetria entre as gêmeas (o `update` do `client_rating` fica fora, R6)
- Mudar o contrato HTTP das respostas
- Mexer na `trip`, que liga em outra relação

## Decisions

### D1 — Três migrations, uma por entrega

`V37` conserta o soft delete do `client_rating`. `V38` move `driver_rating` para o hub. `V39` move `client_rating`.

O conserto do soft delete **vale sozinho**: se as fases seguintes forem reprovadas na revisão, o `DELETE` destrutivo já parou de destruir. Amarrar as três faz o conserto barato refém do conserto caro.

A separação por **tabela** (V38/V39) não é estética. Trocar o model para `link` quebra na hora serviço, mapper, seeder e testes daquela feature — não dá para separar schema de consumidor. Mas dá para separar as duas features entre si, e é o que mantém cada PR dentro da regra 41. A V39 ainda exercita o caminho de reuso: os pares que a V38 vinculou caem no `NOT EXISTS` e não viram vínculo novo.

### D2 — `deleted_at` e índice parcial na MESMA migration

Não dá para acrescentar `deleted_at` ao `client_rating` e deixar o índice único total para depois.

Hoje o índice `(driver_id, client_id)` é total, e funciona porque o `delete` é físico: a linha some, o par libera. No instante em que o `delete` vira soft, a linha **fica**, e o índice total passa a barrar qualquer avaliação nova daquele par. **O par trava para sempre**, e o `restore` vira a única saída de um bloqueio que ele deveria evitar.

É a mesma armadilha do D2 da #30, com uma diferença que a torna pior: lá o índice parcial nasceu junto; aqui ele tem que ser trocado **no mesmo passo** em que o soft delete entra. Meio conserto é uma regressão.

### D3 — O backfill cria os vínculos que faltam, como `ACTIVE`

A `V38` não pode assumir que existe um `client_driver` para cada par já avaliado — não existe; o hub nasceu ontem.

```sql
insert into client_driver (token, client_id, driver_id, status)
select ... from (select distinct client_id, driver_id from <tabela de rating>)
where not exists (vínculo ativo para aquele par)
```

Status `ACTIVE`, não `PENDING`: uma avaliação registrada é prova de que a relação aconteceu de verdade. Marcar como `PENDING` descreveria um convite pendente que nunca houve.

O `DISTINCT` e o `NOT EXISTS` carregam dois casos diferentes. O `DISTINCT` resolve **dentro** da tabela: uma avaliação soft-deletada repete o par de uma ativa, e sem ele o índice único do `client_driver` quebra na inserção. O `NOT EXISTS` resolve **entre** as tabelas: um par avaliado nas duas direções é vinculado pela `V38` e reencontrado pela `V39`, que não cria outro. Um relacionamento visto dos dois lados continua sendo **um**.

### D4 — `client_id` e `driver_id` saem das tabelas de avaliação

Manter as colunas "por segurança" mantém o problema: duas fontes para o mesmo fato, livres para divergir. Uma avaliação cujo `client_driver_id` aponta a Maria e cujo `client_id` aponta o Bruno é um estado que ninguém consegue interpretar.

O par passa a ser lido por navegação: `rating.link.client`, `rating.link.driver`.

### D5 — A resposta HTTP não muda

`DriverRatingResponseDTO` e `ClientRatingResponseDTO` continuam com `driverToken`, `driverName`, `clientToken`, `clientName`. Só o mapper muda, de `model.getDriver()` para `model.getLink().getDriver()`.

O mobile não fica sabendo desta change. Uma migração de modelo de dados que quebra o app é uma migração que não vai ser feita.

### D6 — Sem vínculo, 404

Os TODOs pedem a exigência; falta decidir o código HTTP.

`404 Not Found`, com a chave `*_rating.link.not_found`. Não `403`: não é falta de permissão, é ausência do recurso que ancora a operação. Não `400`: o corpo enviado está correto; o que falta está no banco.

É uma mudança de comportamento observável — hoje a mesma chamada devolve `201`. Está declarada no `Impact`.

### D7 — Unicidade por vínculo, não por par

O índice deixa de ser `(driver_id, client_id)` e passa a ser `(client_driver_id)`, parcial. Uma avaliação ativa por vínculo, por direção.

Isso é **mais forte** do que parece: se um par for desvinculado e vinculado de novo, o `client_driver_id` é outro, e uma avaliação nova é permitida. O histórico antigo continua lá, apontando o vínculo antigo. É o comportamento correto — são duas relações distintas no tempo.

### D8 — `restore` entra no `client_rating`; `update` não

Soft delete sem `restore` é um caixote sem tampa: o dado fica no banco e ninguém alcança. Por isso o `restore` vem junto, na fase 1.

O `update` fica fora. Não é conserto de bug — é superfície nova, com DTO, endpoint, permissão e testes, e a regra 41 já está apertada. Vira issue (R6).

## Risks / Trade-offs

**R1 — Nenhum teste executa a `V37`, a `V38` e a `V39`.** A suíte roda H2 com `flyway.enabled=false` e `ddl-auto=create-drop`. As migrations, e principalmente o **backfill**, não passam por teste algum. Mitigação: aplicar as três manualmente contra o PostgreSQL local, com dado dentro, e conferir linha a linha — tasks 1.6, 2.6 e 3.6. Foi assim na `trip` e na #30.

**R2 — O backfill é a parte perigosa.** `V38` e `V39` inserem em `client_driver` e depois marcam `not null`. Se um par avaliado não gerar vínculo, o `alter column set not null` derruba a migration no meio. Mitigação: as tasks 2.6 e 3.6 exigem conferir `count(*) where client_driver_id is null` **igual a zero** antes do `set not null`, contra dado real.

**R3 — `client_rating` pode ter pares duplicados hoje.** O índice é total, então não pode haver dois ativos; mas como o `delete` é físico, não há linhas removidas para atrapalhar. O risco real é o inverso: em `driver_rating`, pares **soft-deletados** podem repetir o mesmo par. O backfill precisa de `distinct` e o índice novo precisa ser parcial, ou a migration quebra na criação do índice.

**R4 — `@SoftDelete` muda o significado do `delete()` sem mudar a assinatura.** Nenhum chamador quebra na compilação; o comportamento muda em silêncio. Quem lê `client_rating` por SQL cru passa a ver linhas que achava apagadas. Mitigação: teste nomeado provando que a linha permanece com `deleted_at` preenchido, e a mudança declarada no `Impact`.

**R5 — N+1 na listagem.** `link` é `EAGER` e leva `client.user` e `driver.user` junto. Sem fetch join, listar N avaliações dispara queries por linha — regra 17. Mitigação: fetch join explícito nas queries paginadas das duas.

**R6 — A assimetria não fecha de todo.** `client_rating` segue sem `update`. Vira issue, task 3.8.

**R7 — Ordem de merge.** Esta pilha depende da `V36`. Se a #30 não entrar na `main` primeiro, a `V38` referencia uma tabela que não existe. Task 0.3.

## Open Questions

**Q1 — Avaliação de vínculo `INACTIVE` deve ser permitida?** Hoje o serviço vai exigir apenas que o vínculo exista. Um cliente que encerrou a relação ainda pode avaliar? Provavelmente sim — a nota vem depois do serviço prestado. Fica como está (qualquer status serve) até a #42 definir o ciclo de vida.

**Q2 — O que fazer com avaliações órfãs se um vínculo for removido?** Hoje nada: `client_driver_id` continua apontando a linha soft-deletada e a avaliação segue visível. Registrar na #42.
