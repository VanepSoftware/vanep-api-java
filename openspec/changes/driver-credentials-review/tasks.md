> **Documentos do motorista e da van, CNH revisada, credenciais de transporte escolar e a situação do motorista para o admin e para o próprio motorista.** Esta change é dividida em 11 PRs pelas regras 36 a 43, **em sequência** sobre a branch `VAN-86/spec-driver-credentials-review`, que só traz esta change do openspec: cada branch nasce da anterior e cada PR aponta a anterior como `--base`. Ver o grafo no fim do `design.md`. A task desta change no Genwork é a **VAN-86**: a chave vai no título e na branch de cada PR (regra 47).
>
> ```
> main ─ spec ─ PR 1 ─ PR 2 ─ PR 3 ─→ PR 4 da VAN-31 (contract-generation)
>                                  └─ PR 4 ─ 5 ─ 6 ─ 7 ─┬─ 8
>                                                       └─ 9 ─ 10 ─ 11
> ```

> [!IMPORTANT]
> **O PR 3 precisa estar na `main` antes do PR 4 da `contract-generation`** (VAN-31), que trava a proposta pelas credenciais.
>
> **Motoristas aprovados antes desta change não passam nas credenciais (R2)** até mandar o curso, a autorização da van e os números, e o admin aprovar. O seed sai completo.
>
> **Os PRs 4 a 11 não travam a VAN-31.** O contrato de `findCredentials` não muda. A partir do PR 6, a CNH precisa estar aprovada (R11), e o seed a aprova.

## Preparação

- [x] Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] Revisar `proposal.md`, `design.md` e os cinco specs (`vehicle-documents`, `driver-documents`, `document-review`, `school-transport-credentials`, `driver-onboarding-school-transport`)
- [x] **Confirmar os números de Flyway** na `main` e em todas as branches remotas (`git ls-tree`). Esta change usa `V72`, supondo a VAN-57 em `V69`–`V71` e a `contract-generation` em `V73`–`V74`. **Reconfirmar antes do merge do PR 1** (R5)
- [x] Reconferir que `vanep-mobile` e `vanep-frontend` não chamam `/api/driver-documents`, `/api/vehicles/{token}/photo-document` nem `GET /api/drivers/me/onboarding`, e que o app só lê `photoDocumentUrl`
- [x] Criar a task desta change no Genwork (**VAN-86**) e citá-la na VAN-31 como dependência
- [x] Branch `VAN-86/spec-driver-credentials-review` a partir da `main`, só com `openspec/changes/driver-credentials-review/`. É a base da pilha
- [x] **Confirmar o número de Flyway dos PRs 4 a 10:** `V75` (PR 4), depois da `V73` e da `V74` da `contract-generation`. Reconfirmar antes do merge de cada um (R5)
- [x] Mandar à sessão do `vanep-frontend` o contrato final das rotas, dos DTOs, dos enums e dos códigos de erro dos PRs 5 a 10 (D10 a D19), depois do ok da spec
- [ ] Mandar às sessões do `vanep-mobile` e do `vanep-frontend` o contrato do envio só com o arquivo e da aprovação com os dados (D2, D14), com o `document.review.field_required` e o `NOT_ELIGIBLE`, depois do ok da spec

## 1. PR 1 — Schema, revisão compartilhada e van sem foto de documento

> Goal: o banco tem a `vehicle_document` com dono obrigatório, o `driver_document` só com os tipos da pessoa e com número, e os dados antigos no lugar certo. A van perde a foto de documento própria. O onboarding lê os documentos da van no lugar novo, sem mudar o que exige.
> Depends on: `VAN-86/spec-driver-credentials-review` | Parallel with: PRs 1–3 da VAN-31
> Order: test → migration → enums → model → repository → DTO

- [x] 1.1 Criar branch `VAN-86/1-schema` a partir da `VAN-86/spec-driver-credentials-review`
- [x] 1.2 Testes do repositório: `VehicleDocumentModel` grava e relê van, tipo, número, validade, arquivo e a revisão embutida, e um envio `PENDING` sem número nem validade; soft delete e restore; lista da van ordenada por validade desc e filtrada por tipo e status; `findDriverUserTokenByDocumentToken`; documentos de van removida não aparecem
- [x] 1.3 Testes do `driver_document`: grava e relê `documentNumber` e a revisão pelo `@Embeddable`, com o mesmo resultado de antes para os documentos existentes
- [x] 1.4 Testes da van: `VehicleResponseDTO` sem `photoDocumentUrl`; `POST`/`GET /api/vehicles/{token}/photo-document` → 404; `photo-front` e `photo-side` continuam funcionando
- [x] 1.5 Migration `V72__vehicle_document_and_driver_document_cleanup.sql` com os passos do D6, na ordem; conferir que a aplicação sobe contra um PostgreSQL limpo e contra um banco com o seed antigo
- [x] 1.6 `shared.document`: `DocumentStatus` e `ReviewMethod` (movidos do `driverdocument`, com os usos atualizados) e o `@Embeddable` `DocumentReview`; `grep` confirma que nada usa mais `DocumentStatusEnum` nem `ReviewMethodEnum`
- [x] 1.7 `driverdocument.enums.DriverDocumentType` no lugar do `DocumentTypeEnum` (sem `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO`, `VEHICLE_PHOTO`, `RESIDENCE_PROOF`, `OTHER`; com `SCHOOL_DRIVER_COURSE`); `DriverDocumentModel` com `documentNumber` e a revisão embutida. Testes 1.3 verdes
- [x] 1.8 `vehicledocument`: `VehicleDocumentType`, `VehicleDocumentModel` (van por `@ManyToOne` `LAZY`, revisão embutida, arquivo), `VehicleDocumentRepository`. Testes 1.2 verdes
- [x] 1.9 Van sem a foto de documento: sai o `photoDocument` do `VehicleModel`, do `VehicleRepository` (`join fetch`), do `VehiclePhotoService`, do `VehicleMapper`, do `VehicleResponseDTO` e as duas rotas do `VehicleController`; `MediaSlot.VEHICLE_PHOTO_DOCUMENT` sai e `VEHICLE_DOCUMENT_FILE` entra. Testes 1.4 verdes
- [x] 1.9a Onboarding lendo os documentos da van na `vehicle_document`, **com o mesmo conjunto exigido de hoje** (motorista: `MUNICIPAL_AUTHORIZATION`; pelo menos uma van ativa: `CRLV` e `VEHICLE_INSPECTION`), e o `DriverOnboardingDocumentsStepDTO` com `missingDriverTypes` e `vehicles` (por van ativa: `vehicleToken`, `plate`, `missingTypes`, documentos enviados); a mensagem do `submitOnboarding` lista os tipos do motorista e os de cada van com a placa. Testes do onboarding (`DriverOnboardingServiceTest`, `DriverOnboardingControllerTest`) adaptados e verdes
- [x] 1.10 `make lint` + `./mvnw verify -B`
- [x] 1.11 QA da regra 55 (`scripts/qa/driver-credentials-review/qa.sh`, resultado no `qa.md`): montar por SQL, antes da `V72`, `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO`, `VEHICLE_PHOTO`, `RESIDENCE_PROOF` e `OTHER` no `driver_document`, documentos da pessoa e uma van com foto de documento; aplicar a `V72` e conferir que os tipos errados foram removidos sem virar documento da van, que os da pessoa ficaram e que a coluna da foto saiu; os `CHECK`s recusam tipo da van no `driver_document`, tipo da pessoa na `vehicle_document`, autorização da van ou municipal aprovada sem número e documento da van aprovado sem validade, e aceitam os mesmos envios `PENDING` sem dados; uma `MUNICIPAL_AUTHORIZATION` aprovada sem número, montada antes, volta a `PENDING`; as permissões novas estão no bundle `ADMIN` e em nenhum outro

