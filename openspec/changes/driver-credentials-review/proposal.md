## Why

O contrato de transporte escolar (fase 3, `contract-generation`) precisa dizer que o motorista e a van que ele oferece podem fazer transporte escolar, com o número e a validade de cada documento (CTB arts. 136, 138 e 329). Hoje a API não consegue responder isso com segurança:

- **Documentos da van presos ao motorista.** `driver_document` mistura documentos da pessoa (certidão, comprovante de residência), documentos da van (`CRLV`, `VEHICLE_INSPECTION`) e fotos (`PROFILE_PHOTO`, `VEHICLE_PHOTO`, que já moram nos slots de mídia). Um motorista com duas vans não tem como dizer de qual delas é a vistoria, e remover uma van não leva os documentos dela.
- **Documento da van em dois lugares.** O `vehicle` tem uma foto de documento própria (`photo_document_media_id`) que duplica o CRLV.
- **Sem número e sem os documentos que a lei pede.** Nenhum documento tem número. A autorização escolar do veículo (CTB 136) e o curso especializado do condutor (CTB 138 V) não existem. A `MUNICIPAL_AUTHORIZATION` obrigatória é ambígua (do veículo ou do operador), e a CNH não confere a categoria D.
- **A revisão tem furos.** Trocar o arquivo de um documento aprovado mantém o `APPROVED`. A aprovação do motorista não confere se os documentos foram aprovados. Nada confere a validade.
- **O motorista digita o que já está no documento.** Ele fotografa a CNH e depois preenche o número, a categoria, a emissão e a validade que estão na foto, e o mesmo nos documentos da van. É esforço em dobro para o motorista, e o dado digitado não vale nada sem o admin conferir contra o arquivo.

O admin também precisa revisar tudo isso numa tela, e a API de revisão de hoje não serve para uma:

- **Renovar tira a credencial.** Editar ou trocar o arquivo de um documento aprovado o volta a pendente. Quem renova a vistoria antes de vencer fica sem poder mandar proposta até o admin revisar.
- **Sem proteção contra decisão velha.** Dois admins podem decidir o mesmo documento, e um admin pode aprovar um arquivo que o motorista trocou enquanto ele olhava.
- **A CNH não é revisada,** mas é a credencial principal do contrato.
- **O motivo da recusa é texto livre,** e o motorista não fica sabendo que um documento foi recusado.
- **Revisar usa a mesma permissão de editar.**
- **Sem leitura agregada.** Não há como achar quem precisa de atenção (aguardando aprovação, documento pendente, vencido ou a vencer) nem ver a situação de um motorista numa página.

Nenhum app chama as rotas de documento ainda, então este é o momento mais barato de refazer: depois que o app fizer o upload, a mudança vira **BREAKING**.

## What Changes

- **Documentos da van (`vehicle_document`):** tabela própria, com a van obrigatória e os tipos `CRLV`, `VEHICLE_INSPECTION` e `SCHOOL_VEHICLE_AUTHORIZATION` (autorização do órgão de trânsito para transporte escolar, CTB 136).
  - **Rotas:** `/api/vehicles/{token}/documents` (listar e criar) e `/api/vehicle-documents/{token}` (ler, editar, remover, restaurar, arquivo e revisão).
  - **Acesso:** o dono da van ou o admin.
  - **Histórico:** a van guarda o histórico (várias vistorias), e vale a aprovada e válida mais recente
- **Documentos da pessoa (`driver_document`):** ficam só os tipos da pessoa: `MUNICIPAL_AUTHORIZATION` (alvará ou permissão do operador, quando a cidade exige), `SCHOOL_DRIVER_COURSE` (curso especializado, CTB 138 V, novo), `CRIMINAL_RECORD`. Saem `CRLV`, `VEHICLE_INSPECTION`, `PROFILE_PHOTO` e `VEHICLE_PHOTO`, e também `RESIDENCE_PROOF` e `OTHER`: o CTB não pede comprovante de residência ao condutor escolar, nenhuma regra da aprovação lê esses dois tipos, e guardar um comprovante sem uso é dado pessoal a mais (LGPD art. 6º, III). **BREAKING**, sem consumidor
- **O motorista manda só o arquivo (D2):**
  - cada documento e a CNH são enviados num `POST` multipart, só com o tipo e o arquivo;
  - os `PUT` de dados saem;
  - o revisor preenche os dados na aprovação, olhando o arquivo: `documentNumber` nos dois tipos de documento, obrigatório nas autorizações (`SCHOOL_VEHICLE_AUTHORIZATION`, `MUNICIPAL_AUTHORIZATION`), e validade obrigatória no CRLV, na vistoria e na autorização do veículo. Na CNH (PR 6), o registro, a categoria, a emissão e a validade;
  - o banco garante que um envio aprovado tem os dados obrigatórios.
