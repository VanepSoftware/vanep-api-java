## Context

A motivação está no `proposal.md`, e os requisitos nos specs. O que existe hoje e importa aqui:

- **`driver_document` (V21, V45):**
  - colunas: `driver_id` NOT NULL, `document_type varchar(50)` (`DocumentTypeEnum`: `CRLV`, `VEHICLE_INSPECTION`, `MUNICIPAL_AUTHORIZATION`, `CRIMINAL_RECORD`, `RESIDENCE_PROOF`, `PROFILE_PHOTO`, `VEHICLE_PHOTO`, `OTHER`), `file_media_id`, `expires_at`, `status` (`PENDING`, `APPROVED`, `REJECTED`), `review_method`, `external_check_id`, `rejection_reason`, `reviewed_by`, `reviewed_at`, `notified_at`, `is_active`, soft delete;
  - rotas em `/api/driver-documents`, com dono por `@sec.isDriverDocumentOwner` e revisão em `PUT /{token}/status` (`update_driver_document`, só admin);
  - o serviço resolve o chamador por `userRepository.findByEmail(jwt.getSubject())`;
  - o `PUT` do dono volta a `PENDING`, mas o upload de arquivo (`DriverDocumentFileService.replace`) não mexe no status.
- **`vehicle` (V10, V44):** `photo_front_media_id`, `photo_side_media_id`, `photo_document_media_id` (slots `VEHICLE_PHOTO_*`, privados), `is_active`, soft delete. O dono é conferido por `@sec.isVehicleOwner`. O app lê `photoDocumentUrl` (sempre nulo) e não faz upload dele.
- **Onboarding** (`DriverOnboardingService`):
  - **documentos:** exige `CRLV`, `VEHICLE_INSPECTION` e `MUNICIPAL_AUTHORIZATION` do motorista, com arquivo e não `REJECTED`;
  - **CNH:** exige uma CNH ativa e não vencida, sem olhar a `category` (texto livre, `@NotBlank`);
  - **veículo:** exige uma van ativa;
  - **aprovação** (`approve`): só confere `UNDER_REVIEW`.

  Nenhum app chama o `GET /api/drivers/me/onboarding` nem as rotas de documento, e o frontend do admin não tem tela de aprovação nem de revisão: o admin usa a API direto.
- **Permissões:** o bundle `DRIVER` tem `create_driver_document` e `list_driver_documents`. O `ADMIN` recebe as permissões por migration, como a `V59`.
- **Seed:** `DriverDocumentSeeder` cria um `CRLV` aprovado válido até 31/12/2026 e uma `CRIMINAL_RECORD` pendente vencida em 15/10/2026. `DriverCnhSeeder` cria a CNH categoria `D`.
- **Quem vai consumir:** a `contract-generation` (VAN-31, D5 dela) chama `SchoolTransportCredentialService.findCredentials(driverId, vehicleId, date)` no envio e no aceite da proposta, e grava as credenciais na foto do contrato.

**O que importa para a revisão pelo admin (PRs 4 a 10), no código depois do PR 3:**

- **Revisão:** o `DocumentReview` (`shared.document`) tem `reviewedBy` `@ManyToOne(fetch = EAGER)`. O `UserModel` tem `@SoftDelete`, então o Hibernate carrega o revisor com um select próprio mesmo se ele for `LAZY`: só o `join fetch` evita a consulta extra. A revisão de documento usa `update_driver_document` e `update_vehicle_document`, as mesmas permissões de editar. Nenhuma entidade do projeto tem `@Version`.
- **Mudança do dono:** o `/file` dos documentos da van e do motorista aplica `reviewService.clear()`, o que volta o documento a `PENDING`. Quem renova uma vistoria antes de vencer perde a credencial no ato.
- **CNH (`driver_cnh`, V17):** sem revisão. O arquivo é a `photo` (`photo_media_id`). Os índices `driver_cnh_driver_active_key unique (driver_id)` e `driver_cnh_registration_active_key unique (registration_number)`, ambos `where deleted_at is null`, impedem a CNH renovada (mesmo número) de existir ao lado da atual. O `DriverCnhService.create` também recusa uma segunda CNH do motorista (409 `driver_cnh.driver.duplicate`). O dono pode restaurar a própria CNH.
- **Aprovação do motorista:** `approve` e `reject` (`approve_driver` ou `hasRole('ADMIN')`) mandam o e-mail por `DriverNotificationService` → `mailService.send` **dentro** da transação. O `reject` guarda só o texto do motivo. Os templates ficam em `templates/email/` (`driver-approved.html`, `driver-rejected.html`). O `NegotiationEmailService` já manda e-mail por `@TransactionalEventListener(AFTER_COMMIT)`.
- **Quem grava o `driver`:** `RegistrationService`, `DriverService` (perfil), `DriverPhotoService`, `DriverRatingService` (média a cada avaliação) e o `DriverOnboardingService`.
- **Arquivos:** o `MediaFileService.discard` apaga o `media_file` e o objeto do storage **dentro** da transação. O `MediaUrl` já põe `?v=<token da mídia>` na URL, então uma URL só muda de conteúdo quando muda de `v`. O `MediaResponder` não manda `Cache-Control`.
- **Data e fuso:** o bean `Clock` (`ClockConfig`) é `Clock.systemUTC()`. O `ExpirySweepService` o usa, em `@Scheduled(fixedDelayString = …)` e em lotes. O `SchoolTransportCredentialService.today()` é estático, em `TripService.SERVICE_ZONE` (`America/Sao_Paulo`).
- **Erros:** o `ApiErrorAdvice` responde em `ProblemDetail`.
- **Banco e placa:** não existe `btree_gist` no banco. O PostgreSQL é o 17, em que essa extensão é *trusted* e o dono do banco pode criá-la. A placa aceita `ABC-1D23` e `ABC1D23` (`@Pattern` com hífen opcional) e é gravada como veio, então as duas grafias passam no índice único `vehicle_plate_active_key`.
- **Usuário:** `users.birth_date` e `users.address_id` existem. A cidade do usuário sai do endereço pessoal.
- **Consumidores:** na `main`, o `vanep-mobile` (`lib/`) não chama `/api/driver-documents`, `/api/vehicle-documents` nem `/api/driver-cnhs`. O app está montando o envio dos documentos sobre esta pilha, ainda fora da `main`, e recebe o contrato do D2 antes do merge. As telas de revisão do admin (`vanep-frontend`) são o consumidor das rotas dos PRs 9 e 10 e da aprovação com os dados.

## Goals / Non-Goals

**Goals**

- O banco garante o dono de cada documento: a van para os documentos da van, o motorista para os da pessoa. Nenhum dono é condicional ao tipo
- Um documento aprovado sempre corresponde ao que o admin viu
- O motorista só manda o arquivo; os dados impressos no documento são preenchidos uma vez, pelo revisor, na aprovação (D2)
- Uma pergunta, uma resposta: as credenciais de uma van numa data saem de um único serviço, usado pelo onboarding, pela aprovação, pela proposta e pelo endpoint do motorista
- Renovar um documento antes de vencer não tira a credencial: a aprovação antiga vale até a nova ser aprovada
- A situação do motorista (o que falta, o que espera o admin, o que vence) sai de uma única policy, usada pela aprovação, pelo onboarding, pela fila e pela página do admin
- Dois admins, ou um admin e um reenvio, nunca gravam uma decisão sobre algo que o admin não viu

**Non-Goals**

- Tabela genérica de documentos com dono polimórfico (D1)
- Exigências por cidade ou estado (pergunta jurídica 4 do spike)
- Notificar vencimento
- Guardar os arquivos de envios substituídos: só a decisão fica (D10)
- Tabela de auditoria: o histórico da página do admin sai das datas que já existem (D17)
- Tabela com a situação já calculada de cada motorista: a fila calcula na leitura, como risco aceito (D16, R10)
- Bloqueio terminal de conta (fraude): o `UserModel` não tem esse estado

## Decisions

### D1 — Duas tabelas concretas, revisão compartilhada

`vehicle_document` com `vehicle_id NOT NULL` e `driver_document` com `driver_id NOT NULL`, cada uma com o próprio enum de tipo (`VehicleDocumentType`, `DriverDocumentType`). Os campos de revisão (`status`, `review_method`, `external_check_id`, `rejection_reason`, `reviewed_by`, `reviewed_at`) viram um `@Embeddable` `DocumentReview`, e as regras de revisão uma `DocumentReviewPolicy` pura (D3). Os dois ficam em `br.com.vanep.shared.document`, junto com `DocumentStatus` e `ReviewMethod`, que saem do `driverdocument` (regra 5: o que duas features usam vai para a área compartilhada).

**Rejeitado:**
- **`vehicle_id` anulável em `driver_document`, com `CHECK` por tipo.** O banco ficaria consistente, mas o dono continuaria condicional ao tipo: todo consumidor precisa lembrar de filtrar pela van, e o documento continua "do motorista" quando a van é removida.
- **Tabela `document` com `owner_type` e `owner_id`.** É FK polimórfica, sem integridade referencial, e o modelo do spike já recusa isso para a agenda.
- **Colunas no `vehicle` e no `driver`** (`inspection_valid_until`, …). Perdem o arquivo, a revisão e o histórico: a vistoria é semestral, e cada renovação apagaria a anterior.
- **`@MappedSuperclass` com tudo.** Mistura identidade e dono na herança. O `@Embeddable` compartilha só o que é igual de fato (a revisão), e cada tabela fica explícita.

`is_active` não vai para a `vehicle_document`: o soft delete já diz se o documento existe, e o `is_active` do `driver_document` só é lido no onboarding. A `driver_document` mantém a coluna (regra 2) e passa a ser lida como `deleted_at is null and is_active`, sem caminho novo de escrita. `notified_at` também não vai: nada escreve nele.

### D2 — O motorista manda o arquivo, o admin preenche os dados na aprovação

O motorista manda só o tipo e o arquivo. Não digita nada que esteja impresso no documento. O número, a validade e, na CNH, a categoria e a emissão são preenchidos pelo revisor na aprovação, olhando o arquivo.

**Por quê:**
- **Menos esforço para o motorista.** Antes ele fotografava o documento e depois digitava o que já estava na foto.
- **O dado digitado pelo motorista não é confiável.** O admin já precisava conferir cada campo contra o arquivo. Digitar uma vez, na revisão, custa o mesmo que conferir, e o dado aprovado passa a ser sempre o que o revisor leu.
- **É o padrão de quem faz cadastro de motorista em escala.** O motorista envia o documento e acompanha o status. A empresa extrai os dados, primeiro por revisão humana e depois por leitura automática ou consulta à base oficial, com o revisor confirmando.

**Os dados de cada tipo, preenchidos na aprovação:**

| Tipo | Dono | Número | Validade |
|---|---|---|---|
| `CRLV` | van | opcional | obrigatória |
| `VEHICLE_INSPECTION` | van | opcional | obrigatória |
| `SCHOOL_VEHICLE_AUTHORIZATION` | van | **obrigatório** | obrigatória |
| `MUNICIPAL_AUTHORIZATION` | pessoa | **obrigatório** | opcional |
| `SCHOOL_DRIVER_COURSE` | pessoa | opcional | opcional |
| `CRIMINAL_RECORD` | pessoa | opcional | opcional |
| CNH (D14) | pessoa | **registro obrigatório** | obrigatória, com a emissão e a categoria |

