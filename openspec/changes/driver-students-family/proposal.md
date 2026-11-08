## Why

A tela "Alunos da van" do motorista (VAN-57, vanep-mobile, change `driver-van-students`) junta numa lista os alunos com família no app e os alunos que o próprio motorista cadastrou (aluno não vinculado, change `contract-core`). Os alunos não vinculados mantêm "Convidar família" à vista. Quando a família entra e cadastra o filho, o motorista confirma que é o mesmo aluno e junta os dois, sem perder a posição na rota.

O back hoje só tem o CRUD do aluno não vinculado e a contagem da `occupancy`. Faltam:
- a lista de alunos do motorista com as duas fontes;
- o convite da família;
- o link do motorista para o grupo de pais;
- a sugestão de junção, juntar e descartar;
- os alunos por van.

O app está saindo com essas partes fictícias, marcadas com `TODO(VAN-57)`. Esta change entrega o back para trocá-las.

## What Changes

- **Lista de alunos do motorista:** `GET /api/drivers/me/students`, com contrato `ACTIVE` e aluno não vinculado numa resposta só. Cada aluno traz a fonte, escola, turno escolar, slots, van e o estado da família (sem convite, convidado, família entrou, com família). Filtro opcional por van. Sem N+1 (regra 17).
- **Convite da família de um aluno não vinculado:** tabela `family_invite`.
  - Endpoints:
    - `POST /api/drivers/me/unlinked-passengers/{token}/invite` cria ou renova o convite e devolve `inviteUrl` e `sentAt`;
    - `invitedAt` sai na resposta do aluno não vinculado.
  - Reenviar é o mesmo `POST` e devolve o mesmo código com `sentAt` novo.
- **Link do motorista para o grupo de pais:** `GET /api/drivers/me/invite-link`, um convite sem aluno, um por motorista, criado sob demanda.
- **Lado da família**, para o app do responsável abrir o link:
  - `GET /api/family-invites/{code}`: prévia pública (primeiro nome do motorista, escola, primeiro nome do aluno quando o convite é de um aluno);
  - `POST /api/family-invites/{code}/accept { dependentToken }`: o cliente diz qual filho é.
  - Aceitar cria o vínculo `client_driver` `PENDING`, se ainda não existir, e a sugestão de junção.
- **Sugestão de junção:** tabela `family_match`, com `PENDING`, `CONFIRMED` ou `DISMISSED`. O convite de aluno sugere aquele aluno. O link do grupo sugere o aluno não vinculado da mesma escola com o mesmo primeiro nome, normalizado, quando há exatamente um. `familyMatch` sai na resposta do aluno não vinculado e da lista.
- **Juntar:** `POST /api/drivers/me/unlinked-passengers/{token}/merge`. O aluno não vinculado ganha `dependent_id` e passa a contar como "com família" na lista e na `occupancy`, continuando na rota pela mesma linha (mesma posição, mesmos registros de operação).
- **Descartar:** `POST /api/drivers/me/unlinked-passengers/{token}/family-match/dismiss`.
- **Alunos por van:**
  - `vehicle_id` opcional em `unlinked_passenger` (no `POST` e no `PATCH` do aluno não vinculado);
  - `?vehicleToken=` na lista de alunos e na `occupancy`;
  - o aluno de contrato fica sem van até existir contrato criado a partir da proposta (D7).
- **Ajustes:**
  - `occupancy.studentsWithFamily` passa a contar o aluno não vinculado juntado;
  - o contrato `ACTIVE` que entrar depois para o mesmo dependente tira da lista e da rota o aluno não vinculado juntado, que é apagado logicamente (soft delete), para o aluno não aparecer duas vezes.

## Capabilities

### New Capabilities

- `driver-students`: a lista de alunos do motorista com as duas fontes e o estado da família.
- `family-invites`: o convite por aluno, o link do grupo de pais e o lado do responsável (prévia e aceite).
- `family-match`: a sugestão de junção, juntar e descartar.
- `students-by-vehicle`: a van do aluno e o filtro por van.

### Modified Capabilities

- `unlinked-passengers` (change `contract-core`): o aluno não vinculado passa a poder ter um responsável depois de juntado (`dependent_id`). Antes de juntar, continua sem responsável e sem aviso. A resposta ganha `vehicleToken`, `invitedAt` e `familyMatch`.
- `route-passengers` (change `contract-core`): o aluno não vinculado juntado continua na fonte `UNLINKED_PASSENGER` e passa a carregar o `dependentToken`. Some quando o contrato do mesmo dependente fica `ACTIVE`.

## Impact

- **Migrations:** `V69` (`unlinked_passenger.vehicle_id` e `dependent_id`) e `V70` (`family_invite` e `family_match`). Reconfirmar os números antes do merge.
- **Pacotes:**
  - novos: `familyinvite`, `familymatch` e `driverstudent`;
  - alterados: `unlinkedpassenger`, `driveroccupancy`, `contract` (ativação aposenta o aluno juntado), `clientdriver` (`findOrCreate` com origem), `schedule` (slots em lote) e `routepassenger`.
- **Segurança:** endpoints do motorista em `/api/drivers/me/**`; a prévia do convite é pública e o aceite exige `CLIENT`; os dois ficam declarados no `SecurityConfig` (regra 20).
- **Config:** `vanep.app.invite-base-url` (env `VANEP_APP_INVITE_BASE_URL`) monta o `inviteUrl` (regra 1).
- **Fora do escopo:** aviso por push e e-mail ao responsável e ao motorista, que depende da VAN-70 (`notification`/FCM); a tela de aceite do convite no app do responsável, que é task própria no vanep-mobile.
