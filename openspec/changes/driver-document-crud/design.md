## Context

A funcionalidade de documentos do motorista (`driver_document`) permite que motoristas enviem e mantenham atualizados seus documentos (como CRLV, Antecedentes Criminais, Comprovantes), associados ao seu cadastro de motorista (`DriverModel`). O sistema exige controle de acesso rígido: motoristas podem visualizar e gerenciar apenas seus próprios documentos, enquanto administradores possuem acesso amplo.

---

## Goals / Non-Goals

**Goals:**
- Criar a migration `V21__create_driver_document_table.sql` com Soft Delete (`deleted_at`) e índices parciais.
- Criar a entidade JPA `DriverDocumentModel` com suporte a `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)`.
- Criar os enums `DocumentTypeEnum` (ex.: `CRLV`, `CRIMINAL_RECORD`, `RESIDENCE_PROOF`, `OTHER`) e `DocumentStatusEnum` (ex.: `PENDING`, `APPROVED`, `REJECTED`).
- Implementar DTOs de Request e Response com Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Size`).
- Expor os endpoints REST sob `/api/driver-documents`.
- Adicionar validação de propriedade no `SecurityEvaluator` (`isDriverDocumentOwner`).
- Adicionar chaves de i18n em `messages.properties` e `messages_pt_BR.properties`.
- Desenvolver testes unitários (`DriverDocumentServiceTest`) e testes de controller com `MockMvc` (`DriverDocumentControllerTest`).

**Non-Goals:**
- Upload direto de binários multipart/S3 neste escopo (o campo `documentUrl` armazena o link/identificador do documento).

---

## Decisions

### D1 — Schema do Banco de Dados (`V21__create_driver_document_table.sql`)

```sql
create table driver_document (
    id                   bigint generated always as identity primary key,
    token                varchar(32)  not null,
    driver_id            bigint       not null references driver (id),
    document_type        varchar(50)  not null,
    document_number      varchar(50),
    document_url         varchar(512) not null,
    status               varchar(20)  not null default 'PENDING',
    rejection_reason     varchar(255),
    issue_date           date,
    expiration_date      date,
    is_active            boolean      not null default true,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now(),
    deleted_at           timestamptz
);

comment on table driver_document is 'Documentos anexados pelos motoristas.';

create unique index driver_document_token_active_key on driver_document (token) where deleted_at is null;
create index idx_driver_document_driver_active on driver_document (driver_id) where deleted_at is null;
```

### D2 — Structura de Pacote e Entidade

- Pacote: `br.com.vanep.driverdocument`
  - `controller/DriverDocumentController.java`
  - `dto/DriverDocumentRequestDTO.java`
  - `dto/DriverDocumentResponseDTO.java`
  - `dto/DriverDocumentStatusUpdateRequestDTO.java`
  - `enums/DocumentTypeEnum.java`
  - `enums/DocumentStatusEnum.java`
  - `mapper/DriverDocumentMapper.java`
  - `model/DriverDocumentModel.java`
  - `repository/DriverDocumentRepository.java`
  - `service/DriverDocumentService.java`

### D3 — Endpoints REST sob `/api/driver-documents`

1. **`POST /api/driver-documents`**
   - **Autorização:** `hasAuthority('create_driver_document')`.
   - **Comportamento:** Associa o documento ao motorista vinculado ao usuário autenticado (`callerUid`).

2. **`GET /api/driver-documents`**
   - **Autorização:** `hasAuthority('list_driver_documents')`.
   - **Parâmetros:** `driverToken` (opcional), `documentType` (opcional), `status` (opcional) e `Pageable`.

3. **`GET /api/driver-documents/{token}`**
   - **Autorização:** `hasAuthority('show_driver_document') or @sec.isDriverDocumentOwner(#token, authentication)`.

4. **`PUT /api/driver-documents/{token}`**
   - **Autorização:** `hasAuthority('update_driver_document') or @sec.isDriverDocumentOwner(#token, authentication)`.

5. **`DELETE /api/driver-documents/{token}`**
   - **Autorização:** `hasAuthority('delete_driver_document') or @sec.isDriverDocumentOwner(#token, authentication)`.

6. **`POST /api/driver-documents/{token}/restore`**
   - **Autorização:** `hasAuthority('restore_driver_document')` (Apenas Admin).

### D4 — Segurança e Autorização (`SecurityEvaluator`)

Adicionar em `SecurityEvaluator.java`:
```java
public boolean isDriverDocumentOwner(String token, Authentication authentication) {
  return SecurityHelper.getCallerUid(authentication)
      .flatMap(
          uid ->
              driverDocumentRepository
                  .findDriverUserTokenByDocumentToken(token)
                  .map(driverUserToken -> driverUserToken.equals(uid)))
      .orElse(false);
}
```

---

## Risks / Trade-offs

- **Formato da Data de Expiração:** Documentos com data de validade vencida exigirão validação de negócio no `DriverDocumentService` caso haja tentativas de uso para aprovação.