**Como fica:**
- **O envio** tem o tipo e o arquivo, num `POST` multipart só (D4). Todo envio nasce `PENDING` com arquivo, e não existe envio sem arquivo.
- **O motorista não edita os dados.** Os `PUT` de documento saem. Com o envio `PENDING`, ele pode trocar o arquivo pelo `/file` ou remover o envio. Para mudar o tipo, remove e manda de novo.
- **Enquanto o envio está `PENDING`,** o número e as datas ficam nulos.
- **A aprovação** (`PUT …/status` com `APPROVED`) leva os dados do tipo. Cada tipo diz o que é obrigatório: `requiresNumberOnApproval()` e `requiresExpiryOnApproval()` no enum, como o `latestExpiryOn` do D21.
- **A conferência da aprovação.** A `DocumentReviewPolicy` confere os dados junto com o arquivo. Falta de campo dá 400 `document.review.field_required`, com a propriedade `field`. A obrigatoriedade fica no serviço porque depende do status e do tipo.
- **Gravação.** A decisão e os dados são gravados na mesma transação, e o revisor é quem preencheu.
- **Na recusa,** os dados do corpo são ignorados e não são gravados.

**No banco:** `document_number varchar(64)`, gravado sem espaços nas pontas. As colunas de dados são anuláveis.
- O banco garante o que vale depois da aprovação: `CHECK (status <> 'APPROVED' or expires_at is not null)` na `vehicle_document`, e um `CHECK` do número para cada tipo que o exige (`SCHOOL_VEHICLE_AUTHORIZATION` e `MUNICIPAL_AUTHORIZATION`) só quando `APPROVED`.
- Continua o `CHECK` dos tipos de cada tabela.

Validade opcional na certidão criminal é intencional. A certidão costuma valer 90 dias no emissor, e exigir uma válida a cada proposta obrigaria o motorista a renovar a cada trimestre. O que a lei pede (CTB 329) é a comprovação para exercer a atividade, e o admin confere a data na revisão.

**Dados que só o motorista tem:** nenhum destes documentos tem um. Os dados da van (placa, modelo, capacidade) continuam digitados pelo motorista, porque são do produto: aparecem na proposta e no contrato. Também não são dados de documento.

**Rejeitado:**
- **O motorista digita e o admin confere** (o desenho anterior). Era trabalho em dobro, e o dado só valia depois da conferência.
- **Leitura automática ou consulta à base oficial agora** (OCR, Serpro Datavalid). Têm custo por consulta e só se pagam com volume. O `review_method = AUTO` e o `external_check_id` já existem para isso. Quando entrarem, elas preenchem a mesma aprovação, e o revisor confirma.
- **O motorista declara só a categoria da CNH, para ter retorno na hora.** A categoria está na foto, e a declaração seria mais um dado a conferir. O app mostra antes do envio o que o transporte escolar exige (CNH D ou E, curso, certidão), e uma CNH que não serve é recusada com `NOT_ELIGIBLE` (D12), com e-mail (D18).

### D3 — A revisão: só o admin, aprovar exige arquivo e os dados, mudar volta a pendente

A `DocumentReviewPolicy` decide sem JPA:
- **Rejeitar exige motivo** (400 `document.review.reason_required`).
- **Aprovar exige** arquivo (422 `document.review.file_required`) e os dados obrigatórios do tipo (400 `document.review.field_required`, D2).
- **Toda mudança do dono** (um arquivo novo) produz uma revisão limpa: `PENDING`, sem revisor, data, método nem motivo, e sem os dados da aprovação.

O `DriverDocumentFileService.replace` passa a aplicar a revisão limpa, o que fecha o furo de hoje, e o arquivo de documento da van segue a mesma regra. O revisor é resolvido pelo `uid` (D7).

O PR 2 entrega essa regra. O PR 5 a troca pela do D10: só um envio `PENDING` muda, e um envio revisado é final. O D11 acrescenta a versão esperada, e o D12 o código de motivo.

### D4 — Rotas da van

| Rota | Quem |
|---|---|
| `GET /api/vehicles/{token}/documents` | dono (`@sec.isVehicleOwner`) ou `list_vehicle_documents` |
| `POST /api/vehicles/{token}/documents` (multipart: `documentType` e `file`) | dono ou `update_vehicle_document` |
| `GET`, `DELETE /api/vehicle-documents/{token}` | dono (`@sec.isVehicleDocumentOwner`) ou `show_`/`delete_vehicle_document` |
| `POST`, `GET /api/vehicle-documents/{token}/file` | dono ou `update_`/`show_vehicle_document` |
| `PUT /api/vehicle-documents/{token}/status` | `update_vehicle_document` (só admin) |
| `POST /api/vehicle-documents/{token}/restore` | `restore_vehicle_document` (só admin) |
| `GET /api/vehicles/{token}/school-transport-credentials` | dono ou `show_vehicle` |

As rotas espelham as de `/api/driver-documents`, que o admin já conhece. O `POST` multipart recebe um `VehicleDocumentRequestDTO` (`documentType` e `file`, `@NotNull` os dois) por `@Valid @ModelAttribute`. O arquivo passa pelas regras de tipo e tamanho do `MediaFileService`, e o envio e o arquivo são gravados na mesma transação. Não há `PUT /api/vehicle-documents/{token}`: o dono não edita dados (D2). O `/api/driver-documents` segue o mesmo formato: `POST` multipart com `documentType` e `file`, sem `PUT /{token}`.

**Permissões:** criar não precisa de permissão para o dono, porque o `@sec` da van já basta. O admin cria e troca o arquivo em nome do motorista pela permissão `update_vehicle_document`, sem uma `create_` própria, já que ninguém mais cria documento de van alheia. As cinco permissões novas (`list_vehicle_documents`, `show_vehicle_document`, `update_vehicle_document`, `delete_vehicle_document`, `restore_vehicle_document`) entram no `PermissionEnum` e só no bundle `ADMIN`, pela `V72`.

**Ownership:** `@sec.isVehicleDocumentOwner(token, authentication)` entra no `SecurityEvaluator` (regra 22), pela consulta `findDriverUserTokenByDocumentToken` do repositório, como o do documento do motorista.

A van removida esconde os documentos dela: as consultas exigem `vehicle.deleted_at is null`, e restaurar a van os traz de volta. Remover a van não remove os documentos, porque a FK e o soft delete da van bastam.

Listar ordena por `expires_at desc, created_at desc`, com os envios pendentes, ainda sem validade, primeiro (`nulls first`), com filtro por `documentType` e `status`, paginado. O slot do arquivo é `MediaSlot.VEHICLE_DOCUMENT_FILE` (`vehicle-document/file`, documento, `PRIVATE`), igual ao `DRIVER_DOCUMENT_FILE`.

### D5 — Credenciais: um serviço, um enum de requisitos

`br.com.vanep.schooltransportcredential`, com o formato que a `contract-generation` já espera:

```
SchoolTransportCredentialService.findCredentials(Long driverId, Long vehicleId, LocalDate date)
  → SchoolTransportCredentialsDTO(List<CredentialDTO> credentials, Set<CredentialRequirement> failed)
CredentialDTO(CredentialRequirement requirement, String number, LocalDate validUntil)
CredentialRequirement: DRIVER_LICENSE, SCHOOL_DRIVER_COURSE, CRIMINAL_RECORD,
                       VEHICLE_REGISTRATION, VEHICLE_INSPECTION, SCHOOL_VEHICLE_AUTHORIZATION,
                       MUNICIPAL_AUTHORIZATION (opcional: nunca entra em failed)
```

A regra de cada requisito (spec `school-transport-credentials`) fica numa `SchoolTransportCredentialPolicy` pura, que recebe as listas já carregadas (CNHs, documentos do motorista, documentos da van) e a data. O serviço faz três consultas, uma por fonte, sem N+1.

Entre vários documentos que satisfazem o requisito, vale o de validade mais distante; sem validade, o mais recente.

**CNH de categoria D ou E:** só as categorias que servem para o transporte escolar (`D`, `E`, `AD`, `AE`) são aceitas, sem espaços nas pontas e em maiúscula. Outra categoria da CNH, como `B`, ou outro texto, como `B EAR`, responde 400 (`driver_cnh.category.invalid`, `field: "category"`).
- **Até o PR 5:** a CNH ainda não é revisada, e a regra fica no `DriverCnhRequestDTO` do motorista.
- **A partir do PR 6:** a categoria passa a ser preenchida pelo revisor na aprovação (D14), com a mesma regra no DTO da decisão. Uma CNH que não é D nem E é recusada com `NOT_ELIGIBLE` (D12).

Uma CNH gravada antes com `A`, `B`, `C`, `AB` ou `AC` continua sendo lida, e a policy a reprova pela regra "contém `D` ou `E`".

O endpoint do motorista (`GET /api/vehicles/{token}/school-transport-credentials`) devolve a mesma avaliação em JSON (`SchoolTransportCredentialsResponseDTO`: cada requisito com `satisfied`, `number` e `validUntil`), com data de hoje na zona da aplicação. Para uma van inativa, responde 422 (`vehicle.inactive`).

**Contrato estável para a VAN-31:** a assinatura de `findCredentials`, o `SchoolTransportCredentialsDTO` e o `CredentialDTO` não mudam nos PRs 4 a 11. Duas coisas mudam no que eles contam:
- o PR 5 deixa de contar envios substituídos (D10);
- o PR 6 passa a exigir a CNH `APPROVED` (D14).

O seed sai com a CNH aprovada, então o QA da `contract-generation` continua passando.

### D6 — A `V72` só muda a estrutura

Em ordem, numa migration só:
1. **Cria `vehicle_document`:** `id`, `token`, `vehicle_id`, `document_type varchar(50)`, `document_number varchar(64)`, `file_media_id`, `expires_at date` (anulável: preenchida na aprovação, D2), as colunas da revisão, `created_at`, `updated_at`, `deleted_at`. Índices: único `token where deleted_at is null` e `(vehicle_id, document_type) where deleted_at is null`.
2. **Acrescenta** `driver_document.document_number varchar(64)`.
3. **Remove logicamente** os `driver_document` `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO`, `VEHICLE_PHOTO`, `RESIDENCE_PROOF` e `OTHER`. Os arquivos continuam no `media_file`.
4. **Remove** a `vehicle.photo_document_media_id` (a FK primeiro), sem converter a foto.
5. **`CHECK`s:**
   - os tipos de cada tabela;
   - o que um envio aprovado precisa ter (D2): a validade na `vehicle_document`, e o número na `SCHOOL_VEHICLE_AUTHORIZATION` e na `MUNICIPAL_AUTHORIZATION`.
6. **Permissões:** as cinco novas no bundle `ADMIN`.
7. **Comments.**

