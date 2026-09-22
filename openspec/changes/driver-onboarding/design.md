## Context

O caso de uso UC02 (Onboarding do motorista após login) permite que um motorista com conta criada (seja por signup tradicional ou OAuth) complete as informações obrigatórias exigidas pela regulação de transporte escolar e pela política da plataforma Vanep. Ao concluir todas as etapas, o motorista submete seu cadastro para análise e passa a aguardar a aprovação da moderação na tela S06 ("Aguardando aprovação").

Os recursos individuais de dados (perfil do motorista, van/veículo, CNH e documentos anexados) já possuem modelos e controllers básicos no backend:
- `DriverModel` / `/api/drivers`
- `VehicleModel` / `/api/vehicles`
- `DriverCnhModel` / `/api/driver-cnhs`
- `DriverDocumentModel` / `/api/driver-documents`

Contudo, faltam a camada de orquestração do fluxo de onboarding, validação de completude dos dados, novos tipos documentais, suporte a status em análise (`UNDER_REVIEW`), registro de motivos de rejeição e endpoints para submissão e decisão administrativa.

Este documento detalha o design técnico e arquitetural da solução.

---

## Goals / Non-Goals

**Goals:**
1. Permitir que o motorista autenticado consulte a qualquer momento seu progresso no onboarding através de um endpoint agregador (`GET /api/drivers/me/onboarding`), informando o status de cada etapa (perfil, veículo, CNH e documentos).
2. Viabilizar a submissão formal do onboarding para análise (`POST /api/drivers/me/submit-onboarding`), garantindo que apenas cadastros completos transitem para o estado `UNDER_REVIEW` (Tela S06).
3. Adicionar os tipos documentais obrigatórios `VEHICLE_INSPECTION` (vistoria) e `MUNICIPAL_AUTHORIZATION` (autorização municipal) ao `DocumentTypeEnum`.
4. Garantir que motoristas criados pelo fluxo de OAuth possuam seu registro de `DriverModel` instanciado automaticamente.
5. Permitir que administradores aprovem (`POST /api/drivers/{token}/approve`) ou rejeitem (`POST /api/drivers/{token}/reject`) o onboarding de um motorista com justificativa explícita.
6. Notificar e persistir o motivo de eventual rejeição para que o motorista possa corrigir as pendências no app e reenviar o cadastro.
7. Cumprir integralmente todas as regras de qualidade, Clean Architecture e entrega em fases da `constitution.md`.

**Non-Goals:**
- Implementar processamento de upload de arquivos binários diretamente na API (o upload é realizado diretamente para o storage pelo cliente mobile, persistindo as URLs nos DTOs existentes).
- Automação de aprovação por OCR ou integração externa (validação humana administrativa).
- Permitir que motoristas com status diferente de `APPROVED` recebam propostas de clientes ou fiquem visíveis para contratação na busca pública.

---

## Decisions

### D1 — Evolução do `DriverApprovalStatus` e `DocumentTypeEnum`

1. **`DriverApprovalStatus`:**
   Adicionamos o estado `UNDER_REVIEW`:
   ```java
   package br.com.vanep.driver;

   public enum DriverApprovalStatus {
     PENDING,       // Cadastro em andamento / dados ainda não submetidos
     UNDER_REVIEW,  // Submetido pelo motorista, aguardando análise (Tela S06)
     APPROVED,      // Aprovado pela equipe / ativo para operar
     REJECTED       // Rejeitado pela equipe com justificativa gravada
   }
   ```
   *Compatibilidade de banco:* O campo `approval_status` na tabela `driver` é `varchar(16)`. O literal `UNDER_REVIEW` tem 12 caracteres, sendo plenamente compatível sem requerer alteração de tipo na coluna.

2. **`DocumentTypeEnum`:**
   Expandimos o enum em `br.com.vanep.driverdocument.enums`:
   ```java
   public enum DocumentTypeEnum {
     CRLV,
     VEHICLE_INSPECTION,       // Vistoria veicular periódica
     MUNICIPAL_AUTHORIZATION,  // Autorização municipal de transporte escolar
     CRIMINAL_RECORD,
     RESIDENCE_PROOF,
     PROFILE_PHOTO,
     VEHICLE_PHOTO,
     OTHER
   }
   ```

