## Context

O `vanep-diagram.dbml` descreve `client_driver` como **"HUB do vínculo cliente-motorista. Contratos, avaliações e chat penduram aqui."** São seis colunas e um índice único.

O que existe hoje e importa:

- `client` e `driver`, ambos 1:1 com `users`, sem nada ligando os dois
- `driver_rating` (V16) e `client_rating` (V33) ligam **direto** em `driver_id` + `client_id`, contrariando o canônico — e ainda divergem entre si: o V16 tem `deleted_at`, o V33 não
- Ambos os services carregam o mesmo TODO citando `client_driver` pelo nome
- `SecurityEvaluator` tem seis `is<Entity>Owner`, todos comparando **um** dono
- A pilha do N-177 tomou `V34` e `V35`; o próximo livre é **V36**

## Goals / Non-Goals

**Goals**

- Um vínculo ativo por par `(cliente, motorista)`, garantido pelo banco
- As duas partes leem o próprio vínculo sem depender de permissão de admin
- O ciclo de vida do vínculo é explícito e testável
- `proposal` (#42) encontra a FK pronta, sem tocar no schema

**Non-Goals**

- Migrar as avaliações para o hub
- Criar vínculo por fluxo de produto (é da #42)
- Regra de "só avalia quem já viajou" — depende do hub existir primeiro

## Decisions

### D1 — `status` é `PENDING`, `ACTIVE`, `INACTIVE`

Vem direto do `Enum relationship_status` do DBML. Sem inventar valores.

O significado que o canônico dá: o vínculo **nasce `PENDING`** no primeiro envio de proposta, vira `ACTIVE` quando existe contrato ativo, e `INACTIVE` quando a relação encerra. Esta change não implementa essas transições — não há proposta nem contrato — mas o enum nasce completo para que a #42 não precise de migration.

Rejeitado: começar só com `ACTIVE`/`INACTIVE` e acrescentar `PENDING` depois. Acrescentar valor a enum Java é trivial; ao `check` da coluna, não — exige migration nova. Mesma lição do `CANCELLED` em `trip`.

### D2 — O índice único é parcial

`unique (client_id, driver_id) where deleted_at is null`.

O DBML pede o único simples, mas a tabela tem `deleted_at`. Sem o `where`, um vínculo removido pelo admin travaria aquele par **para sempre** — Maria e Carlos nunca mais poderiam se relacionar, e o `restore` viraria a única saída de um bloqueio que ele deveria evitar.

É exatamente o que a `trip` ensinou (D4 daquela change), e o teste `allowsANewLinkAfterTheSamePairWasSoftDeleted` guarda a lição.

### D3 — O vínculo tem **dois** donos

Esta é a novidade. Os seis `is<Entity>Owner` do `SecurityEvaluator` respondem "este recurso é do chamador?" comparando um único usuário. Um `client_driver` pertence ao cliente **e** ao motorista.

    @sec.isClientDriverLinkParty(#token, authentication)

O nome não é `...Owner` de propósito: não há dono, há **partes**. Chamar de owner sugeriria exclusividade que não existe, e o próximo a ler o `SecurityEvaluator` trataria como os outros seis.

Rejeitado: dois métodos (`isClientDriverClient` e `isClientDriverDriver`). Toda chamada precisaria das duas em `or`, e esquecer uma delas seria um bug silencioso de autorização — o motorista tomando 403 no próprio vínculo.

### D4 — `DELETE` e `restore` são só do admin

Leitura e atualização aceitam qualquer uma das partes; remover não. Um cliente não apaga um vínculo que o motorista também usa, e vice-versa — o dado é compartilhado.

Encerrar uma relação é `status = INACTIVE`, que é dado de negócio e fica visível. `deleted_at` é correção administrativa e some. São coisas diferentes, como `CANCELLED` e `deleted_at` em `trip`.

### D5 — `PATCH` com `JsonNullable`, e só `status` é mutável

Regra 16. O único campo que muda ao longo da vida é `status`.

`client` e `driver` **não** são mutáveis: trocar qualquer um dos dois não edita o vínculo, cria outro — e quebraria em silêncio a unicidade do par, além de deixar propostas e contratos pendurados num par que deixou de existir.

### D6 — Quem cria o vínculo, por enquanto, é o admin

O DBML anota *"criado PENDING no 1º envio"*: na vida real quem cria é a proposta (#42). Ela não existe.

Então esta change entrega `POST /api/client-drivers` para o admin e um seeder — e nada mais cria vínculo. É dependência-primeiro (regra 38), não entrega pela metade: a #42 vai encontrar a tabela, a FK e o `PENDING` prontos.

### D7 — `GET /me` devolve lista, não um vínculo

Um cliente tem vários motoristas; um motorista tem muitos clientes. A rota devolve os vínculos do chamador, seja ele qual for — o tipo do usuário decide de que lado da tabela procurar.

Chamador sem vínculo nenhum recebe `200` com lista vazia, não `204`: lista vazia é a resposta correta de uma coleção sem elementos. Mesma decisão do `GET /trips/today` (D10 daquela change), pelo mesmo motivo.

## Risks / Trade-offs

| # | Risco | Mitigação |
|---|---|---|
| **R1** | `driver_rating` (V16) e `client_rating` (V33) ligam direto em `driver_id`+`client_id`, furando o hub. Nasce um hub que a produção ignora | **Aceito e registrado, não resolvido aqui.** Migrar é migration de dados em tabela com registro vivo, merece issue própria. **Quanto mais cedo, mais barato**: hoje são 2 tabelas; com `proposal`, `contract` e `conversation` viram 5. Os TODOs já plantados nos dois services são o gancho |
| **R2** | Os dois `*_rating` divergem entre si — V16 tem `deleted_at`, V33 não | Fora do escopo, mas some junto se o R1 for pago: ao migrar, os dois passam a seguir o hub |
| **R3** | `V36` não roda na suíte (H2 com `ddl-auto`), então o índice parcial nunca é exercitado em teste | Aplicar manualmente contra PostgreSQL 17 e tentar dois vínculos do mesmo par, como feito na `trip` (tarefa 1.6 daquela change) |
| **R4** | `isClientDriverLinkParty` é o primeiro método de posse com duas partes; quem copiar o padrão dos outros seis pode escrever a comparação só de um lado | Teste nomeado para **cada** lado: cliente enxerga, motorista enxerga, terceiro toma 403 |
| **R5** | Ninguém cria vínculo pelo fluxo real até a #42 | Aceito (D6). O seeder cria um par para que o app e o Postman tenham dado |

## Open Questions

| | Pergunta | Estado |
|---|---|---|
| **Q1** | O vínculo volta a `PENDING` se o contrato encerra e o cliente propõe de novo, ou fica `INACTIVE` e nasce outro? | 🔴 Decidir na #42, que é quem move o status. O enum aceita as duas leituras |
| **Q2** | O admin pode criar vínculo já `ACTIVE`, ou tudo nasce `PENDING`? | 🟡 Proposto: aceita o status no `POST` (é correção administrativa, o D12 do `trip` vale aqui), default `PENDING` |

## Migration Plan

| Migration | Conteúdo |
|---|---|
| **V36** | `create table client_driver` + FKs para `client(id)` e `driver(id)` + índices únicos parciais em `token` e em `(client_id, driver_id)` |

Sem backfill: não há dado de vínculo para migrar (os ratings não viram, ver R1). `V34` e `V35` estão tomadas pela pilha do N-177 — **reconfirmar antes de escrever o arquivo**.

## Rollout — grafo de dependência e plano de PRs

```
main
 └─ fase 1 — schema, model, repository         PR 1 → main
      └─ fase 2 — service, permissões, posse    PR 2 → PR 1
           └─ fase 3 — CRUD HTTP, mapper, seed  PR 3 → PR 2
```

| Fase | Conteúdo | Depends on | Parallel with |
|---|---|---|---|
| 1 | `RelationshipStatus`, V36, `ClientDriverModel`, `ClientDriverRepository` | — | — |
| 2 | `ClientDriverService`, permissões, `@sec.isClientDriverLinkParty` | Fase 1 | — |
| 3 | `ClientDriverController`, DTOs, `ClientDriverMapper`, `ClientDriverSeeder` | Fase 2 | — |

Três fases, não cinco como a `trip`: não há máquina de estados nem policy de coerência para isolar. Cadeia sem paralelismo — cada fase consome o tipo da anterior.

**Mergear pelo `merge stack` na última**, ou apagando a branch a cada merge: o repo tem `delete_branch_on_merge: false`, e sem isso o GitHub não reaponta a base das filhas — foi o que quebrou a pilha do #150.