Nenhum dado é movido. Nenhum app chama as rotas de documento nem a da foto de documento da van, o banco do servidor de deploy não tem documentos de motoristas reais, e o seed cria os documentos da van na tabela nova (PR 3). Os documentos dos tipos da pessoa ficam como estão. Uma `MUNICIPAL_AUTHORIZATION` antiga aprovada e sem número quebraria o `CHECK` do número, então a migration a volta a `PENDING`, sem revisor, antes de criar o `CHECK`. O admin a aprova de novo com o número. Só bancos locais têm esse caso.

**Rejeitado: mover o `CRLV` e a vistoria do motorista para a van, e converter a foto de documento da van em `CRLV`.** Seriam umas 60 linhas de SQL, a parte mais arriscada da migration (escolher a van quando o motorista tem várias, decidir se a revisão vale), para preservar dados que só o seed tinha.

### D7 — O chamador pelo `uid`

`DriverDocumentController` e os controllers novos usam `SecurityHelper.requireCallerUid(authentication)` e `UserService.requireByToken`, como o resto da API. O `findByEmail(jwt.getSubject())` sai do `DriverDocumentService`. O `sub` do token é o e-mail que o usuário tinha no login, nos dois grants (senha e Google). Depois de uma troca de e-mail ele não acha mais a conta, e o endereço liberado pode passar a ser de outra conta; o `uid` é o `users.token`, que nunca muda. O mesmo padrão existe em outros cinco lugares (R4). Não mexo neles aqui.

### D8 — Onboarding e aprovação

- **Passo de documentos:** exige `SCHOOL_DRIVER_COURSE` e `CRIMINAL_RECORD` do motorista e, de pelo menos uma van ativa, `CRLV`, `VEHICLE_INSPECTION` e `SCHOOL_VEHICLE_AUTHORIZATION`, todos enviados (todo envio tem arquivo, D2) e não `REJECTED`. A resposta (`DriverOnboardingDocumentsStepDTO`) passa a ter `missingDriverTypes` e `vehicles` (por van ativa: `vehicleToken`, `plate`, `missingTypes`), no lugar do `missingTypes` único.
- **Passo da CNH:** acrescenta `cnh_category`, com a mesma regra do D5.
- **Aprovação:** chama o serviço de credenciais para cada van ativa e exige que alguma passe (422 `driver.onboarding.documents_not_approved`). Assim, todo motorista aprovado consegue oferecer pelo menos uma van no dia da aprovação. O admin revisa os documentos antes de aprovar o motorista, e hoje faz isso pela API (R3).

O PR 1 já leva o onboarding para os documentos da van na tabela nova e para o DTO por van, com o conjunto exigido de antes, porque os tipos da van saem do enum do motorista nele. O PR 3 só troca o conjunto exigido. O PR 7 passa o onboarding e a aprovação para a `DriverCompliancePolicy` (D15), sem mudar o formato do `GET /api/drivers/me/onboarding`.

**Rejeitado: aprovar o motorista aprovando os documentos junto, em cascata.** Esconderia a revisão de cada arquivo atrás de um botão só.

### D9 — Seed

`DriverDocumentSeeder` e um `VehicleDocumentSeeder` novo criam, para os motoristas e as vans do seed, todos os documentos do conjunto legal `APPROVED`, com número nas autorizações e validade **relativa a hoje** (`LocalDate.now().plusMonths(…)`), para nunca vencer num banco local. O seed atual vence em 31/12/2026, e a certidão já venceu. A CNH do seed continua `D`. Conferir com o endpoint de credenciais que a van do seed passa.

A partir do PR 6, a CNH do seed também sai `APPROVED`.

### D10 — Um envio revisado é final: mudar é um envio novo

Cada linha de `driver_document`, `vehicle_document` e `driver_cnh` é um **envio**. A regra vale para as três tabelas e para qualquer chamador, inclusive o admin com permissão de editar:

| Status do envio | arquivo (`/file`) | `DELETE` |
|---|---|---|
| `PENDING` | troca na mesma linha, a revisão continua limpa | remove |
| `APPROVED` ou `REJECTED` | 409 `document.submission.reviewed` | 409 `document.submission.reviewed` para o dono; o admin com `delete_*` remove |

O dono não tem `PUT` de dados (D2): o envio só muda pelo arquivo.

- **Renovar ou reenviar** é um `POST` de um envio novo do mesmo tipo e do mesmo dono, com o arquivo.
- **No máximo um `PENDING` por dono e tipo.** Um segundo `POST` responde 409 `document.submission.pending_exists`. O serviço confere antes, e o banco garante por índice único parcial em `driver_document (driver_id, document_type)`, `vehicle_document (vehicle_id, document_type)` e `driver_cnh (driver_id)`, todos `where deleted_at is null and superseded_at is null and status = 'PENDING'`. A violação do índice numa corrida também vira o 409. O restore de um envio removido segue a mesma regra. Os índices entram na `V76` (PR 5), com o serviço que os cumpre: só com a `V75`, um segundo envio pendente daria 500.
- **Decidir também é mudar:** um `PUT /status` num envio `APPROVED` ou `REJECTED` responde 409 `document.submission.reviewed`. Um envio substituído é final, mesmo se ficou `PENDING` (os duplicados que a `V76` substitui).
- **Mudar o tipo** de um envio `PENDING` é remover o envio e mandar outro do tipo certo.
- **Substituição:**
  - quando um envio é **aprovado**, os outros envios vivos do mesmo dono e tipo ganham `superseded_at`;
  - quando chega um envio novo, o `REJECTED` vivo do mesmo dono e tipo ganha `superseded_at` na mesma transação.

  Assim, em cada dono e tipo, ficam vivos no máximo o aprovado que vale hoje e um envio novo, pendente ou recusado.
- **Envio substituído:**
  - perde o arquivo: `file_media_id` fica nulo e o `media_file` é removido;
  - o objeto é apagado do storage **depois do commit**;
  - a linha fica só com a decisão: status, código, mensagem, revisor e datas;
  - não aparece nas listagens, não conta nas credenciais e não muda mais;
  - é lido só pela página do admin, como a "última decisão" (D17).
- **Arquivo depois do commit:** o `MediaFileService.discard` passa a remover o `media_file` na transação e a apagar o objeto por `@TransactionalEventListener(AFTER_COMMIT)`, com um evento `MediaDiscardedEvent(objectKey)`. A troca de arquivo de um envio `PENDING` e todos os outros usos do `discard` (fotos) ganham o mesmo efeito. Um rollback não apaga mais um arquivo que continua referenciado.

**Por que `superseded_at` e não soft delete:**
- os três documentos têm `restore`, e o da CNH é do próprio dono. Restaurar um envio substituído traria de volta uma aprovação sem arquivo;
- "removido pelo dono" e "substituído por um envio novo" são fatos diferentes;
- ler a última decisão de uma linha com `@SoftDelete` exige SQL nativo.

**Rejeitado: manter o PR 2 (toda mudança volta a `PENDING`).** Quem renova uma vistoria antes de vencer fica sem a credencial, e sem poder mandar proposta, até o admin revisar. A `SchoolTransportCredentialPolicy` já escolhe o melhor entre vários envios, então o envio novo convive com o aprovado sem regra nova.

### D11 — Concorrência: versão no envio e trava no motorista

**Envios:**
- `@Version` (`version bigint not null default 0`) em `driver_document`, `vehicle_document` e `driver_cnh`.
- As respostas trazem `version`, e o `DocumentStatusUpdateRequestDTO` passa a exigir `version` (`@NotNull`).
- O serviço compara a versão pedida com a da linha antes de decidir. Se for diferente, responde 409 `document.review.stale`.
- Entre a leitura e o commit, o próprio `@Version` garante: o `ApiErrorAdvice` traduz `ObjectOptimisticLockingFailureException` no mesmo 409.
- Isso cobre dois admins no mesmo envio e um admin aprovando enquanto o dono troca o arquivo de um envio `PENDING`, já que a troca muda a versão. Os dados que o admin preencheu olhando o arquivo antigo nunca são gravados no arquivo novo.

**Corpo, não `If-Match`:** o `PUT /status` já tem corpo JSON. O `If-Match` exigiria `ETag` em todo `GET` de documento.

**Motorista, sem `@Version`:**
- O `driver` é gravado também pela média de avaliações (`DriverRatingService`), pelo perfil e pela foto. Com `@Version`, uma avaliação gravada durante a aprovação falharia por um motivo que não tem a ver com a revisão.
- `approve` e `reject` leem o motorista com `PESSIMISTIC_WRITE` (`DriverRepository.findByTokenForUpdate`), conferem `UNDER_REVIEW` e, no `approve`, **recalculam** a situação pela policy (D15) na mesma transação.
- Dois admins ficam em fila na trava. O segundo recebe o 400 `driver.onboarding.not_under_review` de hoje.
- A aprovação nunca usa uma situação velha, porque é recalculada do banco.

### D12 — Motivo estruturado

- **Documentos:** `shared.document.enums.DocumentRejectionReason` com `ILLEGIBLE`, `EXPIRED`, `DATA_MISMATCH`, `WRONG_TYPE`, `INCOMPLETE`, `NOT_ELIGIBLE` e `OTHER`. O `NOT_ELIGIBLE` é o documento verdadeiro que não serve para o transporte escolar, como uma CNH sem `D` nem `E`. Como o motorista não declara mais a categoria (D2), é por esse código que ele fica sabendo.
  - `rejection_reason_code varchar(30)` nas três tabelas, dentro do `DocumentReview` como `rejectionReasonCode`.
  - O `rejection_reason` atual vira a mensagem opcional (até 255 caracteres).
  - A `DocumentReviewPolicy` passa a exigir `rejectionReasonCode` para `REJECTED` (400 `document.review.reason_code_required`) e a mensagem só com `OTHER` (400 `document.review.reason_required`, a chave de hoje).
  - Uma mensagem em branco num código que não é `OTHER` é gravada como nula.
  - No banco: a `V75` cria a coluna, a `CHECK` dos valores e preenche `OTHER` nos recusados de antes. A `CHECK (status <> 'REJECTED' or rejection_reason_code is not null)` entra na `V76` (PR 5), que antes preenche `OTHER` nos recusados sem código: a recusa do PR 4 ainda não manda código e daria 500.
- **Motorista:** um enum próprio, `driver.enums.DriverRejectionReason`, porque recusar o cadastro não é recusar um arquivo: os problemas de arquivo já são recusados no documento, e a aprovação exige os documentos aprovados (D8).
  - Valores: `DATA_MISMATCH` (o cadastro não confere com os documentos), `NOT_ELIGIBLE` (não atende ao CTB 138, por exemplo idade ou infrações), `DUPLICATE_ACCOUNT` e `OTHER`.
  - `driver.rejection_reason_code varchar(30)` com `CHECK`, e `OTHER` nos recusados de antes.
  - O `DriverRejectionRequestDTO` ganha `reasonCode` (`@NotNull`). O `reason` fica obrigatório só com `OTHER` (400 `driver.rejection.reason.required`, a chave de hoje).
  - O `GET /api/drivers/me/onboarding` ganha `rejectionReasonCode`. Quando a recusa não tem mensagem, `rejectionReason` traz o texto do código (`driver.rejection_reason.*`), para o app que só lê `rejectionReason` continuar mostrando o motivo. O reenvio do cadastro limpa os dois.

