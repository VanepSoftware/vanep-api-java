## Context

O fluxo de Onboarding (UC02) prevê que os motoristas preenchem seus dados e submetem para análise na tela S06 ("Aguardando aprovação"). A equipe de moderação (Admin) precisa auditar os cadastros pendentes, avaliar a conformidade dos dados, da van, da CNH e dos documentos obrigatórios (CRLV, vistoria veicular periódica e autorização municipal), tomando a decisão de deferimento (aprovação) ou indeferimento (rejeição justificada).

Para que o ciclo de vida se feche com confiabilidade operacional:
1. O administrador precisa de uma visão dedicada para listar quem está na fila de espera (`UNDER_REVIEW`).
2. O motorista precisa ser notificado por e-mail em tempo hábil quando seu status for alterado.
3. A regra de negócio RN02 deve ser estritamente garantida no backend: motoristas sem status `APPROVED` não podem receber nem aceitar propostas de transporte escolar de clientes (`client_driver`).

---

## Goals / Non-Goals

**Goals:**
- Implementar a consulta paginada de motoristas com status `UNDER_REVIEW` no `DriverRepository`.
- Expor o endpoint `GET /api/drivers/pending` no `DriverOnboardingController` restrito a `ROLE_ADMIN` e/ou permissão `approve_driver`.
- Criar o serviço `DriverNotificationService` para envio de e-mails transacionais utilizando o `MailService` existente.
- Criar os templates HTML Thymeleaf `driver-approved.html` e `driver-rejected.html`.
- Integrar os métodos `approve` e `reject` do `DriverOnboardingService` com `DriverNotificationService`.
- Implementar o bloqueio de propostas e vínculos (RN02) em `ClientDriverService.create` e `ClientDriverService.update` para motoristas com status diferente de `APPROVED` com `HTTP 422 Unprocessable Entity`.
- Garantir cobertura JaCoCo ≥ 75% e formatação segundo Spotless / Google Java Format.

**Non-Goals:**
- Criação de tabelas novas de notificações no banco (o envio de e-mail é transacional via `MailService`).
- Alterações no schema de banco de dados (o enum `DriverApprovalStatus` e as colunas de auditoria na tabela `driver` já foram entregues).
- Interface web administrativa SPA/frontend (escopo restrito à API REST).

---

## Decisions

### D1 — Endpoint de Listagem de Pendentes (`GET /api/drivers/pending`)

No repositório `DriverRepository`:
```java
@Query(
    value =
        """
        select driver from DriverModel driver
        join fetch driver.user
        where driver.approvalStatus = :status
        order by driver.submittedAt asc nulls last, driver.id asc
        """,
    countQuery =
        """
        select count(driver) from DriverModel driver
        where driver.approvalStatus = :status
        """)
Page<DriverModel> findByApprovalStatusWithUser(
    @Param("status") DriverApprovalStatus status, Pageable pageable);
```

No `DriverOnboardingService`:
```java
@Transactional(readOnly = true)
public Page<DriverResponseDTO> findPendingDrivers(Pageable pageable) {
  return driverRepository
      .findByApprovalStatusWithUser(DriverApprovalStatus.UNDER_REVIEW, pageable)
      .map(mapper::toResponse);
}
```

No `DriverOnboardingController`:
```java
@GetMapping("/pending")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('approve_driver')")
public Page<DriverResponseDTO> listPendingDrivers(@PageableDefault(size = 20) Pageable pageable) {
  return onboardingService.findPendingDrivers(pageable);
}
```

### D2 — Serviço de Notificação ao Motorista (`DriverNotificationService`)

Para manter a separação de responsabilidades (Regra 5 e 8 da Constituição) e evitar poluir a lógica de orquestração do onboarding:
- Criar `br.com.vanep.driver.service.DriverNotificationService`.
- Injetar `MailService` e `MessageSource`.
- Métodos públicos:
  - `void notifyApproval(DriverModel driver)`
  - `void notifyRejection(DriverModel driver, String reason)`
- Variáveis do template `driver-approved`:
  - `name`: Nome do motorista (`driver.getUser().getName()`).
- Variáveis do template `driver-rejected`:
  - `name`: Nome do motorista.
  - `reason`: Motivo informado pelo admin (`rejectionReason`).

### D3 — Templates Thymeleaf de E-mail

Localização:
- `src/main/resources/templates/email/driver-approved.html`
- `src/main/resources/templates/email/driver-rejected.html`

Seguindo o mesmo padrão visual de `verification.html` e `password-reset.html`, com estilo inline limpo, texto em pt-BR e mensagens claras sobre os próximos passos.

### D4 — Aplicação da Regra de Negócio RN02 em `ClientDriverService`

A regra RN02 declara: *"O motorista só recebe/aceita propostas quando aprovado."*

No método `ClientDriverService.create`:
```java
DriverModel driver =
    drivers
        .findByToken(request.driverToken())
        .orElseThrow(() -> notFound("client_driver.driver.not_found"));

if (driver.getApprovalStatus() != DriverApprovalStatus.APPROVED) {
  throw unprocessableEntity("client_driver.driver.not_approved");
}
```

No método `ClientDriverService.update`:
```java
if (request.status().isPresent()) {
  RelationshipStatus status = request.status().get();
  if (status == null) {
    throw badRequest("client_driver.status.required");
  }
  if (status == RelationshipStatus.ACTIVE
      && link.getDriver().getApprovalStatus() != DriverApprovalStatus.APPROVED) {
    throw unprocessableEntity("client_driver.driver.not_approved");
  }
  link.setStatus(status);
}
```

Chaves de internacionalização:
- `messages.properties`: `client_driver.driver.not_approved=Driver is not approved to receive or accept proposals.`
- `messages_pt_BR.properties`: `client_driver.driver.not_approved=Motorista não está aprovado para receber ou aceitar propostas.`

### D5 — Autorização Rígida de Administrador

Os endpoints administrativos de análise utilizam `@PreAuthorize("hasRole('ADMIN') or hasAuthority('approve_driver')")`:
- `GET /api/drivers/pending`
- `POST /api/drivers/{token}/approve`
- `POST /api/drivers/{token}/reject`

Chamadas originadas por motoristas (`ROLE_DRIVER`) ou clientes (`ROLE_CLIENT`) retornam `HTTP 403 Forbidden`. Chamadas anônimas retornam `HTTP 401 Unauthorized`.

---

## Risks / Trade-offs

- **Falha no Envio de E-mail:** O envio de e-mail através do `MailService` pode sofrer instabilidade temporária no servidor SMTP. O `MailService` já possui tratamento com `try/catch` registrando logs de erro sem abortar a transação do banco de dados, assegurando que a aprovação/rejeição do motorista permaneça consistente e registrada.
- **Paginação de Pendentes:** A query utiliza `JOIN FETCH driver.user` e `COUNT` otimizado para evitar problemas de N+1 ao transformar o resultado em `DriverResponseDTO`.
