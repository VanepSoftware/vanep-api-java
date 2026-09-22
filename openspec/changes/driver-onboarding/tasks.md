## 0. Preparation

- [ ] 0.1 Confirm active branch `feat/driver-onboarding` from `main`
- [ ] 0.2 Review OpenSpec documents (`proposal.md`, `design.md`, `specs/driver-onboarding/spec.md`, `tasks.md`) and get approval

---

## 1. PR Plan Table

| Phase | Contents | Depends on | Parallel with |
| :--- | :--- | :--- | :--- |
| **Phase 1** | Foundation (Enums `DriverApprovalStatus` & `DocumentTypeEnum`, Migration `V46`, `DriverModel` update, `PermissionEnum`) | — | — |
| **Phase 2** | OAuth Driver Initialization (`OAuthAccountService` creation of `DriverModel`, unit tests) | Phase 1 | — |
| **Phase 3** | Driver Onboarding Query & Submission (`DriverOnboardingService`, `DriverOnboardingController`, DTOs, messages, unit & slice tests) | Phase 2 | — |
| **Phase 4** | Administrative Review (`approve` & `reject` endpoints, `DriverRejectionRequestDTO`, seeder update, unit & slice tests) | Phase 3 | — |

---

## 2. Dependency Graph & Layer Assignment

```
[Phase 1: Foundation]
  - DriverApprovalStatus (UNDER_REVIEW)
  - DocumentTypeEnum (VEHICLE_INSPECTION, MUNICIPAL_AUTHORIZATION)
  - PermissionEnum (approve_driver)
  - Migration V46 (driver columns: submitted_at, rejection_reason, reviewed_at, reviewed_by)
  - DriverModel (JPA mapping)
       │
       ▼
[Phase 2: OAuth Driver Initialization]
  - OAuthAccountService (instantiate DriverModel on DRIVER signup)
  - OAuthAccountServiceTest
       │
       ▼
[Phase 3: Driver Onboarding Query & Submission Engine]
  - DTOs (DriverOnboardingStatusResponseDTO, DriverOnboardingStepDTO, DriverOnboardingDocumentsStepDTO)
  - MessageSource keys (messages.properties & messages_pt_BR.properties)
  - DriverOnboardingService (getOnboardingStatus, submitOnboarding)
  - DriverOnboardingController (GET /me/onboarding, POST /me/submit-onboarding)
  - DriverOnboardingServiceTest & DriverOnboardingControllerTest
       │
       ▼
[Phase 4: Administrative Review Endpoints]
  - DriverRejectionRequestDTO
  - DriverOnboardingService.approve / reject
  - DriverOnboardingController (POST /{token}/approve, POST /{token}/reject)
  - DataSeeder update for approve_driver permission
  - Unit and slice tests covering review flow
```

---

## 3. Checklist of Tasks

### Phase 1 — Foundation
- [x] 1.1 Add `UNDER_REVIEW` to `DriverApprovalStatus.java`.
- [x] 1.2 Add `VEHICLE_INSPECTION` and `MUNICIPAL_AUTHORIZATION` to `DocumentTypeEnum.java`.
- [x] 1.3 Add `APPROVE_DRIVER("approve_driver")` to `PermissionEnum.java`.
- [x] 1.4 Create Flyway migration `V46__add_driver_onboarding_and_review_columns.sql`.
- [x] 1.5 Update `DriverModel.java` with `submittedAt`, `rejectionReason`, `reviewedAt` and `reviewedBy` (`UserModel`).
- [x] 1.6 Verify and adjust any existing seeders/tests if necessary.
- [x] 1.7 Validate using `./mvnw test-compile` and `./mvnw spotless:check`.
- [ ] 1.8 Open PR 1: `feat(driver): phase 1 — onboarding foundation & schema`.

### Phase 2 — OAuth Driver Initialization
- [ ] 2.1 Update `OAuthAccountService.java` to instantiate and save a `DriverModel` (status `PENDING`, `basePrice = 0.00`) when `form.getType() == UserType.DRIVER`.
- [ ] 2.2 Create unit tests in `OAuthAccountServiceTest.java` verifying that completing registration as `DRIVER` creates both `UserModel` and `DriverModel`.
- [ ] 2.3 Validate using `./mvnw verify`.
- [ ] 2.4 Open PR 2: `feat(auth): phase 2 — initialize driver profile in oauth flow`.

### Phase 3 — Driver Onboarding Query & Submission Engine
- [ ] 3.1 Create response DTOs: `DriverOnboardingStatusResponseDTO`, `DriverOnboardingStepDTO`, `DriverOnboardingDocumentsStepDTO`, `DriverDocumentSummaryDTO` in `br.com.vanep.driver.dto`.
- [ ] 3.2 Add MessageSource keys for onboarding validation errors in `src/main/resources/messages.properties` and `messages_pt_BR.properties`.
- [ ] 3.3 Create `DriverOnboardingService.java` in `br.com.vanep.driver.service` with:
  - `getOnboardingStatus(String callerUid)` calculating checklist across `DriverModel`, `VehicleModel`, `DriverCnhModel`, and `DriverDocumentModel`.
  - `submitOnboarding(String callerUid)` validating all 4 dimensions and transitioning to `UNDER_REVIEW`.
- [ ] 3.4 Create `DriverOnboardingController.java` in `br.com.vanep.driver.controller` exposing:
  - `GET /api/drivers/me/onboarding`
  - `POST /api/drivers/me/submit-onboarding`
- [ ] 3.5 Write unit tests `DriverOnboardingServiceTest.java` covering all validation branches (missing vehicle, missing CNH, expired CNH, missing documents, incomplete profile, successful submission).
- [ ] 3.6 Write slice tests `DriverOnboardingControllerTest.java` (MockMvc) verifying security rules, HTTP 200, HTTP 422 on validation failures.
- [ ] 3.7 Validate formatting and test coverage using `./mvnw spotless:check` and `./mvnw verify`.
- [ ] 3.8 Open PR 3: `feat(driver): phase 3 — onboarding status and submission endpoints`.

### Phase 4 — Administrative Review Endpoints
- [ ] 4.1 Create `DriverRejectionRequestDTO.java` with validation (`@NotBlank`, `@Size(max = 255)`).
- [ ] 4.2 Add admin review methods to `DriverOnboardingService.java`:
  - `approve(String driverToken, String adminUid)`: transitions `UNDER_REVIEW` -> `APPROVED`, activates driver, records reviewer.
  - `reject(String driverToken, DriverRejectionRequestDTO request, String adminUid)`: transitions `UNDER_REVIEW` -> `REJECTED`, records reason and reviewer.
- [ ] 4.3 Add admin endpoints to `DriverOnboardingController.java`:
  - `POST /api/drivers/{token}/approve` with `@PreAuthorize("hasAuthority('approve_driver')")`.
  - `POST /api/drivers/{token}/reject` with `@PreAuthorize("hasAuthority('approve_driver')")`.
- [ ] 4.4 Update `DataSeeder.java` to grant `approve_driver` permission to `ROLE_ADMIN`.
- [ ] 4.5 Extend `DriverOnboardingServiceTest.java` with unit tests for approval, rejection, and invalid state transitions.
- [ ] 4.6 Extend `DriverOnboardingControllerTest.java` with slice tests for admin review endpoints (verifying 403 for unauthorized users, 200 on approval/rejection, 400 on blank reason).
- [ ] 4.7 Validate entire test suite and formatting using `./mvnw spotless:check` and `./mvnw verify`.
- [ ] 4.8 Open PR 4: `feat(driver): phase 4 — administrative onboarding review`.
