## Why

`client` e `driver` existem há meses, mas **nada liga um ao outro**. Não há linha no banco que diga "a Maria e o Carlos têm uma relação".

Cinco coisas do modelo canônico precisam dessa linha, porque não penduram no cliente nem no motorista — penduram no **par**:

```
client_driver
 ├── proposal       (#42)  a proposta que a Maria manda pro Carlos
 ├── contract       (#41)  o contrato que nasce dela
 ├── driver_rating         a nota que a Maria dá no Carlos
 ├── client_rating         a nota que o Carlos dá na Maria
 └── conversation   (#161) o chat entre os dois
```

O time já sabe disso: `DriverRatingService` e `ClientRatingService` carregam o mesmo TODO — *"exigir que o cliente já tenha tido algum vínculo com o motorista, uma vez que exista o relacionamento client_driver"*.

Sem o hub, `proposal` e `contract` não têm onde apoiar a FK, e as avaliações continuam sem saber se as duas pessoas sequer se conhecem.

## What Changes

- Nova migration **V36**: tabela `client_driver` com `client_id`, `driver_id`, `status`, `token`, timestamps e `deleted_at`
- Índice único **parcial** em `(client_id, driver_id)` — um par, um vínculo ativo
- Novo enum `RelationshipStatus` (`PENDING`, `ACTIVE`, `INACTIVE`, `BLOCKED`). Os três primeiros vêm do `vanep-diagram.dbml`; `BLOCKED` entra por pedido de revisão na #198 e **precisa ser acrescentado ao diagrama**
- Novo pacote `br.com.vanep.clientdriver` com `controller`, `dto`, `enums`, `mapper`, `model`, `repository`, `seed`, `service`
- CRUD por token sob `/api/client-drivers`: `POST`, `GET` paginado, `GET/{token}`, `PATCH` (regra 16), `DELETE`, `POST /{token}/restore`
- `@sec.isClientDriverLinkParty` — **o vínculo tem dois donos**; cliente e motorista enxergam o próprio
- Novas permissões `list/show/create/update/delete/restore_client_driver` no bundle `ADMIN`
- `GET /api/client-drivers/me` — cada parte lista os próprios vínculos

**Fora de escopo:**

- **Migrar `driver_rating` e `client_rating` para o hub.** As duas ligam direto em `driver_id` + `client_id`, furando o canônico. Fica registrado como R1 e vira issue própria — ver a seção de risco.
- Criação do vínculo pelo fluxo de produto: quem cria um `client_driver` na vida real é o primeiro envio de proposta (#42), que não existe. Aqui só o CRUD administrativo e o seeder criam.
- `proposal`, `contract`, `conversation` — esta change entrega só a âncora.

## Capabilities

### New Capabilities

- `client-driver-link`: o vínculo entre um cliente e um motorista como entidade própria, com um único vínculo ativo por par, ciclo `PENDING → ACTIVE → INACTIVE`, e leitura garantida às duas partes.

## Impact

- **Schema:** `V36` cria `client_driver`. Nenhuma tabela existente é alterada.
- **Permissões:** seis entradas novas no `PermissionEnum` e no bundle `ADMIN`. Tokens emitidos antes exigem novo login.
- **`SecurityEvaluator`:** ganha o primeiro método de posse com **duas** partes possíveis. Os seis existentes comparam um dono só.
- **Sem breaking change.** Nenhum endpoint existente muda de contrato.
- **Dívida registrada, não paga:** os dois `*_rating` seguem furando o hub. Quanto mais tabelas pendurarem no `client_driver`, mais cara fica a migração — hoje são 2, com `proposal`, `contract` e `conversation` viram 5.
