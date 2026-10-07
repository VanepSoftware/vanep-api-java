> **Pilha de branches.** As fases são empilhadas: cada branch nasce de dentro da anterior e cada PR
> aponta a anterior como `--base`. A fase 1 (documentação) é a única que sai de `main`. Depois de
> todas subirem, o GitHub mostra "preview stack"; mergear pela **última** PR (`merge stack`) integra
> tudo de uma vez.
>
> ```
> main ─ absence-crud-docs ─ absence-schema ─ absence-policies ─ absence-service
>      PR 1 → main           PR 2 → PR 1      PR 3 → PR 2        PR 4 → PR 3
>            ─ absence-client-http ─ absence-no-show-http ─ absence-effects-notify
>              PR 5 → PR 4           PR 6 → PR 5            PR 7 → PR 6
> ```

## 1. Phase 1 — OpenSpec e visão de produto (PR 1)

> Goal: a change fica apply-ready, com ida / volta / ida e volta no overview, decisões D1–D12 e plano de PRs. Sem código Java.
> Depends on: — | Parallel with: —

- [x] 1.1 Confirmar a branch `feature/absence-crud-docs` a partir de `main` (já existia; o rascunho do Gemini foi substituído)
- [x] 1.2 Reescrever `proposal.md` com a UX **Avisar ausência** (`BOTH` / `OUTBOUND` / `RETURN`)
- [x] 1.3 Reescrever `design.md` (grafo, tabela de fases, D1–D12, unique por trecho, `trip_id` nullable, `client_driver` no lugar de `contract`)
- [x] 1.4 Specs `absence-client-report`, `absence-no-show`, `absence-day-effects`
- [x] 1.5 Documentar Q2 (desfazer) como **D6** e Q3 (e-mail até existir FCM) como **D10**
- [ ] 1.6 Abrir PR fase 1 contra `main`, em pt-BR, ligando `Closes` só no último PR da pilha — esta PR referencia `#160` sem fechar

## 2. Phase 2 — Enums, migration, model, repository (PR 2)

> Goal: a tabela e os tipos existem. Sem HTTP, sem serviço, sem policy.
> Depends on: Phase 1 | Parallel with: —
> Order: test → migration → model → repository

- [x] 2.1 Branch `feat/absence-schema` **de dentro de** `feature/absence-crud-docs`
- [x] 2.2 **Confirmar o próximo número Flyway** com `git ls-tree` em todas as remotes — **`V52` livre** (última em todas as remotes: `V51__client_rates_driver_and_rating_recount.sql`)
- [x] 2.3 Testes de `AbsenceRepository`: persiste token opaco; busca por `(dependent, date, leg)`; dois trechos no mesmo dia coexistem; soft delete libera o slot (H2 **não** prova o unique — não acrescentar unique só de teste; ver D7 / R4)
- [x] 2.4 Enums `AbsenceSource` (`CLIENT`, `DRIVER`, `ASSISTANT`) e `AbsenceLeg` (`OUTBOUND`, `RETURN`) em `br.com.vanep.absence.enums` (regra 14). **Não** criar enum persistido `BOTH`
- [x] 2.5 Migration `V52__create_absence_table.sql` conforme o esboço do `design.md` (`client_driver_id`, `trip_id` nullable, unique parcial por trecho, `deleted_at`)
- [x] 2.6 Aplicar a migration **manualmente** no PostgreSQL local e verificar: (a) segundo `(dependent, date, leg)` ativo viola o unique; (b) ida e volta no mesmo dia passam; (c) soft-deletado + reinsert passa. ✅ Flyway até V52 no Postgres 17; duplicata OUTBOUND viola o unique; RETURN no mesmo dia ok; soft-delete + reinsert ok.
- [x] 2.7 `AbsenceModel` com `@SoftDelete(columnName = "deleted_at", strategy = TIMESTAMP)` (regra 19); token no `@PrePersist` no padrão das outras features
- [x] 2.8 `AbsenceRepository` com fetch join do `client_driver` / `dependent` / `trip` que a leitura precisar (regra 17)
- [x] 2.9 Acrescentar `absence` em `src/test/resources/db/clean.sql` na ordem de FK
- [x] 2.10 `./mvnw verify`: **1341 testes, JaCoCo ok, BUILD SUCCESS** (Spotless check no Docker Windows falha por CRLF do working copy; CI Linux é a prova). PR apontando `--base feature/absence-crud-docs`

