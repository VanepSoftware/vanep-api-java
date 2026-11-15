## Context

- **Origem:** a tela "Alunos da van" do vanep-mobile (VAN-57, change `driver-van-students`, relatório de API A1 a A7 no `design.md` dela).
- **O que já existe:**
  - `unlinked_passenger` (V57): do motorista, só para a rota, sem responsável;
  - `contract` + `contract_item` (V58): um item por dependente, com agenda própria;
  - `RoutePassengerQueryService`: junta as duas fontes por dia e turno, interno;
  - `DriverOccupancyService`: `students` e `studentsWithFamily`;
  - `proposal.vehicle_id` (V65).
- **O que não serve:** o `assistant_invite` (V15) é endereçado a um usuário que já existe, por e-mail. O convite da família é um link compartilhado para quem ainda não tem conta.

## Goals / Non-Goals

**Goals:** os endpoints A1 a A7; o aluno não vinculado juntado sem perder a posição na rota; nenhuma consulta por aluno (regra 17).

**Non-Goals:**
- push e e-mail (VAN-70);
- tela do responsável no app;
- contrato automático ao juntar (o contrato continua pelo fluxo de proposta);
- busca de motorista por nome, token ou e-mail (VAN-64).

## Decisions

### D1. Juntar não cria contrato: o aluno não vinculado ganha `dependent_id`

Juntar grava `unlinked_passenger.dependent_id` e não cria `contract_item`. O aluno continua na rota pela mesma linha, com a mesma agenda e os mesmos registros de operação (`checklist_entry`, `absence` apontam para `unlinked_passenger_id`, D11 da `contract-core`). É isso que o canvas chama de "mantém a posição na rota".

**Alternativa recusada:** criar um `contract_item` na hora. Exigiria um contrato com valor, parcelas e período que ninguém negociou, e trocaria a referência dos registros de operação.

**Efeitos:**
- o aluno juntado conta em `studentsWithFamily`;
- sai em "Com família" na lista;
- o `dependentToken` vai no passageiro da rota, para os avisos da VAN-70 acharem o responsável;
- quando um contrato `ACTIVE` do mesmo motorista passa a ter item para esse dependente, o serviço do contrato apaga logicamente (soft delete) o aluno não vinculado juntado, na mesma transação, para o aluno não aparecer duas vezes. O histórico continua apontando para a linha apagada (regra 19).

**Muda a regra da V57:** "no guardian, no notification" vale até juntar. A migration atualiza o `comment on table`.

### D2. Convite: `family_invite`

Colunas:

```
family_invite: id, token, code (único, 12 caracteres sem ambíguos), driver_id,
               unlinked_passenger_id (nulo = link do grupo de pais),
               sent_at, created_at, updated_at, deleted_at
```

- Índice único parcial por `unlinked_passenger_id` e outro por `driver_id WHERE unlinked_passenger_id IS NULL`: um convite vivo por aluno e um link de grupo por motorista.
- O convite não expira. O motorista manda no grupo de pais, que é lido dias depois.
- Reenviar só atualiza `sent_at`. O código continua o mesmo, para o link antigo seguir valendo.
- O `code` é o identificador público no link. O `token` segue a regra 13 para as outras referências.
- `inviteUrl = ${vanep.app.invite-base-url}/{code}`. O app do responsável trata esse caminho como deep link (task própria no vanep-mobile).
- Apagar o aluno não vinculado apaga o convite dele.

### D3. Aceite pelo responsável

`POST /api/family-invites/{code}/accept { dependentToken }`, só para `CLIENT`. O dependente precisa ser do cliente.

Na mesma transação:
1. cria o `client_driver` do par como `PENDING` se não houver vínculo vivo; se já houver, usa o que existe. Respeita o índice único `(client_id, driver_id)`;
2. cria o `family_match` `PENDING`:
   - **convite de aluno:** com aquele aluno não vinculado;
   - **link do grupo:** com o aluno não vinculado do motorista, ainda sem família, da mesma escola do dependente e com o mesmo primeiro nome normalizado (sem acento, minúsculo), quando há exatamente um. Com zero ou mais de um, não cria sugestão e o aceite só deixa o vínculo `PENDING`.

**Erros:**
- aluno já juntado → 409 `family_invite.already_linked`;
- dependente de outro cliente → 404;
- aceite repetido do mesmo dependente → 200 idempotente, com o mesmo `family_match`.

### D4. Sugestão de junção: `family_match`

Colunas:

```
family_match: id, token, unlinked_passenger_id, dependent_id, family_invite_id,
              status (PENDING | CONFIRMED | DISMISSED), responded_at,
              created_at, updated_at, deleted_at
```

- Uma sugestão `PENDING` por aluno não vinculado (índice único parcial).
- **Juntar** (`merge`): só com `PENDING`. Grava `dependent_id` no aluno e a sugestão vira `CONFIRMED`.
- **Descartar** (`dismiss`): a sugestão vira `DISMISSED`. O aluno continua sem família e o convite continua valendo, de modo que um novo aceite cria outra sugestão.
- A resposta traz `{ token, dependentName, dependentAge, clientName }`. `dependentAge` vem de `birth_date` e é nulo se não houver data.

### D5. Estado da família, derivado

Não é coluna. É uma função pura em `driverstudent`:

| Situação | `familyStatus` |
|---|---|
| Item de contrato `ACTIVE` | `WITH_FAMILY` |
| Aluno não vinculado com `dependent_id` | `WITH_FAMILY` |
| Aluno com `family_match` `PENDING` | `FAMILY_JOINED` |
| Aluno com convite e sem sugestão pendente | `INVITED` |
| Os demais | `NOT_INVITED` |