- **Revisão comum aos dois:**
  - só o admin muda o status;
  - aprovar exige arquivo e os dados do tipo;
  - trocar o arquivo volta o status para `PENDING` e limpa a revisão (corrige o furo de hoje);
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
- **Envio revisado é final (PRs 4 e 5):**
  - um documento `PENDING` ainda pode trocar de arquivo; um aprovado ou recusado devolve 409;
  - renovar ou reenviar é um envio novo do mesmo tipo, com no máximo um pendente por dono e tipo;
  - o aprovado continua valendo até o novo ser aprovado, e então o anterior é substituído: perde o arquivo e fica só a decisão;
  - os arquivos só são apagados do storage depois do commit.

  **BREAKING** no `/file` dos documentos, sem consumidor

- **Decisão com versão e motivo estruturado (PR 5):**
  - o `PUT …/status` leva a `version` que o admin viu (409 se mudou);
  - recusar exige um código (`ILLEGIBLE`, `EXPIRED`, `DATA_MISMATCH`, `WRONG_TYPE`, `INCOMPLETE`, `NOT_ELIGIBLE`, `OTHER`), e a mensagem fica opcional, salvo com `OTHER`;
  - recusar o cadastro do motorista também ganha um código.

  **BREAKING** no corpo do `PUT …/status` e do `reject`, sem consumidor

- **CNH revisada (PR 6):** a `driver_cnh` entra na revisão comum, com `PUT /api/driver-cnhs/{token}/status`. O motorista manda só o arquivo, e o revisor preenche o registro, a categoria (`D`, `E`, `AD` ou `AE`), a emissão e a validade na aprovação. Só a CNH aprovada vale como credencial. A renovação com o mesmo número convive com a atual, e o número continua de um motorista só. O arquivo da CNH passa a aceitar PDF (a CNH digital), e por isso deixa de se chamar foto: coluna `file_media_id`, rotas `/api/driver-cnhs/{token}/file` e `fileUrl` no JSON.

  **BREAKING** nas rotas `/photo` e no `photoUrl` da CNH: o front do admin troca a rota e o campo
- **Situação do motorista (PR 7):** uma `DriverCompliancePolicy` diz:
  - o estado de cada requisito (`MISSING`, `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`);
  - as vans qualificadas e se o cadastro pode ser aprovado, com os bloqueios;
  - de quem é a vez (`ADMIN`, `DRIVER`, `READY_TO_APPROVE`, `NONE`).

  A aprovação e o onboarding passam a usá-la, e a data é a de hoje no fuso de São Paulo

- **E-mails depois do commit (PR 8):** a aprovação e a recusa do cadastro passam a mandar e-mail só depois de gravadas. O motorista recebe um e-mail quando um documento é recusado, com o motivo
- **Fila e página do admin (PRs 9 e 10):**
  - `GET /api/drivers/compliance` (abas, busca por nome, e-mail ou placa) e `/counts`, calculados a cada leitura pela mesma policy, sem tabela nem job. É um risco aceito para o volume atual (R10 do `design.md`);
  - `GET /api/drivers/{token}/compliance` (checklist, vans, fila de revisão, última decisão e histórico);
  - cache dos arquivos (PR 5)
