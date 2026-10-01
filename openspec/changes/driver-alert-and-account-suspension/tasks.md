## 0. Preparation

- [ ] 0.1 Confirm the active branch, current schema migration version, and the owner of issue #47 before implementation.
- [ ] 0.2 Resolve the required document types, signed-URL TTL, accepted MIME types/size limits, and FCM retry/outbox decision from the open questions.
- [ ] 0.3 Review the OpenSpec proposal, design, and specs; obtain approval before code generation.

## 1. PR Plan

| Phase | Contents | Depends on | Parallel with |
| :--- | :--- | :--- | :--- |
| 1 | Compliance data model, CNH review fields, alert-event persistence, pure policy tests | — | — |
| 2 | Compliance query/service, review transitions, authenticated compliance API, search integration | Phase 1 | — |
| 3 | Private storage port/configuration and signed URL API with fake-adapter tests | Phase 1 | — |
| 4 | Scheduler, notification adapter/outbox decision, milestone/idempotency tests | Phases 1–2 | Phase 3 after its API contract stabilizes |
| 5 | Proposal-flow integration and end-to-end regression tests when the proposal module exists | Phase 2 and proposal module | — |

## 2. Dependency Graph and Layer Assignment

```
[Flyway migrations + enums + JPA models + repositories]
                         |
                         +-----------------------+
                         |                       |
                         v                       v
        [Pure compliance policy]       [Private storage port/config]
                         |                       |
                         v                       v
 [Compliance/review services + API]  [Signed upload/download API]
                         |
                         v
       [Search + future proposal eligibility]
                         |
                         v
       [Daily scheduler + notification adapter]
```

## 3. Phase 1 — Compliance Persistence and Policy Foundation

- [ ] 3.1 Write failing unit tests for CNH/document review state, expiration evaluation, and compliance reasons.
- [ ] 3.2 Add a new Flyway migration for CNH `status`, review metadata, rejection reason, private object key, and alert audit records; add indexes for active status/expiration scans without changing applied migrations.
- [ ] 3.3 Extend CNH model, DTOs, mapper, repository, and status enum reuse so CNH has the same review lifecycle as driver documents.
- [ ] 3.4 Add repository projections/queries that load active CNH and documents without N+1 queries and support the daily expiration scan.
- [ ] 3.5 Implement the framework-free document-compliance policy and its result/reason types, including separate document and plan reason categories.
- [ ] 3.6 Add unit and repository tests for migrations/model persistence, pending/rejected/expired outcomes, and the D+1 expiration boundary.
- [ ] 3.7 Run `make lint` and `make test-coverage`; keep the phase within the PR size limit.

## 4. Phase 2 — Review Lifecycle, Compliance API, and Search Gate

- [ ] 4.1 Write failing service and controller tests for CNH replacement, required rejection reason, approval restoration, compliance authorization, and search exclusion.
- [ ] 4.2 Implement review-transition services that set replacements to `PENDING`, clear stale decisions, require rejection reasons, and preserve visible reasons for owners.
- [ ] 4.3 Implement the compliance query service using the shared policy and batch-loaded resource state.
- [ ] 4.4 Add explicit authorization, request/response DTOs, i18n messages, and endpoint(s) for own and Admin-selected compliance status.
- [ ] 4.5 Apply the shared compliance predicate to the final driver-search filter while retaining ranking and pagination behavior.
- [ ] 4.6 Add regression tests proving an approved renewal restores search eligibility and an existing contract is untouched by document irregularity.
- [ ] 4.7 Run `make lint` and `make test-coverage`; keep the phase within the PR size limit.

## 5. Phase 3 — Private S3-Compatible File Storage

- [ ] 5.1 Write failing tests using a fake storage adapter for upload scope, ownership checks, signed download authorization, and no external network access.
- [ ] 5.2 Define the private document-storage port, environment-backed configuration properties, and a S3-compatible adapter; add dependencies only after confirming the project's existing AWS/S3 client pattern.
- [ ] 5.3 Implement signed upload URL issuance with server-generated object keys scoped to the authorized driver and document kind.
- [ ] 5.4 Replace persisted public file references with validated private object keys and implement signed download URL issuance behind resource ownership/Admin authorization.
- [ ] 5.5 Update request/response contracts so permanent public URLs are never returned, and add i18n validation messages for invalid upload completion.
- [ ] 5.6 Add fake-adapter unit tests and MockMvc security tests; verify tests make no MinIO, AWS, or other external call.
- [ ] 5.7 Run `make lint` and `make test-coverage`; keep the phase within the PR size limit.

## 6. Phase 4 — Daily Alerts and Idempotency

- [ ] 6.1 Write failing tests with an injected `Clock` for D-60, D-30, D0, late recovery after a missed scan, and repeated/concurrent execution.
- [ ] 6.2 Implement the scheduled daily scan in the `America/Sao_Paulo` business date and select eligible active CNH/documents efficiently.
- [ ] 6.3 Persist a unique alert event per resource and milestone transactionally, update compatible `notified_at` metadata, and handle duplicate-key races as idempotent no-ops.
- [ ] 6.4 Define and implement the notification port plus the approved FCM adapter or transactional outbox/retry strategy; provide a fake adapter for all tests.
- [ ] 6.5 Add operational logs/metrics for scan count, emitted alerts, duplicate suppression, and delivery failures without exposing document data.
- [ ] 6.6 Run `make lint` and `make test-coverage`; keep the phase within the PR size limit.

## 7. Phase 5 — Future Proposal-Flow Gate and Release Verification

- [ ] 7.1 When the proposal-creation module is available, add a failing integration test that rejects a document-irregular driver with the document-specific reason and keeps plan-specific reasons distinct.
- [ ] 7.2 Integrate the shared compliance predicate before new proposal creation without changing active contracts.
- [ ] 7.3 Configure private bucket access and environment variables in staging, confirm no public object access, and execute a manual signed-URL smoke test with non-production data.
- [ ] 7.4 Verify migration deployment, job schedule, alert observability, restore-after-approval behavior, JaCoCo threshold, and Spotless before each phase PR.