### D13 — O revisor sem consulta extra

Toda consulta que monta uma resposta com revisor faz `left join fetch document.review.reviewedBy` (ou o `@EntityGraph` equivalente). As consultas paginadas ganham `countQuery` próprio.

O `reviewedBy` continua `EAGER`: o Hibernate 7 não aceita `LAZY` num to-one dentro de um `@Embeddable` quando a entidade de destino tem `@SoftDelete`, e o `UserModel` tem. Mesmo que aceitasse, o `@SoftDelete` faria o select do revisor de qualquer jeito. O que tira esse select é o `join fetch`, e os testes com `SqlStatementCounter` das listagens são a garantia (regra 17).

### D14 — CNH com revisão

- A `driver_cnh` ganha o `DocumentReview` (as mesmas colunas e o código), `version`, `superseded_at` e `notified_at`. Ela continua uma tabela própria, com a estrutura de CNH.
- **O arquivo da CNH aceita PDF.** A CNH digital é um PDF, então o arquivo segue o slot dos documentos (JPEG, PNG, WebP ou PDF), e o nome deixa de ser "foto":
  - a `V77` renomeia `driver_cnh.photo_media_id` para `file_media_id`, e a FK junto;
  - o slot `DRIVER_CNH_PHOTO` vira `DRIVER_CNH_FILE` (`driver-cnh/file`, documento, `PRIVATE`), e os objetos já gravados continuam no `object_key` que têm;
  - as rotas `POST`/`GET /api/driver-cnhs/{token}/photo` viram `/file`, e o `photoUrl` da resposta vira `fileUrl`. O `photoUrl` do corpo, que nada lia, sai.

  Aprovar sem arquivo dá 422 `document.review.file_required`.
- **O motorista manda só o arquivo (D2):**
  - `POST /api/driver-cnhs` passa a ser multipart, só com o `file` (`DriverCnhRequestDTO`). O `driverToken` só vale para o admin, que manda em nome do motorista;
  - o `PUT /api/driver-cnhs/{token}` sai;
  - a CNH `PENDING` tem arquivo, e o registro, a categoria e as datas ficam nulos.
- **Os dados entram na aprovação.** `PUT /api/driver-cnhs/{token}/status` exige `review_documents` e passa pelo mesmo `DocumentReviewService`. O corpo é o `DriverCnhStatusUpdateRequestDTO`: os campos da decisão (D11, D12) e, para `APPROVED`, os dados da CNH:

  | Campo | Na aprovação |
  |---|---|
  | `registrationNumber` | obrigatório, 9 a 11 dígitos |
  | `category` | obrigatória, `D`, `E`, `AD` ou `AE` (D5) |
  | `issueDate` | obrigatória, não futura (D21) |
  | `validUntil` | obrigatória, de hoje até a emissão + 10 anos (D21) |
  | `firstLicenseDate`, `securityNumber`, `issuingState` | opcionais |

  - O formato de cada campo é conferido por Bean Validation, como antes no `DriverCnhRequestDTO`. A falta de um campo obrigatório na aprovação dá 400 `document.review.field_required`, com o `field`.
  - O número de outro motorista dá 409 `driver_cnh.registration.duplicate`, agora na aprovação.
- A CNH segue o D10:
  - o `POST /file` só muda uma CNH `PENDING`;
  - renovar é um `POST /api/driver-cnhs` novo, com o arquivo;
  - a aprovação substitui a anterior.

  O `create` deixa de recusar a segunda CNH do motorista: passa a recusar só um segundo `PENDING`, com 409 `document.submission.pending_exists`.
- **No banco, na `V77`:**
  - `registration_number`, `category`, `issue_date` e `valid_until` deixam de ser `NOT NULL`;
  - entra o `CHECK (status <> 'APPROVED' or (registration_number is not null and category is not null and issue_date is not null and valid_until is not null))`;
  - a constraint de exclusão do número ignora os nulos, porque `null = null` não é verdadeiro.
- **Índices da V17:**
  - sai o `driver_cnh_driver_active_key`, e entra o único parcial de um `PENDING` por motorista (D10);
  - sai o `driver_cnh_registration_active_key`. Na renovação, o número **continua o mesmo**, então a nova e a antiga convivem.
  - entra `exclude using gist (registration_number with =, driver_id with <>) where (deleted_at is null)` (extensão `btree_gist`): o mesmo número nunca pertence a dois motoristas, e o mesmo motorista pode repetir o número.
  - O serviço confere na aprovação, com 409 `driver_cnh.registration.duplicate` (a chave de hoje), e traduz a violação da constraint numa corrida.
- `SchoolTransportCredentialPolicy.findDriverLicense` passa a exigir `APPROVED` e a ignorar as substituídas.
- **Onboarding:** uma CNH `PENDING` conta como enviada. Uma `REJECTED` dá o pendente `cnh_rejected`, com a mensagem `driver.onboarding.cnh_rejected`. Como toda CNH enviada tem arquivo, o pendente "CNH sem arquivo" não existe.
- **Corrida pelo número:** quando dois admins aprovam ao mesmo tempo CNHs de motoristas diferentes com o mesmo número novo, a constraint de exclusão faz uma transação esperar pela outra, e o PostgreSQL aborta uma delas com deadlock (`40P01`) em vez da violação. O serviço traduz esse deadlock, só ao aprovar ou restaurar a CNH, no mesmo 409 `driver_cnh.registration.duplicate`: a transação abortada é a que perdeu o número.
- **CNHs que já existem:** a `V75` as deixa `PENDING`, com os dados que o motorista digitou. Na aprovação, o admin preenche os dados de novo olhando o arquivo (R11). O seed aprova as dele.

**Rejeitado: tirar a unicidade do número da tabela e conferir só no serviço.** Dois motoristas cadastrando o mesmo número ao mesmo tempo passariam. A constraint de exclusão diz exatamente a regra.

### D15 — Uma policy da situação do motorista

Pacote novo `br.com.vanep.drivercompliance`.

A `DriverCompliancePolicy` é pura. Ela **compõe** a `SchoolTransportCredentialPolicy`, que continua decidindo o que satisfaz cada requisito, e acrescenta o estado de cada requisito e as decisões da tela.

**Entrada** (`DriverComplianceInputDTO`):
- o `approvalStatus`;
- as CNHs, os documentos do motorista e os documentos de cada van ativa, só os envios vivos e não substituídos;
- a data.

**Saída** (`DriverComplianceDTO`):

- **Requisitos:** `driverRequirements` e, por van ativa, `vehicles[].requirements`. Cada item tem:
  - `requirement` e `optional`;
  - `satisfied`, igual ao da credencial;
  - `state`;
  - `current`: o envio que conta. É o que satisfaz; se nenhum satisfaz, o envio vivo mais recente;
  - `pending`: um envio `PENDING` além do `current`, quando é uma renovação em revisão.
- **`state`** (`ComplianceState`):
  - `APPROVED` quando há um envio que satisfaz;
  - senão, `PENDING`, `REJECTED` ou `EXPIRED` (aprovado mas vencido na data), conforme o envio vivo mais recente. Um envio `PENDING` não tem validade (D2), então só um aprovado vence;
  - `MISSING` sem envio.

  Uma CNH aprovada e válida sem `D` nem `E` tem `state = APPROVED`, `satisfied = false` e o bloqueio `CNH_CATEGORY`. O bloqueio vale também para uma CNH `PENDING` gravada antes do PR 6 com uma categoria sem `D` nem `E`, que nunca vai satisfazer o requisito. A CNH enviada a partir do PR 6 não tem categoria até a aprovação.
- **`qualifiedVehicles`:** as vans ativas com todos os requisitos satisfeitos.
- **`canApprove`:** `approvalStatus = UNDER_REVIEW`, os requisitos obrigatórios do motorista satisfeitos e pelo menos uma van qualificada. Basta uma van.
- **`blockers`:** só quando `canApprove = false`. Cada item tem `kind` (`APPROVAL_STATUS`, `NO_ACTIVE_VEHICLE`, `DRIVER_REQUIREMENT`, `VEHICLE_REQUIREMENT`, `CNH_CATEGORY`), `requirement`, `state`, `vehicleToken` e `plate`.
  - Os requisitos do motorista que faltam.
  - Se nenhuma van se qualifica, os requisitos que faltam na van mais perto de completar: a com menos requisitos insatisfeitos, e no empate a mais antiga.
- **`nextAction`**, nesta ordem:
  1. `READY_TO_APPROVE` quando `canApprove`;
  2. `ADMIN` quando há um envio revisável do motorista ou das vans ativas: todo envio `PENDING`, que sempre tem arquivo (D2);
  3. `DRIVER` quando o cadastro está `PENDING` ou `REJECTED`, ou está `UNDER_REVIEW` sem poder aprovar, ou está `APPROVED` com requisito do motorista insatisfeito ou sem van qualificada;
  4. `NONE` nos outros casos.
- **Contagens:** `pendingReviewCount` e `oldestPendingAt` (o `created_at` do envio revisável mais antigo), só dos envios revisáveis, e `rejectedCount`, `missingCount` e `expiredCount` (requisitos obrigatórios por estado, do motorista e das vans ativas).
- **`nextExpiryOn`:** a menor `expiresAt` entre os envios `APPROVED` que são o `current` de um requisito obrigatório, vencidos ou não. Uma data passada quer dizer vencido. Um envio substituído não conta, então um vencido já renovado e aprovado sai. O alvará municipal (opcional) não entra, como nas contagens.

**`DriverComplianceService`:**
- `evaluateAll(drivers, date)` carrega a entrada de vários motoristas de uma vez, em consultas constantes: CNHs e documentos do motorista por `driver_id in (…)`, vans ativas por `driver_id in (…)` e documentos das vans por `vehicle_id in (…)`, com `join fetch` de arquivo e revisor (D13). Devolve a avaliação de cada motorista;
- `evaluate(driver, date)` é o mesmo caminho com um motorista só.

O mesmo carregamento serve à aprovação, ao onboarding, à fila (D16) e à página (D17): não há um segundo jeito de montar a entrada.

**Data:** o `SchoolTransportCredentialService.today()` do PR 3, já no fuso de São Paulo (`TripService.SERVICE_ZONE`). A policy recebe a data como parâmetro, então os testes fixam o dia passando a data, sem `Clock` injetado. Isso segue o resto do projeto, que usa `now()` direto.

**Quem usa a policy:**
- **`approve`:** trava o motorista (D11), avalia com `evaluate` e responde 422 `driver.onboarding.documents_not_approved` com os `blockers` no `ProblemDetail` (propriedade `blockers`).
- **`GET /api/drivers/me/onboarding` e o `submit-onboarding`:** um requisito obrigatório conta como enviado em `APPROVED` ou em `PENDING`, como no PR 3. No passo da CNH, `MISSING` dá `cnh`, `REJECTED` dá `cnh_rejected`, `EXPIRED` (aprovada e vencida) dá `cnh_expired`, e o bloqueio `CNH_CATEGORY` dá `cnh_category`. `MISSING`, `REJECTED` e `EXPIRED` vão para `missingDriverTypes` e para os `missingTypes` da van. O formato do DTO não muda.

