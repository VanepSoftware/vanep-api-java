> **Documentos da van separados dos da pessoa, e credenciais de transporte escolar.** Esta change é dividida em 3 PRs pelas regras 36 a 43, **em sequência** sobre a branch `VAN-86/spec-vehicle-documents`, que só traz esta change do openspec: cada branch nasce da anterior e cada PR aponta a anterior como `--base`. Ver o grafo no fim do `design.md`. A task desta change no Genwork é a **VAN-86**: a chave vai no título e na branch de cada PR (regra 47).
>
> ```
> main ─ spec ─ PR 1 ─ PR 2 ─ PR 3 ─→ PR 4 da VAN-31 (contract-generation)
> ```

> [!IMPORTANT]
> **O PR 3 precisa estar na `main` antes do PR 4 da `contract-generation`** (VAN-31), que trava a proposta pelas credenciais.
>
> **Motoristas aprovados antes desta change não passam nas credenciais (R2)** até mandar o curso, a autorização da van e os números, e o admin aprovar. O seed sai completo.

## Preparação

- [x] Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] Revisar `proposal.md`, `design.md` e os cinco specs (`vehicle-documents`, `driver-documents`, `document-review`, `school-transport-credentials`, `driver-onboarding-school-transport`)
- [x] **Confirmar os números de Flyway** na `main` e em todas as branches remotas (`git ls-tree`). Esta change usa `V71`, supondo a VAN-57 em `V69`–`V70` e a `contract-generation` em `V72`–`V73`. **Reconfirmar antes do merge do PR 1** (R5)
- [x] Reconferir que `vanep-mobile` e `vanep-frontend` não chamam `/api/driver-documents`, `/api/vehicles/{token}/photo-document` nem `GET /api/drivers/me/onboarding`, e que o app só lê `photoDocumentUrl`
- [x] Criar a task desta change no Genwork (**VAN-86**) e citá-la na VAN-31 como dependência
- [x] Branch `VAN-86/spec-vehicle-documents` a partir da `main`, só com `openspec/changes/vehicle-documents/`. É a base da pilha

## 1. PR 1 — Schema, revisão compartilhada e van sem foto de documento

> Goal: o banco tem a `vehicle_document` com dono obrigatório, o `driver_document` só com os tipos da pessoa e com número, e os dados antigos no lugar certo. A van perde a foto de documento própria. O onboarding lê os documentos da van no lugar novo, sem mudar o que exige.
> Depends on: `VAN-86/spec-vehicle-documents` | Parallel with: PRs 1–3 da VAN-31
> Order: test → migration → enums → model → repository → DTO

- [ ] 1.1 Criar branch `VAN-86/1-schema` a partir da `VAN-86/spec-vehicle-documents`
- [ ] 1.2 Testes do repositório: `VehicleDocumentModel` grava e relê van, tipo, número, validade, arquivo e a revisão embutida; soft delete e restore; lista da van ordenada por validade desc e filtrada por tipo e status; `findDriverUserTokenByDocumentToken`; documentos de van removida não aparecem
- [ ] 1.3 Testes do `driver_document`: grava e relê `documentNumber` e a revisão pelo `@Embeddable`, com o mesmo resultado de antes para os documentos existentes
- [ ] 1.4 Testes da van: `VehicleResponseDTO` sem `photoDocumentUrl`; `POST`/`GET /api/vehicles/{token}/photo-document` → 404; `photo-front` e `photo-side` continuam funcionando
- [ ] 1.5 Migration `V71__vehicle_document_and_driver_document_cleanup.sql` com os passos do D6, na ordem; conferir que a aplicação sobe contra um PostgreSQL limpo e contra um banco com o seed antigo
- [ ] 1.6 `shared.document`: `DocumentStatus` e `ReviewMethod` (movidos do `driverdocument`, com os usos atualizados) e o `@Embeddable` `DocumentReview`; `grep` confirma que nada usa mais `DocumentStatusEnum` nem `ReviewMethodEnum`
- [ ] 1.7 `driverdocument.enums.DriverDocumentType` no lugar do `DocumentTypeEnum` (sem `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO`, `VEHICLE_PHOTO`; com `SCHOOL_DRIVER_COURSE`); `DriverDocumentModel` com `documentNumber` e a revisão embutida. Testes 1.3 verdes
- [ ] 1.8 `vehicledocument`: `VehicleDocumentType`, `VehicleDocumentModel` (van por `@ManyToOne` `LAZY`, revisão embutida, arquivo), `VehicleDocumentRepository`. Testes 1.2 verdes
- [ ] 1.9 Van sem a foto de documento: sai o `photoDocument` do `VehicleModel`, do `VehicleRepository` (`join fetch`), do `VehiclePhotoService`, do `VehicleMapper`, do `VehicleResponseDTO` e as duas rotas do `VehicleController`; `MediaSlot.VEHICLE_PHOTO_DOCUMENT` sai e `VEHICLE_DOCUMENT_FILE` entra. Testes 1.4 verdes
- [ ] 1.9a Onboarding lendo os documentos da van na `vehicle_document`, **com o mesmo conjunto exigido de hoje** (motorista: `MUNICIPAL_AUTHORIZATION`; pelo menos uma van ativa: `CRLV` e `VEHICLE_INSPECTION`), e o `DriverOnboardingDocumentsStepDTO` com `missingDriverTypes` e `vehicles` (por van ativa: `vehicleToken`, `plate`, `missingTypes`, documentos enviados); a mensagem do `submitOnboarding` lista os tipos do motorista e os de cada van com a placa. Testes do onboarding (`DriverOnboardingServiceTest`, `DriverOnboardingControllerTest`) adaptados e verdes
- [ ] 1.10 `make lint` + `./mvnw verify -B`
- [ ] 1.11 QA da regra 55 (`scripts/qa/vehicle-documents/qa.sh`, resultado no `qa.md`): montar por SQL, antes da `V71`, `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO` e `VEHICLE_PHOTO` no `driver_document`, documentos da pessoa e uma van com foto de documento; aplicar a `V71` e conferir que os tipos errados foram removidos sem virar documento da van, que os da pessoa ficaram e que a coluna da foto saiu; os `CHECK`s recusam tipo da van no `driver_document`, tipo da pessoa na `vehicle_document`, autorização da van sem número e documento da van sem validade; as permissões novas estão no bundle `ADMIN` e em nenhum outro

