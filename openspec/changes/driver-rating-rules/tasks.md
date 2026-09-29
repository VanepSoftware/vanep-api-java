> **Pilha de branches.** A fase 1 sai de `main`. As fases 2 e 3 saem da fase 1 e não
> dependem uma da outra. A fase 4 precisa das duas; ela sai da fase 3 e a fase 2 entra
> por rebase quando for mergeada.
>
> ```
> main ─ feat/158-driver-rating-schema ─┬─ feat/158-driver-rating-immutable ──┐
>        PR 1 → main                    │  PR 2 → PR 1                        │
>                                       └─ feat/158-driver-rating-rules ──────┴─ feat/158-driver-rating-http
>                                          PR 3 → PR 1                           PR 4 → PR 3 (+ PR 2)
> ```
>
> **Mergear pelo `merge stack`**, ou apagando a branch a cada merge: o repo tem
> `delete_branch_on_merge: false`, e sem apagar a branch o GitHub não reaponta a base das filhas.

## Plano

| Fase | Conteúdo | Depende de | Paralela com |
|---|---|---|---|
| 1 | change OpenSpec + `V49` (permissão do CLIENT e recálculo) + seeder | — | — |
| 2 | irreversível: sai `PUT`, `DELETE` só do admin, regra 19 | 1 | 3 |
| 3 | regras no serviço: vínculo `ACTIVE`, média `null`, status de avaliação | 1 | 2 |
| 4 | HTTP: rota de status, testes de anonimato, `Closes #158` | 2, 3 | — |

## 0. Preparation

- [x] 0.1 Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] 0.2 Revisar `proposal.md`, `design.md` e o spec `driver-rating-rules`
- [x] 0.3 **Reconfirmar o número de Flyway.** A `main` termina em `V48`; o plano assume `V49`. A `main` tem **duas `V46`** (`client_reads_drivers_through_profile` e `ibge_city_and_postal_neighborhood`), que o Flyway recusa no boot. Não é desta change, mas precisa ser resolvido antes de a `V49` ir para produção
- [ ] 0.4 Levar Q1 (tempo mínimo) e Q3 (detalhe da van) ao PO e ao mobile

## 1. Phase 1 — permissão do cliente e recálculo da média (PR 1)

> Goal: o cliente passa a conseguir avaliar, e `driver.rating` volta a bater com o que existe.
> Depends on: — | Parallel with: —
> Order: test → migration → seeder

- [x] 1.1 Criar branch `feat/158-driver-rating-schema` a partir de `main`
- [x] 1.2 Teste nomeado: o bundle CLIENT tem `create_driver_rating` e **não** tem `list_driver_ratings`, `update_driver_rating` nem `delete_driver_rating` (D5)
- [x] 1.3 Migration `V49__client_rates_driver_and_rating_recount.sql`: (a) `create_driver_rating` no bundle CLIENT, idempotente; (b) recalcular `driver.rating` de todos os motoristas, `null` sem avaliação (D7)
- [x] 1.4 `DataSeeder.clientPermissions()`: incluir `create_driver_rating`
- [ ] 1.5 **Aplicar a `V49` manualmente contra o PostgreSQL, com dado dentro** (R1): motorista com `5.00` de fallback → `null`; motorista com duas avaliações → média; rodar o `update` do bundle duas vezes e conferir a permissão uma vez só
- [ ] 1.6 `make lint` + `./mvnw verify`; abrir PR em pt-BR, `--base main`, `Refs #158`

## 2. Phase 2 — avaliação irreversível (PR 2)

> Goal: o autor não edita nem apaga. O admin segue podendo remover (D2).
> Depends on: Phase 1 | Parallel with: Phase 3
> Order: test → permissions → service → controller

> **Por que serviço e controller vêm juntos.** Remover `update` do serviço quebra o controller
> na hora; não dá para entregar uma camada sem a outra. A remoção é pequena e cabe na regra 41.

