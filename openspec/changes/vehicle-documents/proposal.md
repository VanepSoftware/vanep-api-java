## Why

O contrato de transporte escolar (fase 3, `contract-generation`) precisa dizer que o motorista e a van que ele oferece podem fazer transporte escolar, com o número e a validade de cada documento (CTB arts. 136, 138 e 329). Hoje a API não consegue responder isso com segurança:

- **Documentos da van presos ao motorista.** `driver_document` mistura documentos da pessoa (certidão, comprovante de residência), documentos da van (`CRLV`, `VEHICLE_INSPECTION`) e fotos (`PROFILE_PHOTO`, `VEHICLE_PHOTO`, que já moram nos slots de mídia). Um motorista com duas vans não tem como dizer de qual delas é a vistoria, e remover uma van não leva os documentos dela.
- **Documento da van em dois lugares.** O `vehicle` tem uma foto de documento própria (`photo_document_media_id`) que duplica o CRLV.
- **Sem número e sem os documentos que a lei pede.** Nenhum documento tem número. A autorização escolar do veículo (CTB 136) e o curso especializado do condutor (CTB 138 V) não existem. A `MUNICIPAL_AUTHORIZATION` obrigatória é ambígua (do veículo ou do operador), e a CNH não confere a categoria D.
- **A revisão tem furos.** Trocar o arquivo de um documento aprovado mantém o `APPROVED`. A aprovação do motorista não confere se os documentos foram aprovados. Nada confere a validade.

Nenhum app chama as rotas de documento ainda, então este é o momento mais barato de refazer: depois que o app fizer o upload, a mudança vira **BREAKING**.

## What Changes

- **Documentos da van (`vehicle_document`):** tabela própria, com a van obrigatória e os tipos `CRLV`, `VEHICLE_INSPECTION` e `SCHOOL_VEHICLE_AUTHORIZATION` (autorização do órgão de trânsito para transporte escolar, CTB 136).
  - **Rotas:** `/api/vehicles/{token}/documents` (listar e criar) e `/api/vehicle-documents/{token}` (ler, editar, remover, restaurar, arquivo e revisão).
  - **Acesso:** o dono da van ou o admin.
  - **Histórico:** a van guarda o histórico (várias vistorias), e vale a aprovada e válida mais recente
- **Documentos da pessoa (`driver_document`):** ficam só os tipos da pessoa: `MUNICIPAL_AUTHORIZATION` (alvará ou permissão do operador, quando a cidade exige), `SCHOOL_DRIVER_COURSE` (curso especializado, CTB 138 V, novo), `CRIMINAL_RECORD`, `RESIDENCE_PROOF` e `OTHER`. Saem `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO` e `VEHICLE_PHOTO`. **BREAKING**, sem consumidor
- **Número do documento:** `documentNumber` nos dois tipos de documento, obrigatório nas autorizações (`SCHOOL_VEHICLE_AUTHORIZATION`, `MUNICIPAL_AUTHORIZATION`); validade obrigatória no CRLV, na vistoria e na autorização do veículo
- **Revisão comum aos dois:**
  - só o admin muda o status;
  - aprovar exige arquivo;
  - editar o documento ou trocar o arquivo volta o status para `PENDING` e limpa a revisão (corrige o furo de hoje);
  - os campos de revisão são os mesmos nas duas tabelas
- **Credenciais de transporte escolar:**
  - **O que contam:** o motorista e uma van cumprem a lei numa data quando têm a CNH ativa e válida de categoria D ou E, o curso especializado e a certidão criminal aprovados, e o CRLV, a vistoria e a autorização escolar da van aprovados e válidos. O alvará municipal é opcional e entra quando estiver aprovado e válido.
  - **Endpoint:** `GET /api/vehicles/{token}/school-transport-credentials` diz, para o dono e para o admin, o que falta em cada van.
  - **Uso:** é o que a fase 3 consulta no envio e no aceite da proposta
- **Onboarding:**
  - o passo de documentos passa a exigir o curso e a certidão do motorista, e o CRLV, a vistoria e a autorização de **pelo menos uma van ativa**;
  - o passo da CNH passa a exigir categoria D ou E;
  - a `MUNICIPAL_AUTHORIZATION` deixa de ser obrigatória;
  - **aprovar o motorista** passa a exigir que esses documentos estejam `APPROVED` (422).

  **BREAKING** no `GET /api/drivers/me/onboarding`, sem consumidor