## 2. PR 2 — Revisão e rotas dos documentos

> Goal: o dono da van gerencia os documentos dela, o admin revisa, e nenhuma aprovação sobrevive a uma mudança do dono, nos documentos da van e nos da pessoa.
> Depends on: PR 1 | Parallel with: PRs 1–3 da VAN-31
> Order: test → policy → security → request DTO → service → controller → response DTO → mensagens

- [x] 2.1 Criar branch `VAN-86/2-review-and-http` de dentro da branch do PR 1
- [x] 2.2 Testes da `DocumentReviewPolicy` (spec `document-review`): rejeitar sem motivo → `reason_required`; aprovar sem arquivo → `file_required`; aprovar sem um dado obrigatório do tipo (D2) → `field_required` com o `field`, e com os opcionais em branco → aceito; a recusa não leva os dados; a revisão limpa zera status, revisor, data, método, motivo e os dados
- [x] 2.3 Testes de slice dos documentos da van (spec `vehicle-documents`):
  - o dono envia com `POST` multipart (tipo e arquivo) → 201 `PENDING` com o `file` e os dados nulos; lista, lê, remove e troca o arquivo (200/204/201);
  - sem arquivo → 400; tipo da pessoa → 400; um campo de dados no envio não é lido; `PUT /api/vehicle-documents/{token}` não existe;
  - outro motorista → 403; cliente → 403; sem token → 401; van ou documento desconhecidos ou removidos → 404;
  - restore só com `restore_vehicle_document`;
  - `PUT /status` pelo dono → 403;
  - aprovação pelo admin com os dados → 200, com o número e a validade gravados; autorização sem número → 400 `document.review.field_required` com `field` `documentNumber`; sem validade → 400 com `field` `expiresAt`; número com 65 caracteres → 400 e com 64 → 200, gravado sem espaços nas pontas; recusa com dados no corpo → os dados continuam nulos
- [x] 2.4 Testes da revisão limpa: novo arquivo num documento `APPROVED`, da van e do motorista → `PENDING` sem revisor e sem os dados da aprovação; aprovar sem arquivo (montado no banco) → 422 nos dois
- [x] 2.5 Testes dos documentos do motorista (spec `driver-documents`): `POST` multipart com tipo e arquivo → 201 `PENDING` com os dados nulos; sem arquivo → 400; tipo da van → 400; `PUT /api/driver-documents/{token}` não existe; aprovar a `MUNICIPAL_AUTHORIZATION` sem número → 400 `document.review.field_required`; aprovar o `SCHOOL_DRIVER_COURSE` sem número nem validade → 200; um motorista com token emitido antes de trocar o e-mail (`sub` com o e-mail antigo) lista os próprios documentos → 200; a listagem com `documentType` e `status` juntos traz só os que atendem aos dois, e o admin sem `driverToken` filtra por tipo e por status
- [x] 2.6 `shared.document.DocumentReviewPolicy` pura, com os dados obrigatórios da aprovação; `requiresNumberOnApproval()` e `requiresExpiryOnApproval()` no `DriverDocumentType` e no `VehicleDocumentType`. Testes 2.2 verdes
- [x] 2.7 `PermissionEnum` com as cinco permissões de documento da van; `SecurityEvaluator.isVehicleDocumentOwner`
- [x] 2.8 `VehicleDocumentRequestDTO` (`documentType` e `file`, por `@Valid @ModelAttribute`) e `VehicleDocumentResponseDTO`; o `DriverDocumentStatusUpdateRequestDTO` vira o `shared.document.dto.DocumentStatusUpdateRequestDTO`, com `documentNumber` e `expiresAt` para a aprovação, usado pelos dois documentos; `VehicleDocumentMapper`; `VehicleDocumentService` (envio e arquivo na mesma transação, dados gravados na aprovação) e `VehicleDocumentFileService`; `VehicleDocumentController` com as rotas do D4, sem `PUT /{token}`. Testes 2.3 verdes
- [x] 2.9 `DriverDocumentRequestDTO` (`documentType` e `file`, multipart) e `DriverDocumentResponseDTO` com `documentNumber`; `DriverDocumentService` com o envio e o arquivo juntos, os dados gravados na aprovação, a revisão pela policy e o chamador pelo `uid`; sai o `PUT /api/driver-documents/{token}`; `DriverDocumentFileService.replace` com a revisão limpa; `DriverDocumentController` com `Authentication` no lugar do `Jwt`; a listagem numa consulta só, com motorista, tipo e status opcionais e combinados. Testes 2.4 e 2.5 verdes
- [x] 2.10 Mensagens `vehicle_document.*` e `document.review.*` (com `document.review.field_required`) em `messages.properties` e `messages_pt_BR.properties`
- [x] 2.10a `shared.document.dto.DocumentFileResponseDTO` e o `file` no `DriverDocumentResponseDTO` e no `VehicleDocumentResponseDTO`, pelos mappers (D22). Testes de slice: com arquivo, `mimeType`, `sizeBytes` e `originalName`; sem arquivo, `file` nulo
- [x] 2.11 `make lint` + `./mvnw verify -B`
- [x] 2.12 QA da regra 55: o ciclo completo de um documento da van com tokens reais (motorista envia o PDF com o tipo, admin aprova com o número e a validade, motorista troca o arquivo → `PENDING` sem os dados, admin aprova de novo); aprovação sem um dado obrigatório → 400 com o `field`, e o documento continua `PENDING`; cada recusa do 2.3 e do 2.5; um terceiro tentando ler o arquivo; a van removida esconde os documentos e restaurá-la os devolve; regressão das rotas de foto da van e de `/api/driver-documents`

