## 0. Preparation

- [ ] 0.1 Confirm active branch `feat-(N-29)/CRUD-of-drivers` from `main`
- [ ] 0.2 Review OpenSpec documents (`proposal.md`, `design.md`, `tasks.md`) and get approval

---

## 1. PR Plan Table

| Phase | Contents | Depends on | Parallel with |
| :--- | :--- | :--- | :--- |
| **Phase 1** | Foundation (JPA model update, repository queries, permission enum additions) | — | — |
| **Phase 2** | REST API (DTOs, mapper, security service, service logic, controller endpoints) | Phase 1 | — |
| **Phase 3** | Seed & Tests (DataSeeder extension, clean.sql update, DriverServiceTest, DriverControllerTest) | Phase 2 | — |

---

## 2. Dependency Graph & Layer Assignment

```
[PermissionEnum & DriverModel (Entity)] 
               │
               ▼
       [DriverRepository]
               │
               ▼
   [DriverSecurityService & DTOs]
               │
               ▼
        [DriverService]
               │
               ▼
       [DriverController]
               │
               ▼
[DataSeeder & Integration Tests (MockMvc)]
```

---

## 3. Checklist of Tasks

### Phase 1 — Foundation
- [x] 1.1 Map the missing fields (`bio`, `workStartTime`, `workEndTime`, `workDays`, `waitToleranceMinutes`, `serviceAreas`) inside `DriverModel.java`.
- [x] 1.2 Add `LIST_DRIVERS`, `SHOW_DRIVER`, `UPDATE_DRIVER`, `DELETE_DRIVER`, `RESTORE_DRIVER` to `PermissionEnum.java`.
- [x] 1.3 Add custom queries to `DriverRepository.java` (`findUserTokenByDriverToken`, `@Modifying restoreByToken`, `existsDeletedByToken`).
- [x] 1.4 Validate using `./mvnw verify` (clean compilation, all existing tests pass).
- [ ] 1.5 Open PR1: `feat(driver): phase 1 — foundation` -> `main`.

### Phase 2 — REST API
- [ ] 2.1 Create DTOs: `DriverResponseDTO` and `DriverUpdateRequestDTO` in `br.com.vanep.driver.dto`. Include validation annotations on the request DTO.
- [ ] 2.2 Create `DriverMapper` in `br.com.vanep.driver.mapper` to handle entity-DTO mapping.
- [ ] 2.3 Create `DriverSecurityService` in `br.com.vanep.driver.security` for ownership check.
- [ ] 2.4 Create `DriverService` in `br.com.vanep.driver.service` (methods: `findAll(Pageable)`, `findByToken(String)`, `update(String, DriverUpdateRequestDTO)`, `delete(String)`, `restore(String)`).
- [ ] 2.5 Create `DriverController` in `br.com.vanep.driver.controller` with routes `/api/drivers/**` and security annotations.
- [ ] 2.6 Validate using `./mvnw verify`.
- [ ] 2.7 Open PR2: `feat(driver): phase 2 — REST API` -> `main`.

### Phase 3 — Seed & Tests
- [ ] 3.1 Extend `src/test/resources/db/clean.sql` with `delete from driver;` (make sure it's deleted after dependent/vehicle but before users).
- [ ] 3.2 Add mock drivers to `DataSeeder.java` to allow testing list pagination and different status filters.
- [ ] 3.3 Create `DriverServiceTest` (unit test covering edge cases: not found, updating restricted fields, deleting and restoring).
- [ ] 3.4 Create `DriverControllerTest` (MockMvc integration tests covering all HTTP endpoints, roles, and ownership authorization).
- [ ] 3.5 Check formatting and tests using `./mvnw spotless:check` and `./mvnw verify`.
- [ ] 3.6 Open PR3: `feat(driver): phase 3 — seed and tests` -> `main`.
