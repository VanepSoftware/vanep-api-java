## 0. Preparation

- [ ] 0.1 Confirm active branch `feat/admin-driver-approval` from `main`
- [ ] 0.2 Review OpenSpec documents (`proposal.md`, `design.md`, `specs/admin-driver-approval/spec.md`, `tasks.md`) and get approval

---

## 1. PR Plan Table

| Phase | Contents | Depends on | Parallel with |
| :--- | :--- | :--- | :--- |
| **Phase 1** | OpenSpec Specification (proposal, design, specs, tasks) | — | — |
| **Phase 2** | Proposal Blocking Enforcement under RN02 (`ClientDriverService` validation, messages, unit & slice tests) | Phase 1 | Phase 3 |
| **Phase 3** | Driver Notification Engine (`DriverNotificationService`, Thymeleaf templates `driver-approved.html` and `driver-rejected.html`, unit tests) | Phase 1 | Phase 2 |
| **Phase 4** | Admin Review & Pending Listing Orchestration (`DriverRepository.findByApprovalStatusWithUser`, `GET /api/drivers/pending`, notification dispatch in `approve`/`reject`, unit & slice tests) | Phase 2, Phase 3 | — |

---

## 2. Dependency Graph & Layer Assignment

```
[Phase 1: OpenSpec Specification]
  - proposal.md, design.md, spec.md, tasks.md
       │
       ├─────────────────────────────────────────┐
       ▼                                         ▼
[Phase 2: RN02 Proposal Blocking]        [Phase 3: Driver Notification Engine]
  - ClientDriverService RN02 check         - driver-approved.html & driver-rejected.html
  - messages & messages_pt_BR              - DriverNotificationService
  - ClientDriverServiceTest &              - DriverNotificationServiceTest
    ClientDriverControllerTest
       │                                         │
       └────────────────────┬────────────────────┘
                            ▼
[Phase 4: Admin Review & Pending Listing Orchestration]
  - DriverRepository.findByApprovalStatusWithUser
  - DriverOnboardingService.findPendingDrivers
  - DriverOnboardingService.approve/reject with DriverNotificationService
  - DriverOnboardingController (GET /api/drivers/pending)
  - DriverOnboardingServiceTest & DriverOnboardingControllerTest
  - make lint & make test-coverage validation
```

---

## 3. Checklist of Tasks

### Phase 1 — OpenSpec Specification
- [x] 1.1 Create `proposal.md`, `design.md`, `specs/admin-driver-approval/spec.md`, and `tasks.md`.
- [ ] 1.2 Open PR 1: `docs(openspec): proposta de aprovacao de documentos do motorista pelo admin (UC02, RN02)`.

### Phase 2 — RN02 Proposal Blocking Enforcement
- [x] 2.1 Add internationalized error messages in `src/main/resources/messages.properties` and `src/main/resources/messages_pt_BR.properties` for `client_driver.driver.not_approved`.
- [x] 2.2 Add unit test in `ClientDriverServiceTest.java` verifying that attempting to create a link with an unapproved driver (`PENDING`, `UNDER_REVIEW`, `REJECTED`) throws `422 Unprocessable Entity`.
- [x] 2.3 Add unit test in `ClientDriverServiceTest.java` verifying that updating link status to `ACTIVE` fails if driver is not approved.
- [x] 2.4 Update `ClientDriverService.java` to enforce driver `approvalStatus == APPROVED` on `create` and `update` (transitions to `ACTIVE`).
- [x] 2.5 Add MockMvc slice test in `ClientDriverControllerTest.java` asserting `HTTP 422` when creating link for an unapproved driver.
- [x] 2.6 Validate with `./mvnw spotless:check` and `./mvnw test`.
- [ ] 2.7 Open PR 2: `feat(client-driver): phase 2 — bloqueio de propostas para motoristas nao homologados (RN02)`.

### Phase 3 — Driver Notification Engine
- [ ] 3.1 Create email template `src/main/resources/templates/email/driver-approved.html` with congratulations and notification of active status.
- [ ] 3.2 Create email template `src/main/resources/templates/email/driver-rejected.html` with feedback and display of `rejectionReason`.
- [ ] 3.3 Create `DriverNotificationService.java` in `br.com.vanep.driver.service` with `notifyApproval(DriverModel driver)` and `notifyRejection(DriverModel driver, String reason)`.
- [ ] 3.4 Write unit tests in `DriverNotificationServiceTest.java` verifying parameters and calls to `MailService`.
- [ ] 3.5 Validate with `./mvnw spotless:check` and `./mvnw test`.
- [ ] 3.6 Open PR 3: `feat(driver): phase 3 — servico de notificacao de aprovacao e rejeicao de motorista`.

### Phase 4 — Admin Review & Pending Listing Orchestration
- [ ] 4.1 Add `findByApprovalStatusWithUser(DriverApprovalStatus status, Pageable pageable)` to `DriverRepository.java`.
- [ ] 4.2 Add `findPendingDrivers(Pageable pageable)` to `DriverOnboardingService.java`.
- [ ] 4.3 Inject `DriverNotificationService` into `DriverOnboardingService.java` and dispatch notifications in `approve` and `reject`.
- [ ] 4.4 Add `GET /api/drivers/pending` in `DriverOnboardingController.java` with `@PreAuthorize("hasRole('ADMIN') or hasAuthority('approve_driver')")`.
- [ ] 4.5 Add unit tests in `DriverOnboardingServiceTest.java` covering `findPendingDrivers` and notification invocation in `approve` and `reject`.
- [ ] 4.6 Add slice tests in `DriverOnboardingControllerTest.java` verifying `GET /api/drivers/pending` (200 for admin with content, 403 for client/driver, 401 for unauthenticated).
- [ ] 4.7 Execute complete verification with `./mvnw spotless:check` and `./mvnw verify` ensuring JaCoCo coverage ≥ 75%.
- [ ] 4.8 Open PR 4: `feat(driver): phase 4 — listagem de pendentes e orquestracao de revisao administrativa`.