### D6. Lista de alunos: `GET /api/drivers/me/students`

Cada aluno da lista traz:

```
{ token, source (CONTRACT_ITEM | UNLINKED_PASSENGER), name, schoolToken, schoolName,
  schoolShift, slots[], vehicleToken, dependentToken, familyStatus, invitedAt, familyMatch }
```

- **Origem dos alunos:**
  - itens de contratos `ACTIVE` com `starts_on <= hoje <= ends_on`;
  - alunos não vinculados vivos.
- **Consultas:** projeções (DTO direto da consulta), uma por fonte, mais uma para os slots (`schedule_id in`), uma para os convites, uma para as sugestões e os tokens de van e dependente em lote. Entidades não servem aqui: o item de contrato tem associações EAGER (contrato, vínculo, cliente, cidade) e o projeto não liga batch fetch (regra 17). O teste conta as consultas.
- **Turno escolar do item de contrato:** nulo. O turno do dependente virou dias por escola (`dependent_school_day`, V62); quem precisa de turno usa os slots.
- **Ordem:** por nome. Sem paginação: a lista de um motorista é limitada pela capacidade das vans.
- **Filtro `?vehicleToken=`:** devolve os alunos daquela van e os alunos sem van. A van precisa ser do motorista (404 se não for).

### D7. Van do aluno

- `unlinked_passenger.vehicle_id` é nulo por padrão. Entra no `POST` (opcional) e no `PATCH` (`JsonNullable`, regra 16).
- `contract.vehicle_id` **fica fora desta change**. Hoje o aceite da proposta não cria contrato (`resulting_contract_id` nunca é gravado), então a coluna ficaria sempre nula. Os alunos de contrato contam como sem van e aparecem em todas as vans. A van do contrato entra com a change que criar o contrato a partir da proposta.
- A `occupancy` aceita `?vehicleToken=` com a mesma regra da lista.
- A van precisa ser do motorista e estar ativa (422 `unlinked_passenger.vehicle.not_owned`).

### D8. Segurança

- `/api/drivers/me/students`, `/invite-link` e `/unlinked-passengers/{token}/{invite,merge,family-match/dismiss}`: `isAuthenticated()` e `UserType` `DRIVER`, com o token resolvido entre os alunos do próprio motorista (404 se for de outro motorista), igual ao CRUD da `contract-core`.
- `GET /api/family-invites/{code}`: público, declarado no `SecurityConfig`. Mostra só o primeiro nome do motorista, o nome da escola e o primeiro nome do aluno, nunca endereço ou horários.
- `POST /api/family-invites/{code}/accept`: `CLIENT`.

### D8a. Concorrência

- Juntar e descartar movem a sugestão com `UPDATE ... WHERE status = 'PENDING'`: de duas respostas simultâneas, só a que muda a linha segue, e a outra recebe 409 `family_match.not_pending`.
- Primeiro convite, link do grupo e aceite simultâneos esbarram nos índices únicos parciais. O perdedor recebe 409 `family_invite.concurrent_request`, e uma nova tentativa lê o que o vencedor gravou.

### D9. Mensagens

As chaves novas ficam em `messages.properties` e `messages_pt_BR.properties` (regra 46):
- `family_invite.not_found`, `family_invite.already_linked`, `family_invite.dependent.required`, `family_invite.dependent.not_found`, `family_invite.link_blocked`, `family_invite.concurrent_request`;
- `family_match.not_pending`, `family_match.already_pending`;
- `unlinked_passenger.vehicle.not_owned`;
- `driver_student.vehicle.not_found`.

## Risks / Trade-offs

- **Junção pelo primeiro nome no link do grupo pode errar:** só sugere, nunca junta sozinha. O motorista confirma, e "Não é" descarta.
- **Link do grupo sem expiração pode circular fora do grupo:** a prévia não expõe dado sensível, e o aceite só cria vínculo `PENDING` e sugestão. Nada vira contrato nem aviso sem o motorista.
- **Aluno juntado sem contrato recebe avisos sem ter pago:** é o mesmo caso do vínculo pré-existente da VAN-64. O contrato continua opcional.

## Open Questions

- **Aceite pelo link do grupo sem aluno correspondente:** deixa o vínculo `PENDING`, mas a tela do motorista ainda não mostra esses casos. Precisa de uma lista de "famílias que entraram sem aluno"? Por ora, fora do escopo.
- **O nome do aluno juntado:** o do aluno não vinculado (como o motorista cadastrou) ou o do dependente? A spec usa o do dependente depois de juntar, porque a família é a fonte.

## Migration Plan

Duas migrations novas, confirmadas contra as branches remotas antes do merge (regra 2):

1. `V69`: `unlinked_passenger.vehicle_id` e `dependent_id`, mais o `comment on table` atualizado;
2. `V70`: `family_invite` e `family_match`.

Não há backfill: as colunas novas começam nulas.

### Rollout

**PR único**, por decisão do desenvolvedor, na branch `feat/VAN-57-driver-students-family`. Isso afasta as regras 36 a 41 (fases, uma camada por PR e o teto de ~600 linhas). A ordem de implementação seguiu a regra 42 dentro do mesmo PR, com os testes (regra 43), `make lint`, `./mvnw verify` e o QA da regra 55 em `scripts/qa/driver-students-family/qa.sh`, com o resultado no `qa.md`.
