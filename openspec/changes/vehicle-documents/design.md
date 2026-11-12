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

## Goals / Non-Goals

**Goals**

- O banco garante o dono de cada documento: a van para os documentos da van, o motorista para os da pessoa. Nenhum dono é condicional ao tipo
- Um documento aprovado sempre corresponde ao que o admin viu
- Uma pergunta, uma resposta: as credenciais de uma van numa data saem de um único serviço, usado pelo onboarding, pela aprovação, pela proposta e pelo endpoint do motorista

**Non-Goals**

- Tabela genérica de documentos com dono polimórfico (D1)
- Exigências por cidade ou estado (pergunta jurídica 4 do spike)
- Notificar vencimento

## Decisions

### D1 — Duas tabelas concretas, revisão compartilhada

`vehicle_document` com `vehicle_id NOT NULL` e `driver_document` com `driver_id NOT NULL`, cada uma com o próprio enum de tipo (`VehicleDocumentType`, `DriverDocumentType`). Os campos de revisão (`status`, `review_method`, `external_check_id`, `rejection_reason`, `reviewed_by`, `reviewed_at`) viram um `@Embeddable` `DocumentReview`, e as regras de revisão uma `DocumentReviewPolicy` pura (D3). Os dois ficam em `br.com.vanep.shared.document`, junto com `DocumentStatus` e `ReviewMethod`, que saem do `driverdocument` (regra 5: o que duas features usam vai para a área compartilhada).

**Rejeitado:**
- **`vehicle_id` anulável em `driver_document`, com `CHECK` por tipo.** O banco ficaria consistente, mas o dono continuaria condicional ao tipo: todo consumidor precisa lembrar de filtrar pela van, e o documento continua "do motorista" quando a van é removida.
- **Tabela `document` com `owner_type` e `owner_id`.** É FK polimórfica, sem integridade referencial, e o modelo do spike já recusa isso para a agenda.
- **Colunas no `vehicle` e no `driver`** (`inspection_valid_until`, …). Perdem o arquivo, a revisão e o histórico: a vistoria é semestral, e cada renovação apagaria a anterior.
- **`@MappedSuperclass` com tudo.** Mistura identidade e dono na herança. O `@Embeddable` compartilha só o que é igual de fato (a revisão), e cada tabela fica explícita.

`is_active` não vai para a `vehicle_document`: o soft delete já diz se o documento existe, e o `is_active` do `driver_document` só é lido no onboarding. A `driver_document` mantém a coluna (regra 2) e passa a ser lida como `deleted_at is null and is_active`, sem caminho novo de escrita. `notified_at` também não vai: nada escreve nele.

### D2 — Tipos, número e validade

| Tipo | Dono | Número | Validade |
|---|---|---|---|
| `CRLV` | van | opcional | obrigatória |
| `VEHICLE_INSPECTION` | van | opcional | obrigatória |
| `SCHOOL_VEHICLE_AUTHORIZATION` | van | **obrigatório** | obrigatória |
| `MUNICIPAL_AUTHORIZATION` | pessoa | **obrigatório** | opcional |
| `SCHOOL_DRIVER_COURSE` | pessoa | opcional | opcional |
| `CRIMINAL_RECORD` | pessoa | opcional | opcional |
| `RESIDENCE_PROOF`, `OTHER` | pessoa | opcional | opcional |

`document_number varchar(64)`, gravado sem espaços nas pontas. A obrigatoriedade por tipo é conferida no serviço (400), porque depende do valor de outro campo. No banco ficam um `CHECK` para os tipos de cada tabela e um `CHECK (document_type <> 'SCHOOL_VEHICLE_AUTHORIZATION' or document_number is not null)`. A validade é `NOT NULL` na `vehicle_document`, porque os três tipos da van têm validade.

Validade opcional na certidão criminal é intencional. A certidão costuma valer 90 dias no emissor, e exigir uma válida a cada proposta obrigaria o motorista a renovar a cada trimestre. O que a lei pede (CTB 329) é a comprovação para exercer a atividade, e o admin confere a data na revisão.

### D3 — A revisão: só o admin, aprovar exige arquivo, mudar volta a pendente

A `DocumentReviewPolicy` decide sem JPA:
- **Rejeitar exige motivo** (400 `document.review.reason_required`).
- **Aprovar exige** arquivo (422 `document.review.file_required`).
- **Toda mudança do dono** (`PUT`, novo arquivo) produz uma revisão limpa: `PENDING`, sem revisor, data, método nem motivo.