Os `evaluate*Step` de documentos e de CNH do `DriverOnboardingService` e o `SchoolTransportCredentialService.hasAnyQualifiedVehicle` saem.

### D16 — A fila calculada na leitura (risco aceito)

As abas, as contagens e o badge dependem da situação de cada motorista, e só a `DriverCompliancePolicy` sabe calculá-la. A fila calcula essa situação a cada leitura, em memória, sem tabela nova e sem job:

1. **Candidatos:** uma consulta de motoristas não removidos, com a busca `q` (D17) e, na aba `AWAITING_DECISION`, `approval_status = UNDER_REVIEW`. Só vão para o SQL os filtros que são colunas do motorista ou do usuário; nada que dependa da situação.
2. **Avaliação em lote:** `DriverComplianceService.evaluateAll(candidatos, hoje)` (D15), em consultas constantes para qualquer número de motoristas.
3. **Aba, ordem e página:** o `DriverComplianceView` é puro. Para cada aba, ele diz se um motorista entra, a partir do cadastro e da avaliação dele e da data, e em que ordem. O serviço filtra, ordena, corta a página e devolve um `PageImpl` com o total.
4. **Linha:** cidade e número de vans só dos motoristas da página, em lote (D17).

O `/counts` avalia todos os motoristas uma vez e conta cada aba com o mesmo `DriverComplianceView`.

**A fila está sempre certa.** Ela lê os envios e a data no momento do pedido: um documento que vence à meia-noite aparece como vencido na primeira leitura do dia seguinte, sem ninguém gravar nada.

**Desacoplamento:**
- o `DriverComplianceQueueService` é o único que sabe que a fila é calculada em memória;
- o controller, os DTOs da resposta, a policy e o `DriverComplianceView` não sabem;
- guardar o resultado numa tabela um dia muda só esse serviço, sem mudar a resposta da API nem a regra.

**Comentário no código:** o Javadoc da classe `DriverComplianceQueueService` tem um parágrafo curto que registra a decisão, para quem abrir o arquivo saber que é uma escolha aceita, e não um descuido. Não é um TODO. A regra 51 aceita comentário para "uma escolha deliberada cuja alternativa parece correta", e a tabela calculada parece a escolha óbvia para uma fila:

```java
/**
 * Accepted decision: every request evaluates the candidate drivers with DriverCompliancePolicy
 * and pages in memory, so the rule lives in one place and no table has to follow every write.
 * The cost grows with the number of drivers; storing the evaluation per driver changes only this class.
 */
```

**Busca sem índice:** o `like` de nome, e-mail e placa roda sem índice trigram e sem extensão nova, no mesmo risco aceito (R10).

**Adiado: guardar o resultado numa tabela (`driver_compliance`).** A fila leria filtro e ordem do banco, mas a linha de cada motorista teria de ser recalculada:
- em cada um dos cerca de oito caminhos de escrita (envios, vans, cadastro);
- e todo dia, por um job, para os documentos que vencem sem ninguém mexer.

Um caminho que esquecer de recalcular mostraria dado errado na fila. Fica para quando a fila ficar lenta (R10).

**Rejeitado: calcular as abas com SQL.** Duplicaria a regra de vencimento, de substituição e de "uma van basta" numa segunda linguagem.

### D17 — Rotas de leitura do admin

Todas com `@PreAuthorize("hasAuthority('review_documents') or hasAuthority('approve_driver')")`.

**a) `GET /api/drivers/compliance?view=&q=&page=&size=`**

Paginado (`Page` do Spring), calculado como no D16.

| `view` (`DriverComplianceView`) | Filtro | Ordem |
|---|---|---|
| `AWAITING_DECISION` | `approvalStatus = UNDER_REVIEW` | `nextAction` em `READY_TO_APPROVE` ou `ADMIN` primeiro, `DRIVER` no fim; dentro de cada grupo, `submittedAt` asc |
| `PENDING_DOCUMENTS` | `pendingReviewCount > 0`, qualquer status de cadastro | `oldestPendingAt` asc |
| `EXPIRING` | `expiredCount > 0` ou `nextExpiryOn <= hoje + 30` | `nextExpiryOn` asc, nulos no fim |
| `ALL` (padrão) | nenhum | `createdAt` desc do motorista |

Os empates terminam pelo `id` do motorista, para a página ser estável entre dois pedidos.

- **Linha** (`DriverComplianceRowResponseDTO`):
  - identificação: `token`, `name`, `email`, `city`, `vehicleCount`;
  - cadastro: `approvalStatus`, `submittedAt`, `createdAt` (a data do cadastro, que ordena a aba `ALL`);
  - situação: `nextAction`, `canApprove`, `pendingReviewCount`, `oldestPendingAt`, `rejectedCount`, `missingCount`, `expiredCount`, `nextExpiryOn`.

  Sem CPF e sem telefone (minimização, LGPD).
