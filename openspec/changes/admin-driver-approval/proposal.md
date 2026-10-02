## Why

No fluxo de Onboarding da plataforma Vanep (UC02, passos 6–9), após o motorista submeter seus dados de perfil, veículo, CNH e documentos comprobatórios obrigatórios, seu cadastro passa para o status de moderação "Aguardando aprovação" (`UNDER_REVIEW`). A moderação por administradores é a etapa essencial que audita e valida os documentos enviados, garantindo segurança aos responsáveis e conformidade com a regulamentação do transporte escolar.

Uma vez concluída a análise, o Admin deve poder aprovar ou rejeitar o motorista (informando o motivo em caso de recusa). De acordo com a Regra de Negócio RN02 (e anotação de domínio na tabela `driver`), **o motorista só pode receber e aceitar propostas quando estiver formalmente aprovado**. Além disso, o motorista precisa ser notificado por e-mail imediatamente ao ter seu cadastro aprovado ou rejeitado com justificativa, para que saiba quando pode operar ou o que deve retificar.

Embora a PR #234 tenha introduzido os estados do enum `DriverApprovalStatus` e endpoints pontuais de submissão, faltam peças vitais para o fluxo completo do Admin:
1. Um endpoint exclusivo para o Admin listar os cadastros pendentes de aprovação (`UNDER_REVIEW`).
2. O envio de notificações (e-mail via `MailService` com templates Thymeleaf dedicados) ao motorista quando seu cadastro for aprovado ou rejeitado.
3. A aplicação estrita da regra RN02 no serviço de propostas e vínculos (`ClientDriverService`), bloqueando o recebimento ou ativação de propostas para motoristas não aprovados.
4. Cobertura de testes automatizados unitários e de integração (slice MockMvc) assegurando o bloqueio de propostas e a integridade de todo o fluxo.

Esta proposta formaliza a especificação e o plano de implementação dessa funcionalidade no backend.

---

## What Changes

- **Listagem de Cadastros Pendentes para o Admin (`GET /api/drivers/pending`):**
  - Endpoint restrito a administradores (`@PreAuthorize("hasRole('ADMIN') or hasAuthority('approve_driver')")`).
  - Consulta paginada (`Pageable`) retornando os motoristas cujo `approvalStatus` é `UNDER_REVIEW` (aguardando moderação), ordenados pelos mais antigos ou submetidos recentemente.
  - Suporte adicional a filtro opcional por `status` em `GET /api/drivers` no `DriverController`.
- **Notificação ao Motorista em Aprovação e Rejeição:**
  - Criação do serviço `DriverNotificationService` em `br.com.vanep.driver.service` integrando com `MailService`.
  - Disparo de e-mail ao motorista em caso de aprovação informando a liberação do perfil e o recebimento de propostas na plataforma (`email/driver-approved.html`).
  - Disparo de e-mail ao motorista em caso de rejeição informando o motivo informado pelo administrador (`email/driver-rejected.html`).
  - Criação dos templates Thymeleaf em `src/main/resources/templates/email/driver-approved.html` e `src/main/resources/templates/email/driver-rejected.html`.
- **Enforcement da Regra RN02 no Recebimento/Aceite de Propostas:**
  - Em `ClientDriverService.create`: validação que impede a criação de vínculo/envio de proposta para motoristas com status diferente de `DriverApprovalStatus.APPROVED` (dispara `HTTP 422 Unprocessable Entity`).
  - Em `ClientDriverService.update`: validação que impede a transição do vínculo para `ACTIVE` caso o motorista não esteja aprovado.
  - Novas mensagens de validação em `messages.properties` e `messages_pt_BR.properties` (`client_driver.driver.not_approved`).
- **Testes Automatizados e Cobertura:**
  - Testes unitários para `DriverNotificationServiceTest`, `DriverOnboardingServiceTest` e `ClientDriverServiceTest`.
  - Testes de slice MockMvc para `DriverOnboardingControllerTest` (`GET /api/drivers/pending`, autorização `@EnableMethodSecurity`) e `ClientDriverControllerTest` (bloqueio de propostas sob RN02).
  - Verificação de gate de cobertura JaCoCo (≥ 75%) e conformidade de formatação Spotless.

---

## Capabilities

### New Capabilities

- `admin-driver-approval`: Gestão completa do fluxo de moderação de cadastros pelo Administrador, incluindo listagem de pendentes de análise (`GET /api/drivers/pending`), notificação transacional por e-mail no deferimento ou indeferimento, e enforcement rigoroso da regra de negócio RN02 para bloqueio de propostas a motoristas não homologados.

### Modified Capabilities

- `driver-onboarding`: Integração do `DriverOnboardingService` com `DriverNotificationService` para envio automático de e-mail ao aprovar (`approve`) ou rejeitar (`reject`) cadastros de motoristas.
- `client-driver-link`: Implementação da validação RN02 em `ClientDriverService` para impedir que motoristas pendentes ou reprovados recebam propostas de clientes ou ativem vínculos.

---

## Impact

- **Database:** Nenhuma alteração de schema necessária (as colunas `approval_status`, `submitted_at`, `rejection_reason`, `reviewed_at`, `reviewed_by` já existem na tabela `driver` via migration `V47`).
- **Templates:** Criação dos templates de e-mail `driver-approved.html` e `driver-rejected.html` em `src/main/resources/templates/email/`.
- **Services:**
  - Novo `DriverNotificationService` em `br.com.vanep.driver.service`.
  - Atualização de `DriverOnboardingService` para orquestrar listagem de pendentes e notificações.
  - Atualização de `ClientDriverService` para aplicar regra RN02.
- **Controllers:**
  - Adição de `GET /api/drivers/pending` em `DriverOnboardingController` protegido por `@PreAuthorize("hasRole('ADMIN') or hasAuthority('approve_driver')")`.
- **Mensagens:** Novas chaves de mensagem em `messages.properties` e `messages_pt_BR.properties`.
- **Testes:** Novos cenários de testes unitários e de integração cobrindo autorização, listagem, notificação e bloqueio de propostas.