## 2. PR 2 — Revisão e rotas dos documentos

> Goal: o dono da van gerencia os documentos dela, o admin revisa, e nenhuma aprovação sobrevive a uma mudança do dono, nos documentos da van e nos da pessoa.
> Depends on: PR 1 | Parallel with: PRs 1–3 da VAN-31
> Order: test → policy → security → request DTO → service → controller → response DTO → mensagens

- [ ] 2.1 Criar branch `VAN-86/2-review-and-http` de dentro da branch do PR 1
- [ ] 2.2 Testes da `DocumentReviewPolicy` (spec `document-review`): rejeitar sem motivo → `reason_required`; aprovar sem arquivo → `file_required`; a revisão limpa zera status, revisor, data, método e motivo
- [ ] 2.3 Testes de slice dos documentos da van (spec `vehicle-documents`): dono cria, lista, lê, edita, remove e sobe arquivo (201/200/204); outro motorista → 403; cliente → 403; sem token → 401; van ou documento desconhecidos ou removidos → 404; tipo da pessoa → 400; autorização sem número → 400 `vehicle_document.number.required`; sem validade → 400; número com 65 caracteres → 400 e com 64 → 201, gravado sem espaços nas pontas; restore só com `restore_vehicle_document`; `PUT /status` pelo dono → 403 e pelo admin → 200
- [ ] 2.4 Testes da revisão limpa: `PUT` e novo arquivo num documento `APPROVED`, da van e do motorista → `PENDING` sem revisor; aprovar sem arquivo → 422 nos dois
- [ ] 2.5 Testes dos documentos do motorista (spec `driver-documents`): tipo da van → 400; `MUNICIPAL_AUTHORIZATION` sem número → 400 `driver_document.number.required`; `SCHOOL_DRIVER_COURSE` com número → 201; um motorista com token emitido antes de trocar o e-mail (`sub` com o e-mail antigo) lista os próprios documentos → 200
- [ ] 2.6 `shared.document.DocumentReviewPolicy` pura. Testes 2.2 verdes
- [ ] 2.7 `PermissionEnum` com as cinco permissões de documento da van; `SecurityEvaluator.isVehicleDocumentOwner`
- [ ] 2.8 `VehicleDocumentRequestDTO` e `VehicleDocumentResponseDTO`; o `DriverDocumentStatusUpdateRequestDTO` vira o `shared.document.dto.DocumentStatusUpdateRequestDTO`, usado pelos dois documentos; `VehicleDocumentMapper`; `VehicleDocumentService` e `VehicleDocumentFileService`; `VehicleDocumentController` com as rotas do D4. Testes 2.3 verdes
- [ ] 2.9 `DriverDocumentRequestDTO` e `DriverDocumentResponseDTO` com `documentNumber`; `DriverDocumentService` com a obrigatoriedade do número, a revisão pela policy e o chamador pelo `uid`; `DriverDocumentFileService.replace` com a revisão limpa; `DriverDocumentController` com `Authentication` no lugar do `Jwt`. Testes 2.4 e 2.5 verdes
- [ ] 2.10 Mensagens `vehicle_document.*`, `driver_document.number.required` e `document.review.*` em `messages.properties` e `messages_pt_BR.properties`
- [ ] 2.11 `make lint` + `./mvnw verify -B`
- [ ] 2.12 QA da regra 55: o ciclo completo de um documento da van com tokens reais (motorista cria, sobe PDF, admin aprova, motorista troca o arquivo → `PENDING`, admin aprova de novo); cada recusa do 2.3 e do 2.5; um terceiro tentando ler o arquivo; a van removida esconde os documentos e restaurá-la os devolve; regressão das rotas de foto da van e de `/api/driver-documents`

