## Context

A #30 entregou o `client_driver`. Esta change é a primeira a consumir o hub, e ela consome **para trás**: pega duas features que já estão em produção, com dado dentro, e as reapoia na linha canônica.

Estado de hoje, medido no código:

| | `driver_rating` (V16) | `client_rating` (V33) |
|---|---|---|
| par | `driver_id` + `client_id` | `driver_id` + `client_id` |
| `deleted_at` | tem | não tem |
| `@SoftDelete` no model | tem | não tem |
| índice único do par | parcial | total |
| `delete()` | soft | físico |
| `update` | tem | não tem |
| `restore` | tem, e **quebra** | não tem |

As duas nasceram de PRs diferentes, meses apart, sem uma revisar a outra. O resultado não é uma decisão — é deriva.

## Goals / Non-Goals

**Goals**

- O par passa a existir em **um** lugar: `client_driver`
- As duas avaliações removem do mesmo jeito: fisicamente, sem restore
- Avaliar exige vínculo; os dois TODOs saem do código
- O dado existente sobrevive à migração, com os vínculos que faltam criados

**Non-Goals**

- Fechar toda a assimetria entre as gêmeas (o `update` do `client_rating` fica fora, R6)
- Mudar o contrato HTTP das respostas
- Mexer na `trip`, que liga em outra relação

## Decisions

### D1 — Três migrations, uma por entrega

`V37` tira o soft delete do `driver_rating`. `V38` move `driver_rating` para o hub. `V39` move `client_rating`.

O alinhamento da remoção **vale sozinho**: se as fases seguintes forem reprovadas, o `restore` que devolve 500 já saiu.

A separação por **tabela** (V38/V39) não é estética. Trocar o model para `link` quebra na hora serviço, mapper, seeder e testes daquela feature — não dá para separar schema de consumidor. Mas dá para separar as duas features entre si, e é o que mantém cada PR dentro da regra 41. A V39 ainda exercita o caminho de reuso: os pares que a V38 vinculou caem no `NOT EXISTS` e não viram vínculo novo.

### D2 — Avaliação não tem soft delete (decisão de revisão)

A primeira versão desta change ia no sentido oposto: dava soft delete ao `client_rating`, seguindo a regra 19 ao pé da letra. A revisão da #201 derrubou isso, e com razão.

Uma nota é a **opinião de um momento**. O cenário que o revisor descreveu:

```
avalia 3 estrelas → remove → avalia 5 estrelas → restaura a de 3?
```

O `restore` não tem significado aqui. E ele não é só inútil, é quebrado: restaurar a de 3 enquanto a de 5 está ativa deixa **duas** ativas para o mesmo par. O índice único recusa, e sem tratamento para `DataIntegrityViolationException` a resposta é **500**. Esse furo existe no `driver_rating` desde a V16.

Então nenhuma das gêmeas tem soft delete. Remover apaga a linha; avaliar de novo cria outra.

**Isso contradiz a regra 19.** A saída não é o desvio silencioso — o próximo revisor barraria pelo motivo oposto. A exceção entra **escrita na própria regra 19**, com o motivo.

### D3 — As removidas saem antes da coluna cair

A `V37` não pode simplesmente dropar `deleted_at`.

Sem a coluna, uma `driver_rating` que estava soft-deletada volta a valer: **reaparece na listagem e entra na média do motorista**. Por isso a ordem é:

```sql
delete from driver_rating where deleted_at is not null;   -- 1. as removidas saem
drop index ...;                                            -- 2. o índice parcial cai
alter table driver_rating drop column deleted_at;          -- 3. a coluna cai
create unique index ... (driver_id, client_id);            -- 4. índice total
```

O passo 1 **destrói dado**. Do ponto de vista do usuário essas avaliações já não existiam; mas o histórico some, e isso está declarado no `Impact`.

O passo 4 também depende do 1: com uma removida e uma ativa para o mesmo par, o índice total não seria criado.

### D4 — O backfill cria os vínculos que faltam, como `ACTIVE`

A `V38` não pode assumir que existe um `client_driver` para cada par já avaliado — não existe; o hub nasceu ontem.