- [ ] 2.1 Criar branch `feat/158-driver-rating-immutable` **de dentro de** `feat/158-driver-rating-schema`
- [ ] 2.2 Testes nomeados: `PUT /api/driver-ratings/{token}` não existe mais; o autor recebe **403** no `DELETE` e a avaliação continua lá; o admin apaga e a média é recalculada
- [ ] 2.3 Remover os testes que exercitam o `update` e o `DELETE` do autor (`updateReturns200ForOwner`, `updateForbidsOtherClient`, `deleteReturns204ForOwner`, `aPairCanBeRatedAgainAfterItsRatingIsDeleted`, `updatePersistsChangesAndRecalculatesAverage`)
- [ ] 2.4 Remover `UPDATE_DRIVER_RATING` do `PermissionEnum`. O seeder reescreve o bundle ADMIN no próximo start
- [ ] 2.5 `DriverRatingService`: remover `update`. `DriverRatingController`: remover `PUT /{token}`; o `DELETE` fica só com `hasAuthority('delete_driver_rating')`
- [ ] 2.6 Remover `DriverRatingUpdateRequestDTO` (regra 34)
- [ ] 2.7 **Reescrever a exceção da regra 19 da constitution**: `driver_rating` é imutável para o autor e só o admin remove. Sem isso o texto diz "rating again creates a new one", que deixa de ser verdade
- [ ] 2.8 `make lint` + `./mvnw verify`; abrir PR `--base feat/158-driver-rating-schema`, `Refs #158`

## 3. Phase 3 — regras no serviço (PR 3)

> Goal: só vínculo `ACTIVE` avalia, a média nunca inventa nota, e o serviço sabe responder "já avaliou?".
> Depends on: Phase 1 | Parallel with: Phase 2
> Order: test → repository → messages → service → seeder

- [ ] 3.1 Criar branch `feat/158-driver-rating-rules` **de dentro de** `feat/158-driver-rating-schema`
- [ ] 3.2 Testes de serviço: vínculo `ACTIVE` → salva; `PENDING`, `INACTIVE` e `BLOCKED` → **422** `driver_rating.link.not_active` e nada é salvo; sem vínculo → 404 (inalterado)
- [ ] 3.3 Teste nomeado: sem avaliação restante, `recalculateDriverAverage` grava **`null`**, não `5.00` (D3)
- [ ] 3.4 Testes do status (D6): `rated`/`canRate` para vínculo ativo sem avaliação, ativo com avaliação, não ativo e sem vínculo
- [ ] 3.5 Teste de repositório (R3): a avaliação de um vínculo soft-deletado não entra no AVG
- [ ] 3.6 `ClientDriverRepository.findActiveByPair` (D1)
- [ ] 3.7 Chave de MessageSource (EN + pt-BR): `driver_rating.link.not_active`
- [ ] 3.8 `DriverRatingService.create`: `findByPair` para o 404, `findActiveByPair` para o 422; `recalculateDriverAverage` sem o fallback `5.00`; novo `findRatingStatus`
- [ ] 3.9 `DriverRatingSeeder`: recalcular a média do motorista depois de semear
- [ ] 3.10 `make lint` + `./mvnw verify`; abrir PR `--base feat/158-driver-rating-schema`, `Refs #158`

## 4. Phase 4 — HTTP e anonimato (PR 4)

> Goal: o app pergunta se mostra o botão, e o anonimato vira teste.
> Depends on: Phase 2, Phase 3 | Parallel with: —
> Order: test → response DTO → controller

- [ ] 4.1 Criar branch `feat/158-driver-rating-http` **de dentro de** `feat/158-driver-rating-rules`; rebase sobre a `main` depois que a fase 2 entrar
- [ ] 4.2 Testes de anonimato (D5): o motorista avaliado recebe **403** em `GET /api/driver-ratings`, `GET /api/driver-ratings?driverToken=` e `GET /api/driver-ratings/{token}`
- [ ] 4.3 Testes da rota de status: cliente com `create_driver_rating` → 200 com `rated`/`canRate`; motorista → 403; sem autenticação → 401
- [ ] 4.4 `DriverRatingStatusResponseDTO(boolean rated, boolean canRate)`
- [ ] 4.5 `GET /api/driver-ratings/status?driverToken=` com `hasAuthority('create_driver_rating')`
- [ ] 4.6 Teste nomeado: `GET /api/drivers/{token}/profile` de motorista sem avaliação traz `"rating": null`
- [ ] 4.7 `make lint` + `./mvnw verify`; abrir PR `--base feat/158-driver-rating-rules`, **`Closes #158`**

## 5. Encerramento

- [ ] 5.1 Mergear pelo **`merge stack`** na última PR, ou apagando a branch a cada merge
- [ ] 5.2 Rodar `./mvnw verify` na `main` integrada e confirmar a cobertura mínima do JaCoCo (regra 24)
- [ ] 5.3 Abrir issue para a Q2 (regras no `client_rating`) e, se o PO quiser, para o R2 (dedução pela variação da média)
- [ ] 5.4 Sincronizar o spec para `openspec/specs/` (`/opsx:sync`) e arquivar a change (`/opsx:archive`)