## 3. PR 3 — Credenciais, onboarding e aprovação

> Goal: um serviço diz se o motorista e uma van cumprem a lei numa data, o motorista vê o que falta em cada van, e o onboarding e a aprovação exigem o conjunto legal.
> Depends on: PR 2 | Parallel with: PRs 1–3 da VAN-31
> Order: test → enums → policy → service → controller → response DTO → onboarding → seeders

- [x] 3.1 Criar branch `VAN-86/3-credentials-and-onboarding` de dentro da branch do PR 2
- [x] 3.2 Testes da `SchoolTransportCredentialPolicy` (spec `school-transport-credentials`): tudo aprovado e válido → `failed` vazio, com número e validade de cada credencial; documento da outra van não conta; vencido na véspera da data → falha; válido até a própria data → passa; `PENDING` e `REJECTED` → falha; CNH `B` → falha, `AD`, `D`, `E` e `ae` → passam; CNH inativa ou vencida → falha; dois documentos válidos → vale o de validade mais distante; `MUNICIPAL_AUTHORIZATION` aprovada entra em `credentials` e nunca em `failed`; van de outro motorista ou inativa → os três requisitos da van falham
- [x] 3.3 Testes do serviço: três consultas para qualquer número de documentos (`SqlStatementCounter`); slice do `GET /api/vehicles/{token}/school-transport-credentials` (dono → 200, admin com `show_vehicle` → 200, outro motorista → 403, sem token → 401, van removida → 404)
- [x] 3.4 Testes do onboarding (spec `driver-onboarding-school-transport`): uma van completa de duas → passo completo; falta o curso → incompleto com `SCHOOL_DRIVER_COURSE`; `MUNICIPAL_AUTHORIZATION` ausente não pesa; CNH `B` → `cnh_category`; a resposta traz `missingDriverTypes` e `vehicles` com `missingTypes` por van ativa
- [x] 3.4a Testes do envio aos poucos: salvar só o CRLV deixa o passo incompleto e mantém o documento; acrescentar os outros depois completa o passo; `submit-onboarding` sem a autorização de nenhuma van → 422 com o tipo e a placa, sem mudar status nem documentos; depois de recusado, trocar o arquivo e reenviar → `UNDER_REVIEW`; criar e trocar documento com o motorista `UNDER_REVIEW` → aceito
- [x] 3.5 Testes da aprovação: com a vistoria da única van pendente → 422 `driver.onboarding.documents_not_approved`, o motorista continua `UNDER_REVIEW`; com tudo aprovado → `APPROVED`
- [x] 3.6 `schooltransportcredential`: `CredentialRequirement`, `SchoolTransportCredentialPolicy`, `SchoolTransportCredentialsDTO` e `CredentialDTO` (o contrato do D5 da `contract-generation`), `SchoolTransportCredentialService.findCredentials`. Testes 3.2 e a primeira parte do 3.3 verdes
- [x] 3.7 `SchoolTransportCredentialsResponseDTO` e a rota no `VehicleController` (ou num controller próprio do pacote), com `@PreAuthorize("hasAuthority('show_vehicle') or @sec.isVehicleOwner(#token, authentication)")`. Testes 3.3 verdes
- [x] 3.8 `DriverOnboardingService`: o conjunto exigido passa ao legal completo (motorista: `SCHOOL_DRIVER_COURSE` e `CRIMINAL_RECORD`; van: `CRLV`, `VEHICLE_INSPECTION` e `SCHOOL_VEHICLE_AUTHORIZATION`; sai a `MUNICIPAL_AUTHORIZATION`), `cnh_category`, `approve` pelas credenciais. Testes 3.4, 3.4a e 3.5 verdes
- [x] 3.8a `DriverCnhRequestDTO` aceita só `D`, `E`, `AD` e `AE`; outra categoria, como `B`, dá 400 (`driver_cnh.category.invalid`) no campo `category`. Teste de slice com `A`, `B`, `C`, `AB`, `AC` e texto fora do formato
- [x] 3.9 Mensagem `driver.onboarding.documents_not_approved` em `messages.properties` e `messages_pt_BR.properties`
- [x] 3.10 `DriverDocumentSeeder` e `VehicleDocumentSeeder` com o conjunto legal `APPROVED`, número nas autorizações e validade relativa a hoje, idempotentes; subir com o seed ligado e conferir pelo endpoint de credenciais que a van do seed passa
- [x] 3.11 `make lint` + `./mvnw verify -B`
- [x] 3.12 QA da regra 55: um motorista novo do zero (cadastro, van, CNH `B` recusada com 400, CNH `B` gravada por SQL → `cnh_category`, CNH `AD`, documentos, onboarding completo, envio, aprovação recusada com documento pendente, admin aprova documentos e motorista), conferindo o endpoint de credenciais em cada passo; segunda van sem documentos não afeta a aprovação e aparece com os três requisitos faltando; vencer a vistoria por SQL e conferir a falha; o motorista do seed passa; regressão do QA da `service-request-proposal`