```sql
insert into client_driver (token, client_id, driver_id, status)
select ... from (select distinct client_id, driver_id from <tabela de rating>)
where not exists (vínculo ativo para aquele par)
```

Status `ACTIVE`, não `PENDING`: uma avaliação registrada é prova de que a relação aconteceu. `PENDING` descreveria um convite que nunca houve.

O `NOT EXISTS` resolve **entre** as tabelas: um par avaliado nas duas direções é vinculado pela `V38` e reencontrado pela `V39`, que não cria outro. Um relacionamento visto dos dois lados continua sendo **um**. O `DISTINCT` é defensivo: depois da `V37`, o índice total já garante um par por linha em cada tabela.

### D5 — `client_id` e `driver_id` saem das tabelas de avaliação

Manter as colunas "por segurança" mantém o problema: duas fontes para o mesmo fato. Uma avaliação cujo `client_driver_id` aponta a Maria e cujo `client_id` aponta o Bruno é um estado que ninguém consegue interpretar.

O par passa a ser lido por navegação: `rating.link.client`, `rating.link.driver`.

### D6 — A resposta HTTP não muda

`DriverRatingResponseDTO` e `ClientRatingResponseDTO` continuam com `driverToken`, `driverName`, `clientToken`, `clientName`. Só o mapper muda, de `model.getDriver()` para `model.getLink().getDriver()`.

O mobile não fica sabendo desta change.

### D7 — Sem vínculo, 404

`404 Not Found`, com a chave `*_rating.link.not_found`. Não `403`: não é falta de permissão. Não `400`: o corpo enviado está correto; o que falta está no banco.

É uma mudança de comportamento observável — hoje a mesma chamada devolve `201`.

### D8 — Unicidade por vínculo, índice total

O índice deixa de ser `(driver_id, client_id)` e passa a ser `(client_driver_id)`, **total** — sem soft delete não há linha removida ocupando o vínculo, então o parcial perde a razão de ser.

Se um par for desvinculado e vinculado de novo, o `client_driver_id` é outro, e uma avaliação nova é permitida contra o vínculo novo.

## Risks / Trade-offs

**R1 — Nenhum teste executa a `V37`, a `V38` e a `V39`.** A suíte roda H2 com `flyway.enabled=false` e `ddl-auto=create-drop`. Mitigação: aplicar as três manualmente contra o PostgreSQL, com dado dentro — tasks 1.6, 2.6 e 3.6.

**R2 — O backfill é a parte perigosa.** `V38` e `V39` inserem em `client_driver` e depois marcam `not null`. Se um par avaliado não gerar vínculo, o `set not null` derruba a migration no meio. Mitigação: conferir `count(*) where client_driver_id is null` **igual a zero** antes do `set not null`.

**R3 — A `V37` destrói dado.** As `driver_rating` soft-deletadas saem de verdade. É consequência direta do D2 e está declarada no `Impact`. Não há como preservá-las sem manter a coluna.

**R4 — Mudança de API.** `POST /api/driver-ratings/{token}/restore` deixa de existir. Quem o chama recebe 404/405. Nenhum consumidor conhecido no mobile.

**R5 — N+1 na listagem.** `link` é `EAGER` e leva `client.user` e `driver.user` junto. Mitigação: fetch join explícito nas queries paginadas (regra 17).

**R6 — A assimetria não fecha de todo.** `client_rating` segue sem `update`. Vira issue.

**R7 — Ordem de merge.** Esta pilha depende da `V36`. Se a #30 não entrar na `main` primeiro, a `V38` referencia uma tabela que não existe.

**R8 — A exceção na constitution é mudança de norma.** Vai junto com a #201 para ser revisada no mesmo lugar em que a decisão foi tomada.

## Open Questions

**Q1 — Avaliação de vínculo `INACTIVE` deve ser permitida?** Hoje o serviço exige apenas que o vínculo exista. Fica como está até a #42 definir o ciclo de vida.

**Q2 — O que fazer com avaliações de um vínculo removido?** Hoje nada: `client_driver_id` continua apontando a linha soft-deletada do vínculo. Registrar na #42.
