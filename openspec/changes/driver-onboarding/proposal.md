## Why

No fluxo da plataforma Vanep, a conta de um motorista é criada inicialmente no fluxo de autenticação (cadastro web ou login OAuth server-side). Entretanto, para estar habilitado a prestar serviços de transporte escolar e receber propostas de clientes, o motorista precisa completar o fluxo de Onboarding (UC02) no aplicativo mobile.

Esse fluxo coleta informações ricas e uploads de arquivos em múltiplos passos:
1. **Dados do perfil do motorista** (`driver`: bio, foto, CNPJ, anos de experiência, cidade de atuação, preço base, horários e dias de atendimento, áreas de atendimento).
2. **Dados da van** (`vehicle`: placa, marca, modelo, ano de fabricação, cor, capacidade de assentos e fotos).
3. **Dados da CNH** (`driver_cnh`: número de registro, categoria, validade, foto da habilitação).
4. **Documentos comprobatórios obrigatórios** (`driver_document`: CRLV, vistoria veicular periódica e autorização municipal de transporte escolar).
5. **Submissão formal para análise** e acompanhamento em tempo real do status na tela "Aguardando aprovação" (S06).

Atualmente, existem apenas CRUDs isolados (`/api/drivers`, `/api/vehicles`, `/api/driver-cnhs` e `/api/driver-documents`), porém:
- Faltam tipos essenciais de documentos de transporte escolar (`VEHICLE_INSPECTION` e `MUNICIPAL_AUTHORIZATION`) em `DocumentTypeEnum`.
- O enum `DriverApprovalStatus` não possui o estado de submissão em análise (`UNDER_REVIEW`), impedindo diferenciar um motorista preenchendo rascunho de um motorista aguardando análise (S06).
- O cadastro de motorista via OAuth (`OAuthAccountService`) não instancia o registro inicial de `DriverModel`, provocando erro 404 em `/api/drivers/me`.
- A tabela `driver` não persiste histórico e metadados de auditoria do onboarding (`submitted_at`, `rejection_reason`, `reviewed_at`, `reviewed_by`).
- Não existe um endpoint agregador do status de onboarding que informe ao app mobile quais etapas estão concluídas, quais estão pendentes e se os requisitos mínimos para submissão foram atingidos.
- Não existe o endpoint de submissão do onboarding (`POST /api/drivers/me/submit-onboarding`) com validação de regras de negócio integradas e transição de estado para `UNDER_REVIEW`.
- Faltam endpoints de aprovação e rejeição administrativa com motivo (`POST /api/drivers/{token}/approve` e `POST /api/drivers/{token}/reject`).

Esta proposta introduz a funcionalidade completa de Onboarding do Motorista no backend, orquestrando as entidades envolvidas e viabilizando o fluxo mobile de ponta a ponta.

---

## What Changes

- **Novos Tipos de Documentos em `DocumentTypeEnum`:**
  - Adição de `VEHICLE_INSPECTION` (Vistoria Veicular) e `MUNICIPAL_AUTHORIZATION` (Autorização Municipal) ao `DocumentTypeEnum.java`.
- **Evolução do Ciclo de Vida de Aprovação em `DriverApprovalStatus`:**
  - Adição do estado `UNDER_REVIEW` ao enum `DriverApprovalStatus.java`:
    - `PENDING`: Cadastro em preenchimento pelo motorista.
    - `UNDER_REVIEW`: Onboarding submetido para análise pela equipe (Tela S06).
    - `APPROVED`: Aprovado e liberado para operar e receber propostas.
    - `REJECTED`: Rejeitado com justificativa gravada em `rejection_reason`.
- **Nova Migration Flyway (`V46__add_driver_onboarding_and_review_columns.sql`):**
  - Adição das colunas `submitted_at` (timestamptz), `rejection_reason` (varchar(255)), `reviewed_at` (timestamptz) e `reviewed_by` (bigint references users) na tabela `driver`.
- **Criação Automática do Perfil `DriverModel` no Cadastro OAuth:**
  - Ajuste em `OAuthAccountService.completeRegistration` para instanciar e vincular o `DriverModel` inicial (com status `PENDING` e `basePrice = 0.00`) quando o usuário se registra como `UserType.DRIVER`.
- **Endpoint de Acompanhamento do Onboarding (`GET /api/drivers/me/onboarding`):**
  - Retorna o progresso detalhado de cada etapa do onboarding:
    - Status geral (`approvalStatus`, `submittedAt`, `rejectionReason`, `canSubmit`).
    - Etapa Perfil: campos preenchidos e campos obrigatórios faltantes.
    - Etapa Veículo: presença de ao menos um veículo ativo e resumo do veículo principal.
    - Etapa CNH: presença de CNH ativa, validação de validade da carteira e presença de foto anexada (`photo != null`).
    - Etapa Documentos: checklist dos documentos obrigatórios (`CRLV`, `VEHICLE_INSPECTION`, `MUNICIPAL_AUTHORIZATION`), exigindo que cada registro possua arquivo associado (`file != null`) e status diferente de `REJECTED`. Documentos criados sem upload de arquivo no endpoint `/file` permanecem com status pendente de envio.
