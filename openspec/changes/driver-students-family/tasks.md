> Back da tela "Alunos da van" (Genwork **VAN-57**, vanep-mobile change `driver-van-students`). **PR único**, por decisão do desenvolvedor, na branch `feat/VAN-57-driver-students-family`. Isso afasta as regras 36 a 41 (fases, uma camada por PR e o teto de ~600 linhas). Os testes (regra 43), o lint e o QA (regra 55) valem para o PR inteiro.

## Preparação

- [x] Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] Revisar `proposal.md`, `design.md` e os quatro specs
- [x] Confirmar a próxima versão livre de Flyway na `main` e em todas as branches remotas (`V68` era a última; esta change usa `V69` e `V70`). Reconfirmar antes do merge
- [x] Branch `feat/VAN-57-driver-students-family` a partir da `main`

## 1. Van do aluno

- [x] 1.1 Testes: `POST` com van própria, de outro motorista, inativa e desconhecida (422 sem gravar); `PATCH` só com `vehicleToken` mantém os outros campos (regra 16) e `null` limpa; `occupancy?vehicleToken=` conta a van e os sem van; van de outro motorista → 404
- [x] 1.2 Migration `V69`: `unlinked_passenger.vehicle_id` (nulo, FK, índice parcial)
- [x] 1.3 Model, DTOs (`vehicleToken` no `POST`, no `PATCH` e na resposta), serviço e `OwnVehicleService` para o filtro
- [x] 1.4 `DriverOccupancyService` com o filtro opcional
- [x] 1.5 ~~`contract.vehicle_id` gravado no aceite da proposta~~: fica para a change que criar o contrato a partir da proposta. Hoje o aceite não cria contrato (`resulting_contract_id` nunca é gravado), então a coluna ficaria sempre nula. Os alunos de contrato contam como sem van (D7)

## 2. Convite da família

- [x] 2.1 Testes: primeiro convite; reenviar mantém o código e atualiza `sentAt`; `invitedAt` na resposta; outro motorista → 404; cliente → 403; sem token → 401; link do grupo criado uma vez e reaproveitado; apagar o aluno apaga o convite; código sem caracteres ambíguos
- [x] 2.2 Migration `V70`: `family_invite` com os índices únicos parciais (D2)
- [x] 2.3 Pacote `familyinvite` (model, repository, gerador de código, service, DTOs, controller): `POST .../{token}/invite` e `GET /api/drivers/me/invite-link`; `invitedAt` buscado em lote
- [x] 2.4 `vanep.app.invite-base-url` com `${VANEP_APP_INVITE_BASE_URL:...}` e o placeholder no `.env.example`
- [x] 2.5 Chaves de mensagem em `messages.properties` e `messages_pt_BR.properties`

## 3. Aceite pelo responsável e sugestão de junção

- [x] 3.1 Testes: prévia anônima mínima; código desconhecido → 404; aceite cria `client_driver` `PENDING` (`DRIVER_INVITE`) e `family_match` `PENDING`; aceite repetido idempotente; dependente de outro cliente → 404 sem criar nada; motorista no aceite → 403; corpo sem `dependentToken` → 400; link do grupo com um, nenhum e dois candidatos
- [x] 3.2 `family_match` na `V70`, com o índice único parcial de `PENDING` por aluno
- [x] 3.3 Pacote `familymatch` e a regra de candidato (`FamilyMatchCandidatePolicy`) como classe pura
- [x] 3.4 `GET /api/family-invites/{code}` público no `SecurityConfig` e `POST /api/family-invites/{code}/accept`; `ClientDriverService.findOrCreate` passa a receber a origem, em vez de duplicar a criação do vínculo

## 4. Juntar e descartar

- [x] 4.1 Testes: `merge` mantém token e agenda, toma o nome do dependente e conta em `studentsWithFamily`; sem `PENDING` → 409; `dismiss` volta a `INVITED` e um novo aceite sugere de novo; outro motorista → 404; contrato `ACTIVE` do dependente juntado apaga logicamente o aluno; `familyMatch` só enquanto `PENDING`
- [x] 4.2 `unlinked_passenger.dependent_id` na `V69`, com o `comment on table` atualizado (D1)
- [x] 4.3 `POST .../{token}/merge` e `POST .../{token}/family-match/dismiss`, com transição atômica (`UPDATE ... WHERE status = 'PENDING'`)
- [x] 4.4 Ajustes: `DriverOccupancyService`, `RoutePassengerDTO` com `dependentToken` e `ContractService` aposentando o aluno juntado ao ativar

## 5. Lista de alunos do motorista

- [x] 5.1 Testes: duas fontes ordenadas por nome; cada linha da tabela do D5; filtro por van; van de outro motorista → 404; cliente → 403; mesmo número de consultas para poucos e muitos alunos (regra 17)
- [x] 5.2 Pacote `driverstudent`: `FamilyStatusPolicy` pura e o serviço com projeções, slots e famílias buscados em lote, mais DTO e controller

## Fechamento

- [x] `make lint` + `./mvnw verify -B` (2169 testes, cobertura 94%)
- [x] QA da regra 55 (`scripts/qa/driver-students-family/qa.sh`, 70/70, resultado no `qa.md`)
- [ ] Abrir o PR, depois do teste manual e da aprovação do desenvolvedor