### D2 — Schema Database: Migration `V46`

Criamos a migration `src/main/resources/db/migration/V46__add_driver_onboarding_and_review_columns.sql` (respeitando a regra 2 e 18 da constituição):
```sql
alter table driver
    add column submitted_at timestamptz,
    add column rejection_reason varchar(255),
    add column reviewed_at timestamptz,
    add column reviewed_by bigint references users (id);

comment on column driver.submitted_at is 'Data e hora da submissão do onboarding para análise.';
comment on column driver.rejection_reason is 'Motivo da rejeição pelo administrador durante análise.';
comment on column driver.reviewed_at is 'Data e hora em que a análise (aprovação/rejeição) foi concluída.';
comment on column driver.reviewed_by is 'Identificador do usuário administrador que realizou a revisão.';

create index idx_driver_approval_status on driver (approval_status) where deleted_at is null;
```

### D3 — Inicialização de `DriverModel` no Cadastro OAuth

Em `br.com.vanep.auth.oauth.OAuthAccountService.completeRegistration`, estendemos a lógica para que usuários que se cadastram com `UserType.DRIVER` tenham sua entidade `DriverModel` criada imediatamente:
```java
if (form.getType() == UserType.DRIVER) {
  DriverModel driver = new DriverModel();
  driver.setUser(user);
  driver.setBasePrice(BigDecimal.ZERO);
  driver.setApprovalStatus(DriverApprovalStatus.PENDING);
  drivers.save(driver);
}
```
Isso assegura que chamadas subsequentes ao endpoint `/api/drivers/me` ou `/api/drivers/me/onboarding` localizem com sucesso o perfil do motorista.

### D4 — Agregação do Status de Onboarding (`GET /api/drivers/me/onboarding`)

Criamos o serviço `DriverOnboardingService` e o controller `DriverOnboardingController` sob o pacote `br.com.vanep.driver`.
O endpoint calcula o status das quatro dimensões do onboarding:
1. **Perfil (`profile`):**
   - Válido se `driver.city != null && !driver.city.isBlank()`, `driver.basePrice != null && driver.basePrice > 0`.
2. **Veículo (`vehicle`):**
   - Válido se existir pelo menos um `VehicleModel` ativo (`deleted_at IS NULL`) vinculado ao `driver.id`.
3. **CNH (`cnh`):**
   - Válido se existir um `DriverCnhModel` ativo vinculado ao `driver.id` e `validUntil >= LocalDate.now()`.
4. **Documentos Obrigatórios (`mandatoryDocuments`):**
   - Os tipos obrigatórios para transporte escolar são: `CRLV`, `VEHICLE_INSPECTION` e `MUNICIPAL_AUTHORIZATION`.
   - Válido se houver ao menos um `DriverDocumentModel` ativo para cada um dos 3 tipos obrigatórios (com status diferente de `REJECTED`).
5. **`canSubmit`:**
   - Retorna `true` apenas se as 4 dimensões forem válidas E o status atual do motorista for `PENDING` ou `REJECTED`.

### D5 — Submissão para Análise (`POST /api/drivers/me/submit-onboarding`)

Quando o motorista clica em "Submeter para análise":
1. O serviço avalia a completude das 4 etapas.
2. Se houver pendências:
   - Dispara `ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message("driver.onboarding.incomplete"))` acompanhado da lista de pendências.
3. Se estiver completo:
   - Altera `driver.approvalStatus = DriverApprovalStatus.UNDER_REVIEW`.
   - Grava `driver.submittedAt = Instant.now()`.
   - Limpa `driver.rejectionReason = null`.
   - Salva o motorista no banco.
   - Retorna o `DriverOnboardingStatusResponseDTO` atualizado (que reflete o status `UNDER_REVIEW`, orientando o Flutter a renderizar a tela S06).

### D6 — Decisão Administrativa: Aprovação e Rejeição