## 4. PR 4 — Schema da revisão nova

> Goal: o banco tem versão, código de motivo, substituição e `notified_at` nos três envios, a CNH com revisão, um pendente por dono e tipo, e o número da CNH de um motorista só. Os models e os repositórios leem tudo isso, e o revisor vem sem consulta extra.
> Depends on: PR 3 | Parallel with: VAN-31
> Order: test → migration → enums → model → repository

- [x] 4.1 Criar branch `VAN-86/4-review-schema` de dentro da branch do PR 3
- [x] 4.2 Testes de repositório:
  - os três models gravam e relêem `version`, `rejectionReasonCode` e `supersededAt`, e a `DriverCnhModel` a revisão embutida;
  - as listagens e as consultas da policy ignoram os substituídos;
  - as listagens de documento do motorista e da van fazem o número de consultas de antes com o revisor carregado (`SqlStatementCounter`)
- [x] 4.3 Migration `V75__document_submission_review.sql` com os passos da `V75` no Migration Plan, na ordem
- [x] 4.4 `shared.document.enums.DocumentRejectionReason` (com `NOT_ELIGIBLE`, D12) e `driver.enums.DriverRejectionReason`; `DocumentReview` com `rejectionReasonCode` (o `reviewedBy` segue `EAGER`, D13); `@Version` e `supersededAt` nos três models; `DriverCnhModel` com o `DocumentReview`, `notifiedAt`; `DriverModel.rejectionReasonCode`
- [x] 4.5 Repositórios: `left join fetch` do revisor (D13) em toda consulta que monta resposta, `countQuery` nas paginadas, filtro `supersededAt is null` nas listagens e nas consultas do `SchoolTransportCredentialService`. Testes 4.2 verdes
- [x] 4.6 `PermissionEnum.REVIEW_DOCUMENTS`
- [x] 4.6a `data_corrected_by` e `data_corrected_at` nas três tabelas (na `V75`) e nos três models, com o corretor mapeado como id, sem associação (D25)
- [x] 4.7 `make lint` + `./mvnw verify -B`
- [x] 4.8 QA da regra 55:
  - montar antes da `V75`: um documento recusado, um motorista recusado e uma CNH;
  - aplicar a `V75` e conferir que os recusados têm `OTHER` e que a CNH ficou `PENDING`, na versão 0;
  - as recusas do banco: código inválido, status inválido da CNH e o mesmo número de CNH em dois motoristas;
  - o mesmo número no mesmo motorista é aceito;
  - `review_documents` só no bundle `ADMIN`;
  - regressão do QA dos PRs 1 a 3

## 5. PR 5 — Decisão com versão, motivo estruturado e envio final

> Goal: só quem tem `review_documents` decide um documento do motorista ou da van, e a decisão leva a versão que o admin viu e um código de motivo. Renovar não tira a credencial, um envio revisado nunca muda, e o substituído fica só com a decisão. Os arquivos são cacheados pelo navegador enquanto a URL não muda.
> Depends on: PR 4 | Parallel with: —
> Order: test → erro → policy → request DTO → service → media → controller → response DTO → mensagens

- [ ] 5.1 Criar branch `VAN-86/5-review-and-submissions` de dentro da branch do PR 4
- [ ] 5.1a Testes da `ApiProblemException` (D20) num controller de teste: a resposta tem o status, o `code` igual à chave, o `detail` com o texto pt-BR da chave, e as propriedades extras; um `ResponseStatusException` antigo continua com a resposta de hoje
- [ ] 5.1b `shared.error.ApiProblemException` estendendo a `ErrorResponseException`, e o ajuste do `ApiErrorAdvice` se o teste pedir. Teste 5.1a verde
- [ ] 5.1c Migration `V76__one_pending_submission_and_rejection_code.sql` com os passos da `V76` no Migration Plan
- [ ] 5.2 Testes da `DocumentReviewPolicy` (spec `document-review`): recusar sem código → `reason_code_required`; `OTHER` sem mensagem → `reason_required`; outro código sem mensagem → aceito; aprovar limpa código e mensagem
- [ ] 5.3 Testes de slice da decisão nos dois documentos:
  - `PUT /status` com `update_*_document` e sem `review_documents` → 403, e com `review_documents` → 200;
  - versão diferente → 409 `document.review.stale`, sem mudar nada;
  - `ObjectOptimisticLockingFailureException` → 409 com a mesma chave;
  - a resposta traz `version` e `rejectionReasonCode`;
  - cada erro da tabela do D20 deste PR traz o `code`
- [ ] 5.4 Testes do D10, nos dois documentos:
  - `/file` e `DELETE` do dono em envio aprovado ou recusado → 409 `document.submission.reviewed`, e em pendente → aceito e continua `PENDING`;
  - o admin com `delete_*` remove um envio revisado;
  - segundo `PENDING` do mesmo tipo → 409 `document.submission.pending_exists`, inclusive no restore;
  - um envio novo substitui o recusado; aprovar substitui o aprovado anterior;
  - o substituído fica sem arquivo, com a decisão, fora da listagem e fora das credenciais;
  - renovar a vistoria antes de vencer mantém `VEHICLE_INSPECTION` satisfeito
- [ ] 5.5 Testes dos arquivos:
  - o objeto só é apagado depois do commit, e num rollback continua no storage (`StorageService` em mock);
  - `Cache-Control`: `v` igual ao token da mídia → `private, max-age=86400, immutable`; sem `v` ou diferente → `private, no-cache`
