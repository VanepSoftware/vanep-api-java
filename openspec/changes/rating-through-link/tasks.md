> **Pilha de branches.** Cada branch nasce de dentro da anterior e cada PR aponta a anterior
> como `--base`. A fase 1 sai da última branch da #30, **não** de `main` — a `V38`
> precisa da `V36`.
>
> ```
> main ─ …-schema ─ …-service ─ …-http ─ …-rating-soft-delete ─ …-driver-rating-link ─ …-client-rating-link
>          ─────────── #30 ───────────    PR 4 → #30 http        PR 5 → PR 4            PR 6 → PR 5
> ```
>
> **Mergear pelo `merge stack` na última PR.** O repo tem `delete_branch_on_merge: false`,
> e sem apagar a branch o GitHub não reaponta a base das filhas — foi assim que a pilha
> do #150 colapsou e precisou de uma PR de recuperação.

## 0. Preparation

- [x] 0.1 Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] 0.2 Revisar `proposal.md`, `design.md` e o spec `rating-through-link`
- [ ] 0.3 **Confirmar que a #30 entrou na `main`.** A `V38` referencia `client_driver`. Se a `V36` não estiver na `main`, esta pilha não pode ser mergeada (R7)
- [x] 0.4 **Reconfirmar os números de Flyway.** `V34`/`V35` são do N-177, `V36` é da #30. O plano assume `V37`, `V38` e `V39`. Rechecado com `git ls-tree` em todas as branches remotas

## 1. Phase 1 — avaliação sem soft delete (PR 4)

> Goal: as duas gêmeas passam a remover do mesmo jeito — fisicamente, sem restore. Independe do hub.
> Depends on: #30 | Parallel with: —
> Order: test → migration → model → repository → permissions → messages → service → controller

> **Decisão de revisão.** A primeira versão desta fase dava soft delete ao `client_rating`.
> A revisão da #201 inverteu o sentido: avaliação é opinião de um momento, e restaurar uma
> antiga ao lado de uma nova não tem significado — além de devolver 500 pelo índice único
> do par. As gêmeas se alinham **para baixo** (D2).

- [x] 1.1 Criar branch `feat/rating-soft-delete` a partir de `feat/30-client-driver-http`
- [x] 1.2 Teste nomeado provando que `delete` **remove a linha**: depois do `DELETE` HTTP, `count()` é zero
- [x] 1.3 Teste nomeado do fluxo da revisão: avaliar, remover, **avaliar o mesmo par de novo** → 201, e existe exatamente uma avaliação, com a nota nova
- [x] 1.4 Migration `V37__driver_rating_hard_delete.sql`, nesta ordem (D3): (a) `delete` das linhas com `deleted_at` preenchido; (b) dropar o índice parcial; (c) dropar `deleted_at`; (d) recriar o índice do par **total**, igual ao da `client_rating`
- [x] 1.5 **O `delete` vem antes da coluna cair.** Sem ele, as avaliações removidas voltam a valer: reaparecem na listagem e entram na média do motorista. E o índice total não seria criado com uma removida e uma ativa no mesmo par
- [x] 1.6 **Aplicar a `V37` manualmente contra o PostgreSQL, com uma avaliação soft-deletada dentro**, e conferir que ela sumiu e não entra na média. A suíte roda `flyway.enabled=false`, nenhum teste executa esta migration (R1)
- [x] 1.7 `DriverRatingModel`: remover `@SoftDelete`
- [x] 1.8 `DriverRatingRepository`: remover `restoreByToken` e `existsDeletedByToken`
- [x] 1.9 `DriverRatingService` e `DriverRatingController`: remover `restore` e `POST /{token}/restore`
- [x] 1.10 Remover `RESTORE_DRIVER_RATING` do `PermissionEnum` e a chave `driver_rating.already_active` dos dois `messages` (regra 34). O seeder reescreve o bundle ADMIN no próximo start
- [x] 1.11 **Registrar a exceção na regra 19 da constitution**, com o motivo. Sem isso o código contradiz a regra e o próximo revisor barra pelo motivo oposto (R8)
- [ ] 1.12 `make lint` + `./mvnw verify`; abrir PR em pt-BR, `--base feat/30-client-driver-http` (regras 44 e 47)

## 2. Phase 2 — `driver_rating` pendura no vínculo (PR 5)

> Goal: a primeira das duas gêmeas passa a apontar `client_driver`, com o dado preservado.
> Depends on: Phase 1 | Parallel with: —
> Order: test → migration → model → repository → service → mapper → seeder

> **Por que schema e serviço vêm juntos.** Trocar o model para `link` quebra na hora
> serviço, mapper, seeder e testes. Não dá para separar schema de consumidor dentro da
> mesma feature — o que dá para separar é uma feature da outra, e é isso que mantém cada
> PR dentro da regra 41 (D1).