Endpoints sob `/api/drivers/{token}` restritos a administradores (`hasAuthority('approve_driver')` ou `ROLE_ADMIN`):
1. **`POST /api/drivers/{token}/approve`:**
   - Requer que o motorista esteja no status `UNDER_REVIEW` (ou lança 400 Bad Request se estiver em `PENDING` ou já `APPROVED`).
   - Define:
     - `approvalStatus = DriverApprovalStatus.APPROVED`
     - `active = true`
     - `reviewedAt = Instant.now()`
     - `reviewedBy = adminUser`
   - Retorna `DriverResponseDTO` atualizado.
2. **`POST /api/drivers/{token}/reject`:**
   - Body: `DriverRejectionRequestDTO` contendo `@NotBlank String reason`.
   - Requer que o motorista esteja no status `UNDER_REVIEW`.
   - Define:
     - `approvalStatus = DriverApprovalStatus.REJECTED`
     - `rejectionReason = request.reason()`
     - `reviewedAt = Instant.now()`
     - `reviewedBy = adminUser`
   - Retorna `DriverResponseDTO` atualizado com o motivo da rejeição. O motorista poderá visualizar este motivo no app e reenviar o onboarding após retificar os dados ou documentos.

### D7 — Contratos de Dados (DTOs)

1. **`DriverOnboardingStatusResponseDTO`:**
   ```java
   public record DriverOnboardingStatusResponseDTO(
       String driverToken,
       DriverApprovalStatus approvalStatus,
       boolean canSubmit,
       Instant submittedAt,
       String rejectionReason,
       DriverOnboardingStepDTO profileStep,
       DriverOnboardingStepDTO vehicleStep,
       DriverOnboardingStepDTO cnhStep,
       DriverOnboardingDocumentsStepDTO documentsStep) {}
   ```
2. **`DriverOnboardingStepDTO`:**
   ```java
   public record DriverOnboardingStepDTO(
       boolean completed,
       List<String> pendingItems) {}
   ```
3. **`DriverOnboardingDocumentsStepDTO`:**
   ```java
   public record DriverOnboardingDocumentsStepDTO(
       boolean completed,
       List<DocumentTypeEnum> missingTypes,
       List<DriverDocumentSummaryDTO> uploadedDocuments) {}
   ```
4. **`DriverRejectionRequestDTO`:**
   ```java
   public record DriverRejectionRequestDTO(
       @NotBlank(message = "{driver.rejection.reason.required}")
       @Size(max = 255, message = "{driver.rejection.reason.max_length}")
       String reason) {}
   ```

### D8 — Segurança e Autorização

- `GET /api/drivers/me/onboarding`: `@PreAuthorize("isAuthenticated()")` (assegura que o caller é um usuário autenticado do tipo `DRIVER`).
- `POST /api/drivers/me/submit-onboarding`: `@PreAuthorize("isAuthenticated()")` (valida que o caller é `DRIVER` e o perfil pertence a ele).
- `POST /api/drivers/{token}/approve`: `@PreAuthorize("hasAuthority('approve_driver')")`.
- `POST /api/drivers/{token}/reject`: `@PreAuthorize("hasAuthority('approve_driver')")`.
- A permissão `approve_driver` será adicionada ao `PermissionEnum` e vinculada à `ROLE_ADMIN` no seeder / banco.

---

## Risks / Trade-offs

- **Requisitos de Documentos Específicos por Cidade:** Algumas prefeituras exigem documentos adicionais além da autorização municipal padrão. Optamos por definir um conjunto fixo mínimo e universal no backend (`CRLV`, `VEHICLE_INSPECTION`, `MUNICIPAL_AUTHORIZATION`) para destravar a submissão, permitindo que outros tipos (`DocumentTypeEnum.OTHER`) sejam anexados opcionalmente.
- **Validação de Data de Validade da CNH:** Se a CNH do motorista estiver vencida no momento da submissão, a submissão é bloqueada com indicação explícita em `cnhStep.pendingItems`.
- **Compatibilidade com JWT Token:** A claim `driver_status` no JWT (configurada em `JwtTokenCustomizer`) passará a emitir o valor `UNDER_REVIEW` quando o motorista submeter, permitindo que o app Flutter inspecione essa claim ou consulte o endpoint `/onboarding` para decidir a rota inicial.
