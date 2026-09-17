---
description: OpenSpec configuration and system rules for Antigravity, aligned with constitution.md and commit restrictions.
alwaysApply: true
---

# Antigravity OpenSpec Configuration & Rules

This file outlines the workspace-specific rules, coding standards, and execution constraints for the **Antigravity** AI assistant in this project.

---

## ⚠️ CRITICAL EXECUTION CONSTRAINT
*   **NO AUTOMATIC COMMITS:** You are strictly **forbidden** from running `git commit`, `git push`, or any other commands that persist commits to the Git history or remote repository without explicit user approval. You must always propose modifications and allow the user to review and execute commits manually.

---

## 📋 General Workflow & Rules

1.  **Read the Constitution First:** At the start of every session, you must read the [constitution.md](file:///home/matheus/Documentos/Projetos/Vanep/vanep-api-java/constitution.md) file at the root of the project to ensure you follow all non-negotiable standards.
2.  **No Code Duplication (DRY):** Always search the repository for existing classes, migrations, helper methods, or test configurations before introducing new ones. Reuse and refactor where possible.

---

## 🛠️ Architecture & Tech Stack

*   **Stack:** Java 25, Spring Boot 4, Maven, JPA/Flyway, PostgreSQL (H2 in tests), Spring Security + OAuth2 Authorization Server, Spotless (Google Java Format).
*   **Feature-Based Organization:** Structure your code package-by-feature under `br.com.vanep.<feature>`. Every feature package must contain subpackages for `controller`, `dto`, `enums`, `mapper`, `model`, `repository`, and `service` as needed.
*   **Suffix Conventions:** Name files according to their role with appropriate architectural suffixes (e.g., `ClientController`, `ClientService`, `ClientRepository`, `ClientDTO`, `ClientMapper`, `ClientModel`). Never use generic names like `Handler`, `Manager`, `Util`, or `Data`.
*   **Business Logic Layer:** Place all business logic inside `@Service` classes. Controllers must remain thin, handling only request orchestration and routing. Keep framework annotations out of the pure domain logic.

---

## 🌐 API Design

*   **Bean Validation:** Validate input using Bean Validation constraints on dedicated request DTOs with `@Valid` in the controllers. Never validate inside the business logic.
*   **DTO Binding:** Never bind request bodies directly to JPA models. Map DTOs to models explicitly.
*   **Response DTOs:** Never return raw JPA model graphs to clients. Shape responses using explicit response DTOs.
*   **Opaque Identifiers:** Expose and accept public resource identifiers as opaque `token` strings (25 characters) instead of internal numeric/sequential IDs.
*   **Endpoints:** Prefix REST controllers with `/api` and put them inside the `*.controller` packages.

---

## 💾 Persistence & Databases

*   **Applied Migrations:** Never modify a Flyway migration file that has already been applied (this changes the checksum and breaks startup). To change schema, write a new versioned migration file under `src/main/resources/db/migration/`.
*   **No N+1 Queries:** Avoid N+1 query problems. Use fetch joins, `@EntityGraph`, or batch fetching instead of lazily iterating associations.
*   **Soft Delete:** Implement soft delete on removable models using Hibernate's `@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)` and a nullable `deleted_at` column in migrations. Do not map `deleted_at` as a Java field unless explicitly needed. Ensure unique indexes on soft-deletable columns use partial indexes with `WHERE deleted_at IS NULL`.
*   **Physical Delete:** Do not use `repository.deleteAll()` or standard JPQL/SQL deletes in tests for soft-deletable entities. Perform test cleanups using the native SQL script `clean.sql`.

---

## 🔒 Security

*   **Authorization Rules:** Always secure new routes and endpoints in `SecurityConfig` (or via method security). Authorization is part of the feature implementation.

---

## 🧪 Testing

*   **Test-First (TDD):** Write unit tests (Mockito) for services/validators and slice tests (`MockMvc` + Security) for HTTP endpoints before writing implementation code.
*   **JaCoCo Coverage:** Run `./mvnw verify` or `make test-coverage` to verify the code passes the minimum coverage threshold prior to opening a PR.
*   **Test Database:** Use in-memory H2 database for testing. Reuse configurations in `src/test/resources`.

---

## 📝 Conventions & Language

*   **Spotless Formatting:** Always format Java code using Spotless before creating PRs. Use `make lint-fix` or `./mvnw spotless:apply`.
*   **Error Messages:** Write user-facing validation and error messages in Portuguese (pt-BR). Use English keys resolved through `MessageSource` properties (`messages_pt_BR.properties`).
*   **Code Identifiers & Comments:** Write all class, variable, method, package names, logs, comments, and internal constants in **English**.
*   **Commits & PRs:** Write commit messages and PR descriptions in Portuguese (pt-BR). Link PRs to their respective issues.