## 3. Phase 3 — Policies puras (PR 3)

> Goal: mapa trecho↔turno, escopo vs `dependent.shift`, e janela de undo decidem sem Spring/JPA/servlet.
> Depends on: Phase 2 | Parallel with: —
> Order: test → policy

- [x] 3.1 Branch `feat/absence-policies` de dentro de `feat/absence-schema`
- [x] 3.2 Testes de `AbsenceLegShiftPolicy`: `MORNING`/`NIGHT` → `OUTBOUND`; `AFTERNOON` → `RETURN`; `FULLTIME` na trip aceita os dois trechos apontando para a mesma trip
- [x] 3.3 Testes de `AbsenceScopePolicy`: `BOTH` só com `dependent.shift = FULLTIME`; `RETURN` recusado em `MORNING`; `OUTBOUND` recusado em `AFTERNOON`
- [x] 3.4 Testes de `AbsenceUndoPolicy`: sem trip ou `SCHEDULED` → permite; `IN_PROGRESS` / `COMPLETED` / `CANCELLED` → recusa; matriz tudo-ou-nada para `BOTH`
- [x] 3.5 Implementar as três policies no pacote `service`, sem `@Service` de repositório e sem tipo de framework na assinatura (regra 9)
- [x] 3.6 `./mvnw verify`: **1353 testes, JaCoCo ok, BUILD SUCCESS**. PR `--base feat/absence-schema`

## 4. Phase 4 — Serviço, permissões, messages (PR 4)

> Goal: report / list / undo / no-show funcionam pela camada de serviço. Sem controller.
> Depends on: Phase 3 | Parallel with: —
> Order: test → security/authorization → messages → service

- [x] 4.1 Branch `feat/absence-service` de dentro de `feat/absence-policies`
- [x] 4.2 Testes Mockito de `AbsenceService`: `BOTH` cria duas linhas; segundo `OUTBOUND` relê; `BOTH` com ida existente só insere volta; dono errado; vínculo não `ACTIVE`; RN-19 não toca `dependent`; no-show preenche `reason` e `trip_id`; undo soft-delete; undo com trip `IN_PROGRESS` não apaga nada
- [x] 4.3 Teste de corrida com repositório mockado lançando `DataIntegrityViolationException` — o serviço relê, não propaga 500
- [x] 4.4 `REPORT_ABSENCE` no bundle `CLIENT`; `REPORT_NO_SHOW` nos bundles `DRIVER` e `ASSISTANT` (`PermissionEnum` + seeder)
- [x] 4.5 **Não** acrescentar `isTripOperator` nesta fase — nasce na fase 6 com a rota que o usa. Permissão sem rota da fase 6 fica no seeder já, porque o serviço já ramifica origem; se isso vazar string morta no JWT, aceitável: o endpoint ainda não existe
- [x] 4.6 Keys MessageSource EN + `messages_pt_BR.properties`: `absence.scope.not_in_shift`, `absence.link.not_active`, `absence.undo.trip_started`, `absence.trip.not_today`, `absence.trip.not_open` (regra 46)
- [x] 4.7 Implementar `AbsenceService` com insert-then-catch (D7), data do servidor (D8), `trip_id` opcional no fluxo cliente (D3)
- [x] 4.8 Porta de efeito e mail: o serviço recebe a porta e um notifier por construtor; nos testes unitários, fakes. Implementações reais na fase 7
- [x] 4.9 `./mvnw verify -Dspotless.check.skip=true`: **1369 testes, JaCoCo ok, BUILD SUCCESS**. PR `--base feat/absence-policies`

## 5. Phase 5 — HTTP do cliente (PR 5)

> Goal: os três endpoints do vínculo respondem. Sem no-show.
> Depends on: Phase 4 | Parallel with: —
> Order: test → request DTO → controller → response DTO → mapper