- **Cidade e número de vans:** em lote só para os motoristas da página, com duas consultas `in (…)`. A cidade sai do endereço pessoal (`users.address_id`).
- **Busca `q`:** nome, e-mail ou placa de uma van ativa, sem diferenciar maiúsculas, por `like` com `%`, na consulta dos candidatos (D16). Sem índice trigram (R10).
  - **Placa normalizada:** sem hífen nem espaço, maiúscula. O `VehicleService` normaliza ao gravar, e a busca compara a placa gravada com o `q` normalizado. Isso também fecha a brecha de `ABC-1D23` e `ABC1D23` passarem como placas diferentes.
  - `%`, `_` e `\` no `q` valem como texto, não como curinga. Um `q` que fica vazio depois de normalizado como placa (só hífens ou espaços) não filtra por placa.

**b) `GET /api/drivers/compliance/counts`** (`DriverComplianceCountsResponseDTO`)

- `awaitingDecision`, `pendingDocuments`, `expiring` e `all`: o total de cada aba, com os mesmos filtros (é o `totalElements` da aba sem busca).
- `awaitingDriver`: dos motoristas de `AWAITING_DECISION`, os com `nextAction = DRIVER`. A tela mostra "esperando você" como `awaitingDecision - awaitingDriver`.
- `badge`: os motoristas com `nextAction` em `READY_TO_APPROVE` ou `ADMIN`.
- `asOf`: a data de hoje em São Paulo, para a lista calcular "vence em X dias" como a página.

Tudo sai de uma avaliação de todos os motoristas, sem busca (D16).

**c) `GET /api/drivers/{token}/compliance`** (`DriverComplianceDetailResponseDTO`)

O agregado da página do motorista e da revisão:

- `asOf`: a data de hoje no fuso de São Paulo, para o front calcular "vence em X dias" no fuso certo.
- **`driver`:**
  - identificação: `token`, `name`, `email`, `phone`, `document` (CPF), `birthDate`, `city`;
  - perfil: `experienceYears`, `createdAt`;
  - cadastro: `approvalStatus`, `submittedAt`, `rejectionReasonCode`, `rejectionReason`, `reviewedAt` e `reviewedBy` (`{token, name}`).
- **`decision`:** `canApprove`, `nextAction`, `pendingReviewCount` e `blockers`. Os `blockers` têm o mesmo formato dos do 422 do `approve` e trazem só a van mais perto de completar (D15): a escolha é da policy, e o front não precisa refazê-la. A situação de todas as vans está em `vehicles[].requirements`.
- **`driverRequirements[]` e `vehicles[].requirements[]`:**
  - cada item: `requirement`, `optional`, `satisfied`, `state`, `current`, `pending` e `lastDecision`;
  - a correção dos dados (D25): `dataCorrectedAt` e `dataCorrectedBy` (`{token, name}`) no envio, com os nomes numa consulta só;
  - envio (`SubmissionResponseDTO`):
    - `kind` (`DRIVER_DOCUMENT`, `VEHICLE_DOCUMENT`, `DRIVER_CNH`), `token`, `documentNumber`, `expiresAt`. Os dois são nulos enquanto o envio está `PENDING` (D2). Com o arquivo aberto, o front mostra o formulário do tipo, com os obrigatórios da tabela do D2, e a API confere na aprovação;
    - revisão: `status`, `reviewMethod`, `rejectionReasonCode`, `rejectionReason`, `reviewedBy` (`{token, name}`), `reviewedAt`;
    - `createdAt`, `version`;
    - arquivo: `fileUrl` e `file` (`{mimeType, sizeBytes, originalName}`);
  - a CNH traz também `category`, `issueDate`, o `registrationNumber` inteiro, `firstLicenseDate`, `securityNumber` e `issuingState`, nulos enquanto `PENDING`. São os dados que o revisor digitou: a correção (D25) parte deles e substitui todos, então a página os mostra inteiros, como já mostra o CPF. A linha do checklist mascara o número no front; Na CNH, `expiresAt` é a `validUntil`, e o `fileUrl` é o arquivo (`/api/driver-cnhs/{token}/file?v=…`);
  - todo `fileUrl` é relativo à API (`/api/…?v=<token da mídia>`), montado pelo `MediaUrl`, nunca uma URL absoluta;
  - `lastDecision`: a decisão mais recente desse requisito que não é a do `current` nem a do `pending`, sem arquivo (`status`, `rejectionReasonCode`, `rejectionReason`, `reviewedBy`, `reviewedAt`, `supersededAt`). Vem de um envio substituído ou de uma recusa ainda não substituída ao lado do aprovado que vale.
  - envios e CNHs inativos não entram em nenhum requisito nem no `history`.
- **`vehicles[]`:** só as vans ativas e não removidas: `token`, `plate`, `brand`, `model`, `year`, `capacity`, `qualified` e `requirements[]`.
- **`reviewQueue[]`:** a ordem do "pendente 1 de N", com `{kind, token, requirement, vehicleToken}`. Segue a ordem do checklist: requisitos do motorista na ordem do `CredentialRequirement`, depois as vans na ordem de `vehicles[]`.
- **`history[]`:** os últimos 50 eventos, do mais novo ao mais antigo, montados das datas que já existem.
  - Cada item tem `{at, kind, requirement, vehicleToken, plate, actor, rejectionReasonCode}`.
  - `actor` é `{token, name}` de quem decidiu nas aprovações e recusas (do envio ou do cadastro), e `null` em `SUBMISSION_SENT` e `ONBOARDING_SUBMITTED`, que são do motorista. A API não grava quem criou um envio: um envio criado pelo admin em nome do motorista aparece como do motorista.
  - `kind` pode ser `SUBMISSION_SENT`, `SUBMISSION_APPROVED`, `SUBMISSION_REJECTED`, `SUBMISSION_DATA_CORRECTED` (com quem corrigiu como `actor`), `ONBOARDING_SUBMITTED`, `DRIVER_APPROVED` ou `DRIVER_REJECTED`.
  - Os envios vêm do `created_at` e do `reviewed_at`, inclusive dos substituídos. O cadastro vem do `submittedAt` e do `reviewedAt`, que guardam só o último envio e a última decisão (R14).

**Consultas da página:** usuário com endereço, CNHs, documentos do motorista, vans ativas, documentos das vans e as últimas decisões (uma consulta por tabela). Arquivo e revisor vêm por `join fetch`. O número de consultas é constante, conferido com `SqlStatementCounter`.

**d) Escrita:**
- os `PUT …/status` de documento do motorista, da van e da CNH passam a exigir `review_documents`, no lugar do `update_*_document`, e recebem `rejectionReasonCode` e `version` (D11, D12) e, na aprovação, os dados do tipo (D2, D14);
- `approve` e `reject` continuam com `approve_driver`.
- As duas permissões vão para o bundle `ADMIN`: `review_documents` é nova, pela `V75`, e `approve_driver` já está lá (V48).

**e) Cache do arquivo:** o `MediaResponder` manda `Cache-Control: private, max-age=86400, immutable` quando o `v` da URL é o token da mídia atual, e `private, no-cache` nos outros casos. Quando o storage responde com redirect para uma URL assinada (válida por 5 minutos), o redirect vai sempre com `private, no-cache`. Isso vale para todas as rotas de arquivo, porque a URL de um `v` nunca muda de conteúdo (PR 5).

### D18 — E-mails depois do commit

- `approve` e `reject` publicam `DriverReviewEvent.Approved` e `DriverReviewEvent.Rejected`. O `DriverNotificationService` passa a mandar por `@TransactionalEventListener(AFTER_COMMIT)`, como o `NegotiationEmailService`. Um rollback não manda mais e-mail de uma decisão que não ficou gravada.
- **Envio recusado:** o `DocumentReviewService` publica `DocumentRejectedEvent(kind, token)`.
  - Um `DocumentNotificationService` manda o template novo `document-rejected.html` (no padrão do `driver-rejected.html`) com o nome do documento, a placa quando é da van, o motivo do código e a mensagem.
  - Depois de mandar, grava `notified_at` numa transação nova (`REQUIRES_NEW`). Uma falha de envio fica no log, com o `notified_at` nulo e sem nova tentativa. Para isso, o `MailService.send` passa a dizer se o e-mail saiu; com o envio desligado, ele não sai e o `notified_at` fica nulo.
  - A `V75` cria o `notified_at` na `vehicle_document` e na `driver_cnh`. A `driver_document` já tem a coluna.
- Os textos ficam em `messages*.properties`: `email.document_rejected.*`, os rótulos `document.rejection_reason.*` e `driver.rejection_reason.*`, e o nome de cada tipo de documento.
- **O que o motorista lê no e-mail, por motivo** (`document.rejection_reason.*` em pt-BR). O front copia os mesmos textos na prévia da recusa, então uma mudança aqui é avisada a ele:

  | Código | Texto |
  |---|---|
  | `ILLEGIBLE` | Não conseguimos ler o documento. Envie uma foto ou PDF nítido, com todas as informações visíveis. |
  | `EXPIRED` | O documento está vencido. Envie a versão válida. |
  | `DATA_MISMATCH` | Os dados do documento não conferem com o seu cadastro. |
  | `WRONG_TYPE` | O arquivo enviado não é o documento pedido. |
  | `INCOMPLETE` | O documento está incompleto. Envie todas as páginas ou os dois lados. |
  | `NOT_ELIGIBLE` | O documento não atende ao que o transporte escolar exige (por exemplo, a CNH precisa ser categoria D ou E). |
  | `OTHER` | A mensagem escrita pelo admin. |

  Nos outros códigos, a mensagem do admin, quando houver, vem abaixo do texto.
- O aviso de recusa é só por e-mail; não há notificação no app.

### D19 — Permissão de revisar separada da de editar

- `review_documents`: aprovar e recusar envio do motorista, da van e da CNH, e ler a fila e a página.
- `approve_driver` (já existe): aprovar e recusar o cadastro, e ler a fila e a página.

As duas vão no `PermissionEnum` e no bundle `ADMIN`. As `update_*_document` continuam valendo para criar um envio e trocar o arquivo em nome do motorista, sem revisar.

### D20 — Erros com código estável

Os erros que esta change cria, ou cuja regra ela muda, levam a propriedade `code` no `ProblemDetail`. O front e o mobile decidem pelo `code`, nunca pelo texto:

```json
{ "status": 409, "detail": "Este envio mudou desde que você abriu.", "code": "document.review.stale" }
```

- **`code`** é a chave da mensagem no `messages*.properties`, no formato que a API já usa em `location`, `cep`, `address` e `dependent`. Não muda com o idioma, e renomear a chave quebra os clientes.
- **`detail`** é o texto da chave em pt-BR (o idioma da API é fixo desde a VAN-77), só para mostrar.
- **`shared.error.ApiProblemException`** (PR 5) estende a `ErrorResponseException` do Spring:
  - recebe o status, a chave e os argumentos;
  - põe a chave em `code`;
  - deixa o `ResponseEntityExceptionHandler` (base do `ApiErrorAdvice`) traduzir o `detail` pela mesma chave;
  - aceita propriedades extras, como os `blockers` do 422.
- Os serviços desta change lançam `ApiProblemException` no lugar do `ResponseStatusException` com texto já traduzido. Os erros antigos não mudam aqui. A padronização do resto da API, do mobile e do admin é a VAN-89.
- Os 400 de validação do corpo (`@NotNull`, `@Size`) continuam no formato de hoje, sem `code`.

| Status | `code` | Quando |
|---|---|---|
| 400 | `document.review.reason_code_required` | recusa sem código |
| 400 | `document.review.reason_required` | `OTHER` sem mensagem |
| 400 | `document.review.field_required` | aprovar sem um dado obrigatório do tipo, com o `field` (D2, D14) |
| 422 | `document.review.file_required` | aprovar sem arquivo (na CNH, sem foto) |
| 409 | `document.review.stale` | `version` diferente da atual |
| 409 | `document.submission.reviewed` | mudar ou remover (dono) um envio já decidido |
| 409 | `document.submission.pending_exists` | segundo `PENDING` do mesmo dono e tipo, ou restore que criaria um |
| 409 | `driver_cnh.registration.duplicate` | número de CNH de outro motorista |
| 422 | `driver.onboarding.documents_not_approved` | `approve` sem as condições, com `blockers` |
| 400 | `driver.onboarding.not_under_review` | `approve` ou `reject` fora de `UNDER_REVIEW` |
| 400 | `driver.rejection.reason.required` | `reject` com `OTHER` sem `reason` |
| 409 | `vehicle.plate.duplicate` | placa já usada, comparada normalizada |
| 409 | `document.data.not_approved` | corrigir os dados de um envio que não está aprovado, ou que foi substituído (D25) |
| 400 | `document.expires_at.past` | validade anterior a hoje (D21) |
| 400 | `document.expires_at.too_far` | validade depois do limite do tipo (D21) |
| 400 | `driver_cnh.issue_date.future` | emissão da CNH depois de hoje (D21) |
| 413 | `media.file.too_large` | arquivo acima do limite do tipo, ou requisição acima do limite de transporte (D24) |

### D21 — Datas dentro dos limites

A validade e a emissão são conferidas na aprovação, onde o revisor as preenche (D2). É uma regra de código, sem coluna nem `CHECK`. O restore não confere as datas de novo, porque não recebe datas novas. "Hoje" é o `SchoolTransportCredentialService.today()`, no fuso de São Paulo, como na policy.

Os limites de cima protegem contra erro de digitação do revisor. Eles não são regra de lei. O limite de baixo impede aprovar um documento já vencido: o caminho é recusar com `EXPIRED`.

| Tipo | Validade mínima | Validade máxima |
|---|---|---|
| `CRLV` | hoje | 31/12 do ano que vem |
| `VEHICLE_INSPECTION` | hoje | hoje + 7 meses |
| `SCHOOL_VEHICLE_AUTHORIZATION` | hoje | hoje + 2 anos |
| `SCHOOL_DRIVER_COURSE` | hoje | hoje + 5 anos |
| `MUNICIPAL_AUTHORIZATION` | hoje | hoje + 5 anos |
| `CRIMINAL_RECORD` | hoje | sem limite |
| CNH | hoje | emissão + 10 anos |

- **Validade opcional:** a do curso, a do alvará e a da certidão continuam opcionais (D2). Sem validade, não há o que conferir.
- **CRLV:** o Detran de cada estado define o mês pelo final da placa, e todos caem antes do fim do ano seguinte. Por isso o limite é 31/12, sem guardar o exercício.
- **CNH:** a emissão não pode ser depois de hoje. Como a validade não pode ser anterior a hoje, ela nunca fica antes da emissão.
- **Onde fica a regra:**
  - cada enum de tipo diz a validade máxima numa data (`latestExpiryOn(today)`, nula quando não há limite);
  - a `shared.document.service.DocumentDatesPolicy`, pura, confere a validade contra hoje e contra esse limite;
  - a CNH usa a mesma conferência, com o limite tirado da emissão.
- **Erros:** 400 pela `ApiProblemException`, na aprovação, sem gravar nada. A propriedade `field` diz o campo (`expiresAt`, `validUntil` ou `issueDate`), como nos 400 de validação.

| `code` | `field` | Quando |
|---|---|---|
| `document.expires_at.past` | `expiresAt`, ou `validUntil` na CNH | validade anterior a hoje |
| `document.expires_at.too_far` | `expiresAt`, ou `validUntil` na CNH | validade depois do limite |
| `driver_cnh.issue_date.future` | `issueDate` | emissão depois de hoje |

Os documentos já aprovados não são conferidos de novo. Um envio que vence depois de aprovado aparece como vencido (`EXPIRED`, D15, e `cnh_expired`).

### D22 — O arquivo nas respostas

`DriverDocumentResponseDTO`, `VehicleDocumentResponseDTO` e `DriverCnhResponseDTO` trazem `file`, com `mimeType`, `sizeBytes` e `originalName`, nulo sem arquivo. O app mostra o nome e o tamanho do que foi enviado sem baixar o arquivo.

O record é o `shared.document.dto.DocumentFileResponseDTO` (PR 2). A CNH (PR 6) e o `SubmissionResponseDTO` da página do admin (PR 10) usam o mesmo.

### D23 — A situação do próprio motorista

`GET /api/drivers/me/compliance` (PR 11) mostra ao motorista o estado de cada requisito numa chamada. Sem ela, o app faria três chamadas e mais uma por van, e repetiria a policy no cliente.

**Como funciona:**
- **Chamador:** é achado pelo `uid` (`DriverService.requireCallerDriver`). Quem não é motorista recebe 403 (`user.type.forbidden`), como no `GET /api/drivers/me`.
- **Avaliação:** o `DriverMeComplianceService` avalia pelo `DriverComplianceService.evaluate` na data `SchoolTransportCredentialService.today()`. São as consultas constantes do D15, sem histórico, sem revisor e sem a página do admin.
- **Mapper e DTO:** são próprios (`DriverMeComplianceMapper`, `DriverMeComplianceResponseDTO`). A resposta não traz quem revisou, nem CPF ou e-mail. O número da CNH vem inteiro, porque o motorista é o dono.

**Resposta:**
- `asOf`: a data da avaliação.
- `approvalStatus`.
- `driverRequirements[]` e `vehicles[]`. Cada van ativa traz `token`, `plate`, `brand`, `model`, `qualified` e `requirements[]`.
- **Cada requisito:**
  - `requirement`, `optional`, `satisfied` e `state`;
  - `current` e `pending`, com o sentido do D15;
  - `expiringSoon`: `true` quando o requisito está `APPROVED` e a validade do `current` cai em até 30 dias (a janela da aba `EXPIRING`, D17).
- **Cada envio:**
  - `kind`, `token`, `documentNumber`, `expiresAt` e `status`;
  - `rejectionReasonCode`, `rejectionReason` e `reviewedAt`;
  - `createdAt`, `version`, `fileUrl` e `file` (D22);
  - na CNH, também `category`, `issueDate` e `registrationNumber`.

Enquanto um envio está `PENDING`, `documentNumber`, `expiresAt` e, na CNH, `category`, `issueDate` e `registrationNumber` vêm nulos, porque são preenchidos na aprovação (D2). O `file` sempre vem, porque todo envio tem arquivo.

**Resumo do perfil:** o `GET /api/drivers/me` ganha:
- `experienceYears`, que já está no motorista;
- `ratingCount`, contado na leitura, numa consulta, pela mesma tabela da média. Assim não precisa de coluna.

### D24 — O limite de transporte acima do limite do arquivo

Antes, a mesma `MEDIA_MAX_DOCUMENT_SIZE_MB` definia o limite do arquivo e o do multipart do Spring. Um arquivo de 9,99 MB estourava no Spring por causa do cabeçalho do multipart: voltava um 413 genérico, sem `code`, e a regra de 10 MB do `MediaFileService` nem rodava.

- **Duas variáveis:**
  - `MEDIA_MAX_UPLOAD_SIZE_MB` (padrão 11) define o `spring.servlet.multipart.max-file-size` e o `max-request-size`. É só o transporte.
  - `MEDIA_MAX_DOCUMENT_SIZE_MB` (10) e `MEDIA_MAX_PHOTO_SIZE_MB` (5) continuam sendo a regra, no `MediaFileService`.
  - 1 MB é 1024 × 1024 bytes.
- **Conferência na subida:** o `MediaFileService` não sobe se o limite de transporte não for maior que o maior limite de mídia. Assim, quem aumentar a regra e esquecer o transporte descobre na subida, e não num 413 silencioso.
- **Erro com `code`:**
  - um arquivo acima do limite do tipo responde 413 `media.file.too_large`;
  - uma requisição acima do limite de transporte responde o mesmo 413 com `code`, pelo `ApiErrorAdvice`.
- **Na frente da API:** não há proxy. Um Cloudflare Tunnel liga a Cloudflare (100 MB no plano grátis) direto ao container.

### D25 — Corrigir os dados de um envio aprovado

O revisor digita os dados (D2), e um erro de digitação dele não pode custar um reenvio ao motorista. Quem tem `review_documents` corrige os dados de um envio aprovado sem mexer no arquivo nem na decisão.

**Rotas:**
- `PUT /api/driver-documents/{token}/data` e `PUT /api/vehicle-documents/{token}/data` (PR 5), com o `DocumentDataCorrectionRequestDTO`: `documentNumber`, `expiresAt` e `version` (`@NotNull`);
- `PUT /api/driver-cnhs/{token}/data` (PR 6), com o `DriverCnhDataCorrectionRequestDTO`: os dados da CNH do D14 e `version`.

**Regras:**
- Só um envio `APPROVED` e não substituído é corrigido. Outro estado dá 409 `document.data.not_approved`: no `PENDING` os dados entram na aprovação, e o recusado e o substituído são finais (D10).
- O corpo substitui todos os dados do envio, como um `PUT`. Valem as mesmas regras da aprovação: os obrigatórios do tipo (`document.review.field_required`), o formato, a categoria da CNH, o número de CNH de um motorista só (409 `driver_cnh.registration.duplicate`) e os limites de data do D21.
- A exceção é a validade, que pode ser anterior a hoje. Corrigir a data de um documento que já venceu deixa o documento vencido, e isso é o dado certo.
- A `version` segue o D11: diferente da atual dá 409 `document.review.stale`, e a correção muda a versão.
- **Registro:** `data_corrected_by` (FK para `users`) e `data_corrected_at`, na `V75`, nas três tabelas. A decisão (`reviewed_by`, `reviewed_at`) não muda: quem aprovou continua sendo quem aprovou.
- O `data_corrected_by` é mapeado como o id do usuário, sem associação. Assim, as listagens não ganham um select a mais por linha (D13), e a página do admin resolve os nomes numa consulta só (D17).
- **Respostas:** os `PUT …/data` respondem 200 com o envio, que traz `dataCorrectedAt`; quem corrigiu aparece na página do admin (D17).
- **Sem e-mail ao motorista.** A situação dele (D23) já mostra o dado novo.
- As credenciais e a situação leem os dados corrigidos na leitura seguinte, porque nada é guardado à parte (D16).

**Rejeitado:**
- **Remover o envio e pedir outro ao motorista** (o desenho anterior do R17). O motorista pagaria pelo erro do revisor.
- **Reabrir o envio como `PENDING`.** O motorista perderia a credencial até a nova aprovação.
- **Guardar cada correção numa tabela própria.** Fica só a última, como a decisão (R14).

## Risks / Trade-offs

| # | Risco | Mitigação |
|---|---|---|
| **R1** | A suíte roda em H2 sem Flyway: a `V72` (remoções, coluna da foto, `CHECK`s, permissões) não é exercitada em teste | QA da regra 55 contra o PostgreSQL com dados montados antes da migration: documentos de van e fotos no `driver_document`, documentos da pessoa, van com foto de documento |
| **R2** | Motoristas aprovados antes desta change não têm curso, autorização da van nem número: continuam aprovados, mas nenhuma van deles passa nas credenciais, e a fase 3 vai recusar as propostas | Não há usuários reais. O seed sai completo (D9); os bancos locais mandam os documentos pela API ou são recriados. O endpoint de credenciais diz o que falta |
| **R3** | Até as telas do admin existirem (`vanep-frontend`), cada envio novo espera alguém chamar a API para revisar | As rotas dos PRs 9 e 10 são o back dessas telas; até lá, o `GET /api/driver-documents?status=PENDING` e o equivalente da van listam o que espera revisão |
| **R4** | `DriverCnhController`, `AssistantController`, `VehicleController` (`POST` e `GET /api/vehicles`), `ClientRatingController`, `DriverRatingController` e `DependentService` continuam resolvendo o chamador por `jwt.getSubject()`: depois de uma troca de e-mail, o token ainda válido dá 404 nessas rotas, ou acha outra conta se o e-mail antigo for reaproveitado. O refresh também parece montar o token novo pelo e-mail antigo | Fora do escopo; dívida registrada no Genwork (VAN-87), com a investigação do refresh. Esta change corrige só os documentos |
| **R5** | Os números das migrations colidem: a VAN-57 usou a `V69` a `V71`, e a `contract-generation` reservou a `V73` e a `V74` | Esta change usa a `V72`, a `V75` e a `V76`. Reconfirmar antes de cada merge com migration; quem entra por último renumera |
| **R7** | A certidão criminal sem validade obrigatória pode ficar antiga | O admin confere a data na revisão; a renovação periódica entra com a fase 8 (documento vencido), se o jurídico pedir |
| **R8** | Não há cadastro nacional de quais municípios exigem autorização própria: com o alvará municipal opcional, a API não sabe quando ele falta | Até as cidades de lançamento serem definidas, o contrato traz a declaração do motorista de cumprir a regulamentação local (`contract-generation`, R12). Depois, uma change própria marca por município a exigência (mantida pelo admin a partir da pesquisa jurídica de cada cidade aberta), e o `SchoolTransportCredentialService` passa a receber também a cidade do serviço, sem mudar quem o chama |
| **R9** | O H2 dos testes não tem `btree_gist`, índices parciais, a constraint de exclusão nem os `CHECK`s da `V75` | O QA da regra 55 de cada PR confere no PostgreSQL: dois `PENDING` do mesmo dono e tipo, o mesmo número de CNH em dois motoristas, e recusa sem código |
| **R10** | **Risco aceito.** A fila e o `/counts` avaliam todos os motoristas candidatos a cada leitura (D16): o tempo e a memória crescem com o número de motoristas e de documentos, e a busca por nome, e-mail e placa roda sem índice | Aceito para o volume atual, porque mantém a regra num lugar só e não exige tabela nem job. As consultas são constantes (`SqlStatementCounter`), e o QA do PR 9 mede a fila e o `/counts` com 1.000 motoristas e registra o tempo no `qa.md`. Se a fila passar de cerca de 1 s, o caminho é o adiado do D16, trocado só dentro do `DriverComplianceQueueService`, sem mudar a API. O Javadoc da classe registra a decisão |
| **R11** | Com a `V75`, toda CNH que já existe fica `PENDING`: motoristas aprovados perdem o `DRIVER_LICENSE` até o admin aprovar a CNH, e a VAN-31 recusa as propostas deles | Não há motoristas reais (R2). O seed aprova as CNHs dele; os bancos locais aprovam pela rota nova |
| **R12** | O arquivo de um envio substituído é apagado: não dá para rever o que foi recusado ou aprovado antes | Decisão do produto: fica a decisão, não o arquivo (minimização de dados pessoais, LGPD). O contrato guarda o número e a validade das credenciais na própria foto (`contract-generation`), sem depender do arquivo |
| **R13** | A `V76` substitui os `PENDING` duplicados que já existam por dono e tipo, mantendo o mais novo, para o índice único poder ser criado; os arquivos deles continuam no storage | Só bancos locais têm duplicados; o QA monta o caso antes da `V75` |
| **R14** | O `driver` guarda só o último `submittedAt` e a última decisão: o histórico não mostra envios e recusas anteriores do cadastro | Aceito; uma tabela de auditoria ficou fora de escopo |
| **R17** | O revisor passa a digitar os dados, e um erro de digitação dele vira dado aprovado | A aprovação confere o formato do número, a categoria e as datas dentro dos limites (D21), e o banco recusa um aprovado sem os dados obrigatórios (D2). Um erro que passar é corrigido pelo revisor (D25), sem reenvio do motorista e com o registro de quem corrigiu |
| **R18** | O motorista só fica sabendo na revisão que um documento não serve (por exemplo, a CNH B), e não mais na hora do envio | O app mostra antes do envio o que o transporte escolar exige. A recusa com `NOT_ELIGIBLE` (D12) chega por e-mail (D18) e aparece na situação do motorista (D23) |
| **R19** | O tempo de revisão cresce com o número de documentos, porque o revisor digita os dados | Cerca de 8 envios por motorista com uma van, e menos de um minuto cada um. Cabe no volume atual. A leitura automática ou a consulta à base oficial preenchem a mesma aprovação (`review_method = AUTO`) quando o volume justificar, em change própria |
| **R16** | `approve` com `PESSIMISTIC_WRITE` segura a linha do motorista durante o recálculo. Remover uma van, um documento ou uma CNH pega a mesma trava, para o recálculo não aprovar com algo que acabou de sair | São milissegundos; quem espera é só outra escrita no mesmo motorista |

## Migration Plan

| Migration | PR | Conteúdo |
|---|---|---|
| **V72** | 1 | D6 inteiro: `vehicle_document` com a validade anulável, `driver_document.document_number`, documentos de van e fotos removidos do `driver_document`, coluna da foto removida, `CHECK`s (os tipos, e o que um aprovado precisa ter, D2), permissões do `ADMIN`, comments |
| **V75** | 4 | A revisão nova nas três tabelas e no motorista, na ordem abaixo |
| **V76** | 5 | Um `PENDING` por dono e tipo e o código obrigatório na recusa, com o serviço que os cumpre |
| **V77** | 6 | `driver_cnh.photo_media_id` renomeada para `file_media_id`, com a FK; os dados da CNH anuláveis e o `CHECK` do que uma CNH aprovada precisa ter (D14) |

A `btree_gist` da `V75` é um módulo *contrib* do próprio PostgreSQL. Ela vem na imagem oficial (`postgres:17-alpine`), existe nos bancos gerenciados (RDS, Cloud SQL, Supabase), entra no `pg_dump` e é atualizada junto com o PostgreSQL. A migration roda com o usuário dono do banco, que pode criá-la.

**`V75`, em ordem:**
1. `create extension if not exists btree_gist`.
2. **`driver_document` e `vehicle_document`:** `version bigint not null default 0`, `rejection_reason_code varchar(30)` e `superseded_at timestamptz`. Na `vehicle_document`, também `notified_at timestamptz`.
3. **`driver_cnh`:**
   - as colunas da revisão: `status varchar(20) not null default 'PENDING'`, `review_method`, `external_check_id`, `rejection_reason`, `rejection_reason_code` e `reviewed_by` (FK para `users`);
   - `reviewed_at`, `notified_at`, `version` e `superseded_at`.
   - Nas três tabelas, `data_corrected_by` (FK para `users`) e `data_corrected_at timestamptz` (D25).
4. **Dados:** `rejection_reason_code = 'OTHER'` nos documentos `REJECTED` e no `driver` `REJECTED`.
5. **Índices:** saem `driver_cnh_driver_active_key` e `driver_cnh_registration_active_key`, e entra a constraint de exclusão do número (D14).
6. **`CHECK`s:** os valores de `rejection_reason_code` (com `NOT_ELIGIBLE`, D12) e do `status` da CNH.
7. **`driver.rejection_reason_code varchar(30)`** com `CHECK` dos valores do `DriverRejectionReason`.
8. **Permissão:** `review_documents` no bundle `ADMIN`, como a `V72`.
9. **Comments.**

**`V76`, em ordem:**
1. Nos `PENDING` duplicados por dono e tipo, `superseded_at = now()` em todos menos o mais novo (R13).
2. Os três únicos parciais de um `PENDING` por dono e tipo (D10).
3. `rejection_reason_code = 'OTHER'` nos recusados que ficaram sem código, e `status <> 'REJECTED' or rejection_reason_code is not null` nas três tabelas (D12).

Nenhuma migration aplicada é editada (regra 2). A `V72`, a `V75`, a `V76` e a `V77` ainda não estão na `main` nem em nenhum banco compartilhado: são ajustadas no próprio PR. Um banco local que já as rodou numa versão anterior é recriado, e o seed o repõe.

**Rollback:**
- a `V72` remove uma coluna da `vehicle`, então voltar exige uma migration nova;
- a `V75` troca índices da `driver_cnh`, e voltar exige recriá-los, o que só funciona sem renovações gravadas;
- a `V77` só renomeia, e voltar é outra migration que renomeia de volta.

Sem dados de produção, o caminho é recriar o banco local.

## Rollout — grafo de dependência e plano de PRs

```
main
 └─ VAN-86/spec-driver-credentials-review                (só a change do openspec)
      └─ 1-schema
           └─ 2-review-and-http
                └─ 3-credentials-and-onboarding   ← destrava o PR 4 da VAN-31
                     └─ 4-review-schema           (V75)
                          └─ 5-review-and-submissions  (V76)
                               └─ 6-cnh-review          (V77)
                                    └─ 7-compliance-policy
                                         ├─ 8-review-notifications
                                         └─ 9-admin-queue
                                              └─ 10-admin-driver-page
                                                   └─ 11-driver-own-compliance