## 3. PR 3 — Credenciais, onboarding e aprovação

> Goal: um serviço diz se o motorista e uma van cumprem a lei numa data, o motorista vê o que falta em cada van, e o onboarding e a aprovação exigem o conjunto legal.
> Depends on: PR 2 | Parallel with: PRs 1–3 da VAN-31
> Order: test → enums → policy → service → controller → response DTO → onboarding → seeders

- [ ] 3.1 Criar branch `VAN-86/3-credentials-and-onboarding` de dentro da branch do PR 2
- [ ] 3.2 Testes da `SchoolTransportCredentialPolicy` (spec `school-transport-credentials`): tudo aprovado e válido → `failed` vazio, com número e validade de cada credencial; documento da outra van não conta; vencido na véspera da data → falha; válido até a própria data → passa; `PENDING` e `REJECTED` → falha; CNH `B` → falha, `AD`, `D`, `E` e `ae` → passam; CNH inativa ou vencida → falha; dois documentos válidos → vale o de validade mais distante; `MUNICIPAL_AUTHORIZATION` aprovada entra em `credentials` e nunca em `failed`; van de outro motorista ou inativa → os três requisitos da van falham
- [ ] 3.3 Testes do serviço: três consultas para qualquer número de documentos (`SqlStatementCounter`); slice do `GET /api/vehicles/{token}/school-transport-credentials` (dono → 200, admin com `show_vehicle` → 200, outro motorista → 403, sem token → 401, van removida → 404)
- [ ] 3.4 Testes do onboarding (spec `driver-onboarding-school-transport`): uma van completa de duas → passo completo; falta o curso → incompleto com `SCHOOL_DRIVER_COURSE`; `MUNICIPAL_AUTHORIZATION` ausente não pesa; CNH `B` → `cnh_category`; a resposta traz `missingDriverTypes` e `vehicles` com `missingTypes` por van ativa
- [ ] 3.4a Testes do envio aos poucos: salvar só o CRLV deixa o passo incompleto e mantém o documento; acrescentar os outros depois completa o passo; `submit-onboarding` sem a autorização de nenhuma van → 422 com o tipo e a placa, sem mudar status nem documentos; depois de recusado, trocar o arquivo e reenviar → `UNDER_REVIEW`; criar e trocar documento com o motorista `UNDER_REVIEW` → aceito
- [ ] 3.5 Testes da aprovação: com a vistoria da única van pendente → 422 `driver.onboarding.documents_not_approved`, o motorista continua `UNDER_REVIEW`; com tudo aprovado → `APPROVED`
- [ ] 3.6 `schooltransportcredential`: `CredentialRequirement`, `SchoolTransportCredentialPolicy`, `SchoolTransportCredentialsDTO` e `Credential` (o contrato do D5 da `contract-generation`), `SchoolTransportCredentialService.findCredentials`. Testes 3.2 e a primeira parte do 3.3 verdes
- [ ] 3.7 `SchoolTransportCredentialsResponseDTO` e a rota no `VehicleController` (ou num controller próprio do pacote), com `@PreAuthorize("hasAuthority('show_vehicle') or @sec.isVehicleOwner(#token, authentication)")`. Testes 3.3 verdes
- [ ] 3.8 `DriverOnboardingService`: o conjunto exigido passa ao legal completo (motorista: `SCHOOL_DRIVER_COURSE` e `CRIMINAL_RECORD`; van: `CRLV`, `VEHICLE_INSPECTION` e `SCHOOL_VEHICLE_AUTHORIZATION`; sai a `MUNICIPAL_AUTHORIZATION`), `cnh_category`, `approve` pelas credenciais. Testes 3.4, 3.4a e 3.5 verdes
- [ ] 3.9 Mensagem `driver.onboarding.documents_not_approved` em `messages.properties` e `messages_pt_BR.properties`
- [ ] 3.10 `DriverDocumentSeeder` e `VehicleDocumentSeeder` com o conjunto legal `APPROVED`, número nas autorizações e validade relativa a hoje, idempotentes; subir com o seed ligado e conferir pelo endpoint de credenciais que a van do seed passa
- [ ] 3.11 `make lint` + `./mvnw verify -B`
- [ ] 3.12 QA da regra 55: um motorista novo do zero (cadastro, van, CNH `B` → `cnh_category`, CNH `D`, documentos, onboarding completo, envio, aprovação recusada com documento pendente, admin aprova documentos e motorista), conferindo o endpoint de credenciais em cada passo; segunda van sem documentos não afeta a aprovação e aparece com os três requisitos faltando; vencer a vistoria por SQL e conferir a falha; o motorista do seed passa; regressão do QA da `service-request-proposal`