- [ ] 5.6 `DocumentReviewPolicy` com o código; `DocumentStatusUpdateRequestDTO` com `rejectionReasonCode` e `version` (`@NotNull`); `DocumentReviewService` conferindo a versão; os erros da policy e do serviço pela `ApiProblemException`. Teste 5.2 verde
- [ ] 5.7 `MediaDiscardedEvent` e `MediaFileService.discard` apagando o objeto em `@TransactionalEventListener(AFTER_COMMIT)`; `MediaResponder` recebendo o `v` e mandando o `Cache-Control` em todas as rotas de arquivo. Teste 5.5 verde, e os testes de foto existentes continuam verdes
- [ ] 5.8 Um `DocumentSubmissionRules` em `shared.document` com a regra do D10 (o que muda em cada status), usado pelos dois serviços; `DriverDocumentService`, `DriverDocumentFileService`, `VehicleDocumentService` e `VehicleDocumentFileService` com o 409, o pendente único (com a violação do índice traduzida) e a substituição; `DocumentReviewService` substituindo na aprovação. Teste 5.4 verde
- [ ] 5.9 `ApiErrorAdvice` com o 409 do `ObjectOptimisticLockingFailureException`; `PUT /status` dos dois controllers com `hasAuthority('review_documents')`; `version` e código nos response DTOs e nos mappers. Teste 5.3 verde
- [ ] 5.10 Mensagens `document.review.reason_code_required`, `document.review.stale`, `document.submission.reviewed`, `document.submission.pending_exists` e os rótulos `document.rejection_reason.*` nos dois `messages*.properties`
- [ ] 5.10a Testes das datas (D21):
  - `DocumentDatesPolicy`: véspera de hoje → `document.expires_at.past`; hoje → aceito; o último dia do limite → aceito, e o dia seguinte → `document.expires_at.too_far`; sem limite → aceito; sem validade → aceito;
  - `latestExpiryOn` de cada tipo, com o CRLV em 31/12 do ano que vem;
  - slice da aprovação nos dois documentos: 400 com o `code` e o `field` `expiresAt`, e o envio continua `PENDING`, sem os dados
- [ ] 5.10b `latestExpiryOn(today)` no `DriverDocumentType` e no `VehicleDocumentType`; `shared.document.service.DocumentDatesPolicy`; a aprovação dos dois documentos conferindo a validade que o revisor preenche, com `SchoolTransportCredentialService.today()`; mensagens `document.expires_at.past` e `document.expires_at.too_far`. Testes 5.10a verdes
- [ ] 5.10c Testes da correção (D25), nos dois documentos: `PUT …/{token}/data` num aprovado → 200 com os dados novos, a decisão, o revisor e o arquivo iguais, e quem corrigiu gravado; num `PENDING`, num recusado ou num substituído → 409 `document.data.not_approved`; versão velha → 409 `document.review.stale`; sem um obrigatório → 400 `document.review.field_required`; validade passada → aceita, e acima do limite → `too_far`; o dono → 403; nenhum e-mail
- [ ] 5.10d `DocumentDataCorrectionRequestDTO`; a correção no `DocumentReviewService` e nos dois serviços, com as regras da aprovação e a validade passada aceita; as rotas `/data` nos dois controllers com `review_documents`; mensagem `document.data.not_approved`. Testes 5.10c verdes
- [ ] 5.11 `make lint` + `./mvnw verify -B`
- [ ] 5.12 QA da regra 55:
  - a correção de uma validade digitada errada num documento aprovado, conferida no endpoint de credenciais; a correção num pendente → 409; o dono → 403;
  - recusa com cada código, e `OTHER` com e sem mensagem;
  - dois `curl` com a mesma versão, o segundo → 409;
  - o dono troca o arquivo de um pendente e o admin aprova com a versão de antes → 409;
  - um token com só `update_driver_document` → 403;
  - o ciclo de renovação com tokens reais: vistoria aprovada, envio novo, credencial ainda satisfeita, aprovação do novo, o anterior substituído e o objeto dele fora do disco;
  - recusa, reenvio e o recusado substituído;
  - dois `POST` simultâneos do mesmo tipo, um → 409;
  - cada 409 do 5.4;
  - os cabeçalhos de cache com e sem `v` numa rota de foto e numa de documento;
  - a `V76` sobre dados montados antes: dois `PENDING` do mesmo dono e tipo (o mais novo fica, o outro é substituído), uma recusa sem código (vira `OTHER`), os três índices de um pendente e as `CHECK`s de código na recusa;
  - as datas do D21 na aprovação dos dois documentos: ontem → `past`; o último dia do limite → 200; o dia seguinte → `too_far`; a certidão dez anos à frente → 200; uma aprovação fora do limite deixa o envio `PENDING`, sem os dados. As datas do script são relativas a hoje;
  - regressão do QA dos PRs 1 a 4, com os fluxos dos PRs 2 e 3 nas regras do PR 5 (versão na decisão, código na recusa, renovação como envio novo); cada rodada termina sem envio pendente, para o script poder ser repetido

## 6. PR 6 — CNH revisada

> Goal: a CNH é revisada como os documentos, só a aprovada vale como credencial, e a renovação com o mesmo número convive com a atual.
> Depends on: PR 5 | Parallel with: —
> Order: test → policy → service → controller → onboarding → seed → mensagens

- [ ] 6.1 Criar branch `VAN-86/6-cnh-review` de dentro da branch do PR 5
- [ ] 6.2 Testes (spec `driver-cnh-review`):
  - `POST /api/driver-cnhs` multipart só com o arquivo → 201 `PENDING`, com o `file` e o registro, a categoria e as datas nulos; sem arquivo → 400; `PUT /api/driver-cnhs/{token}` não existe;
  - `PUT /api/driver-cnhs/{token}/status`: com `review_documents` e os dados → 200 com os dados gravados, o dono → 403, sem arquivo → 422, versão velha → 409;
  - aprovação sem `registrationNumber`, `category`, `issueDate` ou `validUntil` → 400 `document.review.field_required` com o `field`; categoria `B` → 400 `driver_cnh.category.invalid`; registro fora do formato → 400; a recusa com `NOT_ELIGIBLE` não grava os dados do corpo;
  - `POST /api/driver-cnhs/{token}/file` com PDF numa CNH `PENDING` → 201 e o `GET` devolve `application/pdf`; texto → 400; `fileUrl` na resposta;
  - CNH aprovada é final;
  - renovação: o envio → 201 e a aprovação com o mesmo número → 200; segundo pendente → 409;
  - aprovar substitui a anterior;
  - aprovar com o número de outro motorista → 409 `driver_cnh.registration.duplicate`, com o `code`, e a CNH continua `PENDING`
