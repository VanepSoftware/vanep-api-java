## Why

O CRUD de `driver_rating` existe desde a #35 e já pendura no vínculo desde a `rating-through-link`. O que falta são as regras do UC12, e sem elas a nota que aparece na busca não tem credibilidade. Medido no código de hoje:

| Regra (#158) | Hoje |
|---|---|
| Só cliente com vínculo **ativo** avalia | `findByPair` não olha o status: `PENDING`, `INACTIVE` e `BLOCKED` avaliam |
| Uma avaliação por vínculo, no banco | ✅ índice único `idx_driver_rating_link_unique` (`V38`) + 409 no serviço |
| Irreversível | `PUT` e `DELETE /api/driver-ratings/{token}` abertos ao autor |
| Anônima para o motorista | O motorista não tem rota que liste avaliações, mas nenhum teste garante isso |
| App sabe se o cliente já avaliou (RN-03) | Não existe rota |
| Média consistente com o total acumulado | Apagar a última avaliação grava **5.00**; o seeder cria avaliação sem recalcular |
| Motorista sem avaliação mostra "sem avaliações" | Depende do caminho: `null` se nunca foi avaliado, `5.00` se a última foi apagada |
| O cliente consegue avaliar | **Não consegue.** O bundle `CLIENT` nunca recebeu `create_driver_rating`; só o admin cria |

A última linha é a mais séria: a feature existe, mas o ator dela não tem acesso.

## What Changes

- Nova migration **`V49`**: `create_driver_rating` entra no bundle `CLIENT`, e `driver.rating` é recalculado para todos os motoristas a partir das avaliações existentes, com `null` para quem não tem nenhuma
- `DataSeeder.clientPermissions()` passa a incluir `create_driver_rating`
- **`PUT /api/driver-ratings/{token}` deixa de existir**, junto com `DriverRatingUpdateRequestDTO`, `update` no serviço e `update_driver_rating`
- **`DELETE /api/driver-ratings/{token}` passa a ser só do admin** (moderação). O autor perde o ramo `@sec.isDriverRatingOwner`
- Avaliar exige vínculo `ACTIVE`: sem vínculo segue 404; vínculo em outro status responde **422** `driver_rating.link.not_active`
- A média volta a `null` quando não sobra avaliação, em vez de `5.00`
- Nova rota para o app saber se o cliente já avaliou o motorista (RN-03)
- Testes nomeados provando que o motorista não alcança nenhuma avaliação individual
- **Regra 19 da constitution**: a exceção das avaliações passa a dizer que `driver_rating` é imutável e só o admin remove

**Fora de escopo:**

- **`client_rating`.** A #158 pergunta se as mesmas regras valem no sentido inverso. Fica registrado como Q2; o `client_rating` segue como está.
- **Trigger no banco para a média.** A #158 cita `vanep-trigger-rating.dbml`; a média continua em Java (D4).
- **Ordenar a busca por nota.** Nenhuma rota ordena por `rating` hoje; o estado `null` não distorce ordenação nenhuma.

## Capabilities

### New Capabilities

- `driver-rating-rules`: quem pode avaliar o motorista, quantas vezes, o que é imutável, o que o motorista enxerga e como a média é exposta.

## Impact

- **Schema:** só dado, nenhuma coluna nova. A `V49` altera `role_permissions` e `driver.rating`.
- **⚠️ Dado reescrito:** a `V49` sobrescreve `driver.rating` de todos os motoristas. Quem hoje tem `5.00` por fallback passa a `null`.
- **Mudança de API:**
  - `PUT /api/driver-ratings/{token}` some
  - `DELETE /api/driver-ratings/{token}` responde 403 ao autor
  - avaliar com vínculo não `ACTIVE` passa de 201 a 422
  - nova rota "já avaliou?"
- **Permissões:** `update_driver_rating` sai do `PermissionEnum`; o seeder reescreve o bundle ADMIN no próximo start. `create_driver_rating` entra no CLIENT. **Tokens emitidos antes exigem novo login.**
- **Mobile e front:** `rating: null` passa a ser o estado "sem avaliações" em todas as rotas que expõem a nota.
- **Constitution:** a regra 19 muda de texto. É mudança de norma do time.
