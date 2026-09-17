## Why

Para operar na plataforma Vanep de acordo com as exigências legais e de segurança, os motoristas precisam cadastrar e gerenciar seus documentos obrigatórios (ex.: CRLV, Antecedentes Criminais, Comprovante de Residência, Fotos). O sistema e os administradores precisam visualizar, validar e auditar esses documentos.

Esta proposta define a criação da infraestrutura de banco de dados, modelo JPA, permissões de segurança, lógica de negócio e API REST para o gerenciamento de documentos do motorista (`driver_document`).

## What Changes

- **Nova Migration:** `V21__create_driver_document_table.sql` contendo tabela `driver_document` com FK para `driver`, campos de identificação, tipo, URL/caminho do arquivo, status de verificação, suporte a soft delete (`deleted_at`) e índices parciais.
- **Pacote de Negócio:** `br.com.vanep.driverdocument` estruturado por camada (`controller`, `dto`, `enums`, `mapper`, `model`, `repository`, `service`).
- **Enums de Domínio:** `DocumentTypeEnum` (ex.: `CRLV`, `CRIMINAL_RECORD`, `RESIDENCE_PROOF`, `OTHER`) e `DocumentStatusEnum` (ex.: `PENDING`, `APPROVED`, `REJECTED`).
- **Novas Permissões:** `LIST_DRIVER_DOCUMENTS`, `SHOW_DRIVER_DOCUMENT`, `CREATE_DRIVER_DOCUMENT`, `UPDATE_DRIVER_DOCUMENT`, `DELETE_DRIVER_DOCUMENT`, `RESTORE_DRIVER_DOCUMENT` adicionadas em `PermissionEnum.java`.
- **Segurança Global:** Método `isDriverDocumentOwner` adicionado em `SecurityEvaluator.java` (`@sec`).
- **Endpoints REST sob `/api/driver-documents`:**
  - `POST /api/driver-documents` — Cadastro de novo documento para o motorista logado.
  - `GET /api/driver-documents` — Listagem paginada (com filtros opcionais por `driverToken`, `documentType` e `status`).
  - `GET /api/driver-documents/{token}` — Detalhamento do documento por token público.
  - `PUT /api/driver-documents/{token}` — Atualização dos dados ou status do documento.
  - `DELETE /api/driver-documents/{token}` — Remoção lógica (Soft delete) do documento.
  - `POST /api/driver-documents/{token}/restore` — Restauração de documento removido (exclusivo Admin).
- **Seeder:** Inclusão de documentos de teste em `DataSeeder.java` / `DriverDocumentSeeder`.
- **Testes Automatizados:** `DriverDocumentServiceTest` (unitários) e `DriverDocumentControllerTest` (slice HTTP/segurança MockMvc).

## Capabilities

### New Capabilities

- `driver-document-management`: Endpoints REST e serviços para cadastro, consulta, atualização, soft delete e restauração de documentos de motoristas com controle de acesso granular por propriedade (`@sec.isDriverDocumentOwner`).

### Modified Capabilities

- `security-evaluator`: Adição da verificação de propriedade de documentos de motoristas (`isDriverDocumentOwner`).

## Impact

- **Database:** Nova tabela `driver_document` através da migration `V21`.
- **Entity:** Nova entidade JPA `DriverDocumentModel` com `@SoftDelete`.
- **Security:** Inclusão de 6 novas permissões em `PermissionEnum` e método em `@sec`.
- **API:** Novos endpoints sob o prefixo global `/api/driver-documents`.