- [ ] 6.3 Testes da credencial e do onboarding: CNH `PENDING` não satisfaz `DRIVER_LICENSE`, e só a aprovada satisfaz; no onboarding, a CNH `PENDING` conta como enviada e a `REJECTED` dá `cnh_rejected`
- [ ] 6.4 `DriverCnhService` e `DriverCnhFileService` com o `DocumentSubmissionRules`, sem a recusa de segunda CNH, conferindo o número na aprovação só contra outros motoristas e traduzindo a violação da constraint e o deadlock que ela pode causar (D14); `PUT /status` no `DriverCnhController` pelo `DocumentReviewService`; `version` e revisão no `DriverCnhResponseDTO`. Teste 6.2 verde
- [ ] 6.4b Arquivo da CNH com PDF (D14): `V77__driver_cnh_file.sql` renomeia `photo_media_id` para `file_media_id` e a FK; slot `DRIVER_CNH_FILE`; `DriverCnhPhotoService` vira `DriverCnhFileService`; rotas `/file`; `fileUrl` no `DriverCnhResponseDTO`, sem o `photoUrl` do `DriverCnhRequestDTO`. Teste 6.2 verde
- [ ] 6.4c A CNH enviada só com o arquivo e os dados na aprovação (D2, D14): a `V77` deixa `registration_number`, `category`, `issue_date` e `valid_until` anuláveis e cria o `CHECK` da CNH aprovada; `DriverCnhRequestDTO` multipart só com o `file` (e o `driverToken` do admin); sai o `PUT /api/driver-cnhs/{token}`; `DriverCnhStatusUpdateRequestDTO` com a decisão e os dados da CNH, com o formato por Bean Validation e a categoria `D`, `E`, `AD` ou `AE` (a regra sai do `DriverCnhRequestDTO`); o serviço grava os dados na aprovação. Teste 6.2 verde
- [ ] 6.5 `SchoolTransportCredentialPolicy.findDriverLicense` exigindo `APPROVED`; `cnh_rejected` no passo da CNH. Teste 6.3 verde
- [ ] 6.6 `DriverCnhSeeder` com a CNH `APPROVED`
- [ ] 6.7 Mensagem `driver.onboarding.cnh_rejected`
- [ ] 6.7a Testes das datas da CNH (D21), na aprovação: emissão amanhã → `driver_cnh.issue_date.future`; validade ontem → `document.expires_at.past`; emissão + 10 anos → aceito, e um dia depois → `document.expires_at.too_far`, com o `field` de cada um e a CNH ainda `PENDING`
- [ ] 6.7b A aprovação da CNH pela `DocumentDatesPolicy`, com o limite tirado da emissão; mensagem `driver_cnh.issue_date.future`. Testes 6.7a verdes
- [ ] 6.7c `file` no `DriverCnhResponseDTO` pelo `DocumentFileResponseDTO` (D22), com o teste da CNH enviada e da substituída, sem arquivo
- [ ] 6.7d Correção dos dados da CNH aprovada (D25): `DriverCnhDataCorrectionRequestDTO` e `PUT /api/driver-cnhs/{token}/data`, com as regras da aprovação da CNH; testes como o 5.10c, e o número de outro motorista → 409 `driver_cnh.registration.duplicate`
- [ ] 6.8 `make lint` + `./mvnw verify -B`
- [ ] 6.9 QA da regra 55:
  - motorista manda só a foto da CNH: credencial falha; o admin aprova com os dados e a credencial passa, com o número e a validade que ele preencheu;
  - aprovação sem um dado obrigatório e com a categoria `B` → 400; recusa com `NOT_ELIGIBLE`;
  - a CNH digital em PDF pelo `/file`, lida de volta como `application/pdf`; a rota `/photo` não existe mais;
  - renovação com o mesmo número, aprovação e a anterior substituída;
  - aprovar com o número de outro motorista → 409, também em duas aprovações simultâneas de CNHs de motoristas diferentes com o mesmo número, repetidas 8 vezes;
  - o seed passa nas credenciais;
  - as datas da CNH do D21 na aprovação: emissão amanhã, validade ontem e validade além de 10 anos → 400 com o `code`, e a CNH continua `PENDING`; o `file` da CNH enviada e depois da troca pelo `/file`;
  - regressão do QA dos PRs 1 a 5 e da `service-request-proposal`

## 7. PR 7 — Situação do motorista numa policy

> Goal: uma policy diz o estado de cada requisito, se o cadastro pode ser aprovado e por que não, e de quem é a vez, para um ou para muitos motoristas de uma vez. A aprovação e o onboarding usam essa policy.
> Depends on: PR 6 | Parallel with: —
> Order: test → policy → service → onboarding → aprovação → mensagens

- [ ] 7.1 Criar branch `VAN-86/7-compliance-policy` de dentro da branch do PR 6
- [ ] 7.2 Testes da `DriverCompliancePolicy` (spec `driver-compliance`):
  - cada `state`;
  - a renovação pendente ao lado da aprovada;
  - `canApprove` com uma van completa de duas;
  - os `blockers` da van mais perto de completar, e `CNH_CATEGORY`;
  - cada caso de `nextAction` na ordem do D15;
  - as contagens e `nextExpiryOn`
- [ ] 7.3 Testes do serviço: `evaluateAll` com 3 e com 30 motoristas, cada um com vans e documentos, faz o mesmo número de consultas (`SqlStatementCounter`); `evaluate` de um motorista dá o mesmo resultado que o `evaluateAll`
- [ ] 7.4 Testes da aprovação e do onboarding:
  - 422 com `code` `driver.onboarding.documents_not_approved` e `blockers` no `ProblemDetail`;
  - aprovar ou recusar fora de `UNDER_REVIEW` → 400 com `code` `driver.onboarding.not_under_review`;
  - aprovar e recusar ao mesmo tempo → uma decisão e um 400;
  - documento aprovado mas vencido aparece como faltando no onboarding;
  - os testes do onboarding do PR 3 continuam verdes