- **Correção dos dados aprovados (PRs 5 e 6, D25):** quem revisa corrige um erro de digitação num envio aprovado (`PUT …/{token}/data`), com a versão, sem reenvio do motorista e sem mudar a decisão, registrando quem corrigiu
- **Datas dentro dos limites na aprovação (PRs 5 e 6):** a validade que o revisor preenche não pode ser anterior a hoje nem passar do limite do tipo, que protege contra erro de digitação (o CRLV até o fim do ano que vem, a vistoria até 7 meses, a autorização escolar até 2 anos, o curso e o alvará até 5 anos). Na CNH, a emissão não pode ser futura e a validade vai até 10 anos depois da emissão. Os erros levam `code`
- **Arquivo nas respostas (PRs 2, 6 e 10):** os documentos, a CNH e a página do admin trazem `file` (`mimeType`, `sizeBytes`, `originalName`), nulo sem arquivo
- **Situação do próprio motorista (PR 11):** `GET /api/drivers/me/compliance` mostra ao motorista, numa chamada, o estado de cada requisito dele e de cada van ativa, pela mesma policy, sem os dados do revisor. O `GET /api/drivers/me` ganha `ratingCount` e `experienceYears`
- **Limite de upload (PR 11):** o limite de transporte do Spring (`MEDIA_MAX_UPLOAD_SIZE_MB`, 11) fica separado do limite de 10 MB dos documentos, para um arquivo de até 10 MB sempre chegar à regra, que responde com `code`
- **Permissões:** `review_documents` (nova) revisa os documentos, a CNH e lê a fila; `approve_driver` decide o cadastro e também lê a fila

**Fora de escopo:**

- Documentos exigidos por cidade ou estado (pergunta jurídica 4 do spike): o conjunto é fixo e nacional, o alvará municipal é opcional, e o contrato traz a declaração do motorista de cumprir a regulamentação local. A exigência por município entra numa change própria quando as cidades de lançamento estiverem definidas (R8 do `design.md`)
- Efeito de um documento que vence durante um contrato em vigor (aviso, rescisão sem multa): fase 8 do spike
- Conferência automática e leitura dos dados do arquivo (`review_method = AUTO`, `external_check_id`, OCR, Serpro Datavalid): os campos continuam, sem integração. Quando entrar, preenche a mesma aprovação, e o revisor confirma (R19)
- As telas do admin (Next.js): são do `vanep-frontend`; esta change entrega o back delas
- Guardar os arquivos de envios substituídos ou um histórico de versões: fica só a última decisão
- Tabela de auditoria: o histórico da página do motorista sai das datas que já existem
- Tabela com a situação já calculada de cada motorista, e o job diário que a manteria: a fila calcula na leitura
- Bloqueio terminal de conta por fraude
- O uso das credenciais na proposta e no contrato: `contract-generation` (VAN-31)

## Capabilities

### New Capabilities

- `vehicle-documents`: os documentos da van, com dono obrigatório, histórico e rotas próprias; a van sem foto de documento própria.
- `driver-documents`: os documentos da pessoa, só com os tipos da pessoa, enviados como arquivo, com o número preenchido na aprovação e com o chamador resolvido pelo token.
- `document-review`: a revisão comum aos documentos do motorista, da van e da CNH: só quem tem `review_documents` revisa, aprovar exige arquivo e preenche os dados do documento, a decisão leva a versão e um código de motivo, o envio revisado é final e a renovação o substitui.
- `driver-cnh-review`: a CNH revisada, enviada só com o arquivo, com os dados preenchidos na aprovação, renovável com o mesmo número e com o número de um motorista só.
- `driver-compliance`: a situação do motorista numa data.
- `admin-driver-review`: a fila, as contagens e a página do motorista para o admin.
- `driver-own-compliance`: a situação do próprio motorista e o resumo do perfil dele.
- `driver-review-notifications`: os e-mails de decisão depois do commit e o aviso de documento recusado.
- `school-transport-credentials`: as credenciais do motorista e de uma van numa data, e o endpoint que mostra o que falta.
- `driver-onboarding-school-transport`: o onboarding e a aprovação do motorista exigindo o conjunto legal de documentos, uma van completa e a CNH D.

### Modified Capabilities

Nenhuma em `openspec/specs/`. As changes `driver-document-crud`, `vehicle-crud`, `driver-onboarding` e `admin-driver-approval` não foram arquivadas, então não há spec base para delta delas. Ao arquivar, os specs desta change substituem os requisitos de tipos de documento, de foto de documento da van e de documentos obrigatórios daquelas changes.

## Impact

