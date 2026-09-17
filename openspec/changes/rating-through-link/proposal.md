## Why

O `client_driver` nasceu na #30 para ser a linha canônica que diz "a Maria e o Carlos têm uma relação". **As duas tabelas de avaliação não usam essa linha.** Cada uma reescreve o par por conta própria:

```
client_driver   Maria + Carlos → ACTIVE      a ficha do relacionamento
driver_rating   Maria + Carlos → 5.00        o par, escrito de novo
client_rating   Maria + Carlos → 4.00        o par, escrito de novo
```

O mesmo par em três lugares, livre para divergir. E as consequências já estão no código:

**1. Dá para avaliar quem nunca se conheceu.** Nada exige vínculo. Os dois serviços carregam o mesmo TODO, esperando exatamente a tabela que a #30 entregou:

- `DriverRatingService:55` — *"exigir que o cliente já tenha tido algum vínculo com o motorista"*
- `ClientRatingService:54` — *"exigir que o motorista já tenha tido algum vínculo com o cliente"*

**2. `client_rating` apaga de verdade.** `ClientRatingModel` não tem `@SoftDelete` e a V33 não criou `deleted_at`. `clientRatingRepository.delete()` emite um `DELETE` físico: a avaliação some, sem histórico e sem `restore`. A irmã `driver_rating` (V16) faz soft delete e tem `restore`. **Duas features gêmeas, duas regras.** A regra 19 manda soft delete.

**3. As gêmeas divergem em mais coisas.** `client_rating` não tem `update` nem `restore`; `driver_rating` tem os dois. Faltam as permissões `update_client_rating` e `restore_client_rating`.

Adiar custa caro por aritmética: hoje são **2** tabelas para migrar. `proposal` (#42), `contract` (#41) e `conversation` (#161) também penduram no par e são as próximas da fila. Depois delas são **5**, com dado real dentro.

## What Changes

- Nova migration **V37**: `client_rating` ganha `deleted_at`, e o índice único `(driver_id, client_id)` vira **parcial**
- `ClientRatingModel` ganha `@SoftDelete` — o `delete()` deixa de destruir a linha
- `client_rating` ganha `restore`, com `restore_client_rating` no `PermissionEnum` e no bundle `ADMIN`
- Nova migration **V38**: `driver_rating` ganha `client_driver_id`, o backfill cria os vínculos que faltam, e `client_id`/`driver_id` são **removidas**
- Nova migration **V39**: o mesmo em `client_rating`, reusando os vínculos que a V38 já criou
- `DriverRatingModel` e `ClientRatingModel` passam a apontar `ClientDriverModel`, não `ClientModel` + `DriverModel`
- Os dois serviços exigem vínculo existente para avaliar — **os dois TODOs morrem**
- O índice de unicidade passa a ser por vínculo: uma avaliação ativa por vínculo, por direção

**Fora de escopo:**

- **`update` no `client_rating`.** É superfície HTTP nova, e a regra 41 já aperta com a fase 3. A assimetria fica registrada como R6 e vira issue.
- **Migrar `trip` para o hub.** A `trip` liga em `driver_id` + `dependent_id`; é outra relação, não o par cliente-motorista. Não é a mesma dívida.
- `proposal`, `contract`, `conversation` — quando existirem, já nascem penduradas no hub.

## Capabilities

### Modified Capabilities

- `rating-through-link`: avaliação deixa de carregar o par e passa a pendurar no `client_driver`, com soft delete idêntico nas duas direções e a exigência de vínculo que os TODOs pediam.

## Impact

- **Schema:** `V37` altera `client_rating`; `V38` e `V39` alteram as tabelas de avaliação e **inserem linhas em `client_driver`** (backfill). São as primeiras migrations da base que criam dado derivado.
- **Depende da #30.** A `V36` precisa estar na `main` antes. Esta pilha nasce de dentro da `feat/30-client-driver-hub`.
- **Sem breaking change de API.** A resposta continua expondo `driverToken` e `clientToken`; o mapper passa a navegar pelo vínculo. O app mobile não muda.
- **Mudança de comportamento:** avaliar sem vínculo passa a responder **404**. Hoje responde 201. É intencional — é o TODO sendo cumprido.
- **`DELETE /api/client-ratings/{token}` deixa de destruir dado.** Quem dependia da linha sumir do banco passa a ver `deleted_at` preenchido.
- **Permissões:** `restore_client_rating` entra. Tokens emitidos antes exigem novo login.