O `DriverDocumentFileService.replace` passa a aplicar a revisão limpa, o que fecha o furo de hoje, e o arquivo de documento da van segue a mesma regra. O revisor é resolvido pelo `uid` (D7).

### D4 — Rotas da van

| Rota | Quem |
|---|---|
| `GET`, `POST /api/vehicles/{token}/documents` | dono (`@sec.isVehicleOwner`) ou `list_vehicle_documents` (`GET`) / `update_vehicle_document` (`POST`) |
| `GET`, `PUT`, `DELETE /api/vehicle-documents/{token}` | dono (`@sec.isVehicleDocumentOwner`) ou `show_`/`update_`/`delete_vehicle_document` |
| `POST`, `GET /api/vehicle-documents/{token}/file` | dono ou `update_`/`show_vehicle_document` |
| `PUT /api/vehicle-documents/{token}/status` | `update_vehicle_document` (só admin) |
| `POST /api/vehicle-documents/{token}/restore` | `restore_vehicle_document` (só admin) |
| `GET /api/vehicles/{token}/school-transport-credentials` | dono ou `show_vehicle` |

As rotas espelham as de `/api/driver-documents`, que o admin já conhece.

**Permissões:** criar não precisa de permissão para o dono, porque o `@sec` da van já basta. O admin cria pela permissão `update_vehicle_document`, sem uma `create_` própria, já que ninguém mais cria documento de van alheia. As cinco permissões novas (`list_vehicle_documents`, `show_vehicle_document`, `update_vehicle_document`, `delete_vehicle_document`, `restore_vehicle_document`) entram no `PermissionEnum` e só no bundle `ADMIN`, pela `V71`.

**Ownership:** `@sec.isVehicleDocumentOwner(token, authentication)` entra no `SecurityEvaluator` (regra 22), pela consulta `findDriverUserTokenByDocumentToken` do repositório, como o do documento do motorista.

A van removida esconde os documentos dela: as consultas exigem `vehicle.deleted_at is null`, e restaurar a van os traz de volta. Remover a van não remove os documentos, porque a FK e o soft delete da van bastam.

Listar ordena por `expires_at desc, created_at desc`, com filtro por `documentType` e `status`, paginado. O slot do arquivo é `MediaSlot.VEHICLE_DOCUMENT_FILE` (`vehicle-document/file`, documento, `PRIVATE`), igual ao `DRIVER_DOCUMENT_FILE`.

### D5 — Credenciais: um serviço, um enum de requisitos

`br.com.vanep.schooltransportcredential`, com o formato que a `contract-generation` já espera:

```
SchoolTransportCredentialService.findCredentials(Long driverId, Long vehicleId, LocalDate date)
  → SchoolTransportCredentialsDTO(List<Credential> credentials, Set<CredentialRequirement> failed)
Credential(CredentialRequirement requirement, String number, LocalDate validUntil)
CredentialRequirement: DRIVER_LICENSE, SCHOOL_DRIVER_COURSE, CRIMINAL_RECORD,
                       VEHICLE_REGISTRATION, VEHICLE_INSPECTION, SCHOOL_VEHICLE_AUTHORIZATION,
                       MUNICIPAL_AUTHORIZATION (opcional: nunca entra em failed)
```

A regra de cada requisito (spec `school-transport-credentials`) fica numa `SchoolTransportCredentialPolicy` pura, que recebe as listas já carregadas (CNHs, documentos do motorista, documentos da van) e a data. O serviço faz três consultas, uma por fonte, sem N+1.

Entre vários documentos que satisfazem o requisito, vale o de validade mais distante; sem validade, o mais recente.

**CNH de categoria D ou E:** a `category` é texto livre (`D`, `AD`, `AE`…), então a regra é "contém `D` ou `E`", sem diferenciar maiúsculas. Validar o formato da categoria no cadastro da CNH fica fora: muda o `DriverCnhRequestDTO` e os dados que já existem, sem ganho para esta change.

O endpoint do motorista (`GET /api/vehicles/{token}/school-transport-credentials`) devolve a mesma avaliação em JSON (`SchoolTransportCredentialsResponseDTO`: cada requisito com `satisfied`, `number` e `validUntil`), com data de hoje na zona da aplicação.

### D6 — A `V71` só muda a estrutura

