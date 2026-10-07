## Context

A `rating-through-link` deixou o `driver_rating` pendurado no `client_driver`, com uma avaliação por vínculo e remoção física. Ela resolveu **onde** a avaliação mora. A #158 trata de **quem** avalia, **o que** fica imutável e **o que** cada lado enxerga.

Quem toca o `driver_rating` hoje:

| Rota | Autorização | Quem alcança de fato |
|---|---|---|
| `POST /api/driver-ratings` | `create_driver_rating` | só o admin: nenhum outro bundle tem a permissão |
| `GET /api/driver-ratings` | `list_driver_ratings` | só o admin |
| `GET /api/driver-ratings/{token}` | `show_driver_rating` ou autor | admin e o cliente que avaliou |
| `PUT /api/driver-ratings/{token}` | `update_driver_rating` ou autor | admin e o cliente que avaliou |
| `DELETE /api/driver-ratings/{token}` | `delete_driver_rating` ou autor | admin e o cliente que avaliou |

A nota agregada (`driver.rating`) sai em `DriverSearchResponseDTO` (busca e recomendados), `DriverProfileResponseDTO` (perfil público, #239), `DriverResponseDTO` (admin), `DriverMeSummaryResponseDTO` e `AssistantDriverSummaryDTO`. Todas repassam `driver.getRating()` sem tratamento, e o Jackson serializa `null` como `"rating": null`.

## Goals / Non-Goals

**Goals**

- O cliente com vínculo `ACTIVE` consegue avaliar, uma vez, e não desfaz
- O motorista nunca alcança uma avaliação individual
- `driver.rating` é sempre a média do que existe, ou `null`
- O app sabe quando esconder o botão de avaliar

**Non-Goals**

- Mudar o `client_rating` (Q2)
- Trigger de banco para a média (D4)
- Mitigar a dedução do autor pela variação da média (R2)

## Decisions

### D1 — Só vínculo `ACTIVE` avalia; outro status responde 422

A `rating-through-link` deixou em aberto se `INACTIVE` avalia (Q1 dela). A #158 fecha: **vínculo ativo**. `PENDING` ainda é proposta, `BLOCKED` é relação cortada, `INACTIVE` é relação encerrada.

- **Sem vínculo** segue **404** `driver_rating.link.not_found`. É o comportamento que a `rating-through-link` fixou, e o cliente não tem o que corrigir.
- **Vínculo em outro status** responde **422** `driver_rating.link.not_active`. O pedido é bem formado e o recurso existe; o estado dele é que não permite. É o mesmo status que a #241 usa para "motorista não aprovado" no `client_driver`.

A checagem é um `if` sobre o vínculo que o `findByPair` já carregou, e não uma query nova. Uma `findActiveByPair` devolveria vazio nos dois casos, e o 404 e o 422 deixariam de ser distinguíveis sem uma segunda consulta.

O 422 usa `HttpStatus.UNPROCESSABLE_CONTENT`, e não `UNPROCESSABLE_ENTITY`: são o mesmo 422, mas o segundo está deprecated no Spring 7.

### D2 — Irreversível para o cliente; o admin modera

O cliente não edita nem apaga. `PUT` sai por inteiro. O `DELETE` fica, mas só com `delete_driver_rating`, que só o bundle ADMIN tem.

A #158 pede "sem endpoint de editar ou apagar". O `DELETE` só do admin é um desvio deliberado: sem ele, uma avaliação com conteúdo ofensivo no `comment` só sai do banco à mão. É o mesmo precedente da change `client-rating`: imutável para as partes, apagável pelo admin.

Apagar pelo admin continua físico e recalcula a média (regra 19).

`update_driver_rating` sai do `PermissionEnum` e, pela `V50`, de todo bundle em banco. O `DataSeeder` ressincroniza o ADMIN com o `PermissionRegistry`, mas só roda com `vanep.seed.enabled`, desligado por padrão fora do ambiente local.

### D3 — "Sem avaliações" é `rating: null`, sem contagem

`null` é o estado. Não há coluna nova nem campo `ratingsCount`, e o contrato das rotas que expõem a nota não muda de forma.

O que muda é o **valor**: o fallback `5.00` do `recalculateDriverAverage` vira `null`. Nota cinco para quem não tem nota é tão injusto quanto zero, só que para o outro lado.

### D4 — A média continua em Java, não em trigger

A #158 cita `vanep-trigger-rating.dbml`. A média já é recalculada em `DriverRatingService.recalculateDriverAverage` a cada escrita, e a suíte roda em H2 com `spring.flyway.enabled=false`. Uma trigger **nunca seria exercitada pelo CI**. O recálculo em Java é testado por unidade.

A consistência que a trigger daria vem de duas coisas:

- todo caminho de escrita (criar, apagar pelo admin, seeder) passa pelo recálculo
- a `V49` recalcula tudo uma vez, para corrigir o que ficou para trás

### D5 — Anonimato: o motorista não tem rota, e isso vira teste

Hoje o anonimato vale por omissão: nenhum bundle além do ADMIN tem `list_driver_ratings` ou `show_driver_rating`. A change transforma a omissão em garantia:

- testes nomeados: o motorista avaliado recebe **403** em `GET /api/driver-ratings`, `GET /api/driver-ratings?driverToken=` e `GET /api/driver-ratings/{token}`
- o bundle `CLIENT` recebe só `create_driver_rating`, **não** `list_driver_ratings`. Um cliente listando avaliações de um motorista veria nomes de outros clientes
- **o "já avaliou?" não vai no `ClientDriverResponseDTO`.** Essa resposta também é lida pelo motorista (`GET /api/client-drivers/me`, `@sec.isClientDriverLinkParty`). Um `rated: true` nela diria ao motorista exatamente qual cliente avaliou

### D6 — "Já avaliou?" é uma rota do cliente, por motorista

`GET /api/driver-ratings/status?driverToken=…` responde `DriverRatingStatusResponseDTO`:

```json
{ "rated": true, "canRate": false }
```

- `rated`: existe avaliação no vínculo do cliente com esse motorista
- `canRate`: o vínculo é `ACTIVE`, tem pelo menos 5 minutos (D8) e ainda não foi avaliado. É o que o app usa para mostrar ou esconder o botão, sem replicar as regras do D1 e do D8

Autorização por `create_driver_rating`: quem pode perguntar é quem pode avaliar. O cliente vem do `jwt.getSubject()`, como no `POST`. Por construção, a resposta só fala do vínculo do próprio chamador.

É por `driverToken`, e não por token de vínculo, porque o botão mora no perfil do motorista, que o app abre com o `driverToken`.

### D7 — A `V49` recalcula todos os motoristas

```sql
update driver motorista
set rating = (select round(avg(avaliacao.rating), 2) … where vinculo.deleted_at is null)
```

- `round(…, 2)` casa com o `setScale(2, HALF_UP)` do serviço
- `avg` de zero linhas é `null`, então o motorista sem avaliação cai no D3 sem `coalesce`
- sem `where` no `update`: reescrever todos é idempotente, e mais simples de verificar do que achar os divergentes

### D8 — Cinco minutos de vínculo antes de avaliar

A #158 pede para decidir se a avaliação exige um tempo mínimo de vínculo, para reduzir avaliação impulsiva. A decisão é **5 minutos, contados do `client_driver.created_at`**. Antes disso, o `POST` responde **422** `driver_rating.link.too_recent` e o status do D6 devolve `canRate: false`.

- **Por que `created_at` e não o momento em que o vínculo virou `ACTIVE`.** O `client_driver` não guarda quando mudou de status. Contar da ativação exigiria uma coluna nova (`activated_at`) preenchida no `ClientDriverService`, o arquivo que a #241 está mexendo. Com `created_at`, o tempo em que o vínculo ficou `PENDING` também conta. Como o vínculo ainda precisa estar `ACTIVE` (D1), a regra nunca libera avaliação antes da ativação; só pode liberar logo depois dela.
- **Onde a regra mora.** `DriverRatingEligibilityPolicy`, um `@Component` puro no molde do `WorkWindowPolicy` da trip: recebe status, `created_at` e o "agora" como parâmetros e decide sem banco (regra 8). O `create` e o `findRatingStatus` consultam a mesma policy, então o botão do app e o `POST` nunca discordam.
- **Exatamente 5 minutos já vale.** A fronteira é inclusiva.
- **É constante, não configuração.** `MINIMUM_LINK_AGE` é regra de negócio, não varia por ambiente (regra 3).

## Risks / Trade-offs

- **R1 — A `V49` e a `V50` não rodam na suíte.** `spring.flyway.enabled=false`. Aplicar à mão num PostgreSQL com dado dentro: um motorista sem avaliação que estava com `5.00` → `null`; um com duas avaliações → média delas; o bundle CLIENT com `create_driver_rating` uma vez só, mesmo rodando o `update` duas vezes; nenhum bundle com `update_driver_rating` depois da `V50`.
- **R2 — Dedução pela variação da média.** Um motorista com um único cliente novo vê a nota mudar e sabe quem avaliou. A #158 cita esse caso. Mitigar exige atrasar ou agrupar a publicação da média, o que muda a regra RN-17 ("média pública do total acumulado"). **Aceito e registrado**; vira issue se o PO quiser.
- **R3 — Avaliação de vínculo removido na média.** O AVG do serviço navega por `ClientDriverModel`, que tem `@SoftDelete`; a `V49` filtra `deleted_at is null` para casar com isso. A fase 3 prova com teste de repositório que a avaliação de um vínculo soft-deletado não entra no AVG. Se não for assim, a `V49` e o serviço divergem, e é a `V49` que se ajusta.
- **R4 — Tokens antigos.** Quem já está logado como CLIENT só ganha `create_driver_rating` num novo login.
- **R5 — A #241 mexe no que é `ACTIVE`.** Ela impede que motorista não aprovado tenha vínculo `ACTIVE`. As duas se somam: a #158 lê o status e a #241 controla quem chega nele. Não há conflito de arquivo.
- **R6 — `DELETE` fica, contra a letra da #158.** Ver D2. Se o time preferir a letra, a fase 2 remove também o `DELETE` e o `delete_driver_rating`.

## Open Questions

- ~~**Q1 — Tempo mínimo de vínculo para avaliar?**~~ **Decidido: 5 minutos desde a criação do vínculo** (D8).
- **Q2 — As mesmas regras valem para o `client_rating`?** A #158 pergunta. Hoje o `client_rating` já não tem `update`, mas o autor ainda apaga. E o bundle DRIVER tem `list_client_ratings`, cuja resposta traz `driverName`: um motorista vê quais outros motoristas avaliaram um cliente, e com que nota. Se o anonimato valer no sentido inverso, vira issue própria.
- **Q3 — Detalhe da van.** O cliente não alcança `GET /api/vehicles/{token}`. A van que ele vê vem em `DriverProfileResponseDTO.vehicles`, e a nota já está na raiz do perfil. Confirmar com o mobile se a tela de detalhe da van lê `rating` do perfil. Se sim, a RN-17 já está coberta nesse ponto de contato.
