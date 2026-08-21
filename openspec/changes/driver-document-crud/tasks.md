## 0. Preparation

- [ ] 0.1 Confirm active branch `feat(N-27)/create-crud-of-driver-document` from `main`
- [ ] 0.2 Review OpenSpec documents (`proposal.md`, `design.md`, `tasks.md`) and get approval

---

## 1. PR Plan Table

| Phase | Contents | Depends on | Parallel with |
| :--- | :--- | :--- | :--- |
| **Phase 1** | Foundation (Migration V19, enums, JPA entity, repository queries, SecurityEvaluator update, permission enum additions) | — | — |
| **Phase 2** | REST API (DTOs, mapper, service logic, controller endpoints, i18n messages) | Phase 1 | — |
| **Phase 3** | Seed & Tests (DataSeeder extension, clean.sql check, DriverDocumentServiceTest, DriverDocumentControllerTest) | Phase 2 | — |

---

## 2. Dependency Graph & Layer Assignment

```
[Migration V19 & DocumentTypeEnum & DocumentStatusEnum & PermissionEnum & DriverDocumentModel]
                                           │
                                           ▼
                               [DriverDocumentRepository]
                                           │
                                           ▼
                     [SecurityEvaluator (isDriverDocumentOwner) & DTOs]
                                           │
                                           ▼
                                 [DriverDocumentService]
                                           │
                                           ▼
                                [DriverDocumentController]
                                           │
                                           ▼
                              [DataSeeder & MockMvc Tests]
```

---

## 3. Checklist of Tasks

### Phase 1 — Foundation
- [x] 1.1 Create Flyway migration `V19__create_driver_document_table.sql`.
- [x] 1.2 Create enums `DocumentTypeEnum.java` and `DocumentStatusEnum.java` in `br.com.vanep.driverdocument.enums`.
- [x] 1.3 Create entity `DriverDocumentModel.java` in `br.com.vanep.driverdocument.model`.
- [x] 1.4 Add permissions (`LIST_DRIVER_DOCUMENTS`, `SHOW_DRIVER_DOCUMENT`, `CREATE_DRIVER_DOCUMENT`, `UPDATE_DRIVER_DOCUMENT`, `DELETE_DRIVER_DOCUMENT`, `RESTORE_DRIVER_DOCUMENT`) to `PermissionEnum.java`.
- [x] 1.5 Create `DriverDocumentRepository.java` with queries for `findByToken`, `findDriverUserTokenByDocumentToken`, `@Modifying restoreByToken`, `existsDeletedByToken`.
- [x] 1.6 Add `isDriverDocumentOwner(String token, Authentication auth)` method to `SecurityEvaluator.java`.
- [x] 1.7 Validate using `./mvnw verify`.

### Phase 2 — REST API
- [x] 2.1 Create DTOs: `DriverDocumentRequestDTO`, `DriverDocumentStatusUpdateRequestDTO`, `DriverDocumentResponseDTO` in `br.com.vanep.driverdocument.dto`.
- [x] 2.2 Create `DriverDocumentMapper.java` in `br.com.vanep.driverdocument.mapper`.
- [x] 2.3 Create `DriverDocumentService.java` with methods `create`, `findAll`, `findByToken`, `update`, `delete`, `restore`.
- [x] 2.4 Create `DriverDocumentController.java` with endpoints under `/api/driver-documents` and `@PreAuthorize` security.
- [x] 2.5 Add localized message keys to `messages.properties` and `messages_pt_BR.properties`.
- [x] 2.6 Validate using `./mvnw verify`.

### Phase 3 — Seed & Tests
- [x] 3.1 Verify `clean.sql` has `delete from driver_document;` before `driver`.
- [x] 3.2 Add seed data in `DataSeeder.java` for mock driver documents.
- [x] 3.3 Create `DriverDocumentServiceTest.java` (unit tests covering create, list, update, delete, restore, 400/404/409 exceptions).
- [x] 3.4 Create `DriverDocumentControllerTest.java` (MockMvc integration tests covering status codes and `@sec.isDriverDocumentOwner` security).
- [x] 3.5 Check formatting and tests using `./mvnw spotless:check` (`make lint`) and `./mvnw verify` (`make test-coverage`).