```

Todas as branches têm o prefixo `VAN-86/`. A task desta change no Genwork é a **VAN-86**.

| PR | Conteúdo | Camadas | Depends on | Parallel with |
|---|---|---|---|---|
| 0 | `VAN-86/spec-driver-credentials-review`: só `openspec/changes/driver-credentials-review/` | spec | — | PRs 1–3 da VAN-31 |
| 1 | `V72`; `shared.document` (`DocumentReview`, `DocumentStatus`, `ReviewMethod`); `VehicleDocumentType`, `VehicleDocumentModel`, repositório; `DriverDocumentType` e `document_number` no model; o `vehicle` sem a foto de documento (model, DTO, rotas, slot); o onboarding lendo os documentos da van na tabela nova, com o conjunto exigido de hoje e o DTO por van | migration, model, repository | 0 | PRs 1–3 da VAN-31 |
| 2 | `DocumentReviewPolicy` com os dados da aprovação (D2); serviço, DTOs, mapper e controller dos documentos da van, com o envio multipart (tipo e arquivo) e sem `PUT`; `@sec.isVehicleDocumentOwner`; permissões no `PermissionEnum`; documentos do motorista com os tipos novos, o mesmo envio multipart, sem `PUT`, número e validade na aprovação, revisão limpa no arquivo e chamador pelo `uid`; `file` nas respostas (D22); mensagens | policy, security, service, controller | 1 | PRs 1–3 da VAN-31 |
| 3 | `SchoolTransportCredentialPolicy` e serviço, endpoint de credenciais; onboarding com o conjunto legal e a CNH D, e aprovação; seeds com datas relativas | policy, service, controller | 2 | PRs 1–3 da VAN-31 |
| 4 | `V75`; `DocumentRejectionReason`, `DriverRejectionReason`; `DocumentReview` com o código; `version`, `supersededAt` e a correção dos dados (D25) nos três models; `DriverCnhModel` com a revisão; repositórios com `join fetch` do revisor e sem os substituídos; `PermissionEnum.REVIEW_DOCUMENTS` | migration, model, repository | 3 | VAN-31 |
| 5 | `V76`; `ApiProblemException` (D20); D10 a D12 nos documentos do motorista e da van: código e mensagem na policy, `version` no DTO de status e 409 `stale`, `ObjectOptimisticLockingFailureException` no `ApiErrorAdvice`, `review_documents` no `PUT /status`, `version` e código nas respostas; envio revisado final, um `PENDING` por dono e tipo, substituição na aprovação e no envio novo, listagens sem substituídos; arquivo apagado depois do commit (`MediaDiscardedEvent`) e `Cache-Control` no `MediaResponder` (D17 e); datas dentro dos limites na aprovação dos dois documentos (D21); a correção dos dados aprovados nos dois documentos (D25) | policy, request DTO, service, media, controller, response DTO | 4 | — |
| 6 | D14 e `V77`: revisão da CNH, `PUT /api/driver-cnhs/{token}/status` com os dados da CNH, a CNH enviada só com o arquivo (multipart, sem `PUT`), D10 na CNH, arquivo da CNH com PDF e rotas `/file`, número só de um motorista conferido na aprovação, credencial exige CNH aprovada, `cnh_rejected` no onboarding, seed; datas e categoria da CNH na aprovação (D5, D21), a correção dos dados da CNH aprovada (D25) e `file` na resposta (D22) | service, controller, policy, seed | 5 | — |
| 7 | D15: `DriverCompliancePolicy` e `DriverComplianceService` (`evaluateAll` em lote); `approve` com trava e 422 com `blockers`; onboarding e `submit-onboarding` pela policy | policy, service | 6 | — |
| 8 | D18 e o código no `reject`: eventos de aprovação e recusa do cadastro, e-mail depois do commit, `DocumentRejectedEvent`, `document-rejected.html`, `notified_at`, `DriverRejectionRequestDTO.reasonCode` | service, request DTO, template | 7 | 9, 10 |
| 9 | D16 e D17 a e b: placa normalizada no `VehicleService`; `DriverComplianceView`; `DriverComplianceQueueService` com o Javadoc da decisão; `GET /api/drivers/compliance` e `/counts`; DTOs da linha e das contagens | repository, service, controller, response DTO | 7 | 8 |
| 10 | D17 c: `GET /api/drivers/{token}/compliance` com `lastDecision`, `reviewQueue`, os dados inteiros da CNH, a correção dos dados (D25) e `history`; consultas constantes | repository, service, controller, response DTO | 9 | 8 |
| 11 | D23: `GET /api/drivers/me/compliance` com service, mapper e DTO próprios; `ratingCount` e `experienceYears` no `GET /api/drivers/me`; D24: limite de transporte separado do limite do arquivo | repository, service, controller, response DTO | 10 | 8 |

**Pilha e paralelos:**
- A pilha segue o modelo da VAN-30: cada PR nasce do anterior e aponta para ele como `--base`.
- **Os PRs 4 a 11 nascem do PR 3 e não mexem em nada que a VAN-31 precisa.** O PR 3 continua sendo o único que tem de estar na `main` antes do PR 4 da `contract-generation`. Nenhum PR novo reabre o PR 3: o PR 7 substitui a aprovação dele por um caminho novo, e o contrato do D5 não muda.
- Os PRs 4 a 7 são em série. Cada um usa o anterior: o schema, a decisão e a regra do envio, a CNH no mesmo formato e a policy sobre os três.
- O 8 mexe só nos e-mails e no `reject`, e o 9 e o 10 só leem. Por isso o 8 nasce do 7 e pode ser revisado em paralelo com o 9 e o 10. O 9, o 10 e o 11 ficam em série porque os três mexem no `DriverComplianceController`. Quem entrar depois rebaseia.

**Tamanho:** o teto é de cerca de 600 linhas produtivas e 10 arquivos novos por PR (regra 41). Testes, script e `qa.md` não contam no teto.
- O 5 (cerca de 440 linhas produtivas) e o 10 (cerca de 570) são os maiores.
- Se o 5 passar do teto, o `MediaDiscardedEvent` e o `Cache-Control` saem para um PR entre o 4 e o 5.
- O 11 tem cerca de 250 linhas produtivas e 4 arquivos novos.
- O 10 usa records aninhados no `DriverComplianceDetailResponseDTO` para não passar de 10 arquivos. Se passar do teto, o `history` sai para um PR depois dele.