- [ ] 7.5 `drivercompliance`: `ComplianceState`, `NextAction`, `BlockerKind`, os DTOs do D15 e a `DriverCompliancePolicy`. Teste 7.2 verde
- [ ] 7.6 `DriverComplianceService` com `evaluateAll(drivers, date)` em lote e `evaluate(driver, date)` pelo mesmo caminho. Teste 7.3 verde
- [ ] 7.7 `DriverRepository.findByTokenForUpdate` (`PESSIMISTIC_WRITE`); `approve` e `reject` com a trava; `approve` e os passos de documentos e de CNH pela policy; o 422 com `blockers` pela `ApiProblemException`; o `hasAnyQualifiedVehicle` sai. Teste 7.4 verde
- [ ] 7.8 `make lint` + `./mvnw verify -B`
- [ ] 7.9 QA da regra 55:
  - a aprovação recusada mostra os `blockers` certos em cada etapa do motorista novo do QA do PR 3;
  - aprovar e recusar em paralelo;
  - a data de São Paulo perto da meia-noite (`TZ` do container em UTC);
  - regressão do QA dos PRs 1 a 6

## 8. PR 8 — E-mails depois do commit e código na recusa do cadastro

> Goal: o motorista só recebe e-mail de decisão gravada, fica sabendo de cada documento recusado e do motivo, e a recusa do cadastro tem um código.
> Depends on: PR 7 | Parallel with: PRs 9 e 10
> Order: test → request DTO → service → template → mensagens

- [ ] 8.1 Criar branch `VAN-86/8-review-notifications` de dentro da branch do PR 7
- [ ] 8.2 Testes (spec `driver-review-notifications`):
  - aprovação e recusa com rollback → nenhum e-mail;
  - documento, documento da van e CNH recusados → um e-mail com o nome, a placa, o motivo e a mensagem, e o `notifiedAt` gravado;
  - falha no envio → log e `notifiedAt` nulo
- [ ] 8.3 Testes da recusa do cadastro: sem `reasonCode` → 400; `OTHER` sem `reason` → 400 com `code` `driver.rejection.reason.required`; `DUPLICATE_ACCOUNT` sem `reason` → 200 com o código gravado
- [ ] 8.4 `DriverRejectionRequestDTO` com `reasonCode`; `reject` gravando o código. Teste 8.3 verde
- [ ] 8.5 `DriverReviewEvent`; `DriverNotificationService` em `AFTER_COMMIT`; `DocumentRejectedEvent` no `DocumentReviewService`; `DocumentNotificationService` com o `notifiedAt` em `REQUIRES_NEW`; `templates/email/document-rejected.html`. Teste 8.2 verde
- [ ] 8.6 Mensagens `email.document_rejected.*`, `driver.rejection_reason.*` e os nomes dos tipos de documento; os textos de `document.rejection_reason.*` em pt-BR são os do D18, que o front copia
- [ ] 8.7 `make lint` + `./mvnw verify -B`
- [ ] 8.8 QA da regra 55:
  - no Mailpit, o e-mail de cada recusa de documento (motorista, van, CNH) em pt-BR e o `notified_at` no banco;
  - aprovação e recusa do cadastro só depois do commit;
  - regressão do QA dos PRs 1 a 7

## 9. PR 9 — Fila e contagens do admin

> Goal: o admin acha quem precisa de atenção pelas abas, busca por nome, e-mail ou placa, e vê as contagens e o badge, calculados na hora pela policy, sem tabela nova nem job.
> Depends on: PR 7 | Parallel with: PR 8
> Order: test → repository → service → controller → response DTO

- [ ] 9.1 Criar branch `VAN-86/9-admin-queue` de dentro da branch do PR 7
- [ ] 9.2 Testes do `DriverComplianceView` (puro): cada aba com o filtro e a ordem do D17; `DRIVER` no fim de `AWAITING_DECISION`; em `EXPIRING`, um vencido, um que vence em 30 dias e um que vence em 31 (fora); empate desfeito pelo motorista
- [ ] 9.3 Testes da fila (spec `admin-driver-review`):
  - a busca por nome, por e-mail e por placa com e sem hífen;
  - página e total certos com mais motoristas que o tamanho da página, e a mesma página em dois pedidos iguais;
  - a linha com `createdAt` e sem CPF e telefone;
  - o mesmo número de consultas com 3 e com 30 motoristas (`SqlStatementCounter`);
  - `/counts` com o total de cada aba, `awaitingDriver`, `badge` e `asOf`;
  - um documento vencido ontem aparece em `EXPIRING` hoje, sem nenhuma escrita;
  - com `review_documents` ou `approve_driver` → 200, o motorista → 403, sem token → 401
- [ ] 9.4 Testes da placa normalizada no `VehicleService`: `ABC-1D23` grava `ABC1D23`, e `ABC1D23` depois de `ABC-1D23` → 409 `vehicle.plate.duplicate`, com o `code`
- [ ] 9.5 Placa normalizada no `VehicleService`. Teste 9.4 verde
- [ ] 9.6 `DriverComplianceView`. Teste 9.2 verde
- [ ] 9.7 Consulta dos candidatos no `DriverRepository` (busca e status do cadastro) e as consultas em lote de cidade e de vans; `DriverComplianceQueueService` com o Javadoc da decisão (D16); `DriverComplianceController` com `GET /api/drivers/compliance` e `/counts`; `DriverComplianceRowResponseDTO` e `DriverComplianceCountsResponseDTO`. Teste 9.3 verde
- [ ] 9.8 `make lint` + `./mvnw verify -B`
- [ ] 9.9 QA da regra 55:
  - as quatro abas e as contagens com os motoristas do seed e o motorista novo;
  - a busca por placa com hífen;
  - vencer a vistoria por SQL e conferir que `EXPIRING` a mostra na leitura seguinte, sem rodar nada;
  - **R10:** montar por SQL 1.000 motoristas, cada um com uma van e os documentos, medir a fila de cada aba e o `/counts`, e registrar os tempos no `qa.md`;
  - regressão do QA dos PRs 1 a 7

## 10. PR 10 — Página do motorista para o admin