Em ordem, numa migration só:
1. **Cria `vehicle_document`:** `id`, `token`, `vehicle_id`, `document_type varchar(50)`, `document_number varchar(64)`, `file_media_id`, `expires_at date not null`, as colunas da revisão, `created_at`, `updated_at`, `deleted_at`. Índices: único `token where deleted_at is null` e `(vehicle_id, document_type) where deleted_at is null`.
2. **Acrescenta** `driver_document.document_number varchar(64)`.
3. **Remove logicamente** os `driver_document` `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO` e `VEHICLE_PHOTO`. Os arquivos continuam no `media_file`.
4. **Remove** a `vehicle.photo_document_media_id` (a FK primeiro), sem converter a foto.
5. **`CHECK`s** de tipo nas duas tabelas e o do número da autorização da van.
6. **Permissões:** as cinco novas no bundle `ADMIN`.
7. **Comments.**

Nenhum dado é movido. Nenhum app chama as rotas de documento nem a da foto de documento da van, o banco do servidor de deploy não tem documentos de motoristas reais, e o seed cria os documentos da van na tabela nova (PR 3). Os documentos dos tipos da pessoa ficam como estão, inclusive uma `MUNICIPAL_AUTHORIZATION` antiga sem número: a obrigatoriedade do número vale para o que for gravado pela API daqui em diante.

**Rejeitado: mover o `CRLV` e a vistoria do motorista para a van, e converter a foto de documento da van em `CRLV`.** Seriam umas 60 linhas de SQL, a parte mais arriscada da migration (escolher a van quando o motorista tem várias, decidir se a revisão vale), para preservar dados que só o seed tinha.

### D7 — O chamador pelo `uid`

`DriverDocumentController` e os controllers novos usam `SecurityHelper.requireCallerUid(authentication)` e `UserService.requireByToken`, como o resto da API. O `findByEmail(jwt.getSubject())` sai do `DriverDocumentService`. O mesmo padrão existe no `DriverCnhController` e no `AssistantController`. Não mexo neles aqui, mas registro como dívida (R4).

### D8 — Onboarding e aprovação

- **Passo de documentos:** exige `SCHOOL_DRIVER_COURSE` e `CRIMINAL_RECORD` do motorista e, de pelo menos uma van ativa, `CRLV`, `VEHICLE_INSPECTION` e `SCHOOL_VEHICLE_AUTHORIZATION`, todos com arquivo e não `REJECTED`. A resposta (`DriverOnboardingDocumentsStepDTO`) passa a ter `missingDriverTypes` e `vehicles` (por van ativa: `vehicleToken`, `plate`, `missingTypes`), no lugar do `missingTypes` único.
- **Passo da CNH:** acrescenta `cnh_category`, com a mesma regra do D5.
- **Aprovação:** chama o serviço de credenciais para cada van ativa e exige que alguma passe (422 `driver.onboarding.documents_not_approved`). Assim, todo motorista aprovado consegue oferecer pelo menos uma van no dia da aprovação. O admin revisa os documentos antes de aprovar o motorista, e hoje faz isso pela API (R3).

O PR 1 já leva o onboarding para os documentos da van na tabela nova e para o DTO por van, com o conjunto exigido de antes, porque os tipos da van saem do enum do motorista nele. O PR 3 só troca o conjunto exigido.

**Rejeitado: aprovar o motorista aprovando os documentos junto, em cascata.** Esconderia a revisão de cada arquivo atrás de um botão só.

### D9 — Seed

`DriverDocumentSeeder` e um `VehicleDocumentSeeder` novo criam, para os motoristas e as vans do seed, todos os documentos do conjunto legal `APPROVED`, com número nas autorizações e validade **relativa a hoje** (`LocalDate.now().plusMonths(…)`), para nunca vencer num banco local. O seed atual vence em 31/12/2026, e a certidão já venceu. A CNH do seed continua `D`. Conferir com o endpoint de credenciais que a van do seed passa.

## Risks / Trade-offs