- [x] 2.1 Criar branch `feat/driver-rating-link` **de dentro de** `feat/rating-soft-delete`
- [x] 2.2 Testes de serviço: avaliar **com** vínculo → 201; **sem** vínculo → **404** `driver_rating.link.not_found`; duas vezes o mesmo vínculo → 409; auto-avaliação → 400
- [x] 2.3 Teste nomeado provando que a avaliação grava **no vínculo**, não no par
- [x] 2.4 **VERIFICADO EM SQL, NÃO EM TESTE.** A unicidade por vínculo (D8) vive num índice único do banco, e o H2 com `ddl-auto` não o cria a partir do model. Conferido contra o PostgreSQL na 2.6: duas avaliações no mesmo vínculo não entram; depois de desvincular e revincular, uma nova entra
- [x] 2.5 Migration `V38__driver_rating_through_client_driver.sql`, nesta ordem: (a) `client_driver_id` **nullable**; (b) backfill de `client_driver` com os pares **distintos** de `driver_rating` que não têm vínculo ativo, status `ACTIVE` (D3); (c) preencher `client_driver_id`; (d) `set not null`; (e) FK; (f) trocar o índice único para `(client_driver_id)` **total**; (g) dropar `client_id` e `driver_id`
- [x] 2.6 **Aplicar a `V38` manualmente contra o PostgreSQL, com dado dentro.** Conferir que `count(*) where client_driver_id is null` é **zero** antes do `set not null` — é o passo que derruba a migration no meio se o backfill falhar (R2)
- [x] 2.7 **`DISTINCT` no backfill é defensivo** (D4): depois da `V37`, o índice total já garante um par por linha
- [x] 2.8 `DriverRatingModel`: trocar `ClientModel client` + `DriverModel driver` por `ClientDriverModel link`
- [x] 2.9 `DriverRatingRepository`: reescrever `findByDriverToken`, `calculateAverageRatingForDriver` e `findClientUserTokenByRatingToken` navegando por `link`; trocar `existsByDriverIdAndClientId` por `existsByLinkId`
- [x] 2.10 **Fetch join explícito** nas queries paginadas (R5, regra 17): `link` é `EAGER` e leva `client.user` e `driver.user`; sem o join, listar N avaliações dispara queries por linha
- [x] 2.11 `DriverRatingService.create`: resolver o vínculo por `findByPair` e **404 se não houver** (D6). **Apagar o comentário TODO** (regra 34)
- [x] 2.12 `DriverRatingMapper`: navegar `model.getLink()`. A resposta fica idêntica (D5)
- [x] 2.13 `DriverRatingSeeder`: derivar o par **do vínculo** criado pelo `ClientDriverSeeder`; sem vínculo, não semeia
- [x] 2.14 `clean.sql`: `client_driver` passa a ser apagado **depois** das ratings — a FK nasce aqui, e no H2 (`ddl-auto=create-drop`) a ordem antiga passa a violar a constraint
- [ ] 2.15 `make lint` + `./mvnw verify`; abrir PR apontando `--base feat/rating-soft-delete`

## 3. Phase 3 — `client_rating` pendura no vínculo (PR 6)

> Goal: a gêmea segue o mesmo caminho. Fecha a change.
> Depends on: Phase 2 | Parallel with: —
> Order: test → migration → model → repository → service → mapper → seeder

- [x] 3.1 Criar branch `feat/client-rating-link` **de dentro de** `feat/driver-rating-link`
- [x] 3.2 Testes de serviço: mesmos quatro casos da 2.2, com `client_rating.link.not_found`
- [x] 3.3 Teste nomeado provando que a resposta **não mudou de forma** (D5): `driverToken`, `driverName`, `clientToken`, `clientName` seguem presentes e corretos, agora vindos do vínculo. É a garantia de que o mobile não quebra
- [x] 3.4 Migration `V39__client_rating_through_client_driver.sql`, mesma ordem da `V38`
- [x] 3.5 **Conferir o reuso** (D3): os pares que a `V38` vinculou caem no `NOT EXISTS` da `V39` e **não** viram vínculo novo. Um par avaliado nas duas direções é um relacionamento só
- [x] 3.6 **Aplicar a `V39` manualmente contra o PostgreSQL**, logo depois da `V38`, e conferir órfãs igual a zero (R2)
- [x] 3.7 `ClientRatingModel`, `ClientRatingRepository`, `ClientRatingService`, `ClientRatingMapper` e `ClientRatingSeeder`: o mesmo da fase 2, do outro lado. **Apagar o comentário TODO**
- [x] 3.8 Chaves de MessageSource (EN + pt-BR): `client_rating.link.not_found`
- [ ] 3.9 **Abrir issue para o R6**: `client_rating` segue sem `update`, `driver_rating` tem. É a última assimetria entre as gêmeas
- [ ] 3.10 `make lint` + `./mvnw verify`; abrir PR apontando `--base feat/driver-rating-link`, com `Closes` na issue do R1

## 4. Encerramento

- [ ] 4.1 Mergear pelo **`merge stack`** na última PR, ou apagando a branch a cada merge
- [x] 4.2 Conferir que nenhum TODO sobre `client_driver` restou no código
- [ ] 4.3 Rodar `./mvnw verify` na `main` integrada e confirmar a cobertura mínima do JaCoCo (regra 24)
- [ ] 4.4 Atualizar `vanep-diagram.dbml`: `driver_rating` e `client_rating` passam a referenciar `client_driver`
- [ ] 4.5 Registrar na #42 as **Q1** e **Q2** — avaliar vínculo `INACTIVE`, e avaliação órfã de vínculo removido
- [ ] 4.6 Sincronizar o spec para `openspec/specs/` (`/opsx:sync`) e arquivar a change (`/opsx:archive`)