- [x] 5.1 Branch `feat/absence-client-http` de dentro de `feat/absence-service`
- [x] 5.2 MockMvc: 401; JWT driver → 403; cliente no próprio vínculo `ACTIVE` + próprio dependente `OUTBOUND` → 201; repetir → 200 mesmo token; `BOTH` em `FULLTIME` → 2 itens; `BOTH` em `MORNING` → 400; vínculo `INACTIVE` → 409; dependente de outro cliente → 403/404; GET today; DELETE 204; DELETE com trip `IN_PROGRESS` → 409
- [x] 5.3 Teste nomeado: a resposta **não contém** `id` numérico (regra 13)
- [x] 5.4 `AbsenceClientRequestDTO` com `dependentToken` e `scope` (`AbsenceScope` **só no DTO**: `OUTBOUND`, `RETURN`, `BOTH`), Bean Validation, `@Valid` no controller (regras 10 e 11)
- [x] 5.5 `AbsenceController` fino em `/api/client-drivers/{linkToken}/absences` (regra 7)
- [x] 5.6 `AbsenceResponseDTO` + `AbsenceMapper`
- [x] 5.7 `SecurityConfig` não deixa a rota pública (regras 20–21) — `/api/**` autenticado + `@PreAuthorize("hasAuthority('report_absence')")`
- [x] 5.8 `./mvnw verify -Dspotless.check.skip=true`: **1381 testes, JaCoCo ok, BUILD SUCCESS**. PR `--base feat/absence-service`

## 6. Phase 6 — HTTP de não comparecimento (PR 6)

> Goal: `POST /api/trips/{tripToken}/no-shows` para motorista e assistente.
> Depends on: Phase 5 | Parallel with: —
> Order: test → security → request DTO → controller

- [x] 6.1 Branch `feat/absence-no-show-http` de dentro de `feat/absence-client-http`
- [x] 6.2 MockMvc: motorista na própria trip `IN_PROGRESS` + reason → 201 `source=DRIVER`; assistente `ACTIVE` → 201 `source=ASSISTANT`; outro motorista → 403; reason em branco → 400; trip `COMPLETED` → 409; trip de ontem → 409; cliente → 403
- [x] 6.3 `isTripOperator(String tripToken, Authentication)` em `SecurityEvaluator` (regra 22) — dono da trip **ou** assistente `ACTIVE` cujo `driver_id` é o da trip. Sem `AbsenceSecurityService`
- [x] 6.4 `AbsenceNoShowRequestDTO` (`dependentToken`, `reason` `@NotBlank`)
- [x] 6.5 Endpoint no controller de trip ou no de absence, prefixo `/api`, `@PreAuthorize("hasAuthority('report_no_show') and @sec.isTripOperator(#tripToken, authentication)")`
- [x] 6.6 `./mvnw verify -Dspotless.check.skip=true`: **1388 testes, JaCoCo ok, BUILD SUCCESS**. PR `--base feat/absence-client-http`

## 7. Phase 7 — Efeitos do dia (porta no-op) e notificação (PR 7)

> Goal: o serviço real chama porta + mail; `notified_at` só após envio. Fecha a change.
> Depends on: Phase 6 | Parallel with: —

- [x] 7.1 Branch `feat/absence-effects-notify` de dentro de `feat/absence-no-show-http`
- [x] 7.2 Testes: após persist, a porta é chamada; mail ok → `notified_at` preenchido; mail falha → ausência permanece, `notified_at` nulo; MailService mockado (regra 50)
- [x] 7.3 `AbsenceDayEffectPort` + `NoOpAbsenceDayEffectPort` (substitui o fake da fase 4). A implementação real de checklist/rota fica nas issues #151 e #45
- [x] 7.4 Notifier com `MailService` + templates Thymeleaf `absence-client-report` e `absence-no-show`; keys `absence.notify.client_report` e `absence.notify.no_show`
- [x] 7.5 Não fatiou: ficou abaixo de ~10 arquivos
- [x] 7.6 `./mvnw verify -Dspotless.check.skip=true`: **1392 testes, JaCoCo ok, BUILD SUCCESS**. PR `--base feat/absence-no-show-http` com **`Closes #160`**

## 8. Encerramento

- [ ] 8.1 Criar a GitHub stack e mergear pela última PR
- [ ] 8.2 Registrar na #160 as decisões D1 (unique por trecho), D6 (desfazer) e D10 (e-mail)
- [ ] 8.3 Atualizar `vanep-diagram.dbml` (`leg`, unique, `trip_id` opcional, `client_driver_id`, `deleted_at`)
- [ ] 8.4 Sincronizar specs (`/opsx:sync`) e arquivar a change