- **Schema:** `V72`. Ela faz:
  - cria a `vehicle_document`;
  - acrescenta `driver_document.document_number`;
  - remove do `driver_document` os documentos de van e as fotos, sem mover para a van (nenhum app os enviou);
  - remove a `vehicle.photo_document_media_id`, sem converter a foto;
  - dá as permissões de documento da van ao bundle `ADMIN`.

  A `main` está em `V71` (a VAN-57 usou a `V69` a `V71`), e a `contract-generation` reservou a `V73` e a `V74`. Reconfirmar antes do merge (R5)

- **Schema dos PRs 4 a 10:**
  - `V75`: a revisão nova nas três tabelas e no motorista (versão, código, substituição, `notified_at`, revisão da CNH, constraint do número da CNH, `review_documents` no `ADMIN`);
  - `V76`: um `PENDING` por dono e tipo e o código obrigatório na recusa, com o serviço do PR 5 que os cumpre;
  - `V77`: `driver_cnh.photo_media_id` renomeada para `file_media_id`;
  - sem migration de placas: o `VehicleService` passa a gravar a placa normalizada, e as placas antigas ficam como estão
- **Pacotes novos:** `br.com.vanep.vehicledocument`, `br.com.vanep.schooltransportcredential`, `br.com.vanep.shared.document` (revisão comum: `@Embeddable`, status, método, motivo e a policy), `br.com.vanep.drivercompliance` (policy, serviço, fila calculada na leitura e rotas do admin)
- **Pacotes alterados:**
  - `driverdocument`: tipos, número, revisão, chamador;
  - `vehicle`: sem a foto de documento;
  - `media`: sem o slot `VEHICLE_PHOTO_DOCUMENT`, mais o slot `VEHICLE_DOCUMENT_FILE`;
  - `driver`: onboarding e aprovação;
  - `drivercnh`: categoria, e o arquivo com PDF no slot `DRIVER_CNH_FILE` (PR 6);
  - `auth.security`: `@sec.isVehicleDocumentOwner` e as permissões novas;
  - `seed`: documentos com datas relativas a hoje
- **API:**
  - **novas:** `/api/vehicles/{token}/documents`, `/api/vehicle-documents/{token}` (e `/file`, `/status`, `/restore`), `GET /api/vehicles/{token}/school-transport-credentials`;
  - **mudam:** os tipos aceitos em `/api/driver-documents`; o `POST` dos dois documentos vira multipart com o tipo e o arquivo, e o `PUT /{token}` sai; `documentNumber` e `expiresAt` no `PUT …/status` da aprovação; a resposta do `GET /api/drivers/me/onboarding`;
  - **saem:** `/api/vehicles/{token}/photo-document` e o `photoDocumentUrl`;
  - **PRs 4 a 11:**
    - novas: `PUT /api/driver-cnhs/{token}/status`, `GET /api/drivers/compliance`, `GET /api/drivers/compliance/counts` e `GET /api/drivers/{token}/compliance`;
    - mudam: `/api/driver-cnhs/{token}/photo` vira `/file` e `photoUrl` vira `fileUrl`, o `POST /api/driver-cnhs` vira multipart só com o arquivo e o `PUT /api/driver-cnhs/{token}` sai, o corpo dos `PUT …/status` (com os dados da CNH na aprovação) e do `reject`, o 409 no `PUT` e no `/file` de envio revisado, `version` e código nas respostas, e o 422 do `approve` com `blockers`;
    - datas e arquivo: as datas fora dos limites dão 400 na aprovação (D21), e as respostas dos documentos e da CNH trazem `file` (D22);
    - PR 11: novo `GET /api/drivers/me/compliance`, e `ratingCount` e `experienceYears` no `GET /api/drivers/me` (D23)
- **Permissões:** `list_vehicle_documents`, `show_vehicle_document`, `update_vehicle_document`, `delete_vehicle_document` e `restore_vehicle_document`, só no bundle `ADMIN`; o dono da van usa o `@sec`. A `review_documents` entra no `ADMIN` pela `V75`, e os `PUT …/status` passam a exigi-la
- **Bancos locais:** motoristas já aprovados sem os documentos novos continuam aprovados, mas não passam nas credenciais até mandar os documentos e o admin aprová-los (R2). Com a `V75`, as CNHs que já existem ficam pendentes até o admin aprovar (R11)
- **Task:** VAN-86 no Genwork (`[Contract e Proposal - 3a] Documentos da van e credenciais de transporte escolar`), dependência da VAN-31
