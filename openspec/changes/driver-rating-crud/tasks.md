## 0. Preparation

- [ ] 0.1 Confirm active branch `feat-(N-35)/CRUD-of-driver-ratings` from `main`
- [ ] 0.2 Review OpenSpec documents (`proposal.md`, `design.md`, `tasks.md`) and get approval

---

## 1. PR Plan Table

| Phase | Contents | Depends on | Parallel with |
| :--- | :--- | :--- | :--- |
| **Phase 1** | Foundation (Migration V16, JPA entity, repository queries, SecurityEvaluator update, permission enum additions) | — | — |
| **Phase 2** | REST API (DTOs, mapper, service logic with rating recalculation, controller endpoints) | Phase 1 | — |
| **Phase 3** | Seed & Tests (DataSeeder extension, clean.sql update check, DriverRatingServiceTest, DriverRatingControllerTest) | Phase 2 | — |

---

## 2. Dependency Graph & Layer Assignment

```
[Migration V16 & PermissionEnum & DriverRatingModel]
                       │
                       ▼
            [DriverRatingRepository]
                       │
                       ▼
[SecurityEvaluator (isDriverRatingOwner) & DTOs]
                       │
                       ▼
      [DriverRatingService (Recalculate AVG)]
                       │
                       ▼
            [DriverRatingController]
                       │
                       ▼
       [DataSeeder & MockMvc Tests]
```

---

## 3. Checklist of Tasks

### Phase 1 — Foundation
- [x] 1.1 Create Flyway migration `V16__create_driver_rating_table.sql`.
- [x] 1.2 Create entity `DriverRatingModel.java` in `br.com.vanep.driverrating.model`.
- [x] 1.3 Add permissions (`LIST_DRIVER_RATINGS`, `SHOW_DRIVER_RATING`, `CREATE_DRIVER_RATING`, `UPDATE_DRIVER_RATING`, `DELETE_DRIVER_RATING`, `RESTORE_DRIVER_RATING`) to `PermissionEnum.java`.
- [x] 1.4 Create `DriverRatingRepository.java` with queries for `findByToken`, `existsByDriverIdAndClientId`, `calculateAverageRatingForDriver`, `findClientUserTokenByRatingToken`, `@Modifying restoreByToken`, `existsDeletedByToken`.
- [x] 1.5 Add `isDriverRatingOwner(String token, Authentication auth)` method to `SecurityEvaluator.java`.
- [x] 1.6 Validate using `./mvnw verify`.

### Phase 2 — REST API
- [x] 2.1 Create DTOs: `DriverRatingCreateRequestDTO`, `DriverRatingUpdateRequestDTO`, `DriverRatingResponseDTO` in `br.com.vanep.driverrating.dto`.
- [x] 2.2 Create `DriverRatingMapper.java` in `br.com.vanep.driverrating.mapper`.
- [x] 2.3 Create `DriverRatingService.java` with methods `create`, `findAll`, `findByToken`, `update`, `delete`, `restore` and private `recalculateDriverAverage`.
- [x] 2.4 Create `DriverRatingController.java` with endpoints under `/api/driver-ratings` and `@PreAuthorize` security.
- [x] 2.5 Add localized message keys to `messages.properties` and `messages_pt_BR.properties`.
- [x] 2.6 Validate using `./mvnw verify`.

### Phase 3 — Seed & Tests
- [x] 3.1 Verify `clean.sql` has `delete from driver_rating;` before `driver` and `client`.
- [x] 3.2 Add seed data in `DataSeeder.java` for mock ratings.
- [x] 3.3 Create `DriverRatingServiceTest.java` (unit tests covering create, list, update, delete, restore, average recalculation, 400/404/409 exceptions).
- [x] 3.4 Create `DriverRatingControllerTest.java` (MockMvc integration tests covering status codes and `@sec.isDriverRatingOwner` security).
- [x] 3.5 Check formatting and tests using `./mvnw spotless:check` and `./mvnw verify`.
