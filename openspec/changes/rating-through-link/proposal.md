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

**2. As gêmeas removem de jeitos diferentes.** `driver_rating` (V16) faz soft delete e tem `restore`; `client_rating` (V33) apaga de verdade e não tem. Duas features que fazem a mesma coisa, com regras diferentes.

**3. O `restore` do `driver_rating` quebra.** Se uma avaliação é removida e o mesmo par avalia de novo, restaurar a antiga deixa **duas** ativas para o par. O índice único recusa, e sem tratamento para `DataIntegrityViolationException` a resposta é **500**.

### A decisão de revisão

A primeira versão desta change alinhava as gêmeas **para cima**: dava soft delete ao `client_rating`. Na revisão da #201 o João apontou que avaliação não deveria ter soft delete nenhum:

> *avaliei alguém com 3 estrelas → removi → vou avaliar de novo com 5 → [restaurar a antiga] não faz sentido. Acho melhor rating no geral não ter soft delete.*

Uma nota é a opinião de um momento. Restaurar uma antiga ao lado de uma mais nova não tem significado — e é exatamente o que produz o 500 do item 3. As gêmeas passam a se alinhar **para baixo**: nenhuma tem soft delete, nenhuma tem `restore`.

Isso contradiz a regra 19 da constitution (*"soft delete for all removable domain models"*), então a exceção entra **registrada na própria regra**, e não como desvio silencioso.

Adiar a migração para o hub custa caro por aritmética: hoje são **2** tabelas. `proposal` (#42), `contract` (#41) e `conversation` (#161) também penduram no par. Depois delas são **5**, com dado real dentro.

## What Changes

- Nova migration **V37**: `driver_rating` perde `deleted_at`; as linhas já removidas saem de verdade antes; o índice único do par volta a ser **total**
- `DriverRatingModel` perde `@SoftDelete`; sai o `restore` (endpoint, serviço, repositório, permissão `restore_driver_rating`, chave `driver_rating.already_active`)
- **Regra 19 da constitution** ganha a exceção das avaliações, com o motivo
- Nova migration **V38**: `driver_rating` ganha `client_driver_id`, o backfill cria os vínculos que faltam, e `client_id`/`driver_id` são **removidas**
- Nova migration **V39**: o mesmo em `client_rating`, reusando os vínculos que a V38 já criou
- `DriverRatingModel` e `ClientRatingModel` passam a apontar `ClientDriverModel`, não `ClientModel` + `DriverModel`
- Os dois serviços exigem vínculo existente para avaliar — **os dois TODOs morrem**
- O índice de unicidade passa a ser por vínculo: uma avaliação por vínculo, por direção

**Fora de escopo:**

- **`update` no `client_rating`.** É superfície HTTP nova. A assimetria fica registrada como R6 e vira issue.
- **Migrar `trip` para o hub.** A `trip` liga em `driver_id` + `dependent_id`; é outra relação.
- `proposal`, `contract`, `conversation` — quando existirem, já nascem penduradas no hub.

## Capabilities

### Modified Capabilities

- `rating-through-link`: avaliação deixa de carregar o par e passa a pendurar no `client_driver`, com remoção idêntica nas duas direções — física, sem restore — e a exigência de vínculo que os TODOs pediam.

## Impact

- **Schema:** `V37` altera `driver_rating`; `V38` e `V39` alteram as tabelas de avaliação e **inserem linhas em `client_driver`** (backfill). São as primeiras migrations da base que criam dado derivado.
- **⚠️ Dado destruído:** a `V37` apaga fisicamente as `driver_rating` que já estavam soft-deletadas. Do ponto de vista do usuário elas já não existiam; mas o histórico some. Sem esse `delete`, elas voltariam a valer ao cair a coluna.
- **Mudança de API:** `POST /api/driver-ratings/{token}/restore` **deixa de existir**. `DELETE /api/driver-ratings/{token}` passa a remover a linha de verdade.
- **Depende da #30.** A `V36` precisa estar na `main` antes.
- **Sem breaking change nas respostas.** Continuam expondo `driverToken` e `clientToken`; o mapper navega pelo vínculo. O app mobile não muda.
- **Mudança de comportamento:** avaliar sem vínculo passa a responder **404**. Hoje responde 201. É o TODO sendo cumprido.
- **Permissões:** `restore_driver_rating` sai do `PermissionEnum`; o seeder reescreve o bundle ADMIN no próximo start.
- **Constitution:** a regra 19 ganha uma exceção. É mudança de norma do time, e merece o olhar de quem revisa.
