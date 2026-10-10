## Context

Esta change é a **fase 1** do spike "Propostas e Contratos" (V2, 03/10/2026). As decisões de produto já foram tomadas lá; este documento registra as que valem para o núcleo do contrato e o porquê de cada uma, para que o spike possa ser enxugado sem que nada se perca.

O que existe hoje no repositório e importa para esta change:

- `client_driver` (V36) liga um `client` a um `driver`, com um vínculo ativo por par (`client_driver_pair_active_key`). O `status` (`PENDING`, `ACTIVE`, `INACTIVE`, `BLOCKED`) é escrito à mão: o `POST` aceita qualquer valor e o `PATCH` é aberto ao admin **e às duas partes** (`@sec.isClientDriverLinkParty`). A V38 e a V39 criaram vínculos `ACTIVE` a partir das avaliações, e o `ClientDriverSeeder` cria um vínculo `ACTIVE` solto
- `trip` (V31) é a âncora da operação, única por `(driver_id, service_date, shift)`. `shift` usa o enum partilhado `Shift`, que inclui `FULLTIME`
- `dependent` tem `client_id`, `school_id`, `address_id` e um único `shift`. O `DependentService.delete` não tem trava e limpa o endereço
- `address` é de dono único (V20): cada linha pertence a no máximo um `client`, `dependent`, `school` ou `users`. Não tem coordenadas; o ponto no mapa é o `google_place_id`
- O bundle `ADMIN` do `DataSeeder` recebe todas as permissões do `PermissionRegistry`; em ambiente sem seeder, as permissões novas chegam por migration (padrão da V48)
- A #158 (já na `main`, `dd5f814`) faz a avaliação de motorista exigir vínculo `ACTIVE` (`DriverRatingEligibilityPolicy`)
- A `absence` (#160) está sendo feita em paralelo (`feat/absence-*`, `V52`), fora da convenção do D11, e entra na `main` depois desta change (ver D11 e R9)
- Não existe nada de contrato, proposta, agenda ou checklist

As changes `trip-daily-operation` e `client-driver-link` citam o modelo do `vanep-diagram.dbml`. **Esta change não segue o DBML**: ele ficou desatualizado e saiu do plano. O modelo vale como está aqui, e o que existe vale como está nas migrations.

## Goals / Non-Goals

**Goals**

- O núcleo do contrato existe completo — tabelas, enum de status, regras de período e valores — e nenhuma fase seguinte precisa acrescentar coluna a ele
- A #151 consegue montar o checklist: existe uma consulta que devolve os passageiros de uma trip, das duas fontes, dizendo de onde cada um veio
- O motorista consegue cadastrar os alunos que já tem sem esperar nenhuma família
- `client_driver.status = ACTIVE` passa a significar "existe contrato vigente" — e o banco deixa de conter vínculos que contradizem isso

**Non-Goals**

- Proposta, solicitação, origem do vínculo, bloqueio com direção (fase 2)
- Modelo de contrato, PDF, assinatura (fases 3 e 4)
- Guardas no dependente, retirada de solicitações concorrentes (fase 5)
- Telas do app, convite da família, troca do aluno não vinculado pelo dependente (fase 7)
- Eventos do contrato, aditivo, rescisão, renovação, arrependimento, parcelas, jobs por data (fase 8)

## Decisions

### D1 — Turno escolar e turno da operação são tipos diferentes (E6)

`Shift` mistura duas perguntas. "Em que turno o aluno estuda?" admite `FULLTIME`. "Em que turno a van roda?" não admite: uma ida às 6h40 e uma volta às 17h não são a mesma rota.

| Enum | Valores | Usado por |
|---|---|---|
| `SchoolShift` (renomeia `Shift`) | `MORNING`, `AFTERNOON`, `NIGHT`, `FULLTIME` | `dependent.shift`, `unlinked_passenger.school_shift` |
| `OperationShift` (novo) | `MORNING`, `AFTERNOON`, `NIGHT` | `trip.shift`, `schedule_slot.shift` |

Os dois ficam em `br.com.vanep.shared.enums` (cada um é usado por duas features — regra 5). O JSON de `dependent` não muda: os valores são os mesmos.

**Consequência para a `trip`:** aluno integral gera duas trips, `MORNING` na ida e `AFTERNOON` na volta. Isso responde a **Q1 da `trip-daily-operation`**, que deixou a pergunta para a #151 — a resposta sai daqui porque é o tipo do turno que decide, e não o checklist.

**Linhas existentes:** a `V53` converte `trip.shift = 'FULLTIME'` em `MORNING`. Sem isso, o Hibernate falharia ao ler a linha num enum que não tem o valor. `MORNING` porque uma rota "integral" começava de manhã, e nenhum checklist depende dela ainda (a #151 não existe). Só existe banco local de desenvolvimento, e o `TripSeeder` não grava `FULLTIME`; o `update` é defensivo, para uma trip `FULLTIME` criada à mão pela API.

**Consequência para a `absence` (#160), feita em paralelo:** o `AbsenceLegShiftPolicy` e o `AbsenceScopePolicy` usam o mesmo `Shift` para duas perguntas diferentes — o turno da trip (`uniqueLegOf`) e o turno escolar do dependente (`allows`). Como a `absence` entra depois desta change, é a branch dela que separa os dois ao entrar: o lado da trip passa a receber `OperationShift` (e `uniqueLegOf` deixa de ter o caso vazio do `FULLTIME`) e o lado do dependente passa a receber `SchoolShift`. Só troca de tipo; a regra da `absence` não muda.

Rejeitado: manter `FULLTIME` em `trip` e validar no serviço. O banco e o Java continuariam aceitando um valor que não tem significado operacional — a mesma ambiguidade que o R6 da `trip-daily-operation` registrou.

Rejeitado: `CHECK` na coluna `trip.shift`. Nenhuma coluna de enum do repositório tem `CHECK`; o tipo Java é a garantia, e a `V53` limpa o que já existe.

### D2 — Agenda em `schedule` + `schedule_slot`, um `schedule` por dono

Um contrato real é "seg a sex, ida 6h40–6h50 e volta 12h30", mas pode ser "seg, qua e sex só na ida". A agenda é uma lista de pares (dia, trecho), cada um com a janela de embarque.

```
schedule (id)
  └─ schedule_slot (schedule_id, weekday, leg, shift, window_start, window_end)

contract_item.schedule_id       ─┐
unlinked_passenger.schedule_id  ─┼─→ schedule
(fase 2) proposal_item, service_request_item.schedule_id ─┘
```

- **Sem FK polimórfica.** Cada dono aponta para o seu `schedule`; `schedule_slot` pertence ao `schedule`. A alternativa (`owner_type` + `owner_id` no slot) perde a integridade referencial
- **Um `schedule` por dono:** índice único parcial em `schedule_id` em cada tabela dona. Agenda compartilhada entre dois itens faria a edição de um mudar o outro em silêncio
- **`weekday`** é `java.time.DayOfWeek` gravado como texto (`MONDAY` … `SUNDAY`) — regra 14, sem inventar enum para o que o JDK já tem
- **`leg`** é `RouteLeg` (`OUTBOUND` = casa → escola, `RETURN` = escola → casa), em `shared.enums`. A #160 cria `absence.enums.AbsenceLeg` com os mesmos dois valores; como ela entra depois desta change, o PR 2 cria `RouteLeg` e a branch da `absence` troca o `AbsenceLeg` por ele ao entrar. Fica um enum só (regras 5 e 6). O valor gravado não muda, então a coluna `absence.leg` não precisa de migration
- **`shift` é explícito**, do tipo `OperationShift`, e não deduzido da hora. A `trip-daily-operation` rejeitou deduzir turno pela hora (D6, "fronteira arbitrária"), e o integral é o caso que quebraria: ida no slot `MORNING`, volta no slot `AFTERNOON`
- **Janela:** `window_start` obrigatório, `window_end` opcional (a volta costuma ter só o horário de saída da escola). Quando há `window_end`, ele é posterior ao início
- **Unicidade:** um slot por `(schedule_id, weekday, leg)`. O índice de busca é `(schedule_id, weekday, shift)`, porque a consulta de passageiros parte do motorista, chega ao dono e daí à agenda (D10)

A agenda é editada **como conjunto**: substituir a agenda apaga (soft delete) os slots antigos e grava os novos, numa transação. Slot não tem token nem endpoint próprio — ele só existe dentro do dono.

### D3 — Contrato é o acordo; o documento é outra tabela (fase 3)

`contract` guarda o que foi combinado. PDF, envio ao provedor e assinaturas ficam em `contract_document`, que nasce na fase 3 e ganha as colunas da assinatura na fase 4. Um contrato criado pelo admin é um **contrato sem documento**, não um contrato com colunas vazias.

As tabelas do núcleo (`contract`, `contract_item`, `schedule`, `schedule_slot`, `unlinked_passenger`) **nunca ganham coluna nova**: o que as fases seguintes trazem chega como tabela nova pendurada nelas. A ligação fica na tabela que nasce depois — a proposta aponta para o contrato que gerou, e não o contrário. Por isso não existe `contract.proposal_id`.

Não existe `contract.source`: o contrato digital é o único tipo de contrato da Vanep. O motorista que tem aluno sem contrato digital usa o aluno não vinculado (D9).

### D4 — Enum de status completo e máquina de estados desde o início

`ContractStatus`: `AWAITING_SIGNATURES`, `SIGNED`, `ACTIVE`, `SUSPENDED`, `ENDED`, `TERMINATED`, `CANCELLED`, `SUPERSEDED`. Nesta fase só `ACTIVE` é criado, mas o enum nasce inteiro — é a mesma lição do `CANCELLED` em `trip` e do `PENDING` em `client_driver`: acrescentar valor depois custa migration e revisão de todos os consumidores.

```
AWAITING_SIGNATURES ─→ SIGNED ─→ ACTIVE ─→ SUSPENDED
        │                │          │  ↖______│
        └→ CANCELLED ←───┘          ├─→ ENDED        (fim do período)
                                    ├─→ TERMINATED   (rescisão)
                                    └─→ SUPERSEDED   (aditivo assinado assume)
SUSPENDED ─→ ENDED | TERMINATED
```

`ENDED`, `TERMINATED`, `CANCELLED` e `SUPERSEDED` são terminais. Renovar não é transição: é um contrato novo.

`ContractTransitionPolicy` é pura (sem Spring, JPA ou servlet) e decide a matriz inteira. Diferente da `trip` (D11 de lá), **o `PATCH` do admin passa pela policy**. Contrato é registro jurídico: reabrir um contrato encerrado é criar um novo, não editar o antigo. Contrato cadastrado errado se corrige removendo (`DELETE`, D13) e cadastrando de novo.

O `PATCH` só altera `status` — termos (período, valores, itens, agenda) são imutáveis, e mudança de termo é aditivo (fase 8). `SUPERSEDED` é recusado no `PATCH` (`422 contract.status.requires_successor`): só existe com um contrato sucessor, que é o fluxo do aditivo.

### D5 — Período obrigatório de até 12 meses; valores explícitos

Decisão do produto: **não existe contrato sem data de término na Vanep.** O período é sempre informado e tem teto de 12 meses — cobre o ano letivo civil (fevereiro a dezembro) e o ano letivo que vira o ano (meio do ano a meio do ano).

| Coluna | Regra | Onde é garantida |
|---|---|---|
| `starts_on`, `ends_on` | ambas obrigatórias | `@NotNull` no DTO (400) + `NOT NULL` |
| ordem das datas | `ends_on > starts_on` | `ContractTermsPolicy` (422 `contract.period.invalid`) + `CHECK` |
| período | `ends_on` ≤ `starts_on` + 12 meses | `ContractTermsPolicy` (422 `contract.period.too_long`); o banco não expressa "12 meses" de forma legível |
| `total_amount` | `numeric(12,2)`, maior que zero | `@DecimalMin(value = "0", inclusive = false)` no DTO (400) + `CHECK` |
| `installments` | 1 a 12 | `@Min(1)` `@Max(12)` no DTO (400) + `CHECK` |
| `due_day` | 1 a 28 | `@Min(1)` `@Max(28)` no DTO (400) + `CHECK` |

**Cada regra mora numa camada só, e o banco é a última barreira.** Faixa de um campo isolado é validação de entrada: fica na Bean Validation do DTO (regra 10), com a mensagem por chave (`message = "{contract.due_day.invalid}"`, padrão do `DriverUpdateRequestDTO`). A policy fica só com o que cruza campos — a ordem das datas e o teto de 12 meses —, que é regra de negócio e responde 422. Repetir as faixas na policy daria duas mensagens possíveis para o mesmo erro, e a segunda nunca seria alcançada pelo HTTP. Os `CHECK`s continuam no banco para escritas que não passam pelo DTO (seeder, migrations, fases seguintes).

O valor é o **total do contrato, dividido em parcelas**, e não uma mensalidade. O caso típico é a anuidade diluída em 12 parcelas, embora a escola funcione cerca de 10 meses; modelar o total deixa essa regra visível em vez de escondida num "julho e dezembro também pagam". O mesmo modelo cobre o contrato mensal (total = mensalidade × meses, uma parcela por mês) e o de semestre. O cronograma de parcelas é da fase 8.

`due_day` vai até 28 para que todo mês tenha o dia de vencimento — fevereiro não tem 29, 30 nem 31.

### D6 — Item por dependente, com endereço copiado

`contract_item` liga o contrato a um dependente. Um contrato por par cliente–motorista, com um item por dependente — irmãos ficam no mesmo contrato, com preço próprio cada um.

| Coluna | Origem nesta fase |
|---|---|
| `dependent_id` | informado pelo admin; precisa ser do cliente do vínculo |
| `school_id` | copiado do dependente; obrigatório |
| endereço de embarque | **copiado** do endereço do dependente: `city_id`, `zip_code`, `street`, `number`, `complement`, `neighborhood`, `district_id`, `google_place_id` |
| `monthly_amount` | preço do item, `numeric(12,2)` |
| `schedule_id` | agenda do item (D2) |

**O endereço é cópia, não FK.** O contrato é o retrato do que foi combinado; se o responsável mudar de casa e editar o cadastro, o contrato antigo continua dizendo de onde a van saía — e a mudança vira aditivo. Uma FK para `address` também violaria o dono único da V20.

Dependente sem escola ou sem endereço não entra em contrato (`422 contract.item.school_required` / `contract.item.address_required`). Na fase 2 esses dados passam a vir da proposta aceita.

**Regra que o banco não garante:** o mesmo dependente não pode ter o mesmo `(weekday, leg)` em dois contratos **assinados e não encerrados** (`SIGNED`, `ACTIVE` ou `SUSPENDED`, o mesmo conjunto que mantém o vínculo `ACTIVE` no D8, em `ContractStatus.SIGNED_AND_NOT_ENDED`). O serviço confere na criação e na restauração de um contrato assinado e não encerrado. O contrato suspenso continua reservando os horários: suspensão é atraso de pagamento, e a família espera voltar à van. Se só os `ACTIVE` contassem, outro motorista poderia ocupar o horário durante a suspensão e a reativação deixaria o dependente em duas rotas no mesmo horário. É possível o mesmo dependente em dois motoristas — ida com um, volta com outro — desde que os slots não colidam.

### D7 — Um contrato `ACTIVE` por vínculo

Índice único parcial `(client_driver_id) where status = 'ACTIVE' and deleted_at is null`. O contrato que substitui outro (aditivo, fase 8) só fica `ACTIVE` na mesma transação em que o anterior vira `SUPERSEDED`.

Nesta fase o admin cria o contrato direto em `ACTIVE`. O motorista precisa estar `APPROVED` (mesma regra do vínculo hoje) e o vínculo não pode estar `BLOCKED`.

### D8 — O status do vínculo é derivado dos contratos (E1)

Hoje qualquer uma das partes põe o próprio vínculo em `ACTIVE` sem nunca ter contratado nada. Com contrato, o status deixa de ser dado de entrada e vira **consequência**:

| Contratos do par | `client_driver.status` |
|---|---|
| algum `SIGNED`, `ACTIVE` ou `SUSPENDED` | `ACTIVE` |
| nenhum desses, mas já teve contrato | `INACTIVE` |
| nunca teve contrato | `PENDING` |
| vínculo `BLOCKED` | continua `BLOCKED` (não é derivado) |

`LinkStatusPolicy` é pura e calcula o status a partir dos status dos contratos. O `ContractService` recalcula o vínculo em toda escrita de contrato (criar, mudar status, remover, restaurar), na mesma transação. `SUSPENDED` mantém o vínculo `ACTIVE`: suspensão é atraso de pagamento, a relação continua; o que muda é que o aluno sai da rota (D10).

Consequências no código atual:

- `ClientDriverCreateRequestDTO` perde `status`; todo vínculo nasce `PENDING`. Responde a **Q2 da `client-driver-link`** ("o admin pode criar vínculo já `ACTIVE`?"): não
- **`PATCH /api/client-drivers/{token}` é removido**, com o `ClientDriverUpdateRequestDTO`. Era o único campo mutável, e os quatro valores agora são derivados ou dependem da fase 2. A permissão `update_client_driver` sai do `PermissionEnum` e dos bundles (regra 34)
- `BLOCKED` fica sem caminho de escrita até a fase 2, que traz `blocked_by`/`blocked_at` e as ações de bloquear e desbloquear com quem bloqueou. É a resposta à **Q3 da `client-driver-link`** e ao D9 de lá — bloqueio sem direção não tem semântica, e não vale reabrir uma porta só para fechá-la de novo
- Q1 da `client-driver-link` ("o vínculo volta a `PENDING` ou nasce outro?"): nenhum dos dois. Contrato encerrado deixa o vínculo `INACTIVE`, e um contrato novo do mesmo par reaproveita o vínculo e o devolve a `ACTIVE`

> **Desvio do texto do spike.** A seção "Mudanças em estruturas existentes" da V2 dizia que o admin ganharia um endpoint de correção do status com motivo. Esta change não cria esse endpoint: qualquer escrita direta reabre o estado "vínculo `ACTIVE` sem contrato" que o E1 existe para eliminar. A correção do admin passa a ser sobre o **contrato** (`PATCH` de status e `DELETE`), e o vínculo acompanha. O bloqueio com motivo é da fase 2.

### D9 — Aluno não vinculado: do motorista, só para a rota

O motorista cadastra os alunos que já transporta como **alunos não vinculados** (`unlinked_passenger`). É o que permite montar a rota no primeiro dia sem depender de nenhum pai.

| Coluna | Regra |
|---|---|
| `driver_id` | o motorista dono; sai do `Authentication`, nunca do corpo |
| `name` | obrigatório |
| `school_id` | obrigatório |
| `school_shift` | `SchoolShift`, obrigatório |
| `address_id` | endereço de embarque na tabela `address`, dono único (índice parcial como na V20), obrigatório |
| `notes` | observação livre do motorista, opcional |
| `schedule_id` | agenda (D2), obrigatória |

- **Endereço por FK, ao contrário do item.** Não há contrato a preservar: o motorista edita livremente, e a referência acompanha. Reaproveita o fluxo de endereço do dependente, por catálogo (`cityToken`, rua e CEP, no `DependentAddressRequestDTO`): `AddressService.upsertForDependent` ganha o equivalente `upsertForUnlinkedPassenger` (regra 6)
- **Dados mínimos.** Nome, escola, turno, endereço e agenda. Sem data de nascimento, documento ou telefone: é o que a rota precisa e nada além. Base legal: Enunciado CD/ANPD nº 1/2023 (tratamento de dados de criança por outra base que não o consentimento), com o motorista como controlador e a Vanep como operadora
- **Nunca avisa responsável.** Não existe responsável ligado a ele. O aviso de embarque é exclusivo de quem tem contrato
- **Sem trava.** Nem limite de quantidade, nem data de término — uma data seria burlada apagando e recriando. Limite, se houver, é regra de um eventual plano gratuito, fora daqui
- **Superfície `/me`:** `GET`, `POST`, `GET /{token}`, `PATCH /{token}` (regra 16), `PUT /{token}/schedule` (substitui a agenda como sub-recurso) e `DELETE /{token}` em `/api/drivers/me/unlinked-passengers`, com `@PreAuthorize("isAuthenticated()")` e o tipo `DRIVER` conferido no serviço — o mesmo padrão de `/api/drivers/me/service-areas`. O token na URL é resolvido **dentro do conjunto do motorista**: token de outro motorista devolve `404`, sem revelar que existe

**Sem gate de aprovação.** O motorista em análise já pode cadastrar os alunos e preparar a rota; quem bloqueia a operação é o início da `trip`, que exige `APPROVED` (RN-02).

A troca do aluno não vinculado pelo dependente real ("esse é o João que você cadastrou?") é da fase 7. Ela faz soft delete do aluno não vinculado — nunca delete físico (D11).

### D10 — Passageiros da trip: uma consulta, duas fontes

`RoutePassengerQueryService.findPassengers(driver, serviceDate, OperationShift)` devolve a lista de passageiros da rota:

- **itens de contrato:** contrato `ACTIVE` do motorista, com `starts_on ≤ serviceDate ≤ ends_on`, cujo item tem slot em `(weekday da data, shift)`;
- **alunos não vinculados:** do motorista, com slot em `(weekday da data, shift)`.

Cada passageiro sai como um `RoutePassengerDTO` com `source` (`CONTRACT_ITEM` ou `UNLINKED_PASSENGER`), `token` do item ou do aluno não vinculado, nome, escola, `leg`, janela e endereço de embarque. `SUSPENDED` não entra — só o contrato `ACTIVE` coloca aluno na rota.

A conferência de datas é defensiva: os jobs que ativam e encerram contratos pela data são da fase 8, e até lá um contrato `ACTIVE` com `ends_on` passado não pode continuar gerando passageiro.

Nesta fase a consulta é **serviço interno, sem endpoint**: quem a consome é a #151. O formato final do `RoutePassengerDTO` é ajustado com quem fizer a #151 na integração; o que esta change fixa é a existência das duas fontes, a origem de cada passageiro e o token.

Sem N+1 (regra 17): duas consultas, uma por fonte, cada uma com os joins até o slot e o endereço e projetando direto no `RoutePassengerDTO` (`select new`). Carregar as entidades puxaria a cadeia EAGER do item (contrato → vínculo → cliente → usuário), com um select a mais por contrato.

### D11 — Como a operação aponta para o passageiro

`checklist_entry` (#151) ainda não existe, e a `absence` (#160) está sendo feita em paralelo. Esta change fixa a convenção que as duas seguem, porque é o modelo de dados do passageiro que a decide:

```sql
contract_item_id      bigint references contract_item (id),
unlinked_passenger_id bigint references unlinked_passenger (id),
constraint <tabela>_one_passenger check (
  (contract_item_id is not null)::int + (unlinked_passenger_id is not null)::int = 1
)
```

- Duas FKs anuláveis e um `CHECK` de exatamente uma preenchida. As FKs continuam íntegras e a origem fica explícita
- **Sem `contract_id` nem `dependent_id`**: saem do item, e repetir abre espaço para divergir
- A unicidade "um registro por trip, passageiro e fase" vira dois índices únicos parciais, um por coluna
- `stop_change_request` (#152) aponta só para `contract_item_id`: só responsável com contrato pede troca de parada

> **⚠️ A `absence` (#160) nasce fora da convenção e se adapta ao entrar.** Ela está sendo feita em paralelo (`feat/absence-*`, `V52`) com `client_driver_id` + `dependent_id`, unicidade `(dependent_id, absence_date, leg)` e, no D4 dela, a previsão de um `contract_id` futuro. Isso contradiz a convenção (sem `contract_id`, sem `dependent_id`) e deixa o aluno não vinculado sem ausência. Ficou combinado com o autor que ela entra na `main` depois desta change: esta change não mexe na `absence`, e a branch dela adota a convenção ao entrar (lista no topo do `tasks.md`, R9).

Rejeitado: tabela comum `route_passenger` apontada pelas duas fontes. Uma FK a menos no checklist, ao custo de uma tabela a mais que precisaria ser sincronizada a cada contrato ativado, encerrado ou aluno trocado.

**O que esta change garante para a convenção funcionar:** `contract_item` e `unlinked_passenger` nunca são apagados fisicamente (D13). Quando o aluno não vinculado vira dependente (fase 7), o histórico antigo continua apontando para ele e as linhas novas usam o item do contrato. Aditivo e renovação criam itens novos; o checklist antigo fica no item antigo.

### D12 — Permissões e acesso

| Quem | O quê | Como |
|---|---|---|
| Admin | CRUD de contrato | `list_contracts`, `show_contract`, `create_contract`, `update_contract`, `delete_contract`, `restore_contract` no bundle `ADMIN` |
| Cliente e motorista do vínculo | ler um contrato | `hasAuthority('show_contract') or @sec.isContractParty(#token, authentication)` |
| Cliente e motorista do vínculo | listar os contratos do par | `GET /api/client-drivers/{token}/contracts` com `hasAuthority('list_contracts') or @sec.isClientDriverLinkParty(#token, authentication)` |
| Motorista | gerir os próprios alunos não vinculados | `/me`, `isAuthenticated()` + tipo `DRIVER` no serviço |

`@sec.isContractParty` resolve o token do vínculo a partir do contrato e reaproveita a mesma comparação do `isClientDriverLinkParty` (regra 22, sem `*SecurityService`).

**Por que só o admin escreve.** Há dois motivos, e só um deles é provisório:

| Ação | Só admin porque | Até quando |
|---|---|---|
| criar (`POST`) e mudar status (`PATCH`) | esta fase entrega o núcleo antes dos fluxos que dão origem ao contrato. Sem proposta aceita nem assinatura, um contrato criado por uma das partes seria um acordo que a outra não aceitou, e voltaria o vínculo `ACTIVE` sem nada verificável (D8) | **provisório:** a criação passa a vir da proposta aceita (fase 2) e da assinatura (fase 4); as transições, da assinatura, dos jobs por data, da rescisão e do aditivo (fase 8). O admin fica com a correção |
| remover (`DELETE`) e restaurar | remover não é encerrar: é desfazer um cadastro errado (D13). O fim do contrato é status | **permanente:** a parte que quer sair usa a rescisão (fase 8), que muda o status e preserva o histórico |

Nesta fase, as partes só leem.

O motorista **não** ganha permissão nova: os endpoints `/me` resolvem o dono pelo `Authentication`, como em `driver_service_area`.

### D13 — Soft delete em todo o núcleo

Regra 19 em `contract`, `contract_item`, `schedule`, `schedule_slot` e `unlinked_passenger`, com os índices únicos parciais (`where deleted_at is null`).

| | Significado | Quem faz |
|---|---|---|
| `status = ENDED` / `TERMINATED` / `CANCELLED` | o contrato acabou — dado de negócio, continua visível | fluxo do contrato; admin via `PATCH` |
| `deleted_at` | a linha não deveria existir — erro de cadastro | admin |

Remover um contrato remove seus itens e as agendas deles na mesma transação, e recalcula o status do vínculo (D8). `restore` faz o inverso e é recusado com `409 contract.active_conflict` se o par já tiver outro contrato `ACTIVE`.

Além da regra 19, o soft delete é o que mantém íntegro o histórico do checklist (D11): uma linha de `checklist_entry` nunca aponta para um passageiro que sumiu.

## Risks / Trade-offs

| # | Risco | Mitigação |
|---|---|---|
| **R1** | A suíte roda em H2 com `ddl-auto`, então nenhum índice parcial (um `ACTIVE` por par, um slot por dia e trecho, dono único de agenda e endereço) é exercitado em teste | Mesmo procedimento da tarefa 1.6 da `trip-daily-operation`: aplicar as migrations num PostgreSQL descartável e provar cada índice com inserts manuais. Não declarar `uniqueConstraints` só para teste |
| **R2** | A `V57` devolve a `PENDING` todos os vínculos hoje `ACTIVE`, porque nenhum tem contrato | É a correção, não efeito colateral: `ACTIVE` sem contrato é o estado que o E1 elimina. `PENDING`, e não `INACTIVE` como dizia o texto do E5 no spike, porque a regra do D8 dá `PENDING` a quem nunca teve contrato — e a migration tem de deixar o banco exatamente como a policy o deixaria. **⚠️ Efeito aceito:** desde a #158 (`dd5f814`), o `DriverRatingService` só aceita vínculo `ACTIVE` (`422 driver_rating.link.not_active`). Depois da `V57`, nenhum cliente avalia motorista até o admin cadastrar o contrato do par. Decisão de produto de 07/10: aceitável, porque é o mesmo resultado que o E3 do spike pede (só avalia quem contratou). A avaliação de cliente não confere status. O admin cadastra os contratos reais depois |
| **R3** | A `V53` muda o turno de trips `FULLTIME` existentes | Não há banco de homologação nem de produção, e o seeder não grava `FULLTIME`; só uma trip criada à mão num banco local seria afetada. Nenhum checklist depende delas |
| **R4** | `address` não tem coordenadas; o ponto do embarque copiado é só o `google_place_id` | Suficiente para a rota da fase 1. Se o modelo de endereço ganhar coordenadas, a cópia do item é a única exceção aceitável à regra "núcleo não ganha coluna" — e precisa de decisão própria quando acontecer |
| **R5** | A regra "mesmo dependente sem o mesmo (dia, trecho) em dois contratos `ACTIVE`" é do serviço, não do banco; duas criações simultâneas passariam | Nesta fase só o admin cria contrato, e a corrida exige dois admins no mesmo dependente no mesmo segundo. A fase 5, que ativa contratos pelo fluxo real, revisita com lock por dependente |
| **R6** | O preço por item (`monthly_amount`) e o `total_amount` do contrato não são conciliados | Deliberado: o total pode ter desconto de irmão, taxa de reserva abatida ou parcelamento diferente de 12. O total é o que vale para cobrança; o item é o preço de tabela exibido. A fase 3 mostra os dois no resumo |
| **R7** | Os números das migrations colidem com trabalho em paralelo | Conferido em 07/10: `main` em `V51`, `V52` reservada para a `absence` (#160). Esta change usa `V53` a `V57`. Como a `absence` entra depois, a `V52` dela fica abaixo da última aplicada e o Flyway recusa subir (`outOfOrder` desligado), inclusive no deploy do `cd.yml` — a `absence` renumera a dela para depois da nossa última. O `scripts/check-migrations.sh` da `chore/ci-migration-version-check`, se entrar na `main`, pega isso no CI. Reconfirmar antes de cada merge com migration |
| **R8** | Remover o `PATCH` de `client_driver` é breaking change de API | Sem consumidor: verificado em 03/10 que `vanep-mobile` e `vanep-frontend` não chamam `/client-drivers` |
| **R9** | A `absence` (#160) é feita em paralelo, apontando para `client_driver_id` + `dependent_id`, fora da convenção do D11 | Combinado com o autor: a `absence` entra na `main` depois desta change e se adapta ao entrar — tipos de turno (D1), `RouteLeg` (D2), `contract_item_id` / `unlinked_passenger_id` com o `CHECK` de exatamente um e a unicidade por passageiro (D11), migration renumerada (R7). Esta change não tem PR para a `absence` |

## Open Questions

| | Pergunta | Estado |
|---|---|---|
| **Q1** | Formato final do `RoutePassengerDTO` (campos, ordenação, se traz o responsável para o aviso) | 🟡 Combinar com quem fizer a #151, na integração. A fase 1 entrega a consulta com as duas fontes, a origem e o token |
| **Q2** | `RoutePassengerDTO` deve vir ordenado pela janela de embarque? | 🟡 Proposto: sim, por `window_start`, porque é a ordem natural da rota até a #45 calcular a ordem real |

## Migration Plan

| Migration | PR | Conteúdo |
|---|---|---|
| **V53** | 1 | `update trip set shift = 'MORNING' where shift = 'FULLTIME'`; comments em `trip.shift` (turno da operação, sem `FULLTIME`) e em `dependent.shift` (turno escolar; dias e horários vêm da agenda do contrato — A1) |
| **V54** | 2 | `schedule`, `schedule_slot`, `unlinked_passenger` com FKs, `CHECK` da janela, comments e índices (abaixo) |
| **V55** | 3 | `contract`, `contract_item` com FKs, `CHECK`s de período e valores, comments e índices (abaixo) |
| **V56** | 5 | `role_permissions` do `ADMIN` recebe as seis permissões de contrato — idempotente, padrão da V48, para ambiente com `vanep.seed.enabled=false` |
| **V57** | 7 | Vínculo `ACTIVE` sem contrato vira `PENDING` (E5) — é o valor que a `LinkStatusPolicy` dá a um par que nunca teve contrato (D8); `update_client_driver` sai de todos os bundles (`permissions - 'update_client_driver'`) |

Nenhuma migration aplicada é editada (regra 2). Todas as tabelas novas seguem o padrão do repositório: `id bigint generated always as identity`, `token varchar(32)` com índice único parcial nas tabelas expostas pela API (`contract`, `contract_item`, `unlinked_passenger`), `created_at`/`updated_at timestamptz`, `deleted_at timestamptz`. `schedule` e `schedule_slot` não têm token: não são expostos sozinhos.

**Índices** — cada um vem de uma consulta concreta:

| Tabela | Índice | Para quê |
|---|---|---|
| `contract` | único `(client_driver_id) where status = 'ACTIVE' and deleted_at is null` | um contrato ativo por par (D7) |
| `contract` | `(client_driver_id) where deleted_at is null` | contratos de um par (telas das partes, recálculo do vínculo) |
| `contract` | `(starts_on) where status = 'SIGNED' and deleted_at is null` | job de ativação na data de início (fase 8) |
| `contract` | `(ends_on) where status = 'ACTIVE' and deleted_at is null` | job de encerramento e lembrete de renovação 45 dias antes (fase 8) |
| `contract_item` | `(contract_id) where deleted_at is null` | itens de um contrato |
| `contract_item` | `(dependent_id) where deleted_at is null` | contratos de um dependente; colisão de slots (D6); trava de exclusão (fase 5) |
| `contract_item` | único `(schedule_id) where deleted_at is null` | um dono por agenda (D2) |
| `schedule_slot` | único `(schedule_id, weekday, leg) where deleted_at is null` | o mesmo dia e trecho não aparecem duas vezes |
| `schedule_slot` | `(schedule_id, weekday, shift) where deleted_at is null` | passageiros da trip (D10) |
| `unlinked_passenger` | `(driver_id) where deleted_at is null` | alunos não vinculados de um motorista |
| `unlinked_passenger` | único `(address_id) where deleted_at is null` | dono único do endereço (V20) |
| `unlinked_passenger` | único `(schedule_id) where deleted_at is null` | um dono por agenda (D2) |

## Rollout — grafo de dependência e plano de PRs

```
main
 └─ spec/contract-core   (só a change do openspec, primeira da pilha)
      └─ PR 1 shift-split
           └─ PR 2 schedule-unlinked-schema
                └─ PR 3 contract-schema
                     └─ PR 4 policies
                          └─ PR 5 contract-service  (+ ScheduleService e DTOs da agenda)
                               └─ PR 6 contract-http
                                    └─ PR 7 client-driver-derived-status
                                         └─ PR 8 unlinked-passengers  (serviço + HTTP)
                                              └─ PR 9 route-passengers
```

| PR | Conteúdo | Depends on | Parallel with |
|---|---|---|---|
| 0 | Branch `spec/contract-core`: só `openspec/changes/contract-core/`, sem código | — | — |
| 1 | `SchoolShift` + `OperationShift`, `trip` e `dependent` ajustados, `V53` | 0 | — |
| 2 | `V54`, `shared.enums.RouteLeg`, `ScheduleModel`, `ScheduleSlotModel`, `UnlinkedPassengerModel` e repositórios | 1 | — |
| 3 | `V55`, `ContractStatus`, `ContractModel`, `ContractItemModel` e repositórios | 2 | — |
| 4 | Policies puras: `ContractTransitionPolicy`, `ContractTermsPolicy`, `LinkStatusPolicy`, `ScheduleSlotPolicy` | 3 | — |
| 5 | `ContractService` (criar, ler, mudar status, remover, restaurar, recalcular vínculo), DTOs e mapper do contrato, `ScheduleService`, DTOs e mapper da agenda, `@sec.isContractParty`, permissões, `V56`, mensagens | 4 | — |
| 6 | Controllers do contrato (`/api/contracts`, `/api/client-drivers/{token}/contracts`) | 5 | — |
| 7 | `client_driver` sem `status` no create e sem `PATCH`, `V57`, `ContractSeeder`, `ClientDriverSeeder` em `PENDING` | 6 | — |
| 8 | `UnlinkedPassengerService`, DTOs e mapper, `AddressService.upsertForUnlinkedPassenger`, mensagens e controller de `/api/drivers/me/unlinked-passengers` | 7 | — |
| 9 | `RoutePassengerQueryService` e `RoutePassengerDTO` | 8 | — |

A pilha começa na `spec/contract-core`, que só traz a change do openspec: o time revisa a spec num PR próprio, e o PR 1 aponta para ela como `--base`. Os PRs 1 a 9 saem em sequência, como na `trip-daily-operation`: cada branch nasce da anterior, cada PR aponta a anterior como `--base` e o merge segue a mesma ordem. O PR 9 é o que destrava a #151. A agenda (`ScheduleService` e os DTOs de slot) entra no PR 5 porque é consumida pelo contrato e pelo aluno não vinculado.