| # | Risco | Mitigação |
|---|---|---|
| **R1** | A suíte roda em H2 sem Flyway: a `V71` (remoções, coluna da foto, `CHECK`s, permissões) não é exercitada em teste | QA da regra 55 contra o PostgreSQL com dados montados antes da migration: documentos de van e fotos no `driver_document`, documentos da pessoa, van com foto de documento |
| **R2** | Motoristas aprovados antes desta change não têm curso, autorização da van nem número: continuam aprovados, mas nenhuma van deles passa nas credenciais, e a fase 3 vai recusar as propostas | Não há usuários reais. O seed sai completo (D9); os bancos locais mandam os documentos pela API ou são recriados. O endpoint de credenciais diz o que falta |
| **R3** | Sem tela de revisão no admin, cada documento novo (inclusive de uma van nova) espera alguém chamar a API para aprovar | É o que acontece hoje com a aprovação do motorista. A tela de revisão é uma task do frontend; o `GET /api/driver-documents?status=PENDING` e o equivalente da van listam o que espera revisão |
| **R4** | `DriverCnhController` e `AssistantController` continuam resolvendo o chamador por `jwt.getSubject()` | Fora do escopo; anotar como dívida no Genwork. Esta change corrige só os documentos |
| **R5** | Os números das migrations colidem: a VAN-57 usa a `V69` e a `V70`, e a `contract-generation` reservou a `V72` e a `V73` | Esta change usa a `V71`. Reconfirmar antes do merge; quem entra por último renumera |
| **R6** | A regra "categoria contém D ou E" aceita texto estranho (`"DE"` digitado à mão) | O cadastro da CNH já é revisado na aprovação; validar o formato da categoria é uma melhoria separada |
| **R7** | A certidão criminal sem validade obrigatória pode ficar antiga | O admin confere a data na revisão; a renovação periódica entra com a fase 8 (documento vencido), se o jurídico pedir |
| **R8** | Não há cadastro nacional de quais municípios exigem autorização própria: com o alvará municipal opcional, a API não sabe quando ele falta | Até as cidades de lançamento serem definidas, o contrato traz a declaração do motorista de cumprir a regulamentação local (`contract-generation`, R12). Depois, uma change própria marca por município a exigência (mantida pelo admin a partir da pesquisa jurídica de cada cidade aberta), e o `SchoolTransportCredentialService` passa a receber também a cidade do serviço, sem mudar quem o chama |

## Migration Plan

| Migration | PR | Conteúdo |
|---|---|---|
| **V71** | 1 | D6 inteiro: `vehicle_document`, `driver_document.document_number`, documentos de van e fotos removidos do `driver_document`, coluna da foto removida, `CHECK`s, permissões do `ADMIN`, comments |

Nenhuma migration aplicada é editada (regra 2). **Rollback:** a `V71` remove uma coluna da `vehicle`, então voltar exige uma migration nova. Sem dados de produção, o caminho é recriar o banco local.

## Rollout — grafo de dependência e plano de PRs

```
main
 └─ VAN-86/spec-vehicle-documents          (só a change do openspec)
      └─ VAN-86/1-schema
           └─ VAN-86/2-review-and-http
                └─ VAN-86/3-credentials-and-onboarding   ← destrava o PR 4 da VAN-31
```

A task desta change no Genwork é a **VAN-86**.

| PR | Conteúdo | Depends on | Parallel with |
|---|---|---|---|
| 0 | `VAN-86/spec-vehicle-documents`: só `openspec/changes/vehicle-documents/` | — | PRs 1–3 da VAN-31 |
| 1 | `V71`; `shared.document` (`DocumentReview`, `DocumentStatus`, `ReviewMethod`); `VehicleDocumentType`, `VehicleDocumentModel`, repositório; `DriverDocumentType` e `document_number` no model; o `vehicle` sem a foto de documento (model, DTO, rotas, slot); o onboarding lendo os documentos da van na tabela nova, com o conjunto exigido de hoje e o DTO por van | 0 | PRs 1–3 da VAN-31 |
| 2 | `DocumentReviewPolicy`; serviço, DTOs, mapper e controller dos documentos da van; `@sec.isVehicleDocumentOwner`; permissões no `PermissionEnum`; documentos do motorista com os tipos novos, número, revisão limpa no arquivo e chamador pelo `uid`; mensagens | 1 | PRs 1–3 da VAN-31 |
| 3 | `SchoolTransportCredentialPolicy` e serviço, endpoint de credenciais; onboarding com o conjunto legal e a CNH D, e aprovação; seeds com datas relativas | 2 | PRs 1–3 da VAN-31 |

A pilha segue o modelo da VAN-30: cada PR nasce do anterior e aponta para ele como `--base`. O PR 3 precisa estar na `main` antes do PR 4 da `contract-generation`. O teto é de cerca de 1.000 linhas por PR, com testes, script e `qa.md`. O PR 2 é o maior: se passar muito, os documentos do motorista saem para um PR próprio entre o 2 e o 3.