> Goal: o admin vê numa página o checklist do motorista e de cada van, o envio que conta, a renovação pendente, a última decisão, a fila de revisão e o histórico montado das datas que já existem.
> Depends on: PR 9 | Parallel with: PR 8
> Order: test → repository → service → controller → response DTO

- [ ] 10.1 Criar branch `VAN-86/10-admin-driver-page` de dentro da branch do PR 9
- [ ] 10.2 Testes (spec `admin-driver-review`):
  - o agregado de um motorista com duas vans, uma renovação pendente e um reenvio depois de recusa;
  - `lastDecision` sem arquivo;
  - `reviewQueue` na ordem do checklist;
  - `blockers` só da van mais perto de completar, e `vehicles[]` só com as vans ativas;
  - na CNH, `expiresAt` igual à `validUntil` e o `fileUrl` da foto, relativo;
  - num envio `PENDING`, `documentNumber`, `expiresAt` e os dados da CNH nulos;
  - a correção dos dados: `dataCorrectedAt` e `dataCorrectedBy` no envio, com os nomes numa consulta só, e o evento `SUBMISSION_DATA_CORRECTED` no `history`;
  - na CNH, o número inteiro e os dados opcionais (`firstLicenseDate`, `securityNumber`, `issuingState`), para a correção partir deles;
  - metadados do arquivo;
  - número constante de consultas para qualquer número de vans e documentos;
  - motorista desconhecido ou removido → 404;
  - permissões como no 9.3
- [ ] 10.3 Testes do `history`: envios, aprovações e recusas, inclusive dos substituídos, e o envio e a decisão do cadastro, do mais novo ao mais antigo e no máximo 50; `actor` com o revisor nas decisões e nulo nos envios
- [ ] 10.4 Consultas da página nos repositórios (últimas decisões por tabela); `DriverComplianceDetailService` sobre o `DriverComplianceService.evaluate`, com o `history`; `GET /api/drivers/{token}/compliance` no `DriverComplianceController`; `DriverComplianceDetailResponseDTO` com records aninhados e `SubmissionResponseDTO`. Testes 10.2 e 10.3 verdes
- [ ] 10.4a O `file` do `SubmissionResponseDTO` pelo `DocumentFileResponseDTO` (D22), sem record próprio
- [ ] 10.5 `make lint` + `./mvnw verify -B`
- [ ] 10.6 QA da regra 55: a página em cada etapa do motorista novo, conferindo o `reviewQueue`, o `lastDecision` e o `history` depois de um reenvio, e o CPF e a data de nascimento presentes só nessa rota; regressão do QA dos PRs 1 a 7 e do 9

## 11. PR 11 — Situação do próprio motorista

> Goal: o motorista vê numa chamada o que falta em cada requisito dele e de cada van ativa, pela mesma policy, sem os dados do revisor; o resumo do perfil mostra a quantidade de avaliações e os anos de experiência.
> Depends on: PR 10 | Parallel with: PR 8
> Order: test → repository → service → controller → response DTO

- [ ] 11.1 Criar branch `VAN-86/11-driver-own-compliance` de dentro da branch do PR 10
- [ ] 11.2 Testes (spec `driver-own-compliance`):
  - motorista sem nada: requisitos `MISSING`, `current` e `pending` nulos, `MUNICIPAL_AUTHORIZATION` opcional e `vehicles` vazio;
  - `asOf` e `approvalStatus`; cada van ativa com `token`, `plate`, `brand`, `model`, `qualified` e os requisitos, e a van inativa fora;
  - `expiringSoon` com a validade em 30 dias (`true`) e em 31 (`false`), e `false` fora de `APPROVED`;
  - a recusa com o código e sem revisor; a renovação em `pending`; a CNH aprovada com `category`, `issueDate` e o número inteiro; um envio `PENDING` com o `file` e os dados nulos;
  - cliente → 403; sem token → 401
- [ ] 11.3 Teste de contagem: o mesmo número de consultas com uma van e com três, cada uma com os seus documentos e arquivos (`SqlStatementCounter`)
- [ ] 11.4 Testes do resumo: `ratingCount` com zero e com três avaliações, e `experienceYears` do motorista, no `GET /api/drivers/me` e no `PUT /api/drivers/me/availability`
- [ ] 11.5 `DriverRatingRepository.countByDriverId`; `ratingCount` e `experienceYears` no `DriverMeSummaryResponseDTO` e no `DriverMapper.toMeSummary`. Teste 11.4 verde
- [ ] 11.6 `DriverMeComplianceService` sobre o `DriverService.requireCallerDriver` e o `DriverComplianceService.evaluate`; `DriverMeComplianceMapper`; `DriverMeComplianceResponseDTO` com records aninhados; `GET /api/drivers/me/compliance` no `DriverComplianceController` com `isAuthenticated()`. Testes 11.2 e 11.3 verdes
- [ ] 11.6a Testes do limite de upload (D24): com a API num servidor de verdade, um PDF de 9,9 MB → 201, um de 10,1 MB → 413 `media.file.too_large`, e uma requisição acima do limite de transporte → 413 com o mesmo `code`; o `MediaFileService` recusa subir com o limite de transporte igual ao maior limite de mídia
- [ ] 11.6b `MEDIA_MAX_UPLOAD_SIZE_MB` (padrão 11) no `spring.servlet.multipart.*` e no `.env.example`; a conferência na subida no `MediaFileService`; o 413 com `code` no `MediaFileService` e no `ApiErrorAdvice`. Testes 11.6a verdes
- [ ] 11.7 `make lint` + `./mvnw verify -B`
- [ ] 11.8 QA da regra 55:
  - a rota em cada etapa do motorista novo: sem nada, com a CNH pendente, com documentos recusados e aprovados, e com o cadastro aprovado;
  - `expiringSoon` numa vistoria que vence em 20 dias;
  - a resposta sem revisor, CPF e e-mail;
  - cliente e admin → 403, sem token → 401;
  - `ratingCount` e `experienceYears` no `GET /api/drivers/me`;
  - um PDF de 9,9 MB → 201 e um de 10,1 MB → 413 com `code`, pela API real;
  - regressão do QA dos PRs 1 a 10