- **Endpoint de Submissão do Onboarding (`POST /api/drivers/me/submit-onboarding`):**
  - Valida se todos os pré-requisitos estão atendidos (perfil com cidade e preço base, ao menos 1 veículo ativo, CNH válida cadastrada com foto, e todos os documentos obrigatórios com arquivos anexados).
  - Em caso de inconsistência ou pendência, rejeita com HTTP 422 Unprocessable Entity e payload estruturado detalhando quais etapas/documentos estão faltando.
  - Em caso de sucesso, transiciona o motorista para `UNDER_REVIEW`, marca `submitted_at = Instant.now()`, limpa eventual `rejection_reason` anterior e retorna o status atualizado.
- **Endpoints Administrativos de Revisão:**
  - `POST /api/drivers/{token}/approve` — Aprova o motorista (`approvalStatus = APPROVED`, `reviewed_at = now()`, `reviewed_by = admin.id`), ativando-o na plataforma.
  - `POST /api/drivers/{token}/reject` — Rejeita o onboarding (`approvalStatus = REJECTED`, `rejection_reason = reason`, `reviewed_at = now()`, `reviewed_by = admin.id`).
- **Novas Permissões e Segurança:**
  - Adição de `APPROVE_DRIVER("approve_driver")` em `PermissionEnum.java`.
  - Configuração de autorização no `DriverOnboardingController`:
    - `GET /api/drivers/me/onboarding` e `POST /api/drivers/me/submit-onboarding` acessíveis por motoristas autenticados.
    - `POST /api/drivers/{token}/approve` e `POST /api/drivers/{token}/reject` acessíveis por `ROLE_ADMIN` / `hasAuthority('approve_driver')`.
- **Mensagens e Internacionalização:**
  - Chaves de erro e validação em `messages.properties` e `messages_pt_BR.properties`.
- **Testes Automatizados:**
  - Testes unitários para `DriverOnboardingServiceTest` e `OAuthAccountServiceTest`.
  - Testes de slice HTTP/segurança para `DriverOnboardingControllerTest` com `MockMvc`.

**Fora de Escopo:**
- Criação de nova infraestrutura de storage de arquivos (o fluxo de onboarding consome o motor de mídia multipart já implementado na PR #207 através de `media_file`, `POST /api/driver-documents/{token}/file` e `POST /api/driver-cnhs/{token}/photo`).
- Reanálise automatizada via OCR/IA de documentos (revisão humana por administradores).

---

## Capabilities

### New Capabilities

- `driver-onboarding`: Gestão e orquestração do ciclo de onboarding de motoristas (UC02), incluindo acompanhamento de checklist de progresso (`GET /api/drivers/me/onboarding`), validação integral e submissão para análise (`POST /api/drivers/me/submit-onboarding`), suporte à tela S06 ("Aguardando aprovação") e aprovação/rejeição com justificativa por administradores (`/api/drivers/{token}/approve` e `/api/drivers/{token}/reject`).

### Modified Capabilities

- `driver-document`: Expansão do catálogo de documentos (`DocumentTypeEnum`) para suportar `VEHICLE_INSPECTION` e `MUNICIPAL_AUTHORIZATION`.
- `auth-oauth`: Criação automática do registro `DriverModel` inicial durante a conclusão de cadastro de motoristas via OAuth.
- `security-permissions`: Adição da permissão `approve_driver` ao `PermissionEnum`.

---

## Impact

- **Database:** Nova migration `V46__add_driver_onboarding_and_review_columns.sql` adicionando colunas na tabela `driver`.
- **Entidade JPA:** Atualização de `DriverModel` com os novos campos e mapeamento da relação com `reviewed_by` (`UserModel`).
- **Enums:** Atualização de `DriverApprovalStatus` (`UNDER_REVIEW`) e `DocumentTypeEnum` (`VEHICLE_INSPECTION`, `MUNICIPAL_AUTHORIZATION`).
- **Pacote de Negócio:**
  - Criação de `DriverOnboardingService` em `br.com.vanep.driver.service`.
  - Criação de `DriverOnboardingController` em `br.com.vanep.driver.controller`.
  - Criação de DTOs: `DriverOnboardingStatusResponseDTO`, `DriverOnboardingStepDTO`, `DriverRejectionRequestDTO` em `br.com.vanep.driver.dto`.
- **OAuth:** Atualização de `OAuthAccountService` para instanciar `DriverModel`.
- **Mensagens:** Novas mensagens em `src/main/resources/messages.properties` e `messages_pt_BR.properties`.
- **Segurança:** Adição da permissão `approve_driver` em `PermissionEnum.java` e atualização de permissões do perfil `ROLE_ADMIN` no seeder / banco.
- **Testes:** Novos testes unitários e de integração (`DriverOnboardingServiceTest`, `DriverOnboardingControllerTest`).
- **Phased Delivery:** Entrega estruturada em 4 PRs rigorosamente em conformidade com as regras 36–44 da `constitution.md`.
