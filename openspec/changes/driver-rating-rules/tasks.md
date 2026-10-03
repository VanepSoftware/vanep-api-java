> **Pilha de branches.** Cada branch nasce de dentro da anterior e cada PR aponta a anterior
> como `--base`.
>
> ```
> main ─ feat/158-driver-rating-schema ─ feat/158-driver-rating-immutable ─ feat/158-driver-rating-rules ─ feat/158-driver-rating-http
>        PR 1 → main                     PR 2 → PR 1                        PR 3 → PR 2                    PR 4 → PR 3
> ```
>
> As fases 2 e 3 só dependem da 1, mas as duas mexem no `DriverRatingService` e no
> `DriverRatingServiceTest`. Em paralelo, a segunda a entrar teria conflito; empilhadas, não.
>
> **Mergear pelo `merge stack`**, ou apagando a branch a cada merge: o repo tem
> `delete_branch_on_merge: false`, e sem apagar a branch o GitHub não reaponta a base das filhas.

## Plano

| Fase | Conteúdo | Depende de | Paralela com |
|---|---|---|---|
| 1 | change OpenSpec + `V49` (permissão do CLIENT e recálculo) + seeder | — | — |
| 2 | irreversível: sai `PUT`, `DELETE` só do admin, `V50`, regra 19 | 1 | — |
| 3 | regras no serviço: vínculo `ACTIVE`, média `null`, status de avaliação | 2 | — |
| 4 | HTTP: rota de status, testes de anonimato, `Closes #158` | 3 | — |

## 0. Preparation

- [x] 0.1 Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] 0.2 Revisar `proposal.md`, `design.md` e o spec `driver-rating-rules`
- [x] 0.3 **Reconfirmar o número de Flyway.** A `main` termina em `V48`; o plano assume `V49` e `V50`. A `main` tem **duas `V46`** (`client_reads_drivers_through_profile` e `ibge_city_and_postal_neighborhood`), que o Flyway recusa no boot. Não é desta change, mas precisa ser resolvido antes de a `V49` ir para produção
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
> Depends on: Phase 1 | Parallel with: —
> Order: test → migration → permissions → service → controller

> **Por que serviço e controller vêm juntos.** Remover `update` do serviço quebra o controller
> na hora; não dá para entregar uma camada sem a outra. A remoção é pequena e cabe na regra 41.

- [x] 2.1 Criar branch `feat/158-driver-rating-immutable` **de dentro de** `feat/158-driver-rating-schema`
- [x] 2.2 Testes nomeados: `aRatingCannotBeEdited` (`PUT` → 405 e nota e comentário intactos), `theAuthorCannotDeleteTheirRating` e `anotherClientCannotDeleteTheRating` (403 e a linha continua), `theAdminRemovesTheRowPhysically`
- [x] 2.3 Remover os testes que exercitam o `update` e o `DELETE` do autor (`updateReturns200ForOwner`, `updateForbidsOtherClient`, `deleteReturns204ForOwner`, `deletingRemovesTheRowPhysically`, `aPairCanBeRatedAgainAfterItsRatingIsDeleted`, `updatePersistsChangesAndRecalculatesAverage`)
- [x] 2.4 Migration `V50__driver_rating_is_immutable.sql`: `update_driver_rating` sai de todo bundle. **O seeder não resolve**: ele só roda com `vanep.seed.enabled`, desligado por padrão fora do local
- [x] 2.5 Remover `UPDATE_DRIVER_RATING` do `PermissionEnum`
- [x] 2.6 `DriverRatingService`: remover `update`. `DriverRatingController`: remover `PUT /{token}`; o `DELETE` fica só com `hasAuthority('delete_driver_rating')`
- [x] 2.7 Remover `DriverRatingUpdateRequestDTO` (regra 34)
- [x] 2.8 **Reescrever a exceção da regra 19 da constitution**: `driver_rating` é imutável para o autor e só o admin remove. O texto antigo dizia "rating again creates a new one", que deixa de ser verdade
- [ ] 2.9 **Aplicar a `V50` manualmente contra o PostgreSQL** (R1): nenhum bundle com `update_driver_rating`
- [ ] 2.10 `make lint` + `./mvnw verify`; abrir PR `--base feat/158-driver-rating-schema`, `Refs #158`

## 3. Phase 3 — regras no serviço (PR 3)

> Goal: só vínculo `ACTIVE` avalia, a média nunca inventa nota, e o serviço sabe responder "já avaliou?".
> Depends on: Phase 2 | Parallel with: —
> Order: test → repository → messages → service → seeder

- [ ] 3.1 Criar branch `feat/158-driver-rating-rules` **de dentro de** `feat/158-driver-rating-immutable`
- [ ] 3.2 Testes de serviço: vínculo `ACTIVE` → salva; `PENDING`, `INACTIVE` e `BLOCKED` → **422** `driver_rating.link.not_active` e nada é salvo; sem vínculo → 404 (inalterado)
- [ ] 3.3 Teste nomeado: sem avaliação restante, `recalculateDriverAverage` grava **`null`**, não `5.00` (D3)
- [ ] 3.4 Testes do status (D6): `rated`/`canRate` para vínculo ativo sem avaliação, ativo com avaliação, não ativo e sem vínculo
- [ ] 3.5 Teste de repositório (R3): a avaliação de um vínculo soft-deletado não entra no AVG
- [ ] 3.6 `ClientDriverRepository.findActiveByPair` (D1)
- [ ] 3.7 Chave de MessageSource (EN + pt-BR): `driver_rating.link.not_active`
- [ ] 3.8 `DriverRatingService.create`: `findByPair` para o 404, `findActiveByPair` para o 422; `recalculateDriverAverage` sem o fallback `5.00`; novo `findRatingStatus`
- [ ] 3.9 `DriverRatingSeeder`: recalcular a média do motorista depois de semear
- [ ] 3.10 `make lint` + `./mvnw verify`; abrir PR `--base feat/158-driver-rating-immutable`, `Refs #158`

## 4. Phase 4 — HTTP e anonimato (PR 4)

> Goal: o app pergunta se mostra o botão, e o anonimato vira teste.
> Depends on: Phase 3 | Parallel with: —
> Order: test → response DTO → controller

- [ ] 4.1 Criar branch `feat/158-driver-rating-http` **de dentro de** `feat/158-driver-rating-rules`
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