- **Foto de documento da van removida:** saem a coluna `vehicle.photo_document_media_id`, o slot `VEHICLE_PHOTO_DOCUMENT`, as rotas `POST`/`GET /api/vehicles/{token}/photo-document` e o `photoDocumentUrl` do JSON da van. **BREAKING** leve: o app só lê o campo, que sempre vem nulo
- **Quem chama:** os controllers de documento passam a resolver o chamador pelo `uid` do token (`SecurityHelper`), e não por `findByEmail(jwt.getSubject())`

**Fora de escopo:**

- Documentos exigidos por cidade ou estado (pergunta jurídica 4 do spike): o conjunto é fixo e nacional, o alvará municipal é opcional, e o contrato traz a declaração do motorista de cumprir a regulamentação local. A exigência por município entra numa change própria quando as cidades de lançamento estiverem definidas (R8 do `design.md`)
- Efeito de um documento que vence durante um contrato em vigor (aviso, rescisão sem multa): fase 8 do spike
- Conferência automática de documentos (`review_method = AUTO`, `external_check_id`): os campos continuam, sem integração
- Tela de revisão no admin (Next.js): o admin revisa pela API, como hoje
- O uso das credenciais na proposta e no contrato: `contract-generation` (VAN-31)

## Capabilities

### New Capabilities

- `vehicle-documents`: os documentos da van, com dono obrigatório, histórico e rotas próprias; a van sem foto de documento própria.
- `driver-documents`: os documentos da pessoa, só com os tipos da pessoa, com número e com o chamador resolvido pelo token.
- `document-review`: a revisão comum aos documentos do motorista e da van (só o admin revisa, aprovar exige arquivo, mudar volta a pendente).
- `school-transport-credentials`: as credenciais do motorista e de uma van numa data, e o endpoint que mostra o que falta.
- `driver-onboarding-school-transport`: o onboarding e a aprovação do motorista exigindo o conjunto legal de documentos, uma van completa e a CNH D.

### Modified Capabilities

Nenhuma em `openspec/specs/`. As changes `driver-document-crud`, `vehicle-crud`, `driver-onboarding` e `admin-driver-approval` não foram arquivadas, então não há spec base para delta delas. Ao arquivar, os specs desta change substituem os requisitos de tipos de documento, de foto de documento da van e de documentos obrigatórios daquelas changes.

## Impact

- **Schema:** `V71`. Ela faz:
  - cria a `vehicle_document`;
  - acrescenta `driver_document.document_number`;
  - remove do `driver_document` os documentos de van e as fotos, sem mover para a van (nenhum app os enviou);
  - remove a `vehicle.photo_document_media_id`, sem converter a foto;
  - dá as permissões de documento da van ao bundle `ADMIN`.

  A `main` está em `V68`, a VAN-57 usa a `V69` e a `V70`, e a `contract-generation` reservou a `V72` e a `V73`. Reconfirmar antes do merge (R5)
- **Pacotes novos:** `br.com.vanep.vehicledocument`, `br.com.vanep.schooltransportcredential`, `br.com.vanep.shared.document` (revisão comum: `@Embeddable`, status, método e a policy)
- **Pacotes alterados:**
  - `driverdocument`: tipos, número, revisão, chamador;
  - `vehicle`: sem a foto de documento;
  - `media`: sem o slot `VEHICLE_PHOTO_DOCUMENT`, mais o slot `VEHICLE_DOCUMENT_FILE`;
  - `driver`: onboarding e aprovação;
  - `drivercnh`: categoria;
  - `auth.security`: `@sec.isVehicleDocumentOwner` e as permissões novas;
  - `seed`: documentos com datas relativas a hoje
- **API:**
  - **novas:** `/api/vehicles/{token}/documents`, `/api/vehicle-documents/{token}` (e `/file`, `/status`, `/restore`), `GET /api/vehicles/{token}/school-transport-credentials`;
  - **mudam:** os tipos aceitos em `/api/driver-documents`, `documentNumber` nos dois DTOs, e a resposta do `GET /api/drivers/me/onboarding`;
  - **saem:** `/api/vehicles/{token}/photo-document` e o `photoDocumentUrl`
- **Permissões:** `list_vehicle_documents`, `show_vehicle_document`, `update_vehicle_document`, `delete_vehicle_document` e `restore_vehicle_document`, só no bundle `ADMIN`; o dono da van usa o `@sec`
- **Bancos locais:** motoristas já aprovados sem os documentos novos continuam aprovados, mas não passam nas credenciais até mandar os documentos e o admin aprová-los (R2)
- **Task:** VAN-86 no Genwork (`[Contract e Proposal - 3a] Documentos da van e credenciais de transporte escolar`), dependência da VAN-31
